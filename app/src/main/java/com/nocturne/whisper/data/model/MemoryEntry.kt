package com.nocturne.whisper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntry(
    @PrimaryKey
    val id: String = System.currentTimeMillis().toString(),
    val content: String = "",
    val keywords: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis(),
    val accessCount: Int = 0
) {
    fun getKeywordList(): List<String> {
        return keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        fun extractKeywords(content: String): String {

            val cleaned = content.replace(Regex("[\\p{P}\\p{S}]"), " ")
            val words = cleaned.split(Regex("\\s+"))
                .filter { it.length >= 2 }
                .distinct()
                .take(5)
            return words.joinToString(",")
        }
    }
}
