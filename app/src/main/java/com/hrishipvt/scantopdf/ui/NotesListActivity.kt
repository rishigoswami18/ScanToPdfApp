package com.hrishipvt.scantopdf.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.hrishipvt.scantopdf.adapter.NotesAdapter
import com.hrishipvt.scantopdf.data.Note
import com.hrishipvt.scantopdf.databinding.ActivityNotesListBinding
import com.hrishipvt.scantopdf.viewmodel.NoteViewModel
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity
import kotlinx.coroutines.launch

class NotesListActivity : VoiceEnabledActivity() {

    private lateinit var binding: ActivityNotesListBinding
    private val viewModel: NoteViewModel by viewModels()
    private lateinit var notesAdapter: NotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVoiceAssistant()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    override fun voiceCommandHelp(): String {
        return "Try saying new note, search followed by text, open first note, open note followed by its title, or delete first note."
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        val searchQuery = textAfterCommand(rawCommand, "search ", "find note ", "search note ")
        val openQuery = textAfterCommand(rawCommand, "open note ")
        val deleteQuery = textAfterCommand(rawCommand, "delete note ", "remove note ")

        return when {
            normalizedCommand.contains("new") || normalizedCommand.contains("create") || normalizedCommand.contains("add") -> {
                speak("Creating a new note.")
                binding.fabAddNote.performClick()
                true
            }

            searchQuery.isNotEmpty() -> {
                binding.searchNotes.setText(searchQuery)
                speak("Searching notes for $searchQuery.")
                true
            }

            normalizedCommand.contains("open first") -> {
                val note = notesAdapter.currentList.firstOrNull()
                if (note != null) {
                    openNote(note)
                    speak("Opening the first note.")
                } else {
                    speak("There are no notes to open.")
                }
                true
            }

            openQuery.isNotEmpty() -> {
                val note = notesAdapter.currentList.firstOrNull {
                    it.title.contains(openQuery, ignoreCase = true) || it.content.contains(openQuery, ignoreCase = true)
                }
                if (note != null) {
                    openNote(note)
                    speak("Opening ${note.title}.")
                } else {
                    speak("I could not find a note matching $openQuery.")
                }
                true
            }

            normalizedCommand.contains("delete first") -> {
                val note = notesAdapter.currentList.firstOrNull()
                if (note != null) {
                    viewModel.deleteNote(note)
                    speak("Deleted ${note.title}.")
                } else {
                    speak("There are no notes to delete.")
                }
                true
            }

            deleteQuery.isNotEmpty() -> {
                val note = notesAdapter.currentList.firstOrNull {
                    it.title.contains(deleteQuery, ignoreCase = true) || it.content.contains(deleteQuery, ignoreCase = true)
                }
                if (note != null) {
                    viewModel.deleteNote(note)
                    speak("Deleted ${note.title}.")
                } else {
                    speak("I could not find a note matching $deleteQuery.")
                }
                true
            }

            normalizedCommand.contains("how many notes") || normalizedCommand.contains("status") -> {
                speak("You currently have ${notesAdapter.currentList.size} notes.")
                true
            }

            else -> false
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun openNote(note: Note) {
        val intent = Intent(this, NoteActivity::class.java).apply {
            putExtra("noteId", note.id)
            putExtra("title", note.title)
            putExtra("content", note.content)
        }
        startActivity(intent)
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(
            onClick = { note -> openNote(note) },
            onDelete = { note -> viewModel.deleteNote(note) }
        )

        binding.recyclerNotes.apply {
            layoutManager = LinearLayoutManager(this@NotesListActivity)
            adapter = notesAdapter
        }
    }

    private fun setupListeners() {
        binding.fabAddNote.setOnClickListener {
            startActivity(Intent(this, NoteActivity::class.java))
        }

        binding.btnCreateFirstNote.setOnClickListener {
            startActivity(Intent(this, NoteActivity::class.java))
        }

        binding.searchNotes.addTextChangedListener {
            viewModel.updateSearchQuery(it.toString())
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notes.collect { notes ->
                    notesAdapter.submitList(notes)
                    if (notes.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.recyclerNotes.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.recyclerNotes.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}
