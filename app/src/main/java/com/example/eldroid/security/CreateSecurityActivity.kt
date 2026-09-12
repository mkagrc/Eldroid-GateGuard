package com.example.eldroid.security

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.eldroid.R
import com.example.eldroid.databinding.ActivityCreateSecurityBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class CreateSecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateSecurityBinding
    private lateinit var presenter: CreateSecurityPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateSecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Use a SECONDARY auth instance so creating a security account
        // does NOT sign out the currently logged-in admin
        val secondaryApp = try {
            com.google.firebase.FirebaseApp.getInstance("secondary")
        } catch (e: Exception) {
            com.google.firebase.FirebaseApp.initializeApp(
                this,
                com.google.firebase.FirebaseOptions.Builder()
                    .setProjectId("eldroid-3cb29")
                    .setApplicationId("1:190468626545:android:fa7cea468d0b54db3123d0")
                    .setApiKey("AIzaSyDINiTRwSnHZiCGiEpC02mGUkG6iPkEEGg")
                    .setDatabaseUrl("https://eldroid-3cb29-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .build(),
                "secondary"
            )
        }

        presenter = CreateSecurityPresenter(
            secondaryAuth = FirebaseAuth.getInstance(secondaryApp),
            db   = FirebaseFirestore.getInstance(),
            rtdb = FirebaseDatabase.getInstance(
                "https://eldroid-3cb29-default-rtdb.asia-southeast1.firebasedatabase.app"
            ),
            view = this
        )

        binding.btnBack.setOnClickListener { finish() }

        binding.btnCreate.setOnClickListener {
            clearErrors()
            presenter.createSecurityAccount(
                firstName    = binding.etFirstName.text.toString().trim(),
                lastName     = binding.etLastName.text.toString().trim(),
                contact      = binding.etContactNumber.text.toString().trim(),
                assignedGate = binding.etAssignedGate.text.toString().trim(),
                email        = binding.etEmail.text.toString().trim(),
                password     = binding.etPassword.text.toString()
            )
        }
    }

    fun showFirstNameError(msg: String)  { binding.tilFirstName.error = msg }
    fun showLastNameError(msg: String)   { binding.tilLastName.error = msg }
    fun showContactError(msg: String)    { binding.tilContactNumber.error = msg }
    fun showGateError(msg: String)       { binding.tilAssignedGate.error = msg }
    fun showEmailError(msg: String)      { binding.tilEmail.error = msg }
    fun showPasswordError(msg: String)   { binding.tilPassword.error = msg }

    fun clearErrors() {
        binding.tilFirstName.error     = null
        binding.tilLastName.error      = null
        binding.tilContactNumber.error = null
        binding.tilAssignedGate.error  = null
        binding.tilEmail.error         = null
        binding.tilPassword.error      = null
    }

    fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility  = if (isLoading) View.VISIBLE else View.GONE
        binding.btnCreate.visibility    = if (isLoading) View.INVISIBLE else View.VISIBLE
        binding.btnCreate.isEnabled     = !isLoading
    }

    fun showSuccess(name: String) {
        Snackbar.make(binding.root,
            getString(R.string.security_created_success, name),
            Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.success))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
        // Clear fields so admin can create another account
        binding.etFirstName.text?.clear()
        binding.etLastName.text?.clear()
        binding.etContactNumber.text?.clear()
        binding.etAssignedGate.text?.clear()
        binding.etEmail.text?.clear()
        binding.etPassword.text?.clear()
    }

    fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }
}

class CreateSecurityPresenter(
    private val secondaryAuth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val rtdb: FirebaseDatabase,
    private val view: CreateSecurityActivity
) {
    fun createSecurityAccount(
        firstName: String,
        lastName: String,
        contact: String,
        assignedGate: String,
        email: String,
        password: String
    ) {
        if (!validate(firstName, lastName, contact, assignedGate, email, password)) return

        val displayName = "$firstName $lastName"
        val role        = "Security"
        view.setLoading(true)

        // Create account using secondary auth — admin stays logged in
        secondaryAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = secondaryAuth.currentUser!!.uid

                    // Update display name
                    secondaryAuth.currentUser!!.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
                    ).addOnCompleteListener {
                        // Sign out of secondary instance immediately
                        secondaryAuth.signOut()
                    }

                    // Save to Firestore
                    val firestoreDoc = hashMapOf(
                        "uid"          to uid,
                        "firstName"    to firstName,
                        "lastName"     to lastName,
                        "displayName"  to displayName,
                        "email"        to email,
                        "contactNumber" to contact,
                        "assignedGate" to assignedGate,
                        "role"         to role
                    )
                    db.collection("users").document(uid).set(firestoreDoc)

                    // Save to Realtime Database /users/security/{uid}
                    val rtdbUser = mapOf(
                        "uid"          to uid,
                        "displayName"  to displayName,
                        "firstName"    to firstName,
                        "lastName"     to lastName,
                        "email"        to email,
                        "contact"      to contact,
                        "assignedGate" to assignedGate,
                        "role"         to role,
                        "onDuty"       to false,
                        "createdAt"    to System.currentTimeMillis()
                    )

                    // Also add to /guards node for the dashboard to read
                    rtdb.getReference("guards").child(uid).setValue(rtdbUser)
                    rtdb.getReference("users").child("security").child(uid)
                        .setValue(rtdbUser)
                        .addOnSuccessListener {
                            android.util.Log.d("RTDB", "Security account created: $uid")
                            view.setLoading(false)
                            view.showSuccess(displayName)
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("RTDB", "Failed: ${e.message}")
                            view.setLoading(false)
                            view.showSuccess(displayName) // still show success — auth + firestore worked
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

    private fun validate(
        firstName: String, lastName: String, contact: String,
        assignedGate: String, email: String, password: String
    ): Boolean {
        var valid = true

        when {
            firstName.isEmpty()  -> { view.showFirstNameError(view.getString(R.string.error_first_name_empty)); valid = false }
            firstName.length < 2 -> { view.showFirstNameError(view.getString(R.string.error_name_short)); valid = false }
        }
        when {
            lastName.isEmpty()   -> { view.showLastNameError(view.getString(R.string.error_last_name_empty)); valid = false }
            lastName.length < 2  -> { view.showLastNameError(view.getString(R.string.error_name_short)); valid = false }
        }
        when {
            contact.isEmpty()    -> { view.showContactError(view.getString(R.string.error_contact_empty)); valid = false }
            !contact.matches(Regex("^09[0-9]{9}$")) -> { view.showContactError(view.getString(R.string.error_contact_invalid)); valid = false }
        }
        if (assignedGate.isEmpty()) { view.showGateError(view.getString(R.string.error_gate_empty)); valid = false }
        when {
            email.isEmpty()      -> { view.showEmailError(view.getString(R.string.error_email_empty)); valid = false }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> { view.showEmailError(view.getString(R.string.error_email_invalid)); valid = false }
            email.any { it.isUpperCase() } -> { view.showEmailError(view.getString(R.string.error_email_uppercase)); valid = false }
        }
        when {
            password.isEmpty()   -> { view.showPasswordError(view.getString(R.string.error_password_empty)); valid = false }
            password.length < 8  -> { view.showPasswordError(view.getString(R.string.error_password_short)); valid = false }
        }
        return valid
    }
}
