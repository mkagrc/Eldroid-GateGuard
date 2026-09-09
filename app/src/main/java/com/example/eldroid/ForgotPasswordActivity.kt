package com.example.eldroid

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.eldroid.databinding.ActivityForgotPasswordBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    // ── MVP: View ─────────────────────────────────────────────────────────────
    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var presenter: ForgotPasswordPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presenter = ForgotPasswordPresenter(FirebaseAuth.getInstance(), this)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSendReset.setOnClickListener {
            binding.tilEmail.error = null
            val email = binding.etEmail.text.toString().trim()
            presenter.sendResetLink(email)
        }

        binding.btnBackToLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    // ── View interface methods called by Presenter ────────────────────────────

    fun showEmailError(msg: String) { binding.tilEmail.error = msg }

    fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSendReset.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
        binding.btnSendReset.isEnabled  = !isLoading
        binding.etEmail.isEnabled       = !isLoading
    }

    fun showSuccessState() {
        // Hide input form, show success card
        binding.tilEmail.visibility      = View.GONE
        binding.btnSendReset.visibility  = View.GONE
        binding.layoutSuccess.visibility = View.VISIBLE
        binding.btnBackToLogin.visibility = View.VISIBLE
    }

    fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }
}

// ── MVP Presenter ─────────────────────────────────────────────────────────────
class ForgotPasswordPresenter(
    private val auth: FirebaseAuth,
    private val view: ForgotPasswordActivity
) {
    fun sendResetLink(email: String) {
        if (!validate(email)) return

        view.setLoading(true)

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                view.setLoading(false)
                if (task.isSuccessful) {
                    view.showSuccessState()
                } else {
                    view.showError(
                        task.exception?.localizedMessage
                            ?: view.getString(R.string.error_generic)
                    )
                }
            }
    }

    private fun validate(email: String): Boolean {
        return when {
            email.isEmpty() -> {
                view.showEmailError(view.getString(R.string.error_email_empty))
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                view.showEmailError(view.getString(R.string.error_email_invalid))
                false
            }
            email.any { it.isUpperCase() } -> {
                view.showEmailError(view.getString(R.string.error_email_uppercase))
                false
            }
            else -> true
        }
    }
}
