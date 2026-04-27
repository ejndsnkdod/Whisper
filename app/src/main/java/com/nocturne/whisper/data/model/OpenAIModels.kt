package com.nocturne.whisper.data.model

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

data class ChatRequest(
    @SerializedName("model")
    val model: String,

    @SerializedName("messages")
    val messages: List<ChatMessage>,

    @SerializedName("stream")
    val stream: Boolean = false,

    @SerializedName("temperature")
    val temperature: Float? = null,

    @SerializedName("max_tokens")
    val max_tokens: Int? = null
)

sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class MultiModal(val parts: List<ContentPart>) : MessageContent()
}

data class ContentPart(
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    @SerializedName("url") val url: String,
    @SerializedName("detail") val detail: String = "auto"
)

class ChatMessageContentAdapter : JsonSerializer<MessageContent>, JsonDeserializer<MessageContent> {
    override fun serialize(src: MessageContent, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return when (src) {
            is MessageContent.Text -> JsonPrimitive(src.text)
            is MessageContent.MultiModal -> context.serialize(src.parts)
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MessageContent {
        return if (json.isJsonPrimitive) MessageContent.Text(json.asString)
        else MessageContent.MultiModal(emptyList())
    }
}

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: MessageContent
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun text(role: String, text: String) = ChatMessage(role, MessageContent.Text(text))
        fun withImage(role: String, text: String, base64Image: String, mimeType: String = "image/jpeg") = ChatMessage(
            role,
            MessageContent.MultiModal(listOf(
                ContentPart("text", text = text),
                ContentPart("image_url", imageUrl = ImageUrl("data:$mimeType;base64,$base64Image"))
            ))
        )
    }
}

data class ChatResponse(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("object")
    val `object`: String? = null,

    @SerializedName("created")
    val created: Long? = null,

    @SerializedName("model")
    val model: String? = null,

    @SerializedName("choices")
    val choices: List<Choice>? = null,

    @SerializedName("usage")
    val usage: Usage? = null,

    @SerializedName("error")
    val error: ErrorResponse? = null
)

data class Choice(
    @SerializedName("index")
    val index: Int? = null,

    @SerializedName("message")
    val message: ResponseMessage? = null,

    @SerializedName("delta")
    val delta: DeltaMessage? = null,

    @SerializedName("finish_reason")
    val finish_reason: String? = null
)

data class ResponseMessage(
    @SerializedName("role")
    val role: String? = null,

    @SerializedName("content")
    val content: MessageContent? = null,

    @SerializedName("reasoning_content")
    val reasoningContent: String? = null
)

data class DeltaMessage(
    @SerializedName("role")
    val role: String? = null,

    @SerializedName("content")
    val content: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens")
    val prompt_tokens: Int? = null,

    @SerializedName("completion_tokens")
    val completion_tokens: Int? = null,

    @SerializedName("total_tokens")
    val total_tokens: Int? = null
)

data class ErrorResponse(
    @SerializedName("message")
    val message: String? = null,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("param")
    val param: String? = null,

    @SerializedName("code")
    val code: String? = null
)

data class StreamResponse(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("object")
    val `object`: String? = null,

    @SerializedName("created")
    val created: Long? = null,

    @SerializedName("model")
    val model: String? = null,

    @SerializedName("choices")
    val choices: List<StreamChoice>? = null
)

data class StreamChoice(
    @SerializedName("index")
    val index: Int? = null,

    @SerializedName("delta")
    val delta: DeltaMessage? = null,

    @SerializedName("finish_reason")
    val finish_reason: String? = null
)
