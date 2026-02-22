package com.hrishipvt.scantopdf.view

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert
    suspend fun insertMessage(chat: ChatEntity)

    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatEntity>>

    // DELETE messages older than 7 days (7 * 24 * 60 * 60 * 1000 ms)
    @Query("DELETE FROM chat_history WHERE timestamp < :expirationTime")
    suspend fun deleteOldMessages(expirationTime: Long)
}