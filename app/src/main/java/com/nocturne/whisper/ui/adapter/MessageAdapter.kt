package com.nocturne.whisper.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nocturne.whisper.R
import com.nocturne.whisper.data.model.Message
import com.nocturne.whisper.data.model.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return when (viewType) {
            TYPE_USER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_user, parent, false)
                UserMessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_assistant, parent, false)
                AssistantMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).type) {
            MessageType.USER -> TYPE_USER
            else -> TYPE_ASSISTANT
        }
    }

    abstract class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(message: Message)
    }

    inner class UserMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val ivImage: android.widget.ImageView = itemView.findViewById(R.id.ivImage)

        override fun bind(message: Message) {
            tvContent.text = message.content
            if (!message.imageUri.isNullOrEmpty()) {
                ivImage.visibility = View.VISIBLE
                ivImage.setImageURI(android.net.Uri.parse(message.imageUri))
            } else {
                ivImage.visibility = View.GONE
            }
            tvTime.text = formatTime(message.timestamp)
        }
    }

    inner class AssistantMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvThinking: TextView = itemView.findViewById(R.id.tvThinking)
        private val tvThinkingToggle: TextView = itemView.findViewById(R.id.tvThinkingToggle)

        override fun bind(message: Message) {
            val thinkingParts = extractThinkingParts(message.content)
            val mainContent = stripThinkingContent(message.content)
            val displayContent = if (mainContent.isBlank() && thinkingParts.isNotBlank()) {
                thinkingParts
            } else {
                mainContent
            }

            tvContent.text = displayContent
            tvTime.text = formatTime(message.timestamp)

            if (thinkingParts.isNotEmpty()) {
                tvThinkingToggle.visibility = View.VISIBLE
                tvThinkingToggle.text = "▼ 思考过程"
                tvThinking.visibility = View.GONE
                tvThinking.text = thinkingParts

                tvThinkingToggle.setOnClickListener {
                    if (tvThinking.visibility == View.GONE) {
                        tvThinking.visibility = View.VISIBLE
                        tvThinkingToggle.text = "▲ 思考过程"
                    } else {
                        tvThinking.visibility = View.GONE
                        tvThinkingToggle.text = "▼ 思考过程"
                    }
                }
            } else {
                tvThinkingToggle.visibility = View.GONE
                tvThinking.visibility = View.GONE
            }
        }
    }

    private fun extractThinkingParts(content: String): String {
        val parts = mutableListOf<String>()

        Regex("<think>([\\s\\S]*?)</think>", RegexOption.IGNORE_CASE).findAll(content).forEach { match ->
            val inner = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (inner.isNotEmpty()) {
                parts.add(inner)
            }
        }

        Regex("(\\*[^*]+\\*\\s*\\n?)", RegexOption.DOT_MATCHES_ALL).findAll(content).forEach { match ->
            val item = match.value.trim()
            if (item.isNotEmpty()) {
                parts.add(item)
            }
        }

        val trailingThink = Regex("<think>([\\s\\S]*)$", RegexOption.IGNORE_CASE)
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
        if (trailingThink.isNotEmpty()) {
            parts.add(trailingThink)
        }

        return parts.joinToString("\n\n")
    }

    private fun stripThinkingContent(content: String): String {
        var result = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE).replace(content, "")
        result = result.replace(Regex("(\\*[^*]+\\*\\s*\\n?)", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace(Regex("</think>", RegexOption.IGNORE_CASE), "")

        result = result.replace(Regex("<think>", RegexOption.IGNORE_CASE), "")

        return result.trim()
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
    }
}
