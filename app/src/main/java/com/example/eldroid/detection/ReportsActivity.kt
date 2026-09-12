package com.example.eldroid.detection

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.eldroid.R
import com.example.eldroid.databinding.ActivityReportsBinding
import com.example.eldroid.dashboard.MainActivity
import com.example.eldroid.devices.DevicesActivity
import com.example.eldroid.security.GuardsActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private lateinit var db: FirebaseFirestore
    private val detectionList = mutableListOf<DetectionRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        binding.rvDetections.layoutManager = LinearLayoutManager(this)
        binding.rvDetections.adapter = DetectionAdapter(detectionList)

        binding.btnClearAll.setOnClickListener { confirmClearAll() }

        loadDetections()
        setupBottomNav()
    }

    private fun loadDetections() {
        db.collection("detections")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                detectionList.clear()
                for (doc in snapshot.documents) {
                    if (doc.id == "trigger") continue
                    detectionList.add(
                        DetectionRecord(
                            id        = doc.id,
                            timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now(),
                            status    = doc.getString("status") ?: "Metallic Object Detected",
                            uid       = doc.getString("uid") ?: ""
                        )
                    )
                }

                binding.rvDetections.adapter?.notifyDataSetChanged()
                binding.tvTotalCount.text = detectionList.size.toString()
                binding.tvTodayCount.text = getTodayCount().toString()

                if (detectionList.isEmpty()) {
                    binding.layoutEmpty.visibility  = View.VISIBLE
                    binding.rvDetections.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility  = View.GONE
                    binding.rvDetections.visibility = View.VISIBLE
                }
            }
    }

    private fun getTodayCount(): Int {
        val fmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = fmt.format(Calendar.getInstance().time)
        return detectionList.count { fmt.format(it.timestamp.toDate()) == today }
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_clear_title))
            .setMessage(getString(R.string.dialog_clear_message))
            .setPositiveButton(getString(R.string.btn_clear_confirm)) { _, _ -> clearAll() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearAll() {
        val batch = db.batch()
        db.collection("detections").get().addOnSuccessListener { snap ->
            snap.documents.filter { it.id != "trigger" }
                .forEach { batch.delete(it.reference) }
            batch.commit().addOnSuccessListener {
                detectionList.clear()
                binding.rvDetections.adapter?.notifyDataSetChanged()
                binding.tvTotalCount.text = "0"
                binding.tvTodayCount.text = "0"
                binding.layoutEmpty.visibility  = View.VISIBLE
                binding.rvDetections.visibility = View.GONE
            }
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_reports
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
                R.id.nav_guards   -> {
                    startActivity(Intent(this, GuardsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish(); true
                }
                R.id.nav_reports  -> true
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
