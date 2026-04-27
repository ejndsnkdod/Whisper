package com.nocturne.whisper.ui.chat

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
import com.nocturne.whisper.databinding.ActivitySessionListBinding
import com.nocturne.whisper.utils.ChatSessionManager
import com.nocturne.whisper.utils.getFormattedTime
import com.nocturne.whisper.utils.messageCount

class SessionListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionListBinding
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshSessionList()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SessionAdapter(
            onSessionClick = { session ->
                ChatSessionManager.switchSession(this, session.id)
                setResult(RESULT_OK)
                finish()
            },
            onSessionLongClick = { session ->
                showSessionOptions(session)
                true
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnNewSession.setOnClickListener {
            ChatSessionManager.createSession(this, "新对话")
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun refreshSessionList() {
        val sessions = ChatSessionManager.getAllSessions()
        adapter.submitList(sessions)
        binding.tvEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSessionOptions(session: ChatSession) {
        val options = arrayOf("重命名", "删除")
        AlertDialog.Builder(this)
            .setTitle(session.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(session)
                    1 -> showDeleteConfirm(session)
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
            .setTitle("重命名")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    ChatSessionManager.updateSession(this, session.copy(title = newTitle))
                    refreshSessionList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteConfirm(session: ChatSession) {
        AlertDialog.Builder(this)
            .setTitle("删除会话")
            .setMessage("确定要删除" + session.title + "吗？")
            .setPositiveButton("删除") { _, _ ->
                ChatSessionManager.deleteSession(this, session.id)
                refreshSessionList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    inner class SessionAdapter(
        private val onSessionClick: (ChatSession) -> Unit,
        private val onSessionLongClick: (ChatSession) -> Boolean
    ) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

        private var sessions: List<ChatSession> = emptyList()
        private var currentSessionId: String? = ChatSessionManager.currentSessionId

        fun submitList(newSessions: List<ChatSession>) {
            sessions = newSessions
            currentSessionId = ChatSessionManager.currentSessionId
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: MaterialCardView = itemView.findViewById(R.id.cardSession)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvTime: TextView = itemView.findViewById(R.id.tvTime)
            val tvMessageCount: TextView = itemView.findViewById(R.id.tvMessageCount)
            val ivSelected: View = itemView.findViewById(R.id.ivSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]

            holder.tvTitle.text = session.title
            holder.tvTime.text = session.getFormattedTime()
            holder.tvMessageCount.text = session.messageCount.toString() + "条消息"

            val isCurrent = session.id == currentSessionId
            holder.ivSelected.visibility = if (isCurrent) View.VISIBLE else View.GONE
            holder.cardView.isChecked = isCurrent

            holder.cardView.setOnClickListener { onSessionClick(session) }
            holder.cardView.setOnLongClickListener { onSessionLongClick(session) }
        }

        override fun getItemCount(): Int = sessions.size
    }
}
