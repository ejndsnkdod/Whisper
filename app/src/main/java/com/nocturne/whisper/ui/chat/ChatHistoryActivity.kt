package com.nocturne.whisper.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.nocturne.whisper.R
import com.nocturne.whisper.data.model.ChatSession
import com.nocturne.whisper.databinding.ActivityChatHistoryBinding
import com.nocturne.whisper.utils.ChatSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatHistoryBinding
    private lateinit var adapter: ChatHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadSessions()
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "历史对话"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = ChatHistoryAdapter(
            onSessionClick = { item ->

                ChatSessionManager.switchSession(this, item.session.id)
                val intent = Intent(this, ChatActivity::class.java)
                startActivity(intent)
            },
            onSessionLongClick = { item ->
                showSessionOptions(item)
                true
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.fabNewSession.setOnClickListener {

            val session = ChatSessionManager.createSession(this, "新对话")
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadSessions() {
        val sessions = ChatSessionManager.getAllSessions()

        CoroutineScope(Dispatchers.IO).launch {
            val sessionsWithPreview = sessions.map { session ->
                val messages = ChatSessionManager.loadSessionHistory(this@ChatHistoryActivity, session.id)
                val lastMessage = messages.lastOrNull()
                SessionWithPreview(
                    session = session,
                    lastMessage = lastMessage?.content?.take(30) ?: "",
                    messageTime = lastMessage?.timestamp ?: session.updatedAt
                )
            }

            withContext(Dispatchers.Main) {
                adapter.submitList(sessionsWithPreview)
                binding.tvEmpty.visibility = if (sessionsWithPreview.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showSessionOptions(session: SessionWithPreview) {
        val options = arrayOf("重命名", "删除")
        AlertDialog.Builder(this)
            .setTitle(session.session.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(session.session)
                    1 -> showDeleteConfirm(session.session)
                }
            }
            .show()
    }

    private fun showRenameDialog(session: ChatSession) {
        val editText = android.widget.EditText(this).apply {
            setText(session.title)
            setSelection(session.title.length)
        }

        AlertDialog.Builder(this)
            .setTitle("重命名会话")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    ChatSessionManager.updateSession(this, session.copy(title = newTitle))
                    loadSessions()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteConfirm(session: ChatSession) {
        AlertDialog.Builder(this)
            .setTitle("删除会话")
            .setMessage("确定要删除" + session.title + "吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                ChatSessionManager.deleteSession(this, session.id)
                loadSessions()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    data class SessionWithPreview(
        val session: ChatSession,
        val lastMessage: String,
        val messageTime: Long
    )

    inner class ChatHistoryAdapter(
        private val onSessionClick: (SessionWithPreview) -> Unit,
        private val onSessionLongClick: (SessionWithPreview) -> Boolean
    ) : RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder>() {

        private var sessions: List<SessionWithPreview> = emptyList()

        fun submitList(newSessions: List<SessionWithPreview>) {
            sessions = newSessions
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: MaterialCardView = itemView.findViewById(R.id.cardSession)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvPreview: TextView = itemView.findViewById(R.id.tvPreview)
            val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = sessions[position]
            val session = item.session

            holder.tvTitle.text = session.title
            holder.tvPreview.text = if (item.lastMessage.isNotEmpty()) {
                item.lastMessage + if (item.lastMessage.length >= 30) "..." else ""
            } else {
                "暂无消息"
            }
            holder.tvTime.text = formatTime(item.messageTime)

            val isCurrent = session.id == ChatSessionManager.currentSessionId
            if (isCurrent) {
                holder.cardView.setCardBackgroundColor(getColor(R.color.purple_100))
                holder.cardView.strokeWidth = 2
                holder.cardView.strokeColor = getColor(R.color.purple_500)
            } else {
                holder.cardView.setCardBackgroundColor(getColor(android.R.color.white))
                holder.cardView.strokeWidth = 0
            }

            holder.cardView.setOnClickListener { onSessionClick(item) }
            holder.cardView.setOnLongClickListener { onSessionLongClick(item) }
        }

        override fun getItemCount(): Int = sessions.size

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60_000 -> "刚刚"
                diff < 3_600_000 -> "${diff / 60_000}分钟前"
                diff < 86_400_000 -> "${diff / 3_600_000}小时前"
                diff < 7 * 86_400_000 -> "${diff / 86_400_000}天前"
                else -> {
                    val date = Date(timestamp)
                    SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
                }
            }
        }
    }
}
