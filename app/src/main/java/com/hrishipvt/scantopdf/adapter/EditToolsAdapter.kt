package com.hrishipvt.scantopdf.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.view.EditTool

class EditToolsAdapter(
    private val tools: List<EditTool>,
    private val onToolClick: (EditTool) -> Unit
) : RecyclerView.Adapter<EditToolsAdapter.ToolHolder>() {

    class ToolHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgToolIcon)
        val name: TextView = view.findViewById(R.id.txtToolName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_edit_tool, parent, false)
        return ToolHolder(v)
    }

    override fun onBindViewHolder(holder: ToolHolder, position: Int) {
        val tool = tools[position]
        holder.name.text = tool.name
        holder.icon.setImageResource(tool.icon)
        holder.itemView.setOnClickListener { onToolClick(tool) }
    }

    override fun getItemCount() = tools.size
}