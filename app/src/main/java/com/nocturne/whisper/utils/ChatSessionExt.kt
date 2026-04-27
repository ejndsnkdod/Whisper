package com.nocturne.whisper.utils

import com.nocturne.whisper.data.model.ChatSession
import com.nocturne.whisper.data.model.Message
import java.util.Date

fun ChatSession.getFormattedTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - updatedAt

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 7 * 86_400_000 -> "${diff / 86_400_000}天前"
        else -> {
            val date = Date(updatedAt)
            java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(date)
        }
    }
}

val ChatSession.messageCount: Int
    get() = messages.size
