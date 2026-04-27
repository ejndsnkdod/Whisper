package com.nocturne.whisper.data.model

data class ModelProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val apiKey: String = "",
    val apiUrl: String = "",
    val modelName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
