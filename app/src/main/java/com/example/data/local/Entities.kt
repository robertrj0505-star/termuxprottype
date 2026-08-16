package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val command: String,
    val timestamp: Long = System.currentTimeMillis(),
    val workingDir: String = "~",
    val exitCode: Int = 0
)

@Entity(tableName = "saved_scripts")
data class SavedScriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val code: String,
    val category: String = "General",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "installed_packages")
data class InstalledPackageEntity(
    @PrimaryKey
    val packageId: String,
    val installedAt: Long = System.currentTimeMillis()
)
