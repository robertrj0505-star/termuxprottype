package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CommandHistoryEntity::class,
        SavedScriptEntity::class,
        InstalledPackageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TerminalDatabase : RoomDatabase() {
    abstract fun terminalDao(): TerminalDao

    companion object {
        @Volatile
        private var INSTANCE: TerminalDatabase? = null

        fun getInstance(context: Context): TerminalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TerminalDatabase::class.java,
                    "terminal_database.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
