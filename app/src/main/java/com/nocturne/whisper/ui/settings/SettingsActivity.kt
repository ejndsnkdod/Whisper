package com.nocturne.whisper.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.nocturne.whisper.data.api.RetrofitClient
import com.nocturne.whisper.data.model.ApiProvider
import com.nocturne.whisper.data.model.ChatMessage
import com.nocturne.whisper.data.model.ChatRequest
import com.nocturne.whisper.data.model.ChatSettings
import com.nocturne.whisper.databinding.ActivitySettingsBinding
import com.nocturne.whisper.utils.ModelProfileManager
import com.nocturne.whisper.utils.ProactiveMessageManager
import com.nocturne.whisper.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "SettingsActivity"
        private const val REQUEST_CODE_SHIZUKU_PERMISSION = 10086
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var proactiveMessageManager: ProactiveMessageManager
    private lateinit var profileManager: ModelProfileManager
    private var suppressAutoSave = false
    private var isAwaitingShizukuPermission = false

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_CODE_SHIZUKU_PERMISSION) {
                return@OnRequestPermissionResultListener
            }

            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            isAwaitingShizukuPermission = false
            Log.d(TAG, "Shizuku permission result granted=$granted")

            if (granted) {
                binding.switchExecMode.isChecked = true
                saveExecModeEnabled(true)
                Toast.makeText(this, "Shizuku 已授权，执行模式已启用", Toast.LENGTH_SHORT).show()
            } else {
                binding.switchExecMode.isChecked = false
                saveExecModeEnabled(false)
                Toast.makeText(this, "Shizuku 授权失败，执行模式已关闭", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager.getInstance(this)
        proactiveMessageManager = ProactiveMessageManager.getInstance(this)
        profileManager = ModelProfileManager.getInstance(this)

        setupToolbar()
        setupApiProviderSpinner()
        setupSwitches()
        setupActiveMessageControls()
        setupButtons()
        loadSettings()

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onResume() {
        super.onResume()
        suppressAutoSave = true
        setupApiProviderSpinner()
        loadSettings()
        syncExecModeSwitchWithShizukuState()
        binding.spinnerApiProvider.post { suppressAutoSave = false }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupApiProviderSpinner() {
        suppressAutoSave = true

        val modelProfiles = profileManager.getProfiles()
        val builtinProviders = listOf("DeepSeek", "硅基流动", "Kimi Code", "自定义")
        val profileNames = modelProfiles.map { it.name }
        val allOptions = builtinProviders + profileNames

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, allOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerApiProvider.tag = null
        binding.spinnerApiProvider.adapter = adapter

        binding.spinnerApiProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!suppressAutoSave) {
                    val currentPos = binding.spinnerApiProvider.tag as? Int
                    if (currentPos != null && currentPos < 4) {
                        saveProviderKey(
                            currentPos,
                            binding.etApiKey.text.toString().trim(),
                            binding.etCustomUrl.text.toString().trim(),
                            binding.etModelName.text.toString().trim()
                        )
                    }
                }

                binding.spinnerApiProvider.tag = position

                val profiles = profileManager.getProfiles()
                when {
                    position == 0 -> {
                        binding.layoutCustomUrl.visibility = View.GONE
                        binding.etApiKey.setText(loadProviderKey(0))
                        binding.etCustomUrl.setText(
                            loadProviderUrl(0).ifEmpty { ChatSettings.getDefaultUrl(ApiProvider.DEEPSEEK) }
                        )
                        binding.etModelName.setText(
                            loadProviderModelName(0).ifEmpty { ChatSettings.getDefaultModel(ApiProvider.DEEPSEEK) }
                        )
                        if (!suppressAutoSave) profileManager.setCurrentProfileId("")
                    }

                    position == 1 -> {
                        binding.layoutCustomUrl.visibility = View.GONE
                        binding.etApiKey.setText(loadProviderKey(1))
                        binding.etCustomUrl.setText(
                            loadProviderUrl(1).ifEmpty { ChatSettings.getDefaultUrl(ApiProvider.SILICON_FLOW) }
                        )
                        binding.etModelName.setText(
                            loadProviderModelName(1).ifEmpty { ChatSettings.getDefaultModel(ApiProvider.SILICON_FLOW) }
                        )
                        if (!suppressAutoSave) profileManager.setCurrentProfileId("")
                    }

                    position == 2 -> {
                        binding.layoutCustomUrl.visibility = View.GONE
                        binding.etApiKey.setText(loadProviderKey(2))
                        binding.etCustomUrl.setText(
                            loadProviderUrl(2).ifEmpty { ChatSettings.getDefaultUrl(ApiProvider.KIMI_CODE) }
                        )
                        binding.etModelName.setText(
                            loadProviderModelName(2).ifEmpty { ChatSettings.getDefaultModel(ApiProvider.KIMI_CODE) }
                        )
                        if (!suppressAutoSave) profileManager.setCurrentProfileId("")
                    }

                    position == 3 -> {
                        binding.layoutCustomUrl.visibility = View.VISIBLE
                        binding.etApiKey.setText(loadProviderKey(3))
                        binding.etCustomUrl.setText(loadProviderUrl(3))
                        binding.etModelName.setText(
                            loadProviderModelName(3).ifEmpty {
                                settingsManager.getSettings().modelName.ifEmpty {
                                    ChatSettings.getDefaultModel(ApiProvider.CUSTOM)
                                }
                            }
                        )
                        if (!suppressAutoSave) profileManager.setCurrentProfileId("")
                    }

                    position >= 4 -> {
                        val profile = profiles[position - 4]
                        binding.layoutCustomUrl.visibility = View.VISIBLE
                        binding.etApiKey.setText(profile.apiKey)
                        binding.etCustomUrl.setText(profile.apiUrl)
                        binding.etModelName.setText(profile.modelName)
                        if (!suppressAutoSave) profileManager.setCurrentProfileId(profile.id)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun saveProviderKey(position: Int, key: String, url: String, model: String) {
        val prefs = getSharedPreferences(ChatSettings.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(providerPrefKey(position, "api_key"), key)
            .putString(providerPrefKey(position, "api_url"), url)
            .putString(providerPrefKey(position, "model_name"), model)
            .apply()
    }

    private fun loadProviderKey(position: Int): String {
        val prefs = getSharedPreferences(ChatSettings.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(providerPrefKey(position, "api_key"), "") ?: ""
    }

    private fun loadProviderUrl(position: Int): String {
        val prefs = getSharedPreferences(ChatSettings.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(providerPrefKey(position, "api_url"), "") ?: ""
    }

    private fun loadProviderModelName(position: Int): String {
        val prefs = getSharedPreferences(ChatSettings.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(providerPrefKey(position, "model_name"), "") ?: ""
    }

    private fun providerPrefKey(position: Int, suffix: String): String {
        return "provider_${position}_$suffix"
    }

    private fun setupSwitches() {
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.switchYandereMode.setOnCheckedChangeListener { _, isChecked ->
            if (!binding.switchYandereMode.isPressed) {
                return@setOnCheckedChangeListener
            }

            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("需要文件访问权限")
                    .setMessage("病娇模式需要 MANAGE_EXTERNAL_STORAGE 权限来删除照片和管理文件。现在去授权吗？")
                    .setPositiveButton("去授权") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton("取消") { _, _ ->
                        binding.switchYandereMode.isChecked = false
                    }
                    .setOnCancelListener {
                        binding.switchYandereMode.isChecked = false
                    }
                    .show()
            }
        }

        binding.switchExecMode.setOnCheckedChangeListener { _, isChecked ->
            if (!binding.switchExecMode.isPressed) {
                return@setOnCheckedChangeListener
            }

            if (!isChecked) {
                isAwaitingShizukuPermission = false
                saveExecModeEnabled(false)
                return@setOnCheckedChangeListener
            }

            requestShizukuPermissionIfNeeded()
        }
    }

    private fun requestShizukuPermissionIfNeeded() {
        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ping Shizuku binder", e)
            false
        }

        if (!binderAlive) {
            Toast.makeText(this, "Shizuku 当前不可用，执行模式将自动降级为普通进程", Toast.LENGTH_SHORT).show()
            isAwaitingShizukuPermission = false
            binding.switchExecMode.isChecked = true
            saveExecModeEnabled(true)
            return
        }

        if (Shizuku.isPreV11()) {
            Toast.makeText(this, "当前 Shizuku 版本过旧，执行模式将自动降级", Toast.LENGTH_SHORT).show()
            isAwaitingShizukuPermission = false
            binding.switchExecMode.isChecked = true
            saveExecModeEnabled(true)
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            isAwaitingShizukuPermission = false
            binding.switchExecMode.isChecked = true
            saveExecModeEnabled(true)
            Toast.makeText(this, "Shizuku 已授权，执行模式已启用", Toast.LENGTH_SHORT).show()
            return
        }

        isAwaitingShizukuPermission = true
        binding.switchExecMode.isChecked = true
        Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
    }

    private fun syncExecModeSwitchWithShizukuState() {
        if (!isAwaitingShizukuPermission) {
            return
        }

        val granted = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync Shizuku permission state", e)
            false
        }

        if (granted) {
            Log.d(TAG, "Shizuku permission detected during onResume")
            isAwaitingShizukuPermission = false
            binding.switchExecMode.isChecked = true
            saveExecModeEnabled(true)
        } else {
            Log.d(TAG, "Still waiting for Shizuku permission result")
            binding.switchExecMode.isChecked = true
        }
    }

    private fun saveExecModeEnabled(enabled: Boolean) {
        val settings = settingsManager.getSettings().copy(isExecModeEnabled = enabled)
        settingsManager.saveSettings(settings)
    }

    private fun setupActiveMessageControls() {
        binding.switchActiveMessage.setOnCheckedChangeListener { _, isChecked ->
            binding.switchBackgroundMessage.isEnabled = isChecked
            binding.sliderInterval.isEnabled = isChecked
            binding.tvIntervalLabel.alpha = if (isChecked) 1.0f else 0.5f
        }

        binding.sliderInterval.addOnChangeListener { _, value, _ ->
            binding.tvIntervalLabel.text = "主动消息时间间隔: ${formatMinutes(value.toInt())}"
        }
    }

    private fun setupButtons() {
        binding.btnModelProfiles.setOnClickListener {
            startActivity(Intent(this, ModelProfileActivity::class.java))
        }

        binding.btnFetchModels.setOnClickListener {
            fetchModels()
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        updateTestButtonState()
    }

    private fun fetchModels() {
        val apiKey = binding.etApiKey.text.toString().trim()
        val baseUrl = if (binding.spinnerApiProvider.selectedItemPosition == 3 ||
            binding.spinnerApiProvider.selectedItemPosition >= 4
        ) {
            binding.etCustomUrl.text.toString().trim()
        } else {
            ChatSettings.getDefaultUrl(
                when (binding.spinnerApiProvider.selectedItemPosition) {
                    0 -> ApiProvider.DEEPSEEK
                    1 -> ApiProvider.SILICON_FLOW
                    2 -> ApiProvider.KIMI_CODE
                    else -> ApiProvider.CUSTOM
                }
            )
        }

        if (apiKey.isEmpty() || baseUrl.isEmpty()) {
            Toast.makeText(this, "请先填写 API Key 和 URL", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("正在获取模型列表...")
            .setCancelable(false)
            .create()
        dialog.show()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()

                    val trimmed = baseUrl.trimEnd('/')
                    val apiRoot = if (trimmed.endsWith("/chat/completions")) {
                        trimmed.removeSuffix("/chat/completions")
                    } else {
                        trimmed
                    }
                    val modelsUrl = when {
                        apiRoot.contains("/v3") -> "$apiRoot/models"
                        apiRoot.contains("/v2") -> "$apiRoot/models"
                        apiRoot.contains("/v1") -> "$apiRoot/models"
                        else -> "$apiRoot/v1/models"
                    }

                    val request = Request.Builder()
                        .url(modelsUrl)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .build()
                    client.newCall(request).execute().use { response ->
                        response.body?.string().orEmpty()
                    }
                }

                dialog.dismiss()
                val json = JSONObject(result)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val models = (0 until data.length())
                        .map { data.getJSONObject(it).optString("id", "") }
                        .filter { it.isNotEmpty() }
                        .sorted()

                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("找到 ${models.size} 个模型")
                        .setItems(models.toTypedArray()) { _, which ->
                            binding.etModelName.setText(models[which])
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    Toast.makeText(this@SettingsActivity, "没有获取到模型列表", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                dialog.dismiss()
                Toast.makeText(this@SettingsActivity, "获取模型失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadSettings() {
        val settings = settingsManager.getSettings()

        val providerPos = when (settings.apiProvider) {
            ApiProvider.DEEPSEEK -> 0
            ApiProvider.SILICON_FLOW -> 1
            ApiProvider.KIMI_CODE -> 2
            ApiProvider.CUSTOM -> 3
        }
        binding.spinnerApiProvider.setSelection(providerPos)

        binding.etApiKey.setText(settings.apiKey)
        binding.etCustomUrl.setText(settings.apiUrl)
        binding.etModelName.setText(settings.modelName)
        binding.etJailbreakPrompt.setText(settings.jailbreakPrompt)

        binding.switchYandereMode.isChecked = settings.isYandereMode
        binding.switchExecMode.isChecked = settings.isExecModeEnabled
        binding.switchDarkMode.isChecked = settings.isDarkMode
        binding.switchMemoryEnabled.isChecked = settings.isMemoryEnabled
        binding.switchChatHistory.isChecked = settings.isChatHistoryEnabled
        binding.switchStreamResponse.isChecked = settings.isStreamResponse
        binding.switchKimiThinkingMode.isChecked = settings.isKimiThinkingModeEnabled

        binding.switchActiveMessage.isChecked = settings.isActiveMessageEnabled
        binding.switchBackgroundMessage.isChecked = settings.isBackgroundMessageEnabled
        binding.switchBackgroundMessage.isEnabled = settings.isActiveMessageEnabled

        val intervalMinutes = (settings.activeMessageIntervalMs / 60000).coerceIn(15, 240)
        binding.sliderInterval.value = intervalMinutes.toFloat()
        binding.sliderInterval.isEnabled = settings.isActiveMessageEnabled
        binding.tvIntervalLabel.text = "主动消息时间间隔: ${formatMinutes(intervalMinutes.toInt())}"
        binding.tvIntervalLabel.alpha = if (settings.isActiveMessageEnabled) 1.0f else 0.5f

        when (providerPos) {
            0 -> {
                binding.etApiKey.setText(loadProviderKey(0).ifEmpty { settings.apiKey })
                binding.etCustomUrl.setText(
                    loadProviderUrl(0).ifEmpty {
                        settings.apiUrl.ifEmpty { ChatSettings.getDefaultUrl(ApiProvider.DEEPSEEK) }
                    }
                )
                binding.etModelName.setText(
                    loadProviderModelName(0).ifEmpty {
                        settings.modelName.ifEmpty { ChatSettings.getDefaultModel(ApiProvider.DEEPSEEK) }
                    }
                )
            }

            1 -> {
                binding.etApiKey.setText(loadProviderKey(1).ifEmpty { settings.apiKey })
                binding.etCustomUrl.setText(
                    loadProviderUrl(1).ifEmpty {
                        settings.apiUrl.ifEmpty { ChatSettings.getDefaultUrl(ApiProvider.SILICON_FLOW) }
                    }
                )
                binding.etModelName.setText(
                    loadProviderModelName(1).ifEmpty {
                        settings.modelName.ifEmpty { ChatSettings.getDefaultModel(ApiProvider.SILICON_FLOW) }
                    }
                )
            }

            2 -> {
                binding.etApiKey.setText(loadProviderKey(2).ifEmpty { settings.apiKey })
                binding.etCustomUrl.setText(
                    loadProviderUrl(2).ifEmpty {
                        settings.apiUrl.ifEmpty { ChatSettings.getDefaultUrl(ApiProvider.KIMI_CODE) }
                    }
                )
                binding.etModelName.setText(
                    loadProviderModelName(2).ifEmpty {
                        settings.modelName.ifEmpty { ChatSettings.getDefaultModel(ApiProvider.KIMI_CODE) }
                    }
                )
            }

            else -> {
                binding.etApiKey.setText(loadProviderKey(3).ifEmpty { settings.apiKey })
                binding.etCustomUrl.setText(loadProviderUrl(3).ifEmpty { settings.apiUrl })
                binding.etModelName.setText(
                    loadProviderModelName(3).ifEmpty {
                        settings.modelName.ifEmpty { ChatSettings.getDefaultModel(ApiProvider.CUSTOM) }
                    }
                )
            }
        }
    }

    private fun saveSettings() {
        val apiProvider = when (binding.spinnerApiProvider.selectedItemPosition) {
            0 -> ApiProvider.DEEPSEEK
            1 -> ApiProvider.SILICON_FLOW
            2 -> ApiProvider.KIMI_CODE
            else -> ApiProvider.CUSTOM
        }

        val intervalMs = binding.sliderInterval.value.toLong() * 60 * 1000

        val currentPosition = binding.spinnerApiProvider.selectedItemPosition
        if (currentPosition < 4) {
            saveProviderKey(
                currentPosition,
                binding.etApiKey.text.toString().trim(),
                binding.etCustomUrl.text.toString().trim(),
                binding.etModelName.text.toString().trim()
            )
        }

        val settings = ChatSettings(
            apiProvider = apiProvider,
            apiKey = binding.etApiKey.text.toString().trim(),
            apiUrl = binding.etCustomUrl.text.toString().trim(),
            modelName = binding.etModelName.text.toString().trim(),
            isYandereMode = binding.switchYandereMode.isChecked,
            isExecModeEnabled = binding.switchExecMode.isChecked,
            isDarkMode = binding.switchDarkMode.isChecked,
            isMemoryEnabled = binding.switchMemoryEnabled.isChecked,
            isChatHistoryEnabled = binding.switchChatHistory.isChecked,
            isActiveMessageEnabled = binding.switchActiveMessage.isChecked,
            isBackgroundMessageEnabled = binding.switchBackgroundMessage.isChecked,
            activeMessageIntervalMs = intervalMs,
            isStreamResponse = binding.switchStreamResponse.isChecked,
            jailbreakPrompt = binding.etJailbreakPrompt.text?.toString().orEmpty(),
            isKimiThinkingModeEnabled = binding.switchKimiThinkingMode.isChecked
        )

        settingsManager.saveSettings(settings)
        proactiveMessageManager.updateProactiveMessageWork()

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateTestButtonState() = Unit

    private fun testConnection() {
        val apiKey = binding.etApiKey.text.toString().trim()
        val modelName = binding.etModelName.text.toString().trim()

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请先输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        if (modelName.isEmpty()) {
            Toast.makeText(this, "请先输入模型名称", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("测试连接")
            .setMessage("正在测试 API 连接...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            try {
                val result = testApiConnection(apiKey, modelName)
                progressDialog.dismiss()

                if (result.success) {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("连接成功")
                        .setMessage("API 连接正常\n\n模型: ${result.model}\n提供商: ${result.provider}")
                        .setPositiveButton("确定", null)
                        .show()
                } else {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("连接失败")
                        .setMessage("错误信息: ${result.error}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("连接失败")
                    .setMessage("异常: ${e.message}")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    private data class TestResult(
        val success: Boolean,
        val model: String = "",
        val provider: String = "",
        val error: String = ""
    )

    private suspend fun testApiConnection(apiKey: String, modelName: String): TestResult {
        return withContext(Dispatchers.IO) {
            try {
                val apiProvider = when (binding.spinnerApiProvider.selectedItemPosition) {
                    0 -> ApiProvider.DEEPSEEK
                    1 -> ApiProvider.SILICON_FLOW
                    2 -> ApiProvider.KIMI_CODE
                    else -> ApiProvider.CUSTOM
                }

                val baseUrlRaw = if (apiProvider == ApiProvider.CUSTOM) {
                    binding.etCustomUrl.text.toString().trim()
                } else {
                    ChatSettings.getDefaultUrl(apiProvider)
                }
                val baseUrl = baseUrlRaw.trimEnd('/')
                val chatUrl = if (baseUrl.endsWith("chat/completions")) baseUrl else "$baseUrl/chat/completions"

                if (baseUrl.isEmpty()) {
                    return@withContext TestResult(false, error = "API URL 不能为空")
                }

                val api = RetrofitClient.getInstance().getApi()

                Log.d(TAG, "Testing URL: $chatUrl")
                Log.d(TAG, "Provider: $apiProvider, Model: $modelName")

                val request = ChatRequest(
                    model = modelName,
                    messages = listOf(
                        ChatMessage.text(ChatMessage.ROLE_SYSTEM, "You are a helpful assistant."),
                        ChatMessage.text(ChatMessage.ROLE_USER, "Hi")
                    ),
                    stream = false,
                    max_tokens = 10
                )

                val response = api.chatCompletion(chatUrl, "Bearer $apiKey", request = request)

                if (response.isSuccessful) {
                    val body = response.body()
                    when {
                        body?.error != null -> TestResult(false, error = body.error.message ?: "未知错误")
                        body?.choices?.isNotEmpty() == true -> TestResult(
                            success = true,
                            model = body.model ?: modelName,
                            provider = apiProvider.name
                        )
                        else -> TestResult(false, error = "响应为空")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = try {
                        JSONObject(errorBody ?: "")
                            .optJSONObject("error")
                            ?.optString("message", "请求失败")
                            ?: "请求失败"
                    } catch (e: Exception) {
                        errorBody ?: "HTTP ${response.code()}"
                    }
                    TestResult(false, error = errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Test connection failed", e)

                val errorDetails = buildString {
                    appendLine("异常类型: ${e.javaClass.simpleName}")
                    appendLine("错误消息: ${e.message}")
                    if (e.cause != null) {
                        appendLine("根本原因: ${e.cause?.javaClass?.simpleName}")
                        appendLine("原因详情: ${e.cause?.message}")
                    }
                    appendLine("---")
                    e.stackTrace.take(3).forEach { element ->
                        appendLine("  at $element")
                    }
                }
                TestResult(false, error = errorDetails.trim())
            }
        }
    }

    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) {
            if (mins > 0) "${hours}小时${mins}分钟" else "${hours}小时"
        } else {
            "${minutes}分钟"
        }
    }
}
