package com.hrishipvt.scantopdf.adapter

import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.view.ChatMessage

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val containerAi: LinearLayout = v.findViewById(R.id.containerAi)
        val containerUser: LinearLayout = v.findViewById(R.id.containerUser)
        val txtAi: TextView = v.findViewById(R.id.txtAiMessage)
        val txtUser: TextView = v.findViewById(R.id.txtUserMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        if (msg.isUser) {
            holder.containerUser.visibility = View.VISIBLE
            holder.containerAi.visibility = View.GONE
            holder.txtUser.text = msg.text
        } else {
            holder.containerAi.visibility = View.VISIBLE
            holder.containerUser.visibility = View.GONE
            holder.txtAi.text = msg.text
        }
    }

    override fun getItemCount(): Int = messages.size
}