package com.nocturne.whisper.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nocturne.whisper.data.model.MemoryEntry
import com.nocturne.whisper.databinding.ItemMemoryBinding
import java.text.SimpleDateFormat
import java.util.*

class MemoryAdapter(
    private val onMemoryDelete: (MemoryEntry) -> Unit
) : ListAdapter<MemoryEntry, MemoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMemoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemMemoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(memory: MemoryEntry) {
            binding.tvContent.text = memory.content
            binding.tvKeywords.text = "关键词: ${memory.keywords}"
            binding.tvTime.text = formatTime(memory.timestamp)

            binding.btnDelete.setOnClickListener {
                onMemoryDelete(memory)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MemoryEntry>() {
        override fun areItemsTheSame(oldItem: MemoryEntry, newItem: MemoryEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MemoryEntry, newItem: MemoryEntry): Boolean {
            return oldItem == newItem
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
