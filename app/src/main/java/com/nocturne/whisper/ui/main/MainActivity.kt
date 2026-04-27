package com.nocturne.whisper.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nocturne.whisper.databinding.ActivityMainBinding
import com.nocturne.whisper.ui.chat.ChatActivity
import com.nocturne.whisper.ui.chat.ChatHistoryActivity
import com.nocturne.whisper.ui.persona.PersonaListActivity
import com.nocturne.whisper.ui.settings.SettingsActivity
import com.nocturne.whisper.utils.ChatSessionManager
import com.nocturne.whisper.utils.PersonaManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "部分功能可能需要权限", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PersonaManager.loadJsonFromDisk(this)

        ChatSessionManager.init(this)

        checkPermissions()
        setupButtons()
        updateUI()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WAKE_LOCK)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun setupButtons() {

        binding.btnStartChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        binding.btnChatHistory.setOnClickListener {
            startActivity(Intent(this, ChatHistoryActivity::class.java))
        }

        binding.btnPersona.setOnClickListener {
            startActivity(Intent(this, PersonaListActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun updateUI() {

        val currentPersona = PersonaManager.getCurrentPersona()
        if (currentPersona != null) {
            binding.tvCurrentPersona.text = "当前人格: ${currentPersona.name}"
            binding.tvPersonaDesc.text = currentPersona.description.take(50) + "..."
        } else {
            binding.tvCurrentPersona.text = "当前人格: 默认"
            binding.tvPersonaDesc.text = "暂无描述"
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("退出应用")
            .setMessage("确定要退出吗？")
            .setPositiveButton("退出") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
