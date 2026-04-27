package com.nocturne.whisper.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nocturne.whisper.R
import com.nocturne.whisper.databinding.ActivityChatBinding
import com.nocturne.whisper.ui.adapter.MessageAdapter
import com.nocturne.whisper.ui.persona.PersonaListActivity
import com.nocturne.whisper.ui.settings.SettingsActivity
import com.nocturne.whisper.utils.PersonaManager
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var viewModel: ChatViewModel
    private var pendingImageUri: android.net.Uri? = null

    private val sessionListLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {

            viewModel.refreshSessions()
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Some GetContent providers grant temporary read access only.
            }
            showImagePreview(uri)
            Toast.makeText(this, "图片已选择", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, 0, 0, maxOf(imeInsets.bottom, navInsets.bottom))
            insets
        }

        viewModel = ChatViewModel(application)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = PersonaManager.getCurrentPersona()?.name ?: "AI助手"

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.toolbar.setOnClickListener {
            startActivity(Intent(this, PersonaListActivity::class.java))
        }

        binding.toolbar.inflateMenu(R.menu.menu_chat)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_sessions -> {

                    val intent = Intent(this, SessionListActivity::class.java)
                    sessionListLauncher.launch(intent)
                    true
                }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_clear -> {
                    showClearConfirmDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener {
            val content = binding.etInput.text.toString().trim()
            if (content.isNotEmpty()) {
                viewModel.sendMessage(content, pendingImageUri?.toString())
                clearSelectedImage()
                binding.etInput.setText("")
                hideKeyboard()
            }
        }

        binding.btnImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnClear.setOnClickListener {
            showClearConfirmDialog()
        }

        binding.btnRemoveImage.setOnClickListener {
            clearSelectedImage()
        }

        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val content = binding.etInput.text.toString().trim()
                if (content.isNotEmpty()) {
                    viewModel.sendMessage(content, pendingImageUri?.toString())
                    clearSelectedImage()
                    binding.etInput.setText("")
                    hideKeyboard()
                }
                true
            } else {
                false
            }
        }
    }

    private fun showImagePreview(uri: android.net.Uri) {
        binding.ivImagePreview.setImageURI(uri)
        binding.imagePreviewContainer.visibility = View.VISIBLE
    }

    private fun clearSelectedImage() {
        pendingImageUri = null
        binding.ivImagePreview.setImageDrawable(null)
        binding.imagePreviewContainer.visibility = View.GONE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        messageAdapter.submitList(messages.toList())
                        if (messages.isNotEmpty()) {
                            binding.recyclerView.scrollToPosition(messages.size - 1)
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                        binding.btnSend.isEnabled = !isLoading
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Toast.makeText(this@ChatActivity, it, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }

                launch {
                    viewModel.currentPersonaName.collect { name ->
                        supportActionBar?.title = name ?: "AI助手"
                    }
                }
            }
        }
    }

    private fun showClearConfirmDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("清空对话")
            .setMessage("确定要清空当前对话吗？")
            .setPositiveButton("清空") { _, _ ->
                viewModel.clearMessages()
                Toast.makeText(this, "对话已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()

        supportActionBar?.title = PersonaManager.getCurrentPersona()?.name ?: "AI助手"
    }
}
