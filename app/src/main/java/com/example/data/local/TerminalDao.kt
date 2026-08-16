package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TerminalDao {
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 200")
    fun getAllHistory(): Flow<List<CommandHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: CommandHistoryEntity): Long

    @Query("DELETE FROM command_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM saved_scripts ORDER BY isFavorite DESC, createdAt DESC")
    fun getAllScripts(): Flow<List<SavedScriptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: SavedScriptEntity): Long

    @Update
    suspend fun updateScript(script: SavedScriptEntity)

    @Delete
    suspend fun deleteScript(script: SavedScriptEntity)

    @Query("SELECT * FROM installed_packages")
    fun getAllInstalledPackages(): Flow<List<InstalledPackageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun installPackage(pkg: InstalledPackageEntity)

    @Query("DELETE FROM installed_packages WHERE packageId = :pkgId")
    suspend fun uninstallPackage(pkgId: String)
}
