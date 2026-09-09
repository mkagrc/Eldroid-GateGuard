package com.example.eldroid

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.eldroid.databinding.ActivityChangePasswordBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

class ChangePasswordActivity : AppCompatActivity() {

    // ── MVP: View ─────────────────────────────────────────────────────────────
    private lateinit var binding: ActivityChangePasswordBinding
    private lateinit var presenter: ChangePasswordPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presenter = ChangePasswordPresenter(FirebaseAuth.getInstance(), this)

        setupPasswordWatcher()

        binding.btnBack.setOnClickListener { finish() }

        binding.btnChangePassword.setOnClickListener {
            clearErrors()
            presenter.changePassword(
                currentPw  = binding.etCurrentPassword.text.toString(),
                newPw      = binding.etNewPassword.text.toString(),
                confirmPw  = binding.etConfirmNewPassword.text.toString()
            )
        }
    }

    // ── Live requirement indicators for new password ───────────────────────────
    private fun setupPasswordWatcher() {
        binding.etNewPassword.addTextChangedListener(object : TextWatcher {
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

    // ── View interface methods called by Presenter ────────────────────────────

    fun showCurrentPasswordError(msg: String) { binding.tilCurrentPassword.error = msg }
    fun showNewPasswordError(msg: String)     { binding.tilNewPassword.error = msg }
    fun showConfirmPasswordError(msg: String) { binding.tilConfirmNewPassword.error = msg }

    fun clearErrors() {
        binding.tilCurrentPassword.error   = null
        binding.tilNewPassword.error       = null
        binding.tilConfirmNewPassword.error = null
    }

    fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility         = if (isLoading) View.VISIBLE else View.GONE
        binding.btnChangePassword.visibility   = if (isLoading) View.INVISIBLE else View.VISIBLE
        binding.btnChangePassword.isEnabled    = !isLoading
        binding.etCurrentPassword.isEnabled   = !isLoading
        binding.etNewPassword.isEnabled       = !isLoading
        binding.etConfirmNewPassword.isEnabled = !isLoading
    }

    fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.success))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
        // Clear fields after success
        binding.etCurrentPassword.text?.clear()
        binding.etNewPassword.text?.clear()
        binding.etConfirmNewPassword.text?.clear()
    }

    fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }
}

// ── MVP Presenter ─────────────────────────────────────────────────────────────
class ChangePasswordPresenter(
    private val auth: FirebaseAuth,
    private val view: ChangePasswordActivity
) {
    fun changePassword(currentPw: String, newPw: String, confirmPw: String) {
        if (!validateInputs(currentPw, newPw, confirmPw)) return

        val user  = auth.currentUser ?: return
        val email = user.email ?: return

        view.setLoading(true)

        // Step 1: Re-authenticate with current password
        val credential = EmailAuthProvider.getCredential(email, currentPw)
        user.reauthenticate(credential)
            .addOnCompleteListener { reAuthTask ->
                if (reAuthTask.isSuccessful) {
                    // Step 2: Update to new password
                    user.updatePassword(newPw)
                        .addOnCompleteListener { updateTask ->
                            view.setLoading(false)
                            if (updateTask.isSuccessful) {
                                view.showSuccess(view.getString(R.string.change_pw_success))
                            } else {
                                view.showError(
                                    updateTask.exception?.localizedMessage
                                        ?: view.getString(R.string.error_generic)
                                )
                            }
                        }
                } else {
                    view.setLoading(false)
                    val msg = when (reAuthTask.exception) {
                        is FirebaseAuthInvalidCredentialsException ->
                            view.getString(R.string.error_reauthenticate)
                        else -> reAuthTask.exception?.localizedMessage
                            ?: view.getString(R.string.error_generic)
                    }
                    view.showCurrentPasswordError(msg)
                }
            }
    }

    private fun validateInputs(currentPw: String, newPw: String, confirmPw: String): Boolean {
        var valid = true

        // Current password
        if (currentPw.isEmpty()) {
            view.showCurrentPasswordError(view.getString(R.string.error_current_password_empty))
            valid = false
        }

        // New password — all 5 complexity rules
        when {
            newPw.isEmpty() -> { view.showNewPasswordError(view.getString(R.string.error_password_empty)); valid = false }
            newPw.length < 8 -> { view.showNewPasswordError(view.getString(R.string.error_password_short)); valid = false }
            !newPw.any { it.isUpperCase() } -> { view.showNewPasswordError(view.getString(R.string.error_password_no_uppercase)); valid = false }
            !newPw.any { it.isLowerCase() } -> { view.showNewPasswordError(view.getString(R.string.error_password_no_lowercase)); valid = false }
            !newPw.any { it.isDigit() } -> { view.showNewPasswordError(view.getString(R.string.error_password_no_digit)); valid = false }
            !newPw.any { it in "!@#\$%^&*()_+-=[]{}|;':\",./<>?" } -> { view.showNewPasswordError(view.getString(R.string.error_password_no_special)); valid = false }
            newPw == currentPw -> { view.showNewPasswordError(view.getString(R.string.error_same_password)); valid = false }
        }

        // Confirm new password
        when {
            confirmPw.isEmpty() -> { view.showConfirmPasswordError(view.getString(R.string.error_confirm_password_empty)); valid = false }
            newPw != confirmPw -> { view.showConfirmPasswordError(view.getString(R.string.error_passwords_mismatch)); valid = false }
        }

        return valid
    }
}
