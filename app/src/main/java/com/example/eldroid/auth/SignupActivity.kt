package com.example.eldroid.auth

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Patterns
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.eldroid.R
import com.example.eldroid.databinding.ActivitySignupBinding
import com.example.eldroid.dashboard.MainActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var presenter: SignupPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presenter = SignupPresenter(
            auth = FirebaseAuth.getInstance(),
            db   = FirebaseFirestore.getInstance(),
            rtdb = FirebaseDatabase.getInstance(
                "https://eldroid-3cb29-default-rtdb.asia-southeast1.firebasedatabase.app"
            ),
            view = this
        )

        setupPasswordWatcher()
        setupClickListeners()
        styleLoginPrompt()
    }

    private fun setupPasswordWatcher() {
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val pw = s.toString()
                setReq(binding.tvReqLength,    pw.length >= 8)
                setReq(binding.tvReqUppercase, pw.any { it.isUpperCase() })
                setReq(binding.tvReqLowercase, pw.any { it.isLowerCase() })
                setReq(binding.tvReqDigit,     pw.any { it.isDigit() })
                setReq(binding.tvReqSpecial,   pw.any { it in "!@#\$%^&*()_+-=[]{}|;':\",./<>?" })
            }
        })
    }

    private fun setReq(view: TextView, met: Boolean) {
        if (met) {
            view.setTextColor(ContextCompat.getColor(this, R.color.success))
            view.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(this, R.drawable.ic_check), null, null, null)
        } else {
            view.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            view.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(this, R.drawable.ic_dot), null, null, null)
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnSignup.setOnClickListener {
            clearErrors()
            presenter.register(
                firstName = binding.etFirstName.text.toString().trim(),
                lastName  = binding.etLastName.text.toString().trim(),
                contact   = binding.etContactNumber.text.toString().trim(),
                address   = binding.etAddress.text.toString().trim(),
                email     = binding.etEmail.text.toString().trim(),
                password  = binding.etPassword.text.toString(),
                confirmPw = binding.etConfirmPassword.text.toString()
            )
        }

        binding.tvGoToLogin.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    fun showFirstNameError(msg: String)       { binding.tilFirstName.error = msg }
    fun showLastNameError(msg: String)        { binding.tilLastName.error = msg }
    fun showContactError(msg: String)         { binding.tilContactNumber.error = msg }
    fun showAddressError(msg: String)         { binding.tilAddress.error = msg }
    fun showEmailError(msg: String)           { binding.tilEmail.error = msg }
    fun showPasswordError(msg: String)        { binding.tilPassword.error = msg }
    fun showConfirmPasswordError(msg: String) { binding.tilConfirmPassword.error = msg }

    fun clearErrors() {
        binding.tilFirstName.error       = null
        binding.tilLastName.error        = null
        binding.tilContactNumber.error   = null
        binding.tilAddress.error         = null
        binding.tilEmail.error           = null
        binding.tilPassword.error        = null
        binding.tilConfirmPassword.error = null
    }

    fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility      = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSignup.visibility        = if (isLoading) View.INVISIBLE else View.VISIBLE
        binding.btnSignup.isEnabled         = !isLoading
        binding.etFirstName.isEnabled       = !isLoading
        binding.etLastName.isEnabled        = !isLoading
        binding.etContactNumber.isEnabled   = !isLoading
        binding.etAddress.isEnabled         = !isLoading
        binding.etEmail.isEnabled           = !isLoading
        binding.etPassword.isEnabled        = !isLoading
        binding.etConfirmPassword.isEnabled = !isLoading
    }

    fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }

    fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun styleLoginPrompt() {
        val fullText  = getString(R.string.go_to_login)
        val spannable = SpannableString(fullText)
        val linkText  = getString(R.string.log_in_span)
        val start     = fullText.indexOf(linkText)
        if (start >= 0) {
            val end = start + linkText.length
            spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(StyleSpan(Typeface.BOLD),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvGoToLogin.text = spannable
    }
}

// Public signup always creates an Admin account.
// Security accounts are created by the admin from within the app.
class SignupPresenter(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val rtdb: FirebaseDatabase,
    private val view: SignupActivity
) {
    fun register(
        firstName: String,
        lastName: String,
        contact: String,
        address: String,
        email: String,
        password: String,
        confirmPw: String
    ) {
        if (!validateInputs(firstName, lastName, contact, address, email, password, confirmPw)) return

        val displayName = "$firstName $lastName"
        val role        = "Admin"   // public signup always creates Admin
        view.setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser!!.uid
                    auth.currentUser!!.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
                    ).addOnCompleteListener {
                        saveUser(uid, firstName, lastName, displayName, email, contact, address, role)
                    }
                } else {
                    view.setLoading(false)
                    val msg = when (task.exception) {
                        is FirebaseAuthUserCollisionException -> view.getString(R.string.error_email_in_use)
                        is FirebaseAuthWeakPasswordException  -> view.getString(R.string.error_password_weak)
                        else -> task.exception?.localizedMessage ?: view.getString(R.string.error_generic)
                    }
                    view.showError(msg)
                }
            }
    }

    fun saveUser(
        uid: String,
        firstName: String,
        lastName: String,
        displayName: String,
        email: String,
        contact: String,
        address: String,
        role: String
    ) {
        // 1 — Firestore: full profile
        val firestoreDoc = hashMapOf(
            "uid"           to uid,
            "firstName"     to firstName,
            "lastName"      to lastName,
            "displayName"   to displayName,
            "email"         to email,
            "contactNumber" to contact,
            "address"       to address,
            "role"          to role
        )
        db.collection("users").document(uid).set(firestoreDoc)

        // 2 — Realtime Database: /users/{role}/{uid}
        val rtdbUser = mapOf(
            "uid"         to uid,
            "displayName" to displayName,
            "firstName"   to firstName,
            "lastName"    to lastName,
            "email"       to email,
            "contact"     to contact,
            "address"     to address,
            "role"        to role,
            "createdAt"   to System.currentTimeMillis()
        )
        rtdb.getReference("users")
            .child(role.lowercase())   // /users/admin/{uid} or /users/security/{uid}
            .child(uid)
            .setValue(rtdbUser)
            .addOnSuccessListener {
                android.util.Log.d("RTDB", "$role user saved: $uid")
                view.setLoading(false)
                view.navigateToMain()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("RTDB", "Failed: ${e.message}")
                view.setLoading(false)
                view.navigateToMain()
            }
    }

    private fun validateInputs(
        firstName: String, lastName: String, contact: String,
        address: String, email: String, password: String, confirmPw: String
    ): Boolean {
        var valid = true

        when {
            firstName.isEmpty()   -> { view.showFirstNameError(view.getString(R.string.error_first_name_empty)); valid = false }
            firstName.length < 2  -> { view.showFirstNameError(view.getString(R.string.error_name_short)); valid = false }
        }
        when {
            lastName.isEmpty()    -> { view.showLastNameError(view.getString(R.string.error_last_name_empty)); valid = false }
            lastName.length < 2   -> { view.showLastNameError(view.getString(R.string.error_name_short)); valid = false }
        }
        when {
            contact.isEmpty()     -> { view.showContactError(view.getString(R.string.error_contact_empty)); valid = false }
            !contact.matches(Regex("^09[0-9]{9}$")) -> { view.showContactError(view.getString(R.string.error_contact_invalid)); valid = false }
        }
        if (address.isEmpty()) { view.showAddressError(view.getString(R.string.error_address_empty)); valid = false }
        when {
            email.isEmpty()       -> { view.showEmailError(view.getString(R.string.error_email_empty)); valid = false }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> { view.showEmailError(view.getString(R.string.error_email_invalid)); valid = false }
            email.any { it.isUpperCase() } -> { view.showEmailError(view.getString(R.string.error_email_uppercase)); valid = false }
        }
        when {
            password.isEmpty()    -> { view.showPasswordError(view.getString(R.string.error_password_empty)); valid = false }
            password.length < 8   -> { view.showPasswordError(view.getString(R.string.error_password_short)); valid = false }
            !password.any { it.isUpperCase() } -> { view.showPasswordError(view.getString(R.string.error_password_no_uppercase)); valid = false }
            !password.any { it.isLowerCase() } -> { view.showPasswordError(view.getString(R.string.error_password_no_lowercase)); valid = false }
            !password.any { it.isDigit() }     -> { view.showPasswordError(view.getString(R.string.error_password_no_digit)); valid = false }
            !password.any { it in "!@#\$%^&*()_+-=[]{}|;':\",./<>?" } -> { view.showPasswordError(view.getString(R.string.error_password_no_special)); valid = false }
        }
        when {
            confirmPw.isEmpty()   -> { view.showConfirmPasswordError(view.getString(R.string.error_confirm_password_empty)); valid = false }
            password != confirmPw -> { view.showConfirmPasswordError(view.getString(R.string.error_passwords_mismatch)); valid = false }
        }
        return valid
    }
}
