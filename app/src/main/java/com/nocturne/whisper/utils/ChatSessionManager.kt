package com.nocturne.whisper.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nocturne.whisper.data.model.ChatSession
import com.nocturne.whisper.data.model.Message
import com.nocturne.whisper.data.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object ChatSessionManager {

    private const val TAG = "ChatSessionManager"
    private const val SESSIONS_FILE = "chat_sessions.json"
    private const val CURRENT_SESSION_KEY = "current_session_id"
    private val gson = Gson()

    private var sessions: MutableList<ChatSession> = mutableListOf()

    var currentSessionId: String? = null
        private set

    fun init(context: Context) {
        loadSessions(context)
        currentSessionId = getSavedCurrentSessionId(context)
        Log.d(TAG, "初始化完成，共 ${sessions.size} 个会话，当前: $currentSessionId")
    }

    fun getAllSessions(): List<ChatSession> {
        return sessions.sortedByDescending { it.updatedAt }
    }

    fun getCurrentSession(): ChatSession? {
        return sessions.find { it.id == currentSessionId }
    }

    fun getSessionHistoryFileName(sessionId: String): String {
        return "chat_history_$sessionId.json"
    }

    fun createSession(context: Context, title: String = "新对话", personaId: String? = null): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            personaId = personaId
        )
        sessions.add(session)
        saveSessions(context)

        switchSession(context, session.id)

        Log.d(TAG, "创建新会话: ${session.id}, 标题: $title")
        return session
    }

    fun switchSession(context: Context, sessionId: String): Boolean {
        if (sessions.none { it.id == sessionId }) {
            Log.e(TAG, "切换失败，会话不存在: $sessionId")
            return false
        }

        currentSessionId = sessionId
        saveCurrentSessionId(context, sessionId)
        Log.d(TAG, "切换到会话: $sessionId")
        return true
    }

    fun deleteSession(context: Context, sessionId: String): Boolean {
        val removed = sessions.removeIf { it.id == sessionId }
        if (removed) {

            val historyFile = File(context.filesDir, getSessionHistoryFileName(sessionId))
            if (historyFile.exists()) {
                historyFile.delete()
            }

            if (currentSessionId == sessionId) {
                currentSessionId = sessions.maxByOrNull { it.updatedAt }?.id
                saveCurrentSessionId(context, currentSessionId)
            }

            saveSessions(context)
            Log.d(TAG, "删除会话: $sessionId")
        }
        return removed
    }

    fun updateSession(context: Context, session: ChatSession) {
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index != -1) {
            sessions[index] = session.copy(updatedAt = System.currentTimeMillis())
            saveSessions(context)
        }
    }

    fun updateSessionTitle(context: Context, sessionId: String, messages: List<Message>) {
        val session = sessions.find { it.id == sessionId } ?: return

        val firstUserMessage = messages.find { it.type == MessageType.USER }
        val newTitle = if (firstUserMessage != null) {
            firstUserMessage.content.take(20).let {
                if (firstUserMessage.content.length > 20) "$it..." else it
            }
        } else {
            "新对话"
        }

        if (session.title.startsWith("新对话") || session.title != newTitle) {
            updateSession(context, session.copy(title = newTitle))
        }
    }

    suspend fun loadSessionHistory(context: Context, sessionId: String): List<Message> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, getSessionHistoryFileName(sessionId))
                if (!file.exists()) {
                    return@withContext emptyList()
                }

                val json = file.readText(Charsets.UTF_8)
                val type = object : TypeToken<List<Message>>() {}.type
                gson.fromJson<List<Message>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "加载历史失败: $sessionId", e)
                emptyList()
            }
        }
    }

    suspend fun saveSessionHistory(context: Context, sessionId: String, messages: List<Message>) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, getSessionHistoryFileName(sessionId))
                val json = gson.toJson(messages)
                file.writeText(json, Charsets.UTF_8)

                val session = sessions.find { it.id == sessionId }
                if (session != null) {
                    updateSession(context, session)
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存历史失败: $sessionId", e)
            }
            Unit
        }
    }

    fun ensureSession(context: Context): ChatSession {
        var session = getCurrentSession()
        if (session == null) {

            session = createSession(context, "默认对话")
        }
        return session
    }

    private fun loadSessions(context: Context) {
        try {
            val file = File(context.filesDir, SESSIONS_FILE)
            if (!file.exists()) {
                sessions = mutableListOf()
                return
            }

            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<ChatSession>>() {}.type
            sessions = (gson.fromJson<List<ChatSession>>(json, type) ?: emptyList()).toMutableList()
        } catch (e: Exception) {
            Log.e(TAG, "加载会话列表失败", e)
            sessions = mutableListOf()
        }
    }

    private fun saveSessions(context: Context) {
        try {
            val file = File(context.filesDir, SESSIONS_FILE)
            val json = gson.toJson(sessions)
            file.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "保存会话列表失败", e)
        }
    }

    private fun getSavedCurrentSessionId(context: Context): String? {
        return context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
            .getString(CURRENT_SESSION_KEY, null)
    }

    private fun saveCurrentSessionId(context: Context, sessionId: String?) {
        context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(CURRENT_SESSION_KEY, sessionId)
            .apply()
    }
}
