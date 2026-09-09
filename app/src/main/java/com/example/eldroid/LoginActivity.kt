package com.example.eldroid

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.eldroid.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    // ── MVP: View binds to Presenter ─────────────────────────────────────────
    private lateinit var binding: ActivityLoginBinding
    private lateinit var presenter: LoginPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presenter = LoginPresenter(FirebaseAuth.getInstance(), this)

        setupClickListeners()
        styleSignupPrompt()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            clearErrors()
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            presenter.login(email, password)
        }

        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        binding.tvGoToSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    // ── View interface methods called by Presenter ────────────────────────────

    fun showEmailError(msg: String)    { binding.tilEmail.error = msg }
    fun showPasswordError(msg: String) { binding.tilPassword.error = msg }

    fun clearErrors() {
        binding.tilEmail.error    = null
        binding.tilPassword.error = null
    }

    fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.visibility    = if (isLoading) View.INVISIBLE else View.VISIBLE
        binding.btnLogin.isEnabled     = !isLoading
        binding.etEmail.isEnabled      = !isLoading
        binding.etPassword.isEnabled   = !isLoading
    }

    fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }

    fun showInfo(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.primary))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }

    fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ── Span styling ──────────────────────────────────────────────────────────

    private fun styleSignupPrompt() {
        val fullText  = getString(R.string.go_to_signup)
        val spannable = SpannableString(fullText)
        val linkText  = getString(R.string.sign_up_span)
        val start     = fullText.indexOf(linkText)
        if (start >= 0) {
            val end = start + linkText.length
            spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(StyleSpan(Typeface.BOLD),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvGoToSignup.text = spannable
    }
}

// ── MVP Presenter ─────────────────────────────────────────────────────────────
class LoginPresenter(
    private val auth: FirebaseAuth,
    private val view: LoginActivity
) {
    fun login(email: String, password: String) {
        if (!validateInputs(email, password)) return

        view.setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                view.setLoading(false)
                if (task.isSuccessful) {
                    view.navigateToMain()
                } else {
                    val msg = when (task.exception) {
                        is FirebaseAuthInvalidUserException        ->
                            view.getString(R.string.error_user_not_found)
                        is FirebaseAuthInvalidCredentialsException ->
                            view.getString(R.string.error_wrong_credentials)
                        else -> task.exception?.localizedMessage
                            ?: view.getString(R.string.error_generic)
                    }
                    view.showError(msg)
                }
            }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var valid = true

        // Email validations
        when {
            email.isEmpty() -> {
                view.showEmailError(view.getString(R.string.error_email_empty))
                valid = false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                view.showEmailError(view.getString(R.string.error_email_invalid))
                valid = false
            }
            email.any { it.isUpperCase() } -> {
                view.showEmailError(view.getString(R.string.error_email_uppercase))
                valid = false
            }
        }

        // Password validations
        when {
            password.isEmpty() -> {
                view.showPasswordError(view.getString(R.string.error_password_empty))
                valid = false
            }
            password.length < 8 -> {
                view.showPasswordError(view.getString(R.string.error_password_short))
                valid = false
            }
        }

        return valid
    }
}
