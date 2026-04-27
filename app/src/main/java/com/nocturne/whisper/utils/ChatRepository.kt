package com.nocturne.whisper.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nocturne.whisper.data.model.ChatSession
import com.nocturne.whisper.data.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ChatRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions

    init {
        loadSessions()
    }

    private fun loadSessions() {
        val json = prefs.getString(KEY_SESSIONS, null)
        if (json != null) {
            val type = object : TypeToken<List<ChatSession>>() {}.type
            val list: List<ChatSession> = gson.fromJson(json, type) ?: emptyList()
            _sessions.value = list.sortedByDescending { it.updatedAt }
        }
    }

    private fun saveSessions() {
        val json = gson.toJson(_sessions.value)
        prefs.edit().putString(KEY_SESSIONS, json).apply()
    }

    fun createSession(title: String = "新对话", personaId: String? = null): ChatSession {
        val session = ChatSession(
            title = title,
            personaId = personaId
        )
        val currentList = _sessions.value.toMutableList()
        currentList.add(session)
        _sessions.value = currentList
        saveSessions()
        return session
    }

    fun getSession(id: String): ChatSession? {
        return _sessions.value.find { it.id == id }
    }

    fun updateSession(session: ChatSession) {
        val currentList = _sessions.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == session.id }
        if (index != -1) {
            currentList[index] = session.copy(updatedAt = System.currentTimeMillis())
            _sessions.value = currentList.sortedByDescending { it.updatedAt }
            saveSessions()
        }
    }

    fun deleteSession(id: String) {
        val currentList = _sessions.value.toMutableList()
        currentList.removeAll { it.id == id }
        _sessions.value = currentList
        saveSessions()
    }

    fun addMessage(sessionId: String, message: Message) {
        val session = getSession(sessionId) ?: return
        session.messages.add(message)
        updateSession(session)
    }

    fun clearSessionMessages(sessionId: String) {
        val session = getSession(sessionId) ?: return
        session.messages.clear()
        updateSession(session)
    }

    companion object {
        private const val PREFS_NAME = "chat_repository"
        private const val KEY_SESSIONS = "sessions"

        @Volatile
        private var INSTANCE: ChatRepository? = null

        fun getInstance(context: Context): ChatRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
