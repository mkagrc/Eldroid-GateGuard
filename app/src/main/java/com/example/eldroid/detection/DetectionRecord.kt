package com.example.eldroid.detection

import com.google.firebase.Timestamp

data class DetectionRecord(
    val id: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: String = "Metallic Object Detected",
    val uid: String = ""
)
