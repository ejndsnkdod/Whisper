package com.nocturne.whisper.utils

import android.content.Context
import android.content.SharedPreferences
import com.nocturne.whisper.data.model.ApiProvider
import com.nocturne.whisper.data.model.ChatSettings
import com.nocturne.whisper.utils.ModelProfileManager

class SettingsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        ChatSettings.PREFS_NAME, Context.MODE_PRIVATE
    )

    fun getSettings(): ChatSettings {
        val encryptedApiKey = prefs.getString(ChatSettings.KEY_API_KEY, "") ?: ""
        val encryptedApiUrl = prefs.getString(ChatSettings.KEY_API_URL, "") ?: ""

        return ChatSettings(
            apiProvider = ApiProvider.valueOf(
                prefs.getString(ChatSettings.KEY_API_PROVIDER, ApiProvider.DEEPSEEK.name)!!
            ),
            apiKey = decryptIfNeeded(encryptedApiKey),
            apiUrl = decryptIfNeeded(encryptedApiUrl),
            modelName = prefs.getString(ChatSettings.KEY_MODEL_NAME, "deepseek-chat") ?: "deepseek-chat",
            isYandereMode = prefs.getBoolean(ChatSettings.KEY_YANDERE_MODE, false),
            isExecModeEnabled = prefs.getBoolean(ChatSettings.KEY_EXEC_MODE, false),
            isDarkMode = prefs.getBoolean(ChatSettings.KEY_DARK_MODE, false),
            isMemoryEnabled = prefs.getBoolean(ChatSettings.KEY_MEMORY_ENABLED, true),
            isChatHistoryEnabled = prefs.getBoolean(ChatSettings.KEY_CHAT_HISTORY_ENABLED, true),
            maxHistoryMessages = prefs.getInt(ChatSettings.KEY_MAX_HISTORY, 50),
            temperature = prefs.getFloat(ChatSettings.KEY_TEMPERATURE, 0.7f),
            maxTokens = prefs.getInt(ChatSettings.KEY_MAX_TOKENS, 2048),
            currentPersonaId = prefs.getString(ChatSettings.KEY_CURRENT_PERSONA, null),
            isActiveMessageEnabled = prefs.getBoolean(ChatSettings.KEY_ACTIVE_MSG_ENABLE, false),
            isBackgroundMessageEnabled = prefs.getBoolean(ChatSettings.KEY_BG_MSG_ENABLE, false),
            activeMessageIntervalMs = prefs.getLong(ChatSettings.KEY_ACTIVE_MSG_INTERVAL, 60 * 60 * 1000L),
            isStreamResponse = prefs.getBoolean(ChatSettings.KEY_STREAM_RESPONSE, true),
            jailbreakPrompt = prefs.getString(ChatSettings.KEY_JAILBREAK_PROMPT, "") ?: "",
            isKimiThinkingModeEnabled = prefs.getBoolean(ChatSettings.KEY_KIMI_THINKING_MODE, false)
        )
    }

    fun saveSettings(settings: ChatSettings) {
        prefs.edit().apply {
            putString(ChatSettings.KEY_API_PROVIDER, settings.apiProvider.name)
            putString(ChatSettings.KEY_API_KEY, encryptIfNeeded(settings.apiKey))
            putString(ChatSettings.KEY_API_URL, encryptIfNeeded(settings.apiUrl))
            putString(ChatSettings.KEY_MODEL_NAME, settings.modelName)
            putBoolean(ChatSettings.KEY_YANDERE_MODE, settings.isYandereMode)
            putBoolean(ChatSettings.KEY_EXEC_MODE, settings.isExecModeEnabled)
            putBoolean(ChatSettings.KEY_DARK_MODE, settings.isDarkMode)
            putBoolean(ChatSettings.KEY_MEMORY_ENABLED, settings.isMemoryEnabled)
            putBoolean(ChatSettings.KEY_CHAT_HISTORY_ENABLED, settings.isChatHistoryEnabled)
            putInt(ChatSettings.KEY_MAX_HISTORY, settings.maxHistoryMessages)
            putFloat(ChatSettings.KEY_TEMPERATURE, settings.temperature)
            putInt(ChatSettings.KEY_MAX_TOKENS, settings.maxTokens)
            putString(ChatSettings.KEY_CURRENT_PERSONA, settings.currentPersonaId)
            putBoolean(ChatSettings.KEY_ACTIVE_MSG_ENABLE, settings.isActiveMessageEnabled)
            putBoolean(ChatSettings.KEY_BG_MSG_ENABLE, settings.isBackgroundMessageEnabled)
            putLong(ChatSettings.KEY_ACTIVE_MSG_INTERVAL, settings.activeMessageIntervalMs)
            putBoolean(ChatSettings.KEY_STREAM_RESPONSE, settings.isStreamResponse)
            putString(ChatSettings.KEY_JAILBREAK_PROMPT, settings.jailbreakPrompt)
            putBoolean(ChatSettings.KEY_KIMI_THINKING_MODE, settings.isKimiThinkingModeEnabled)
            apply()
        }
    }

    private fun encryptIfNeeded(value: String): String {
        return if (value.isNotEmpty() && !CryptoUtils.isEncrypted(value)) {
            CryptoUtils.encrypt(context, value)
        } else {
            value
        }
    }

    private fun decryptIfNeeded(value: String): String {
        return if (value.isNotEmpty() && CryptoUtils.isEncrypted(value)) {
            CryptoUtils.decrypt(context, value)
        } else {
            value
        }
    }

    fun getApiBaseUrl(): String {
        val settings = getSettings()
        return if (settings.apiUrl.isNotEmpty()) {
            settings.apiUrl.trimEnd('/') + "/"
        } else {
            ChatSettings.getDefaultUrl(settings.apiProvider)
        }
    }

    fun getApiKey(): String {
        return getSettings().apiKey
    }

    fun getModelName(): String {
        return getSettings().modelName
    }

    fun isYandereMode(): Boolean = getSettings().isYandereMode

    fun isMemoryEnabled(): Boolean = getSettings().isMemoryEnabled

    companion object {
        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
