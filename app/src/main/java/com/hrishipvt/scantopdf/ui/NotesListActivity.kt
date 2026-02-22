package com.hrishipvt.scantopdf.ui

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.adapter.NotesAdapter
import com.hrishipvt.scantopdf.data.NoteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotesAdapter
    private lateinit var searchBar: EditText

    private var notesList = ArrayList<com.hrishipvt.scantopdf.data.Note>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes_list)

        recyclerView = findViewById(R.id.recyclerNotes)
        searchBar = findViewById(R.id.searchNotes)

        val fabAdd: FloatingActionButton = findViewById(R.id.fabAddNote)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = NotesAdapter(
            notesList,
            onClick = { note ->
                val intent = Intent(this, NoteActivity::class.java)
                intent.putExtra("noteId", note.id)
                intent.putExtra("title", note.title)
                intent.putExtra("content", note.content)
                startActivity(intent)
            },
            onDelete = { note ->
                deleteNote(note)
            }
        )

        recyclerView.adapter = adapter

        // ✅ Add New Note
        fabAdd.setOnClickListener {
            startActivity(Intent(this, NoteActivity::class.java))
        }

        // ✅ Search Notes Live
        searchBar.addTextChangedListener {
            filterNotes(it.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    // ✅ Load Notes from Database
    private fun loadNotes() {

        lifecycleScope.launch(Dispatchers.IO) {

            val db = NoteDatabase.getDatabase(this@NotesListActivity)
            val notes = db.noteDao().getAllNotes()

            withContext(Dispatchers.Main) {

                notesList.clear()
                notesList.addAll(notes)

                adapter.notifyDataSetChanged()
            }
        }
    }

    // ✅ Delete Note
    private fun deleteNote(note: com.hrishipvt.scantopdf.data.Note) {

        lifecycleScope.launch(Dispatchers.IO) {

            val db = NoteDatabase.getDatabase(this@NotesListActivity)
            db.noteDao().delete(note)

            loadNotes()
        }
    }

    // ✅ Filter Notes
    private fun filterNotes(query: String) {

        val filtered = notesList.filter {
            it.title.contains(query, true) ||
                    it.content.contains(query, true)
        }

        adapter.updateList(filtered)
    }
}
