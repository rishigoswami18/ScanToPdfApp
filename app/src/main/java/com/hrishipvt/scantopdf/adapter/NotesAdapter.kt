package com.hrishipvt.scantopdf.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.data.Note

class NotesAdapter(
    private var notes: List<Note>,
    val onClick: (Note) -> Unit,
    val onDelete: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteHolder>() {

    class NoteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.txtTitle)
        val content: TextView = itemView.findViewById(R.id.txtContent)
        val delete: ImageView = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteHolder(view)
    }

    override fun onBindViewHolder(holder: NoteHolder, position: Int) {

        val note = notes[position]

        holder.title.text = note.title
        holder.content.text =
            if (note.content.length > 60)
                note.content.substring(0, 60) + "..."
            else
                note.content

        holder.itemView.setOnClickListener {
            onClick(note)
        }

        holder.delete.setOnClickListener {
            onDelete(note)
        }
    }

    override fun getItemCount(): Int = notes.size

    fun updateList(newList: List<Note>) {
        notes = newList
        notifyDataSetChanged()
    }
}
