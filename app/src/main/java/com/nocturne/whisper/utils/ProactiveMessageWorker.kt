package com.nocturne.whisper.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nocturne.whisper.R
import com.nocturne.whisper.data.api.RetrofitClient
import com.nocturne.whisper.data.model.ChatMessage
import com.nocturne.whisper.data.model.ChatRequest
import com.nocturne.whisper.data.model.MessageContent
import com.nocturne.whisper.ui.chat.ChatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ProactiveMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_TAG = "proactive_message_work"
        const val CHANNEL_ID = "proactive_message_channel"
        const val CHANNEL_NAME = "主动消息"
        const val NOTIFICATION_ID_BASE = 10000

        private const val TAG = "ProactiveMessageWorker"
    }

    private val settingsManager = SettingsManager.getInstance(context)
    private val memoryManager = MemoryManager(context)

    override suspend fun doWork(): Result {
        Log.d(TAG, "ProactiveMessageWorker started")

        val settings = settingsManager.getSettings()

        if (!settings.isActiveMessageEnabled) {
            Log.d(TAG, "Active message disabled, skipping")
            return Result.success()
        }

        if (!settings.isBackgroundMessageEnabled && !isAppInForeground(applicationContext)) {
            Log.d(TAG, "Background message disabled and app not in foreground, skipping")
            return Result.success()
        }

        if (settings.apiKey.isEmpty()) {
            Log.e(TAG, "API key not configured")
            return Result.failure()
        }

        return try {
            val response = generateProactiveMessage(settings)
            if (response != null) {

                saveMessageToSession(response)

                sendNotification(response)
                Log.d(TAG, "Proactive message sent: $response")
                Result.success()
            } else {
                Log.e(TAG, "Failed to generate proactive message")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in proactive message worker", e)
            Result.retry()
        }
    }

    private suspend fun generateProactiveMessage(settings: com.nocturne.whisper.data.model.ChatSettings): String? {
        return withContext(Dispatchers.IO) {
            try {

                val persona = PersonaManager.getCurrentPersona()
                val basePrompt = if (persona != null) {
                    """你是${persona.name}，${persona.description}

人设特点：${persona.personality}
场景：${persona.scenario}

现在请主动给用户发一条消息，根据角色人设主动联系用户。消息应该自然、有情感，体现角色特点。
不要重复之前说过的话，可以是关心、分享、或者想引起用户注意的内容。
请直接写出消息内容，不需要加任何前缀。"""
                } else {
                    """你现在是一个AI聊天伙伴。请主动给用户发一条消息，可以是关心、分享或者想聊天的话题。
消息应该自然、有情感，让用户愿意回复。
请直接写出消息内容，不需要加任何前缀。"""
                }

                var memoryContext = ""
                if (settings.isMemoryEnabled) {
                    val sessionId = ChatSessionManager.currentSessionId
                    if (sessionId != null) {
                        val recentMessages = ChatSessionManager.loadSessionHistory(applicationContext, sessionId)
                            .takeLast(5)
                        val contextText = recentMessages.joinToString(" ") { it.content }
                        val memories = memoryManager.retrieveRelevantMemories(contextText)
                        if (memories.isNotEmpty()) {
                            memoryContext = "\n\n相关记忆：\n" +
                                memories.joinToString("\n") { "- ${it.content}" }
                        }
                    }
                }

                val systemPrompt = basePrompt + memoryContext

                val baseUrl = settingsManager.getApiBaseUrl().trimEnd('/')
                val chatUrl = if (baseUrl.endsWith("chat/completions")) baseUrl else "$baseUrl/chat/completions"
                val api = RetrofitClient.getInstance().getApi()

                val request = ChatRequest(
                    model = settings.modelName,
                    messages = listOf(
                        ChatMessage.text(ChatMessage.ROLE_SYSTEM, systemPrompt),
                        ChatMessage.text(ChatMessage.ROLE_USER, "请主动发一条消息")
                    ),
                    stream = false,
                    max_tokens = 200,
                    temperature = settings.temperature.coerceIn(0.7f, 1.0f)
                )

                val response = api.chatCompletion(
                    chatUrl,
                    authorization = "Bearer ${settings.apiKey}",
                    request = request
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    when (val content = body?.choices?.firstOrNull()?.message?.content) {
                        is MessageContent.Text -> content.text.trim()
                        is MessageContent.MultiModal -> content.parts.firstOrNull { it.type == "text" }?.text?.trim()
                        null -> null
                    }
                } else {
                    Log.e(TAG, "API error: ${response.code()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating message", e)
                null
            }
        }
    }

    private suspend fun saveMessageToSession(content: String) {
        withContext(Dispatchers.IO) {
            try {
                val sessionId = ChatSessionManager.currentSessionId
                if (sessionId == null) {
                    Log.e(TAG, "No current session")
                    return@withContext
                }

                val messages = ChatSessionManager.loadSessionHistory(applicationContext, sessionId).toMutableList()

                val aiMessage = com.nocturne.whisper.data.model.Message(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    type = com.nocturne.whisper.data.model.MessageType.ASSISTANT,
                    timestamp = System.currentTimeMillis()
                )

                messages.add(aiMessage)

                ChatSessionManager.saveSessionHistory(applicationContext, sessionId, messages)

                val session = ChatSessionManager.getCurrentSession()
                session?.let {
                    val updatedSession = it.copy(
                        updatedAt = System.currentTimeMillis(),
                        messages = messages
                    )
                    ChatSessionManager.updateSession(applicationContext, updatedSession)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving message", e)
            }
        }
    }

    private fun sendNotification(content: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "AI主动发送的消息通知"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val persona = PersonaManager.getCurrentPersona()
        val title = persona?.name ?: "AI伴侣"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content.take(50) + if (content.length > 50) "..." else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = NOTIFICATION_ID_BASE + System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
    }

    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = context.packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }
}
