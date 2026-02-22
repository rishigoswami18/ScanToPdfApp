package com.hrishipvt.scantopdf.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
// Ensure this import points to where your ChatEntity is located
import com.hrishipvt.scantopdf.view.ChatEntity
import com.hrishipvt.scantopdf.view.ChatDao

@Database(entities = [ChatEntity::class], version = AppDatabase.DATABASE_VERSION)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

    companion object {
        // FIX: Must use 'const val' so the annotation can see it at compile time
        const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_database"
                )
                    // Wipes the database if you change the schema (useful for dev)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}