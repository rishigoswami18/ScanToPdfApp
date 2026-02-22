package com.hrishipvt.scantopdf.data

import androidx.room.*

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes ORDER BY time DESC")
    suspend fun getAllNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' ")
    suspend fun search(query: String): List<Note>
}
