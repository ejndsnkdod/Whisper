package com.nocturne.whisper.utils

import android.content.Context
import com.nocturne.whisper.data.db.AppDatabase
import com.nocturne.whisper.data.model.MemoryEntry
import kotlinx.coroutines.flow.Flow

class MemoryManager(context: Context) {

    private val memoryDao = AppDatabase.getDatabase(context).memoryDao()

    val allMemories: Flow<List<MemoryEntry>> = memoryDao.getAllMemories()

    suspend fun addMemory(content: String) {
        val keywords = MemoryEntry.extractKeywords(content)
        val memory = MemoryEntry(
            content = content,
            keywords = keywords
        )
        memoryDao.insertMemory(memory)
    }

    suspend fun searchMemories(query: String): List<MemoryEntry> {
        val results = mutableListOf<MemoryEntry>()

        val keywords = query.split(Regex("\\s+"))
        for (keyword in keywords) {
            if (keyword.length >= 2) {
                val matches = memoryDao.searchByKeyword(keyword)
                results.addAll(matches)
            }
        }

        val contentMatches = memoryDao.searchMemories(query)
        results.addAll(contentMatches)

        return results.distinctBy { it.id }
            .sortedByDescending { it.accessCount }
            .take(5)
    }

    suspend fun retrieveMemoriesByKeywords(keywords: List<String>): List<MemoryEntry> {
        val results = mutableListOf<MemoryEntry>()
        for (keyword in keywords) {
            if (keyword.length >= 2) {
                val matches = memoryDao.searchByKeyword(keyword)
                results.addAll(matches)
            }
        }
        return results.distinctBy { it.id }
            .sortedByDescending { it.accessCount }
            .take(5)
    }

    suspend fun retrieveRelevantMemories(userInput: String): List<MemoryEntry> {
        val keywords = extractKeywords(userInput)
        return retrieveMemoriesByKeywords(keywords)
    }

    private fun extractKeywords(text: String): List<String> {
        return text.replace(Regex("[\\p{P}\\p{S}]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .distinct()
            .take(5)
    }

    suspend fun incrementAccessCount(memoryId: String) {
        memoryDao.updateAccessCount(memoryId)
    }

    suspend fun deleteMemory(memory: MemoryEntry) {
        memoryDao.deleteMemory(memory)
    }

    suspend fun clearAllMemories() {
        memoryDao.deleteAllMemories()
    }

    suspend fun getMemoryCount(): Int {
        return memoryDao.getMemoryCount()
    }

    fun parseAddMemoryTags(response: String): Pair<String, List<String>> {
        val memoriesToAdd = mutableListOf<String>()
        val pattern = Regex("<add:(.+?)>")
        val cleanedResponse = pattern.replace(response) { matchResult ->
            val content = matchResult.groupValues[1].trim()
            if (content.isNotEmpty()) {
                memoriesToAdd.add(content)
            }
            ""
        }.trim()
        return Pair(cleanedResponse, memoriesToAdd)
    }

    fun parseSearchMemoryTags(response: String): Pair<String, List<String>> {
        val keywordsToSearch = mutableListOf<String>()
        val pattern = Regex("<search:(.+?)>")
        val cleanedResponse = pattern.replace(response) { matchResult ->
            val keyword = matchResult.groupValues[1].trim()
            if (keyword.isNotEmpty()) {
                keywordsToSearch.add(keyword)
            }
            ""
        }.trim()
        return Pair(cleanedResponse, keywordsToSearch)
    }

    companion object {
        @Volatile
        private var INSTANCE: MemoryManager? = null

        fun getInstance(context: Context): MemoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MemoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
