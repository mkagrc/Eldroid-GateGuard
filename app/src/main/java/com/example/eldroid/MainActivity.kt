package com.example.eldroid

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

// ── MVP: View interface ───────────────────────────────────────────────────────
interface DashboardView {
    fun showGreeting(text: String)
    fun showSystemStatus(devicesOnlineText: String, lastSync: String, anyOnline: Boolean)
    fun showStats(total: Int, cleared: Int, flagged: Int)
    fun showHarmfulReports(reports: List<HarmfulReport>)
    fun showNoReports()
    fun showDevices(devices: List<DeviceInfo>)
    fun showNoDevices()
    fun showGuards(guards: List<GuardInfo>)
    fun showNoGuards()
    fun setRefreshing(isRefreshing: Boolean)
}

// ── Data models ───────────────────────────────────────────────────────────────
data class HarmfulReport(
    val gate: String,
    val handledBy: String,
    val dateTime: String,
    val docId: String
)

data class DeviceInfo(
    val name: String,
    val isOnline: Boolean,
    val subInfo: String,     // e.g. "Wi-Fi · Connected" or "Not synced in 2 hrs"
    val lastSyncTimestamp: Date?
)

data class GuardInfo(
    val name: String,
    val assignedGate: String,
    val isOnDuty: Boolean
)

// ── MVP: Presenter ────────────────────────────────────────────────────────────
class DashboardPresenter(
    private val db: FirebaseFirestore,
    private val view: DashboardView
) {
    private val dateTimeFmt = SimpleDateFormat("MMM d · hh:mm a", Locale.getDefault())
    private val syncFmt     = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())

    fun loadAll(displayName: String, greeting: String) {
        view.showGreeting("$greeting, $displayName")
        view.setRefreshing(true)
        loadDevices { view.setRefreshing(false) }
        loadStats()
        loadHarmfulReports()
        loadGuards()
    }

    // ── Devices — source of truth for both banner and device list ──────────────
    private fun loadDevices(onComplete: (() -> Unit)? = null) {
        db.collection("devices")
            .get()
            .addOnSuccessListener { snapshot ->
                val devices = snapshot.documents.mapNotNull { doc ->
                    val name     = doc.getString("name") ?: return@mapNotNull null
                    val isOnline = doc.getBoolean("isOnline") ?: false
                    val connType = doc.getString("connectionType") ?: "Wi-Fi"
                    val lastSync = doc.getTimestamp("lastSync")?.toDate()
                    val subInfo  = if (isOnline) {
                        "$connType · Connected"
                    } else {
                        lastSync?.let { "Not synced since ${syncFmt.format(it)}" }
                            ?: "Never synced"
                    }
                    DeviceInfo(name, isOnline, subInfo, lastSync)
                }

                // Banner: count online vs total (same data, no duplication)
                val onlineCount = devices.count { it.isOnline }
                val totalCount  = devices.size
                val latestSync  = devices.mapNotNull { it.lastSyncTimestamp }
                    .maxOrNull()
                    ?.let { syncFmt.format(it) } ?: "—"

                val bannerText = if (totalCount == 0) {
                    "No devices registered"
                } else {
                    "$onlineCount of $totalCount devices online"
                }

                view.showSystemStatus(bannerText, latestSync, onlineCount > 0)

                if (devices.isEmpty()) view.showNoDevices()
                else view.showDevices(devices.take(3))

                onComplete?.invoke()
            }
            .addOnFailureListener { onComplete?.invoke() }
    }

    // ── Stats — filtered by resolution status field ────────────────────────────
    fun loadStats() {
        db.collection("detections")
            .whereNotEqualTo("status", "trigger") // exclude trigger doc
            .get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents.filter { it.id != "trigger" }
                val total   = docs.size
                val flagged = docs.count {
                    (it.getString("resolution") ?: "").equals("Harmful", ignoreCase = true)
                }
                val cleared = docs.count {
                    (it.getString("resolution") ?: "").equals("Not Harmful", ignoreCase = true)
                }
                view.showStats(total, cleared, flagged)
            }
            .addOnFailureListener {
                // Fall back — count from all docs
                db.collection("detections").get().addOnSuccessListener { snap ->
                    val docs    = snap.documents.filter { it.id != "trigger" }
                    val total   = docs.size
                    val flagged = docs.count {
                        (it.getString("status") ?: "").contains("Metallic", ignoreCase = true)
                    }
                    view.showStats(total, total - flagged, flagged)
                }
            }
    }

    // ── Harmful reports — detections resolved as Harmful ──────────────────────
    fun loadHarmfulReports() {
        db.collection("detections")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(3)
            .get()
            .addOnSuccessListener { snapshot ->
                val reports = snapshot.documents
                    .filter { it.id != "trigger" }
                    .mapNotNull { doc ->
                        val ts        = doc.getTimestamp("timestamp")?.toDate() ?: return@mapNotNull null
                        val gate      = doc.getString("gate") ?: "Unknown Gate"
                        val handledBy = doc.getString("handledBy") ?: "—"
                        HarmfulReport(
                            gate      = gate,
                            handledBy = handledBy,
                            dateTime  = dateTimeFmt.format(ts),
                            docId     = doc.id
                        )
                    }
                if (reports.isEmpty()) view.showNoReports()
                else view.showHarmfulReports(reports)
            }
            .addOnFailureListener { view.showNoReports() }
    }

    // ── Guards on duty ─────────────────────────────────────────────────────────
    fun loadGuards() {
        db.collection("guards")
            .whereEqualTo("onDuty", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val guards = snapshot.documents.mapNotNull { doc ->
                    val name  = doc.getString("name") ?: return@mapNotNull null
                    val gate  = doc.getString("assignedGate") ?: "Unassigned"
                    GuardInfo(name, gate, true)
                }
                if (guards.isEmpty()) view.showNoGuards()
                else view.showGuards(guards.take(3))
            }
            .addOnFailureListener { view.showNoGuards() }
    }
}

// ── View (Activity) ───────────────────────────────────────────────────────────
class MainActivity : AppCompatActivity(), DashboardView {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var presenter: DashboardPresenter

    private var detectionListener: ListenerRegistration? = null
    private var alertShowing = false

    private val greetingHandler  = Handler(Looper.getMainLooper())
    private val greetingRunnable = object : Runnable {
        override fun run() {
            refreshGreeting()
            greetingHandler.postDelayed(this, 60_000L)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) { goToLogin(); return }

        binding   = ActivityMainBinding.inflate(layoutInflater)
        presenter = DashboardPresenter(db, this)
        setContentView(binding.root)

        setupBottomNav()
        setupSwipeRefresh()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        alertShowing = false
        refreshGreeting()
        greetingHandler.postDelayed(greetingRunnable, 60_000L)
        loadDashboard()
        startDetectionListener()
    }

    override fun onPause() {
        super.onPause()
        greetingHandler.removeCallbacks(greetingRunnable)
        detectionListener?.remove()
        detectionListener = null
    }

    // ── Load / refresh ────────────────────────────────────────────────────────

    private fun loadDashboard() {
        val user        = auth.currentUser ?: return
        val displayName = user.displayName?.ifBlank { null }
            ?: user.email
            ?: getString(R.string.default_user)
        presenter.loadAll(displayName, getTimeGreeting())
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary)
        )
        binding.swipeRefresh.setOnRefreshListener { loadDashboard() }
    }

    private fun setupClickListeners() {
        binding.cardStatTotal.setOnClickListener   { openHistory() }
        binding.cardStatCleared.setOnClickListener { openHistory() }
        binding.cardStatFlagged.setOnClickListener { openHistory() }
        binding.tvViewAllReports.setOnClickListener { openHistory() }
        binding.tvViewAllDevices.setOnClickListener { openHistory() }
        binding.tvViewAllGuards.setOnClickListener  { /* future: Guards screen */ }
    }

    // ── IoT real-time listener ─────────────────────────────────────────────────

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

    // ── DashboardView implementation ──────────────────────────────────────────

    override fun showGreeting(text: String) {
        binding.tvGreeting.text = text
    }

    override fun showSystemStatus(devicesOnlineText: String, lastSync: String, anyOnline: Boolean) {
        binding.tvDevicesOnline.text = devicesOnlineText
        binding.tvLastSync.text      = lastSync
        binding.viewStatusDot.setBackgroundResource(
            if (anyOnline) R.drawable.bg_status_active else R.drawable.bg_status_offline
        )
    }

    override fun showStats(total: Int, cleared: Int, flagged: Int) {
        binding.tvStatTotal.text   = total.toString()
        binding.tvStatCleared.text = cleared.toString()
        binding.tvStatFlagged.text = flagged.toString()
    }

    override fun showHarmfulReports(reports: List<HarmfulReport>) {
        binding.tvNoRecentAlerts.visibility = View.GONE
        binding.layoutRecentDetections.removeAllViews()
        val inflater = LayoutInflater.from(this)

        reports.forEachIndexed { index, report ->
            val row = inflater.inflate(
                R.layout.item_recent_detection,
                binding.layoutRecentDetections, false
            )
            row.findViewById<TextView>(R.id.tvRecentStatus).text =
                getString(R.string.flagged_prefix) + report.gate
            row.findViewById<TextView>(R.id.tvRecentDate).text =
                "${report.dateTime} · ${getString(R.string.label_handled_by)} ${report.handledBy}"

            binding.layoutRecentDetections.addView(row)

            if (index < reports.size - 1) addDivider(binding.layoutRecentDetections)
        }
    }

    override fun showNoReports() {
        binding.layoutRecentDetections.removeAllViews()
        binding.tvNoRecentAlerts.visibility = View.VISIBLE
    }

    override fun showDevices(devices: List<DeviceInfo>) {
        binding.tvNoDevices.visibility = View.GONE
        binding.layoutDevices.removeAllViews()
        val inflater = LayoutInflater.from(this)

        devices.forEachIndexed { index, device ->
            val row = inflater.inflate(
                R.layout.item_device_row,
                binding.layoutDevices, false
            )
            row.findViewById<TextView>(R.id.tvDeviceName).text = device.name
            row.findViewById<TextView>(R.id.tvDeviceSub).text  = device.subInfo

            val statusTv = row.findViewById<TextView>(R.id.tvDeviceStatus)
            val dotView  = row.findViewById<View>(R.id.viewDeviceDot)

            if (device.isOnline) {
                statusTv.text = getString(R.string.status_online)
                statusTv.setTextColor(ContextCompat.getColor(this, R.color.success))
                statusTv.setBackgroundResource(R.drawable.bg_tag_online)
                dotView.setBackgroundResource(R.drawable.bg_status_active)
            } else {
                statusTv.text = getString(R.string.status_offline_tag)
                statusTv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                statusTv.setBackgroundResource(R.drawable.bg_tag_offline)
                dotView.setBackgroundResource(R.drawable.bg_status_offline)
            }

            binding.layoutDevices.addView(row)
            if (index < devices.size - 1) addDivider(binding.layoutDevices)
        }
    }

    override fun showNoDevices() {
        binding.layoutDevices.removeAllViews()
        binding.tvNoDevices.visibility = View.VISIBLE
    }

    override fun showGuards(guards: List<GuardInfo>) {
        binding.tvNoGuards.visibility = View.GONE
        binding.layoutGuards.removeAllViews()
        val inflater = LayoutInflater.from(this)

        guards.forEachIndexed { index, guard ->
            val row = inflater.inflate(
                R.layout.item_guard_row,
                binding.layoutGuards, false
            )
            row.findViewById<TextView>(R.id.tvGuardName).text  = getString(R.string.label_security)
            row.findViewById<TextView>(R.id.tvGuardGate).text  =
                "${getString(R.string.label_assigned_gate)}: ${guard.assignedGate}"
            row.findViewById<TextView>(R.id.tvGuardStatus).text =
                if (guard.isOnDuty) getString(R.string.status_on_duty)
                else getString(R.string.status_off_duty)

            binding.layoutGuards.addView(row)
            if (index < guards.size - 1) addDivider(binding.layoutGuards)
        }
    }

    override fun showNoGuards() {
        binding.layoutGuards.removeAllViews()
        binding.tvNoGuards.visibility = View.VISIBLE
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        binding.swipeRefresh.isRefreshing = isRefreshing
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun addDivider(container: android.widget.LinearLayout) {
        val divider = View(this)
        divider.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
        )
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider))
        container.addView(divider)
    }

    private fun refreshGreeting() {
        val user        = auth.currentUser ?: return
        val displayName = user.displayName?.ifBlank { null }
            ?: user.email
            ?: getString(R.string.default_user)
        binding.tvGreeting.text = "${getTimeGreeting()}, $displayName"
    }

    private fun getTimeGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 0..11  -> getString(R.string.greeting_morning)
            hour in 12..16 -> getString(R.string.greeting_afternoon)
            else           -> getString(R.string.greeting_evening)
        }
    }

    private fun openHistory() = startActivity(Intent(this, HistoryActivity::class.java))

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_dashboard
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_devices   -> {
                    startActivity(Intent(this, DevicesActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_guards    -> {
                    startActivity(Intent(this, GuardsActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_reports   -> {
                    startActivity(Intent(this, ReportsActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
