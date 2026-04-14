package com.hrishipvt.scantopdf.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.data.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesAdapter(
    private val onClick: (Note) -> Unit,
    private val onDelete: (Note) -> Unit
) : ListAdapter<Note, NotesAdapter.NoteHolder>(NoteDiffCallback()) {

    class NoteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.txtTitle)
        val content: TextView = itemView.findViewById(R.id.txtContent)
        val date: TextView = itemView.findViewById(R.id.txtDate)
        val delete: ImageView = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteHolder(view)
    }

    override fun onBindViewHolder(holder: NoteHolder, position: Int) {
        val note = getItem(position)

        holder.title.text = note.title
        holder.content.text = note.content
        
        val sdf = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        holder.date.text = sdf.format(Date(note.time))

        holder.itemView.setOnClickListener {
            onClick(note)
        }

        holder.delete.setOnClickListener {
            onDelete(note)
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }
    }
}
