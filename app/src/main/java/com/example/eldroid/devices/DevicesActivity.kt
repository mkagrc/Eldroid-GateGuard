package com.example.eldroid.devices

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.eldroid.R
import com.example.eldroid.databinding.ActivityDevicesBinding
import com.example.eldroid.dashboard.MainActivity
import com.example.eldroid.security.GuardsActivity
import com.example.eldroid.detection.ReportsActivity

class DevicesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevicesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_devices
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
                R.id.nav_devices  -> true
                R.id.nav_guards   -> {
                    startActivity(Intent(this, GuardsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
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
