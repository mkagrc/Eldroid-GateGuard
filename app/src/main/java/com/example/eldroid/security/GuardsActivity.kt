package com.example.eldroid.security

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.eldroid.R
import com.example.eldroid.databinding.ActivityGuardsBinding
import com.example.eldroid.dashboard.MainActivity
import com.example.eldroid.devices.DevicesActivity
import com.example.eldroid.detection.ReportsActivity

class GuardsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuardsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuardsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAddSecurity.setOnClickListener {
            startActivity(Intent(this, CreateSecurityActivity::class.java))
        }

        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_guards
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
                R.id.nav_devices  -> {
                    startActivity(Intent(this, DevicesActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
                R.id.nav_guards   -> true
                R.id.nav_reports  -> {
                    startActivity(Intent(this, ReportsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
                R.id.nav_profile  -> {
                    startActivity(Intent(this, com.example.eldroid.profile.ProfileActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
                else -> false
            }
        }
    }
}
