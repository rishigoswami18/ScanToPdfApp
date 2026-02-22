package com.hrishipvt.scantopdf.ui

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.data.Note
import com.hrishipvt.scantopdf.data.NoteDatabase
import com.hrishipvt.scantopdf.utils.FirebaseNoteBackup
import com.hrishipvt.scantopdf.utils.NotePdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etContent: EditText

    private var noteId: Int = -1   // ✅ Used for Edit Mode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note)

        val toolbar: MaterialToolbar = findViewById(R.id.noteToolbar)
        etTitle = findViewById(R.id.etNoteTitle)
        etContent = findViewById(R.id.etNoteContent)

        val btnSave: MaterialButton = findViewById(R.id.btnSaveNote)
        val btnShare: MaterialButton = findViewById(R.id.btnShareNote)
        val btnPdf: MaterialButton = findViewById(R.id.btnConvertPdf)
        val btnBackup: MaterialButton = findViewById(R.id.btnBackup)
        val btnViewNotes: MaterialButton = findViewById(R.id.btnViewNotes)

        btnViewNotes.setOnClickListener {
            startActivity(Intent(this, NotesListActivity::class.java))
        }



        // ✅ Back Button
        toolbar.setNavigationOnClickListener { finish() }

        // ✅ Get Intent Data (Edit Note Mode)
        noteId = intent.getIntExtra("noteId", -1)
        val oldTitle = intent.getStringExtra("title")
        val oldContent = intent.getStringExtra("content")

        if (noteId != -1) {
            etTitle.setText(oldTitle)
            etContent.setText(oldContent)
            toolbar.title = "Edit Note"
        } else {
            toolbar.title = "New Note"
        }

        // ✅ Save Note
        btnSave.setOnClickListener {
            saveNote()
        }


        // ✅ Share Note
        btnShare.setOnClickListener {
            shareNote()
        }

        // ✅ Convert Note → PDF
        btnPdf.setOnClickListener {
            convertToPdf()
        }

        // ✅ Backup Note → Firebase
        btnBackup.setOnClickListener {
            backupToFirebase()
        }
    }

    // ✅ SAVE or UPDATE NOTE
    private fun saveNote() {

        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()

        if (content.isEmpty()) {
            Toast.makeText(this, "Note is empty!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {

            val db = NoteDatabase.getDatabase(this@NoteActivity)
            val dao = db.noteDao()

            val note = Note(
                id = if (noteId == -1) 0 else noteId,
                title = if (title.isEmpty()) "Untitled Note" else title,
                content = content
            )

            if (noteId == -1) {
                dao.insert(note)
            } else {
                dao.update(note)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@NoteActivity, "✅ Note Saved!", Toast.LENGTH_SHORT).show()

                // ✅ Go Back to Notes List
                val intent = Intent(this@NoteActivity, NotesListActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)

                finish()
            }

        }
    }

    // ✅ DELETE CONFIRMATION
    private fun confirmDelete() {

        AlertDialog.Builder(this)
            .setTitle("Delete Note?")
            .setMessage("Are you sure you want to delete this note permanently?")
            .setPositiveButton("Delete") { _, _ ->
                deleteNote()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteNote() {

        lifecycleScope.launch(Dispatchers.IO) {

            val db = NoteDatabase.getDatabase(this@NoteActivity)
            val dao = db.noteDao()

            val note = Note(
                id = noteId,
                title = "",
                content = ""
            )

            dao.delete(note)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@NoteActivity, "🗑 Note Deleted!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // ✅ SHARE NOTE
    private fun shareNote() {

        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()

        if (content.isEmpty()) {
            Toast.makeText(this, "Nothing to share!", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }

        startActivity(Intent.createChooser(intent, "Share Note via"))
    }

    // ✅ CONVERT NOTE → PDF
    private fun convertToPdf() {

        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()

        if (content.isEmpty()) {
            Toast.makeText(this, "Write something first!", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfFile = NotePdfUtils.createPdf(
            this,
            if (title.isEmpty()) "Note" else title,
            content
        )

        Toast.makeText(
            this,
            "✅ PDF Saved:\n${pdfFile.absolutePath}",
            Toast.LENGTH_LONG
        ).show()
    }

    // ✅ BACKUP NOTE → FIREBASE
    private fun backupToFirebase() {

        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()

        if (content.isEmpty()) {
            Toast.makeText(this, "Empty note cannot backup!", Toast.LENGTH_SHORT).show()
            return
        }

        val note = Note(
            title = if (title.isEmpty()) "Untitled Note" else title,
            content = content
        )

        FirebaseNoteBackup.backup(note)

        Toast.makeText(this, "✅ Note Backed Up to Firebase!", Toast.LENGTH_SHORT).show()
    }
}
