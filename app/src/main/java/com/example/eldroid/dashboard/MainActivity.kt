package com.example.eldroid.dashboard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.eldroid.R
import com.example.eldroid.alert.AlertActivity
import com.example.eldroid.auth.LoginActivity
import com.example.eldroid.databinding.ActivityMainBinding
import com.example.eldroid.detection.HistoryActivity
import com.example.eldroid.detection.ReportsActivity
import com.example.eldroid.devices.DevicesActivity
import com.example.eldroid.security.GuardsActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

data class HarmfulReport(
    val gate: String,
    val handledBy: String,
    val dateTime: String,
    val docId: String
)

data class DeviceInfo(
    val name: String,
    val isOnline: Boolean,
    val subInfo: String,
    val lastSyncTimestamp: Date?
)

data class GuardInfo(
    val name: String,
    val assignedGate: String,
    val isOnDuty: Boolean
)

class DashboardPresenter(
    private val db: FirebaseFirestore,
    private val rtdb: FirebaseDatabase,
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

    private fun loadDevices(onComplete: (() -> Unit)? = null) {
        rtdb.getReference("device-status")
            .get()
            .addOnSuccessListener { snapshot ->
                val devices = mutableListOf<DeviceInfo>()
                for (child in snapshot.children) {
                    val name       = child.key ?: continue
                    val isOnline   = child.child("isOnline").getValue(Boolean::class.java) ?: false
                    val connType   = child.child("connectionType").getValue(String::class.java) ?: "Wi-Fi"
                    val lastSeenMs = child.child("lastSeen").getValue(Long::class.java)
                    val lastSync   = lastSeenMs?.let { Date(it) }
                    val subInfo    = if (isOnline) "$connType · Connected"
                    else lastSync?.let { "Not synced since ${syncFmt.format(it)}" } ?: "Never synced"
                    devices.add(DeviceInfo(name, isOnline, subInfo, lastSync))
                }
                val onlineCount = devices.count { it.isOnline }
                val totalCount  = devices.size
                val latestSync  = devices.mapNotNull { it.lastSyncTimestamp }
                    .maxOrNull()?.let { syncFmt.format(it) } ?: "—"
                val bannerText  = if (totalCount == 0) "No devices registered"
                else "$onlineCount of $totalCount devices online"
                view.showSystemStatus(bannerText, latestSync, onlineCount > 0)
                if (devices.isEmpty()) view.showNoDevices() else view.showDevices(devices.take(3))
                onComplete?.invoke()
            }
            .addOnFailureListener { onComplete?.invoke() }
    }

    private fun loadStats() {
        db.collection("detections")
            .get()
            .addOnSuccessListener { snap ->
                val docs    = snap.documents.filter { it.id != "trigger" }
                val total   = docs.size
                val flagged = docs.count { (it.getString("resolution") ?: "").equals("Harmful", ignoreCase = true) }
                val cleared = docs.count { (it.getString("resolution") ?: "").equals("Not Harmful", ignoreCase = true) }
                view.showStats(total, cleared, flagged)
            }
            .addOnFailureListener { view.showStats(0, 0, 0) }
    }

    private fun loadHarmfulReports() {
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
                        HarmfulReport(gate, handledBy, dateTimeFmt.format(ts), doc.id)
                    }
                if (reports.isEmpty()) view.showNoReports() else view.showHarmfulReports(reports)
            }
            .addOnFailureListener { view.showNoReports() }
    }

    private fun loadGuards() {
        rtdb.getReference("guards")
            .orderByChild("onDuty")
            .equalTo(true)
            .get()
            .addOnSuccessListener { snapshot ->
                val guards = mutableListOf<GuardInfo>()
                for (child in snapshot.children) {
                    val gate   = child.child("assignedGate").getValue(String::class.java) ?: "Unassigned"
                    val onDuty = child.child("onDuty").getValue(Boolean::class.java) ?: false
                    guards.add(GuardInfo("Security", gate, onDuty))
                }
                if (guards.isEmpty()) view.showNoGuards() else view.showGuards(guards.take(3))
            }
            .addOnFailureListener { view.showNoGuards() }
    }
}

class MainActivity : AppCompatActivity(), DashboardView {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var rtdb: FirebaseDatabase
    private lateinit var presenter: DashboardPresenter

    private var rtdbTriggerListener: ValueEventListener? = null
    private var alertShowing = false

    private val greetingHandler  = Handler(Looper.getMainLooper())
    private val greetingRunnable = object : Runnable {
        override fun run() {
            refreshGreeting()
            greetingHandler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()
        rtdb = FirebaseDatabase.getInstance()
        if (auth.currentUser == null) { goToLogin(); return }
        binding   = ActivityMainBinding.inflate(layoutInflater)
        presenter = DashboardPresenter(db, rtdb, this)
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
        rtdbTriggerListener?.let { rtdb.getReference("trigger").removeEventListener(it) }
        rtdbTriggerListener = null
    }

    private fun startDetectionListener() {
        rtdbTriggerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val detected = snapshot.child("detected").getValue(Boolean::class.java) ?: false
                if (detected && !alertShowing) {
                    alertShowing = true
                    rtdb.getReference("trigger").child("detected").setValue(false)
                    startActivity(Intent(this@MainActivity, AlertActivity::class.java))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        rtdb.getReference("trigger").addValueEventListener(rtdbTriggerListener!!)
    }

    private fun loadDashboard() {
        val user        = auth.currentUser ?: return
        val displayName = user.displayName?.ifBlank { null } ?: user.email ?: getString(R.string.default_user)
        presenter.loadAll(displayName, getTimeGreeting())
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.primary))
        binding.swipeRefresh.setOnRefreshListener { loadDashboard() }
    }

    private fun setupClickListeners() {
        binding.cardStatTotal.setOnClickListener    { openHistory() }
        binding.cardStatCleared.setOnClickListener  { openHistory() }
        binding.cardStatFlagged.setOnClickListener  { openHistory() }
        binding.tvViewAllReports.setOnClickListener { openHistory() }
        binding.tvViewAllDevices.setOnClickListener { openHistory() }
        binding.tvViewAllGuards.setOnClickListener  {}
    }

    override fun showGreeting(text: String) { binding.tvGreeting.text = text }

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
        val inflater   = LayoutInflater.from(this)
        val handledStr = getString(R.string.label_handled_by)
        reports.forEachIndexed { index, report ->
            val row = inflater.inflate(R.layout.item_recent_detection, binding.layoutRecentDetections, false)
            row.findViewById<TextView>(R.id.tvRecentStatus).text =
                getString(R.string.flagged_prefix) + report.gate
            row.findViewById<TextView>(R.id.tvRecentDate).text =
                "${report.dateTime} · $handledStr ${report.handledBy}"
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
        val inflater   = LayoutInflater.from(this)
        val onlineStr  = getString(R.string.status_online)
        val offlineStr = getString(R.string.status_offline_tag)
        devices.forEachIndexed { index, device ->
            val row      = inflater.inflate(R.layout.item_device_row, binding.layoutDevices, false)
            val statusTv = row.findViewById<TextView>(R.id.tvDeviceStatus)
            val dotView  = row.findViewById<View>(R.id.viewDeviceDot)
            row.findViewById<TextView>(R.id.tvDeviceName).text = device.name
            row.findViewById<TextView>(R.id.tvDeviceSub).text  = device.subInfo
            if (device.isOnline) {
                statusTv.text = onlineStr
                statusTv.setTextColor(ContextCompat.getColor(this, R.color.success))
                statusTv.setBackgroundResource(R.drawable.bg_tag_online)
                dotView.setBackgroundResource(R.drawable.bg_status_active)
            } else {
                statusTv.text = offlineStr
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
        val inflater    = LayoutInflater.from(this)
        val gateLabel   = getString(R.string.label_assigned_gate)
        val onDutyStr   = getString(R.string.status_on_duty)
        val offDutyStr  = getString(R.string.status_off_duty)
        val securityStr = getString(R.string.label_security)
        guards.forEachIndexed { index, guard ->
            val row = inflater.inflate(R.layout.item_guard_row, binding.layoutGuards, false)
            row.findViewById<TextView>(R.id.tvGuardName).text   = securityStr
            row.findViewById<TextView>(R.id.tvGuardGate).text   = "$gateLabel: ${guard.assignedGate}"
            row.findViewById<TextView>(R.id.tvGuardStatus).text = if (guard.isOnDuty) onDutyStr else offDutyStr
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
        val displayName = user.displayName?.ifBlank { null } ?: user.email ?: getString(R.string.default_user)
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
                R.id.nav_devices   -> { startActivity(Intent(this, DevicesActivity::class.java)); true }
                R.id.nav_guards    -> { startActivity(Intent(this, GuardsActivity::class.java)); true }
                R.id.nav_reports   -> { startActivity(Intent(this, ReportsActivity::class.java)); true }
                R.id.nav_profile   -> { startActivity(Intent(this, com.example.eldroid.profile.ProfileActivity::class.java)); true }
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
