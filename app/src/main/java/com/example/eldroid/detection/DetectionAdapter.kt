package com.example.eldroid.detection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class DetectionAdapter(
    private val items: List<DetectionRecord>
) : RecyclerView.Adapter<DetectionAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle : TextView = view.findViewById(com.example.eldroid.R.id.tvItemTitle)
        val tvDate  : TextView = view.findViewById(com.example.eldroid.R.id.tvItemDate)
        val tvTime  : TextView = view.findViewById(com.example.eldroid.R.id.tvItemTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.example.eldroid.R.layout.item_detection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = items[position]
        val date       = record.timestamp.toDate()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        holder.tvTitle.text = record.status
        holder.tvDate.text  = dateFormat.format(date)
        holder.tvTime.text  = timeFormat.format(date)
    }

    override fun getItemCount() = items.size
}
