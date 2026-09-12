package com.example.eldroid.profile

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.eldroid.R
import com.example.eldroid.databinding.ActivityProfileBinding
import com.example.eldroid.dashboard.MainActivity
import com.example.eldroid.forgotpass.ChangePasswordActivity
import com.example.eldroid.auth.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            goToSplash()
            return
        }

        populateProfile()
        setupBottomNav()

        binding.btnChangePassword.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            goToSplash()
        }
    }

    private fun populateProfile() {
        val user        = auth.currentUser!!
        val displayName = user.displayName ?: ""
        val parts       = displayName.trim().split(" ", limit = 2)
        val firstName   = parts.getOrElse(0) { "" }
        val lastName    = parts.getOrElse(1) { "" }
        val email       = user.email ?: ""

        binding.tvFullName.text    = displayName.ifBlank { email }
        binding.tvEmailHeader.text = email
        binding.tvFirstName.text   = firstName
        binding.tvLastName.text    = lastName
        binding.tvEmail.text       = email

        // Load contact + address from Firestore
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    binding.tvContact.text = doc.getString("contactNumber") ?: ""
                    binding.tvAddress.text = doc.getString("address") ?: ""
                }
            }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_profile
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
                R.id.nav_devices -> {
                    startActivity(Intent(this, com.example.eldroid.devices.DevicesActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
                R.id.nav_guards  -> {
                    startActivity(Intent(this, com.example.eldroid.security.GuardsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
                R.id.nav_reports -> {
                    startActivity(Intent(this, com.example.eldroid.detection.ReportsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }

    private fun goToSplash() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
