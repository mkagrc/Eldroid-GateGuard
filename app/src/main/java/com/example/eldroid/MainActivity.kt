package com.example.eldroid

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.eldroid.databinding.ActivityMainBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var detectionListener: ListenerRegistration? = null
    private var alertShowing = false

    // Runs every 60 seconds to keep greeting accurate while screen is open
    private val greetingHandler = Handler(Looper.getMainLooper())
    private val greetingRunnable = object : Runnable {
        override fun run() {
            updateGreeting()
            // Schedule next check in 60 seconds
            greetingHandler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) { goToSplash(); return }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav()
        loadStats()
        loadRecentDetections()
        refreshDeviceStatus()

        // Stat cards → History
        binding.cardStatTotal.setOnClickListener   { openHistory() }
        binding.cardStatCleared.setOnClickListener { openHistory() }
        binding.cardStatFlagged.setOnClickListener { openHistory() }
        binding.tvViewAll.setOnClickListener       { openHistory() }
    }

    override fun onResume() {
        super.onResume()
        alertShowing = false

        // Update greeting immediately, then start the 60-second ticker
        updateGreeting()
        greetingHandler.postDelayed(greetingRunnable, 60_000L)

        startDetectionListener()
        loadStats()
        loadRecentDetections()
        refreshDeviceStatus()
    }

    override fun onPause() {
        super.onPause()
        // Stop the greeting ticker and detection listener when screen is not visible
        greetingHandler.removeCallbacks(greetingRunnable)
        detectionListener?.remove()
        detectionListener = null
    }

    // ── IoT real-time listener ────────────────────────────────────────────────
    private fun startDetectionListener() {
        detectionListener = db.collection("detections")
            .document("trigger")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val detected = snapshot.getBoolean("detected") ?: false
                if (detected && !alertShowing) {
                    alertShowing = true
                    db.collection("detections").document("trigger")
                        .update("detected", false)
                    startActivity(Intent(this, AlertActivity::class.java))
                }
            }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    private fun loadStats() {
        db.collection("detections")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                // Exclude the trigger document from counts
                val records = snapshot.documents.filter { it.id != "trigger" }
                val total   = records.size
                // "Flagged" = documents with status field set to metallic detected
                val flagged = records.count {
                    (it.getString("status") ?: "").contains("Metallic", ignoreCase = true)
                }
                val cleared = total - flagged

                binding.tvStatTotal.text   = total.toString()
                binding.tvStatCleared.text = cleared.toString()
                binding.tvStatFlagged.text = flagged.toString()
            }
    }

    // ── Recent detections (top 3) ─────────────────────────────────────────────
    private fun loadRecentDetections() {
        db.collection("detections")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { snapshot ->
                binding.layoutRecentDetections.removeAllViews()

                // Filter out the trigger doc
                val records = snapshot.documents.filter { it.id != "trigger" }

                if (records.isEmpty()) {
                    binding.tvNoRecentAlerts.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                binding.tvNoRecentAlerts.visibility = View.GONE
                val inflater   = LayoutInflater.from(this)
                val dateFormat = SimpleDateFormat("MMM d · 'Detected at' hh:mm a", Locale.getDefault())

                records.take(3).forEachIndexed { index, doc ->
                    val ts   = doc.getTimestamp("timestamp") ?: Timestamp.now()
                    val date = ts.toDate()

                    val row = inflater.inflate(
                        R.layout.item_recent_detection,
                        binding.layoutRecentDetections, false
                    )

                    // Gate name from document or default
                    val gate = doc.getString("gate") ?: getString(R.string.device_main_gate)
                    row.findViewById<TextView>(R.id.tvRecentStatus).text =
                        getString(R.string.flagged_prefix) + gate
                    row.findViewById<TextView>(R.id.tvRecentDate).text =
                        dateFormat.format(date)

                    binding.layoutRecentDetections.addView(row)

                    // Divider between items
                    if (index < records.take(3).size - 1) {
                        val divider = View(this)
                        divider.layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                        divider.setBackgroundColor(resources.getColor(R.color.divider, theme))
                        binding.layoutRecentDetections.addView(divider)
                    }
                }
            }
    }

    // ── Device / hardware status ──────────────────────────────────────────────
    private fun refreshDeviceStatus() {
        db.collection("detections").document("trigger")
            .get()
            .addOnSuccessListener { doc ->
                val syncFormat = SimpleDateFormat("h 'min ago'", Locale.getDefault())
                val timeFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                val now = timeFormat.format(Date())

                if (doc != null && doc.exists()) {
                    binding.tvDetectorStatus.text = getString(R.string.status_devices_online)
                    binding.viewStatusDot.setBackgroundResource(R.drawable.bg_status_active)
                    binding.tvLastSync.text       = getString(R.string.sync_1_min_ago)
                    binding.tvDevice1Sub.text     = getString(R.string.device_sub_connected)
                    binding.tvDevice1Status.text  = getString(R.string.status_online)
                    binding.tvDevice1Status.setTextColor(resources.getColor(R.color.success, theme))
                    binding.tvDevice1Status.setBackgroundResource(R.drawable.bg_tag_online)
                } else {
                    binding.tvDetectorStatus.text = getString(R.string.status_offline)
                    binding.viewStatusDot.setBackgroundResource(R.drawable.bg_status_offline)
                    binding.tvLastSync.text       = getString(R.string.sync_never)
                }
            }
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────
    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_dashboard
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_history   -> { openHistory(); true }
                R.id.nav_profile   -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun openHistory() = startActivity(Intent(this, HistoryActivity::class.java))

    // ── Greeting helpers ──────────────────────────────────────────────────────

    private fun updateGreeting() {
        val displayName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: ""
        binding.tvGreeting.text = "${getTimeGreeting()}, $displayName"
    }

    private fun getTimeGreeting(): String {
        val hour   = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) // 0–23
        val minute = Calendar.getInstance().get(Calendar.MINUTE)

        return when {
            // 12:00 AM (00:00) to 11:59 AM (11:59) → Good Morning
            hour in 0..11 -> getString(R.string.greeting_morning)
            // 12:00 PM (12:00) to 4:59 PM (16:59) → Good Afternoon
            hour in 12..16 -> getString(R.string.greeting_afternoon)
            // 5:00 PM (17:00) to 11:59 PM (23:59) → Good Evening
            else -> getString(R.string.greeting_evening)
        }
    }

    private fun isToday(date: Date): Boolean {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return fmt.format(date) == fmt.format(Date())
    }

    private fun isThisWeek(date: Date): Boolean {
        val cal     = Calendar.getInstance()
        val calItem = Calendar.getInstance().also { it.time = date }
        return cal.get(Calendar.WEEK_OF_YEAR) == calItem.get(Calendar.WEEK_OF_YEAR)
                && cal.get(Calendar.YEAR) == calItem.get(Calendar.YEAR)
    }

    private fun goToSplash() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
