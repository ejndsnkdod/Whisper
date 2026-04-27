package com.nocturne.whisper.ui.memory

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nocturne.whisper.databinding.ActivityMemoryListBinding
import com.nocturne.whisper.ui.adapter.MemoryAdapter
import com.nocturne.whisper.utils.MemoryManager
import kotlinx.coroutines.launch

class MemoryListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryListBinding
    private lateinit var memoryManager: MemoryManager
    private lateinit var memoryAdapter: MemoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        memoryManager = MemoryManager.getInstance(this)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        observeMemories()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        memoryAdapter = MemoryAdapter(
            onMemoryDelete = { memory ->
                showDeleteDialog(memory)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MemoryListActivity)
            adapter = memoryAdapter
        }
    }

    private fun setupButtons() {
        binding.btnAdd.setOnClickListener {
            showAddMemoryDialog()
        }

        binding.btnClearAll.setOnClickListener {
            showClearAllDialog()
        }
    }

    private fun observeMemories() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                memoryManager.allMemories.collect { memories ->
                    memoryAdapter.submitList(memories)
                    binding.tvEmpty.visibility = if (memories.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvMemoryCount.text = "共 ${memories.size} 条记忆"
                }
            }
        }
    }

    private fun showAddMemoryDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "输入记忆内容"
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("添加记忆")
            .setView(editText)
            .setPositiveButton("添加") { _, _ ->
                val content = editText.text.toString().trim()
                if (content.isNotEmpty()) {
                    lifecycleScope.launch {
                        memoryManager.addMemory(content)
                        Toast.makeText(this@MemoryListActivity, "记忆已添加", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteDialog(memory: com.nocturne.whisper.data.model.MemoryEntry) {
        AlertDialog.Builder(this)
            .setTitle("删除记忆")
            .setMessage("确定要删除这条记忆吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    memoryManager.deleteMemory(memory)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("清空所有记忆")
            .setMessage("确定要清空所有记忆吗？此操作不可恢复。")
            .setPositiveButton("清空") { _, _ ->
                lifecycleScope.launch {
                    memoryManager.clearAllMemories()
                    Toast.makeText(this@MemoryListActivity, "所有记忆已清空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
