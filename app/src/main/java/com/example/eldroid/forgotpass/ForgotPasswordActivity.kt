package com.example.eldroid.forgotpass

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.eldroid.R
import com.example.eldroid.auth.LoginActivity
import com.example.eldroid.databinding.ActivityForgotPasswordBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var presenter: ForgotPasswordPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presenter = ForgotPasswordPresenter(
            auth = FirebaseAuth.getInstance(),
            view = this
        )

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

    fun showEmailError(msg: String) { binding.tilEmail.error = msg }

    fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility  = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSendReset.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
        binding.btnSendReset.isEnabled  = !isLoading
        binding.etEmail.isEnabled       = !isLoading
    }

    fun showSuccessState() {
        binding.tilEmail.visibility       = View.GONE
        binding.btnSendReset.visibility   = View.GONE
        binding.layoutSuccess.visibility  = View.VISIBLE
        binding.btnBackToLogin.visibility = View.VISIBLE
    }

    fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }
}

class ForgotPasswordPresenter(
    private val auth: FirebaseAuth,
    private val view: ForgotPasswordActivity
) {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun sendResetLink(email: String) {
        if (!validate(email)) return

        view.setLoading(true)

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                view.setLoading(false)
                if (task.isSuccessful) {
                    // Record the reset request in Realtime Database
                    logResetRequest(email)
                    view.showSuccessState()
                } else {
                    view.showError(
                        task.exception?.localizedMessage
                            ?: view.getString(R.string.error_generic)
                    )
                }
            }
    }

    private fun logResetRequest(email: String) {
        val record = mapOf(
            "email"       to email,
            "requestedAt" to dateFmt.format(Date()),
            "timestamp"   to System.currentTimeMillis(),
            "status"      to "sent"
        )

        android.util.Log.d("RTDB", "Attempting to write to Realtime Database...")

        FirebaseDatabase.getInstance(
            "https://eldroid-3cb29-default-rtdb.asia-southeast1.firebasedatabase.app"
        )
            .getReference("password-resets")
            .push()
            .setValue(record)
            .addOnSuccessListener {
                android.util.Log.d("RTDB", "SUCCESS — password reset record saved")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("RTDB", "FAILED — ${e.javaClass.simpleName}: ${e.message}")
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
