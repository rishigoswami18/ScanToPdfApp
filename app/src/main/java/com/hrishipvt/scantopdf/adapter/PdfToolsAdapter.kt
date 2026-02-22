package com.hrishipvt.scantopdf.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.view.PdfTool

class PdfToolsAdapter(
    private val tools: List<PdfTool>,
    private val onClick: (PdfTool) -> Unit
) : RecyclerView.Adapter<PdfToolsAdapter.ToolViewHolder>() {

    // ViewHolder holds references to the views for each tool card
    class ToolViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgToolIcon)
        val title: TextView = view.findViewById(R.id.txtToolTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        // Inflate the professional card layout
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_tool, parent, false)
        return ToolViewHolder(view)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        val tool = tools[position]
        holder.title.text = tool.title
        holder.icon.setImageResource(tool.iconRes)

        // Handle item clicks
        holder.itemView.setOnClickListener { onClick(tool) }
    }

    override fun getItemCount() = tools.size
}