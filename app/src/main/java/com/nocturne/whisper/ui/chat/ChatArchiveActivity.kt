package com.nocturne.whisper.ui.chat

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nocturne.whisper.databinding.ActivityChatArchiveBinding
import com.nocturne.whisper.ui.adapter.ChatSessionAdapter
import com.nocturne.whisper.utils.ChatRepository
import kotlinx.coroutines.launch

class ChatArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatArchiveBinding
    private lateinit var chatRepository: ChatRepository
    private lateinit var sessionAdapter: ChatSessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatRepository = ChatRepository.getInstance(this)

        setupToolbar()
        setupRecyclerView()
        observeSessions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        sessionAdapter = ChatSessionAdapter(
            onSessionClick = { session ->

                Toast.makeText(this, "会话: ${session.title}", Toast.LENGTH_SHORT).show()
            },
            onSessionDelete = { session ->
                showDeleteDialog(session.id)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatArchiveActivity)
            adapter = sessionAdapter
        }
    }

    private fun observeSessions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatRepository.sessions.collect { sessions ->
                    sessionAdapter.submitList(sessions)
                    binding.tvEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showDeleteDialog(sessionId: String) {
        AlertDialog.Builder(this)
            .setTitle("删除会话")
            .setMessage("确定要删除这个会话吗？")
            .setPositiveButton("删除") { _, _ ->
                chatRepository.deleteSession(sessionId)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
