package com.example.eldroid

import android.content.Intent
import android.os.Bundle
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.view.animation.AnimationSet
import androidx.appcompat.app.AppCompatActivity
import com.example.eldroid.databinding.ActivityAlertBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Set current detection time
        val now = Date()
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.tvDetectionTime.text = "${dateFormat.format(now)}  ${timeFormat.format(now)}"

        // Save detection to Firestore
        saveDetectionRecord()

        // Start pulse animation
        startPulseAnimation()

        binding.btnDismiss.setOnClickListener {
            finish()
        }

        binding.btnViewHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            finish()
        }
    }

    private fun saveDetectionRecord() {
        val uid = auth.currentUser?.uid ?: "unknown"
        val record = hashMapOf(
            "uid"       to uid,
            "status"    to "Metallic Object Detected",
            "timestamp" to Timestamp.now()
        )
        db.collection("detections").add(record)
    }

    private fun startPulseAnimation() {
        // Outer pulse
        val outerScale = ScaleAnimation(
            1f, 1.15f, 1f, 1.15f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 900
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        val outerAlpha = AlphaAnimation(0.15f, 0.35f).apply {
            duration = 900
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        val outerSet = AnimationSet(true).apply {
            addAnimation(outerScale)
            addAnimation(outerAlpha)
        }
        binding.viewPulseOuter.startAnimation(outerSet)

        // Inner pulse (slightly offset)
        val innerScale = ScaleAnimation(
            1f, 1.1f, 1f, 1.1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 700
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            startOffset = 150
        }
        binding.viewPulseInner.startAnimation(innerScale)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.viewPulseOuter.clearAnimation()
        binding.viewPulseInner.clearAnimation()
    }
}
