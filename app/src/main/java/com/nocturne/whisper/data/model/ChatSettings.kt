package com.nocturne.whisper.data.model

enum class ApiProvider {
    DEEPSEEK,
    SILICON_FLOW,
    KIMI_CODE,
    CUSTOM
}

data class ChatSettings(
    val apiProvider: ApiProvider = ApiProvider.DEEPSEEK,
    val apiKey: String = "",
    val apiUrl: String = "",
    val modelName: String = "deepseek-chat",
    val isYandereMode: Boolean = false,
    val isExecModeEnabled: Boolean = false,
    val isDarkMode: Boolean = false,
    val isMemoryEnabled: Boolean = true,
    val isChatHistoryEnabled: Boolean = true,
    val maxHistoryMessages: Int = 50,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val currentPersonaId: String? = null,
    val isActiveMessageEnabled: Boolean = false,
    val isBackgroundMessageEnabled: Boolean = false,
    val activeMessageIntervalMs: Long = 60 * 60 * 1000L,
    val isStreamResponse: Boolean = true,
    val jailbreakPrompt: String = "",
    val isKimiThinkingModeEnabled: Boolean = false
) {
    companion object {
        const val PREFS_NAME = "ai_chat_settings"
        const val KEY_API_PROVIDER = "api_provider"
        const val KEY_API_KEY = "api_key"
        const val KEY_API_URL = "api_url"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_YANDERE_MODE = "yandere_mode"
        const val KEY_EXEC_MODE = "exec_mode_enabled"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_MEMORY_ENABLED = "memory_enabled"
        const val KEY_CHAT_HISTORY_ENABLED = "chat_history_enabled"
        const val KEY_MAX_HISTORY = "max_history"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_CURRENT_PERSONA = "current_persona"
        const val KEY_ACTIVE_MSG_ENABLE = "key_active_msg_enable"
        const val KEY_BG_MSG_ENABLE = "key_bg_msg_enable"
        const val KEY_ACTIVE_MSG_INTERVAL = "key_active_msg_interval"
        const val KEY_STREAM_RESPONSE = "key_stream_response"
        const val KEY_JAILBREAK_PROMPT = "key_jailbreak_prompt"
        const val KEY_KIMI_THINKING_MODE = "key_kimi_thinking_mode"

        fun getDefaultUrl(provider: ApiProvider): String {
            return when (provider) {
                ApiProvider.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions"
                ApiProvider.SILICON_FLOW -> "https://api.siliconflow.cn/v1/chat/completions"
                ApiProvider.KIMI_CODE -> "https://api.kimi.com/coding/v1/chat/completions"
                ApiProvider.CUSTOM -> ""
            }
        }

        fun getDefaultModel(provider: ApiProvider): String {
            return when (provider) {
                ApiProvider.DEEPSEEK -> "deepseek-chat"
                ApiProvider.SILICON_FLOW -> "deepseek-ai/DeepSeek-V2.5"
                ApiProvider.KIMI_CODE -> "kimi-for-coding"
                ApiProvider.CUSTOM -> "gpt-3.5-turbo"
            }
        }
    }
}
