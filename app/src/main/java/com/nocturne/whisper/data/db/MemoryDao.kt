package com.nocturne.whisper.data.db

import androidx.room.*
import com.nocturne.whisper.data.model.MemoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<MemoryEntry>>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    suspend fun getAllMemoriesList(): List<MemoryEntry>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: String): MemoryEntry?

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :keyword || '%' OR keywords LIKE '%' || :keyword || '%'")
    suspend fun searchMemories(keyword: String): List<MemoryEntry>

    @Query("SELECT * FROM memories WHERE keywords LIKE '%' || :keyword || '%'")
    suspend fun searchByKeyword(keyword: String): List<MemoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<MemoryEntry>)

    @Update
    suspend fun updateMemory(memory: MemoryEntry)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntry)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getMemoryCount(): Int

    @Query("UPDATE memories SET accessCount = accessCount + 1, lastAccessed = :timestamp WHERE id = :id")
    suspend fun updateAccessCount(id: String, timestamp: Long = System.currentTimeMillis())
}
