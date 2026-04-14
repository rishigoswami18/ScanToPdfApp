package com.hrishipvt.scantopdf.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.view.PdfTool

class PdfToolsAdapter(
    private val tools: List<PdfTool>,
    private val onClick: (PdfTool) -> Unit
) : RecyclerView.Adapter<PdfToolsAdapter.ToolViewHolder>() {

    class ToolViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconContainer: FrameLayout = view.findViewById(R.id.iconContainer)
        val icon: ImageView = view.findViewById(R.id.imgToolIcon)
        val title: TextView = view.findViewById(R.id.txtToolTitle)
        val subtitle: TextView = view.findViewById(R.id.txtToolSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_tool, parent, false)
        return ToolViewHolder(view)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        val tool = tools[position]
        val context = holder.itemView.context
        val accentColor = ContextCompat.getColor(context, tool.accentColorRes)

        holder.title.text = tool.title
        holder.subtitle.text = tool.subtitle
        holder.icon.setImageResource(tool.iconRes)
        holder.icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface))
        holder.iconContainer.backgroundTintList = ColorStateList.valueOf(accentColor)

        holder.itemView.setOnClickListener { onClick(tool) }
    }

    override fun getItemCount() = tools.size
}
