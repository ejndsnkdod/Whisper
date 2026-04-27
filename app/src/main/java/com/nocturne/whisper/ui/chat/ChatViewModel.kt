package com.nocturne.whisper.ui.chat

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.nocturne.whisper.data.api.RetrofitClient
import com.nocturne.whisper.data.model.*
import com.nocturne.whisper.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "ChatViewModel"
        const val KIMI_CODE_AGENT_PROMPT =
            "You are Claude Code, a coding agent working in a terminal with access to tools, files, and shell commands."
    }

    private val settingsManager = SettingsManager.getInstance(application)
    private val memoryManager = MemoryManager.getInstance(application)
    private val retrofitClient = RetrofitClient.getInstance()
    private val gson = Gson()
    private val execModeManager = ExecModeManager

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentPersonaName = MutableStateFlow<String?>(null)
    val currentPersonaName: StateFlow<String?> = _currentPersonaName.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private fun settings() = settingsManager.getSettings()

    init {

        ChatSessionManager.init(application)

        viewModelScope.launch {
            initSession()
        }
    }

    private suspend fun initSession() {
        val context = getApplication<Application>()

        val session = ChatSessionManager.ensureSession(context)
        _currentSession.value = session

        loadHistory()

        val persona = PersonaManager.getCurrentPersona()
        _currentPersonaName.value = persona?.name ?: "AI助手"

        refreshSessions()
    }

    private suspend fun loadHistory() {
        val context = getApplication<Application>()
        val sessionId = ChatSessionManager.currentSessionId ?: return

        val history = ChatSessionManager.loadSessionHistory(context, sessionId)

        val limit = settings().maxHistoryMessages
        _messages.value = if (history.size > limit) {
            history.takeLast(limit)
        } else {
            history
        }
    }

    private fun saveHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val sessionId = ChatSessionManager.currentSessionId ?: return@launch

            ChatSessionManager.saveSessionHistory(context, sessionId, _messages.value)

            ChatSessionManager.updateSessionTitle(context, sessionId, _messages.value)

            refreshSessions()
        }
    }

    fun refreshSessions() {
        _sessions.value = ChatSessionManager.getAllSessions()
        _currentSession.value = ChatSessionManager.getCurrentSession()
    }

    fun createNewSession() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val persona = PersonaManager.getCurrentPersona()

            val newSession = ChatSessionManager.createSession(
                context,
                title = "新对话",
                personaId = PersonaManager.currentPersonaId
            )

            _currentSession.value = newSession
            _messages.value = emptyList()
            refreshSessions()
        }
    }

    fun switchToSession(sessionId: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()

            if (ChatSessionManager.switchSession(context, sessionId)) {
                _currentSession.value = ChatSessionManager.getCurrentSession()
                loadHistory()
                refreshSessions()
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()

            ChatSessionManager.deleteSession(context, sessionId)

            if (_currentSession.value?.id == sessionId) {
                _currentSession.value = ChatSessionManager.getCurrentSession()
                loadHistory()
            }

            refreshSessions()
        }
    }

    fun sendMessage(content: String, imageUri: String? = null) {
        viewModelScope.launch {
            try {
                val settings = settings()

                val context = getApplication<Application>()
                val session = ChatSessionManager.ensureSession(context)
                _currentSession.value = session

                val userMessage = Message(content = content, type = MessageType.USER, imageUri = imageUri)
                _messages.value = _messages.value + userMessage

                saveHistory()

                _isLoading.value = true

                var memoryContext = ""
                if (settings.isMemoryEnabled) {
                    val relevantMemories = memoryManager.retrieveRelevantMemories(content)
                    if (relevantMemories.isNotEmpty()) {
                        memoryContext = "相关记忆：\n" +
                            relevantMemories.joinToString("\n") { "- ${it.content}" } + "\n\n"
                    }
                }

                val requestMessages = buildRequestMessages(content, memoryContext, imageUri)
                val shouldUseStream = shouldUseStreamResponse(settings)

                if (shouldUseStream) {
                    sendStreamChatRequest(requestMessages)
                } else {
                    val response = sendChatRequest(requestMessages)
                    if (response != null) {
                        processResponse(response, requestMessages)
                        saveHistory()
                    }
                }

            } catch (e: Exception) {
                _error.value = "发送失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun buildRequestMessages(
        userContent: String,
        memoryContext: String,
        imageUri: String? = null
    ): List<ChatMessage> {
        val settings = settings()
        val messages = mutableListOf<ChatMessage>()

        if (settings.apiProvider == ApiProvider.KIMI_CODE) {
            messages.add(ChatMessage.text(ChatMessage.ROLE_SYSTEM, KIMI_CODE_AGENT_PROMPT))
        }

        val systemPrompt = PersonaManager.buildSystemPrompt(settingsManager)
        val fullSystemPrompt = if (memoryContext.isNotEmpty()) {
            "$systemPrompt\n\n$memoryContext"
        } else {
            systemPrompt
        }

        messages.add(ChatMessage.text(ChatMessage.ROLE_SYSTEM, fullSystemPrompt))

        if (settings.isChatHistoryEnabled) {

            val historyMessages = _messages.value
                .dropLast(1)
                .filterNot { it.type == MessageType.ASSISTANT && it.content.isBlank() }
                .takeLast(settings.maxHistoryMessages - 1)
            historyMessages.forEach { msg ->
                val role = when (msg.type) {
                    MessageType.USER -> ChatMessage.ROLE_USER
                    MessageType.ASSISTANT -> ChatMessage.ROLE_ASSISTANT
                    MessageType.SYSTEM -> ChatMessage.ROLE_SYSTEM
                }
                messages.add(ChatMessage.text(role, msg.content))
            }
        }

        if (imageUri != null) {
            val context = getApplication<Application>()
            val base64 = uriToBase64(context, android.net.Uri.parse(imageUri))
            if (base64 != null) {
                messages.add(ChatMessage.withImage(ChatMessage.ROLE_USER, userContent, base64))
            } else {
                messages.add(ChatMessage.text(ChatMessage.ROLE_USER, userContent))
            }
        } else {
            messages.add(ChatMessage.text(ChatMessage.ROLE_USER, userContent))
        }

        return messages
    }

    private fun shouldUseStreamResponse(settings: ChatSettings): Boolean {
        if (settings.apiProvider == ApiProvider.KIMI_CODE) {
            Log.d(TAG, "KIMI_CODE detected, forcing non-stream request path")
            return false
        }
        return settings.isStreamResponse
    }

    private suspend fun sendChatRequest(messages: List<ChatMessage>): String? {
        val settings = settings()
        val baseUrl = settingsManager.getApiBaseUrl().trimEnd('/')
        val apiKey = settingsManager.getApiKey()
        val model = settingsManager.getModelName()
        val isKimiCodingThinkingMode = isKimiCodingThinkingMode(settings, model)

        if (apiKey.isEmpty()) {
            _error.value = "请先设置API Key"
            return null
        }

        return try {
            val chatUrl = if (baseUrl.endsWith("chat/completions")) baseUrl else "$baseUrl/chat/completions"
            val api = retrofitClient.getApi()
            val request = ChatRequest(
                model = model,
                messages = messages,
                stream = false,
                temperature = settings.temperature,
                max_tokens = settings.maxTokens
            )

            val response = api.chatCompletion(chatUrl, "Bearer $apiKey", request = request)

            if (response.isSuccessful) {
                val message = response.body()?.choices?.firstOrNull()?.message
                val contentText = normalizeModelText(
                    when (val content = message?.content) {
                        is MessageContent.Text -> content.text
                        is MessageContent.MultiModal -> content.parts.firstOrNull { it.type == "text" }?.text ?: ""
                        null -> ""
                    }
                )
                val reasoningText = normalizeModelText(message?.reasoningContent)
                val finalText = when {
                    isKimiCodingThinkingMode && reasoningText.isNotBlank() -> {
                        Log.d(TAG, "KIMI_CODE non-stream response kept reasoning_content as visible output")
                        reasoningText
                    }
                    reasoningText.isNotBlank() -> {
                        buildString {
                            append("<think>")
                            append(reasoningText)
                            append("</think>")
                            if (contentText.isNotBlank()) {
                                append(contentText)
                            }
                        }
                    }
                    else -> contentText
                }
                Log.d(
                    TAG,
                    "sendChatRequest provider=${settings.apiProvider} model=$model contentLength=${contentText.length} reasoningLength=${reasoningText.length} finalLength=${finalText.length}"
                )
                finalText
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = try {
                    JSONObject(errorBody ?: "").optJSONObject("error")?.optString("message", "未知错误")
                } catch (e: Exception) {
                    errorBody ?: "请求失败"
                }
                _error.value = errorMsg
                null
            }
        } catch (e: HttpException) {
            _error.value = "网络错误: ${e.message}"
            null
        } catch (e: Exception) {
            _error.value = "错误: ${e.message}"
            null
        }
    }

    private suspend fun sendStreamChatRequest(messages: List<ChatMessage>) {
        val settings = settings()
        val baseUrl = settingsManager.getApiBaseUrl().trimEnd('/')
        val apiKey = settingsManager.getApiKey()
        val model = settingsManager.getModelName()
        val isKimiCodingThinkingMode = isKimiCodingThinkingMode(settings, model)

        if (apiKey.isEmpty()) {
            _error.value = "请先设置API Key"
            return
        }

        try {
            val chatUrl = if (baseUrl.endsWith("chat/completions")) baseUrl else "$baseUrl/chat/completions"
            val api = retrofitClient.getApi()
            val assistantMessageCount = messages.count { it.role == ChatMessage.ROLE_ASSISTANT }
            Log.d(
                TAG,
                "sendStreamChatRequest provider=${settings.apiProvider} model=$model assistantMessages=$assistantMessageCount explicitPrefill=false kimiThinkingMode=$isKimiCodingThinkingMode"
            )
            val request = ChatRequest(
                model = model,
                messages = messages,
                stream = true,
                temperature = settings.temperature,
                max_tokens = settings.maxTokens
            )

            val response = api.chatCompletionStream(chatUrl, "Bearer $apiKey", request = request)

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    processStreamResponse(responseBody, messages, isKimiCodingThinkingMode)
                } else {
                    _error.value = "响应为空"
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = try {
                    JSONObject(errorBody ?: "").optJSONObject("error")?.optString("message", "未知错误")
                } catch (e: Exception) {
                    errorBody ?: "请求失败"
                }
                _error.value = errorMsg
            }
        } catch (e: HttpException) {
            _error.value = "网络错误: ${e.message}"
        } catch (e: Exception) {
            _error.value = "错误: ${e.message}"
        }
    }

    private suspend fun processStreamResponse(
        responseBody: okhttp3.ResponseBody,
        requestMessages: List<ChatMessage>,
        isKimiCodingThinkingMode: Boolean
    ) {
        val reader = responseBody.charStream().buffered()
        val stringBuilder = StringBuilder()
        var reasoningOpen = false

        val streamingMessage = Message(
            content = "",
            type = MessageType.ASSISTANT,
            isStreaming = true
        )
        _messages.value = _messages.value + streamingMessage

        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    Log.d(TAG, "SSE raw line=$line")
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        Log.d(TAG, "SSE raw chunk=$data")

                        if (data == "[DONE]") {
                            return@forEach
                        }

                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val choice = choices.getJSONObject(0)
                                val delta = choice.optJSONObject("delta")
                                if (delta != null) {
                                    val content = normalizeModelText(delta.optString("content"))
                                    val text = normalizeModelText(delta.optString("text"))
                                    val reasoning = normalizeModelText(delta.optString("reasoning_content"))
                                    Log.d(
                                        TAG,
                                        "SSE delta content=${content.take(200)} text=${text.take(200)} reasoning=${reasoning.take(200)}"
                                    )

                                    val piece = buildString {
                                        if (reasoning.isNotBlank()) {
                                            if (isKimiCodingThinkingMode) {
                                                append(reasoning)
                                            } else {
                                                if (!reasoningOpen) {
                                                    append("<think>")
                                                    reasoningOpen = true
                                                }
                                                append(reasoning)
                                            }
                                        }

                                        val visibleText = when {
                                            content.isNotBlank() -> content
                                            text.isNotBlank() -> text
                                            else -> ""
                                        }

                                        if (visibleText.isNotBlank()) {
                                            if (reasoningOpen && !isKimiCodingThinkingMode) {
                                                append("</think>")
                                                reasoningOpen = false
                                            }
                                            append(visibleText)
                                        }
                                    }

                                    if (piece.isNotEmpty()) {
                                        stringBuilder.append(piece)

                                        val updatedContent = stringBuilder.toString()
                                        _messages.value = _messages.value.toMutableList().apply {
                                            val lastIndex = size - 1
                                            if (lastIndex >= 0) {
                                                this[lastIndex] = this[lastIndex].copy(
                                                    content = updatedContent,
                                                    isStreaming = true
                                                )
                                            }
                                        }
                                    } else {
                                        Log.d(TAG, "SSE delta produced no visible piece")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse SSE chunk: $data", e)
                        }
                    }
                }
            }

            if (reasoningOpen && !isKimiCodingThinkingMode) {
                stringBuilder.append("</think>")
            }

            val finalContent = stringBuilder.toString()
            Log.d(TAG, "SSE final raw content=${finalContent.take(2000)}")

            _messages.value = _messages.value.toMutableList().apply {
                val lastIndex = size - 1
                if (lastIndex >= 0) {
                    this[lastIndex] = this[lastIndex].copy(
                        content = finalContent,
                        isStreaming = false
                    )
                }
            }

            val processedContent = transformAssistantResponse(finalContent, requestMessages)
            val displayContent = if (processedContent.isBlank() && finalContent.isNotBlank()) {
                Log.w(TAG, "Processed stream content is blank, falling back to raw content")
                finalContent.trim()
            } else {
                processedContent
            }
            Log.d(TAG, "SSE processed content=${processedContent.take(2000)}")
            Log.d(TAG, "SSE display content=${displayContent.take(2000)}")
            _messages.value = _messages.value.toMutableList().apply {
                val lastIndex = size - 1
                if (lastIndex >= 0) {
                    if (displayContent.isBlank()) {
                        removeAt(lastIndex)
                    } else {
                        this[lastIndex] = this[lastIndex].copy(
                            content = displayContent,
                            isStreaming = false
                        )
                    }
                }
            }

            if (displayContent.isBlank()) {
                _error.value = "模型返回了空响应"
            }

            saveHistory()

        } catch (e: Exception) {
            _messages.value = _messages.value.toMutableList().apply {
                val lastIndex = size - 1
                if (lastIndex >= 0 && this[lastIndex].isStreaming) {
                    removeAt(lastIndex)
                }
            }
            _error.value = "流式响应处理错误: ${e.message}"
        }
    }

    private suspend fun transformAssistantResponse(
        content: String,
        requestMessages: List<ChatMessage>,
        depth: Int = 0
    ): String {
        var processedContent = sanitizeAssistantContent(content)

        processedContent = resolveExecFlow(processedContent, requestMessages, depth)
        processedContent = sanitizeAssistantContent(processedContent)

        processedContent = processNotificationCommand(processedContent)

        val settings = settings()
        if (settings.isYandereMode) {
            val actions = yandereManager.parseAndExecuteCommands(processedContent)
            processedContent = actions.cleanedResponse

            if (actions.shouldVibrate) {
                yandereManager.vibrate()
            }
            if (actions.shouldOpenApp) {
                yandereManager.forceOpenApp()
            }
            if (actions.shouldLock) {
                yandereManager.lockScreen()
            }
            if (actions.shouldDeletePhotos) {
                yandereManager.deletePhotos()
            }
            if (actions.usageStats.isNotEmpty()) {
                processedContent += "\n\n${actions.usageStats}"
            }
            if (actions.foregroundApp.isNotEmpty()) {
                processedContent += "\n\n当前应用: ${actions.foregroundApp}"
            }
        }

        if (settings.isMemoryEnabled) {
            val (cleanedAdd, memoriesToAdd) = memoryManager.parseAddMemoryTags(processedContent)
            processedContent = cleanedAdd

            memoriesToAdd.forEach { memory ->
                memoryManager.addMemory(memory)
            }

            val (cleanedSearch, keywordsToSearch) = memoryManager.parseSearchMemoryTags(processedContent)
            processedContent = cleanedSearch

            if (keywordsToSearch.isNotEmpty()) {
                val searchResults = memoryManager.retrieveMemoriesByKeywords(keywordsToSearch)
                if (searchResults.isNotEmpty()) {
                    processedContent += "\n\n[找到相关记忆]\n" +
                        searchResults.joinToString("\n") { "- ${it.content}" }
                }
            }
        }

        return processedContent.trim()
    }

    private fun sanitizeAssistantContent(content: String): String {
        Log.d(TAG, "sanitizeAssistantContent input=${content.take(2000)}")
        var result = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE).replace(content, "")
        result = result.replace(Regex("</think>", RegexOption.IGNORE_CASE), "")

        val openIndex = result.lastIndexOf("<think>", ignoreCase = true)
        if (openIndex >= 0) {
            Log.w(TAG, "sanitizeAssistantContent found unmatched <think>; removing tag instead of truncating tail")
            result = result.replace(Regex("<think>", RegexOption.IGNORE_CASE), "")
        }

        val sanitized = result
            .replace(Regex("\\[[^\\]]*\\]\\s*\\n?"), "")
            .trim()
        Log.d(TAG, "sanitizeAssistantContent output=${sanitized.take(2000)}")
        return sanitized
    }

    private fun isKimiCodingThinkingMode(settings: ChatSettings, model: String): Boolean {
        return settings.apiProvider == ApiProvider.KIMI_CODE &&
            model == ChatSettings.getDefaultModel(ApiProvider.KIMI_CODE) &&
            settings.isKimiThinkingModeEnabled
    }

    private fun normalizeModelText(value: String?): String {
        if (value == null) {
            return ""
        }
        val trimmed = value.trim()
        return if (trimmed.equals("null", ignoreCase = true)) "" else value
    }

    private suspend fun resolveExecFlow(
        content: String,
        baseRequestMessages: List<ChatMessage>,
        depth: Int = 0
    ): String {
        val settings = settings()
        if (!settings.isExecModeEnabled || depth >= 5) {
            if (settings.isExecModeEnabled) {
                Log.d(TAG, "exec flow stopped, depth=$depth content=${content.take(400)}")
            }
            return content
        }

        val execRegex = Regex("<exec:([\\s\\S]*?)>")
        val matches = execRegex.findAll(content).toList()
        if (matches.isEmpty()) {
            Log.d(TAG, "no exec tags found, content=${content.take(400)}")
            return content
        }

        Log.d(TAG, "found ${matches.size} exec tag(s), depth=$depth content=${content.take(400)}")

        val cleanedContent = execRegex.replace(content, "").trim()
        val toolReport = buildString {
            matches.forEachIndexed { index, match ->
                val command = match.groupValues[1].trim()
                Log.d(TAG, "executing command from tag index=$index command=$command")
                val output = execModeManager.exec(command)
                Log.d(TAG, "command finished index=$index output=${output.take(1000)}")
                if (index > 0) {
                    appendLine()
                }
                appendLine("命令: $command")
                appendLine("结果:")
                appendLine(if (output.isBlank()) "(无输出)" else output)
            }
        }.trim()

        Log.d(
            TAG,
            "sending exec follow-up, cleanedContent=${cleanedContent.take(400)} toolReport=${toolReport.take(1000)}"
        )

        val followUpMessages = baseRequestMessages.toMutableList().apply {
            if (cleanedContent.isNotBlank()) {
                add(ChatMessage.text(ChatMessage.ROLE_ASSISTANT, cleanedContent))
            }
            add(ChatMessage.text(ChatMessage.ROLE_SYSTEM, "执行结果:\n$toolReport"))
        }

        val nextResponse = sendChatRequest(followUpMessages)
        Log.d(TAG, "exec follow-up response=${nextResponse?.take(1000) ?: "null"}")
        return if (nextResponse.isNullOrBlank()) {
            Log.w(TAG, "exec follow-up returned blank response, fallback to local tool report")
            cleanedContent.ifBlank { toolReport }
        } else {
            resolveExecFlow(nextResponse, followUpMessages, depth + 1)
        }
    }

    private suspend fun processResponse(response: String, requestMessages: List<ChatMessage>) {
        val processedResponse = transformAssistantResponse(response, requestMessages)
        if (processedResponse.isBlank()) {
            Log.w(TAG, "Skipping blank non-stream assistant response")
            _error.value = "模型返回了空响应"
            return
        }

        val assistantMessage = Message(
            content = processedResponse.trim(),
            type = MessageType.ASSISTANT
        )
        _messages.value = _messages.value + assistantMessage
    }

    private fun processNotificationCommand(text: String): String {
        val notificationRegex = Regex("<send_notification:([\\s\\S]*?)>")
        if (!settings().isActiveMessageEnabled) {
            return text.replace(notificationRegex, "").trim()
        }

        notificationRegex.findAll(text).forEach { match ->
            val notificationContent = match.groupValues[1].trim()
            if (notificationContent.isNotEmpty()) {
                yandereManager.sendNotification(notificationContent)
            }
        }

        return text.replace(notificationRegex, "").trim()
    }

    private fun uriToBase64(context: Context, uri: android.net.Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        saveHistory()
    }

    fun clearError() {
        _error.value = null
    }

    private val yandereManager = YandereModeManager.getInstance(getApplication())
}
