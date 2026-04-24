package com.hrishipvt.scantopdf.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrishipvt.scantopdf.data.Note
import com.hrishipvt.scantopdf.data.NoteDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val noteDao = NoteDatabase.getDatabase(application).noteDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes = _searchQuery.flatMapLatest { query ->
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "local"
        if (query.isEmpty()) {
            noteDao.getAllNotes(userId)
        } else {
            noteDao.searchNotes(query, userId)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun saveNote(note: Note) {
        viewModelScope.launch {
            if (note.id == 0) {
                noteDao.insert(note)
            } else {
                noteDao.update(note)
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteDao.delete(note)
        }
    }
}
