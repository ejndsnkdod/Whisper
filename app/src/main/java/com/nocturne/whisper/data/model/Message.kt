package com.nocturne.whisper.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class MessageType {
    USER,
    ASSISTANT,
    SYSTEM
}

@Parcelize
data class Message(
    val id: String = System.currentTimeMillis().toString(),
    val content: String = "",
    val type: MessageType = MessageType.USER,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val imageUri: String? = null,
    val personaId: String? = null
) : Parcelable {

    fun isFromUser(): Boolean = type == MessageType.USER
    fun isFromAssistant(): Boolean = type == MessageType.ASSISTANT
    fun isSystem(): Boolean = type == MessageType.SYSTEM
}

@Parcelize
data class ChatSession(
    val id: String = System.currentTimeMillis().toString(),
    val title: String = "",
    val personaId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: MutableList<Message> = mutableListOf()
) : Parcelable
