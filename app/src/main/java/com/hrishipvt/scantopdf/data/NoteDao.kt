package com.hrishipvt.scantopdf.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes WHERE userId = :userId ORDER BY time DESC")
    fun getAllNotes(userId: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY time DESC")
    fun searchNotes(query: String, userId: String): Flow<List<Note>>

    @Query("UPDATE notes SET userId = :userId WHERE userId = ''")
    suspend fun claimAllOrphanedNotes(userId: String)
}
