package com.hrishipvt.scantopdf.adapter

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.utils.ScanSession

class PreviewPagerAdapter(
    private val pages: MutableList<Bitmap> // Use ScanSession.bitmaps reference directly
) : RecyclerView.Adapter<PreviewPagerAdapter.PageHolder>() {

    class PageHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.pageImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preview_page, parent, false)
        return PageHolder(view)
    }

    // FIX 1: Change 'RecyclerView.ViewHolder' to 'PageHolder'
    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        // FIX 2: Use the list passed in the constructor or ScanSession directly
        val bitmap = pages[position]

        // FIX 3: Use 'holder.image' (the name you defined in PageHolder)
        holder.image.setImageBitmap(bitmap)
    }

    // FIX 4: Ensure the count follows the actual data source
    override fun getItemCount(): Int = pages.size
}