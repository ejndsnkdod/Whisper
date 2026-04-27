package com.nocturne.whisper.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nocturne.whisper.data.model.ChatSession
import com.nocturne.whisper.databinding.ItemChatSessionBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatSessionAdapter(
    private val onSessionClick: (ChatSession) -> Unit,
    private val onSessionDelete: (ChatSession) -> Unit
) : ListAdapter<ChatSession, ChatSessionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatSessionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemChatSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: ChatSession) {
            binding.tvTitle.text = session.title
            binding.tvTime.text = formatTime(session.updatedAt)
            binding.tvMessageCount.text = "${session.messages.size} 条消息"

            binding.root.setOnClickListener {
                onSessionClick(session)
            }

            binding.btnDelete.setOnClickListener {
                onSessionDelete(session)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatSession>() {
        override fun areItemsTheSame(oldItem: ChatSession, newItem: ChatSession): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatSession, newItem: ChatSession): Boolean {
            return oldItem == newItem
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
