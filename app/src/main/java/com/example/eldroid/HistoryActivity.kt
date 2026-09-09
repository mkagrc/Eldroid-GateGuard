package com.example.eldroid

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.eldroid.databinding.ActivityHistoryBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val detectionList = mutableListOf<DetectionRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // RecyclerView setup
        binding.rvDetections.layoutManager = LinearLayoutManager(this)
        binding.rvDetections.adapter = DetectionAdapter(detectionList)

        binding.btnClearAll.setOnClickListener { confirmClearAll() }

        loadDetections()

        // Bottom nav
        binding.bottomNav.selectedItemId = R.id.nav_history
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_history -> true
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadDetections() {
        db.collection("detections")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                detectionList.clear()
                for (doc in snapshot.documents) {
                    val record = DetectionRecord(
                        id        = doc.id,
                        timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now(),
                        status    = doc.getString("status") ?: "Metallic Object Detected",
                        uid       = doc.getString("uid") ?: ""
                    )
                    detectionList.add(record)
                }

                binding.rvDetections.adapter?.notifyDataSetChanged()

                // Update summary counts
                binding.tvTotalCount.text = detectionList.size.toString()
                binding.tvTodayCount.text = getTodayCount().toString()

                // Show/hide empty state
                if (detectionList.isEmpty()) {
                    binding.layoutEmpty.visibility   = View.VISIBLE
                    binding.rvDetections.visibility  = View.GONE
                } else {
                    binding.layoutEmpty.visibility   = View.GONE
                    binding.rvDetections.visibility  = View.VISIBLE
                }
            }
    }

    private fun getTodayCount(): Int {
        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(cal.time)
        return detectionList.count {
            dateFormat.format(it.timestamp.toDate()) == today
        }
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_clear_title))
            .setMessage(getString(R.string.dialog_clear_message))
            .setPositiveButton(getString(R.string.btn_clear_confirm)) { _, _ -> clearAllDetections() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearAllDetections() {
        val batch = db.batch()
        db.collection("detections")
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
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
}
