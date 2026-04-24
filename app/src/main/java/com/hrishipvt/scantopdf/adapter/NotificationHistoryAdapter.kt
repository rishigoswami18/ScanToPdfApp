package com.hrishipvt.scantopdf.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hrishipvt.scantopdf.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotificationHistoryItem(
    val title: String,
    val message: String,
    val timestamp: Long
)

class NotificationHistoryAdapter(
    private val items: MutableList<NotificationHistoryItem> = mutableListOf()
) : RecyclerView.Adapter<NotificationHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvHistoryTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvHistoryMessage)
        val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvMessage.text = item.message
        holder.tvTime.text = formatTime(item.timestamp)
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<NotificationHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
