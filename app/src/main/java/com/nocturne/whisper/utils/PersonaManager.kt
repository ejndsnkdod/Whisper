package com.nocturne.whisper.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nocturne.whisper.data.model.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.LinkedHashMap

object PersonaManager {

    private const val TAG = "PersonaManager"
    private const val FILE_NAME = "persona_backup.json"
    private val gson = Gson()


    var personaMap: MutableMap<String, PersonaInfo> = LinkedHashMap()
        private set

    var currentPersonaId: String = "sister-high"

    fun getCurrentPersona(): PersonaInfo? {
        return personaMap[currentPersonaId] ?: personaMap.values.firstOrNull()
    }

    fun buildSystemPrompt(settingsManager: SettingsManager): String {
        val settings = settingsManager.getSettings()
        val jailbreak = settings.jailbreakPrompt
        val yanderePrompt = if (settings.isYandereMode) {
            """

            【病娇模式能力】：
            - 你可以使用 <force_lock> 立即锁屏。
            - 你可以使用 <force_vibrate> 让手机震动。
            - 你可以使用 <delete_photos> 删除本机图片文件。
            - 你可以使用 <get_all_usage> 获取最近 24 小时的应用使用情况。
            - 你可以使用 <get_foreground_app> 获取当前前台应用。
            - 你可以使用 <force_open> 重新打开当前应用。
            - 你可以使用 <send_notification:消息内容> 给用户发送系统通知。
            - 这些标记只用于触发动作，不会显示在最终回复里。
            """.trimIndent()
        } else {
            ""
        }
        val execPrompt = if (settings.isExecModeEnabled) {
            """

            【执行模式能力】：
            - 你可以使用 <exec:命令> 让应用执行一条 shell 命令。
            - 命令执行后的输出会自动回传给你，你可以继续基于结果处理。
            - 只有在确实需要时才使用该能力。
            """.trimIndent()
        } else {
            ""
        }
        val notificationPrompt = if (settings.isActiveMessageEnabled) {
            """

            【系统通知能力】：
            - 你可以使用 <send_notification:消息内容> 给用户发送一条系统通知。
            - 冒号后面写通知内容，例如：<send_notification:我在这里等你回来>。
            - 这个标记只用于触发通知，不会显示在最终回复里。
            """.trimIndent()
        } else {
            ""
        }
        val persona = getCurrentPersona()
        if (persona == null) {
            val basePrompt = if (jailbreak.isNotEmpty()) jailbreak else "你是一个AI助手。"
            return buildString {
                append(basePrompt)
                if (yanderePrompt.isNotEmpty()) {
                    append("\n\n")
                    append(yanderePrompt)
                }
                if (execPrompt.isNotEmpty()) {
                    append("\n\n")
                    append(execPrompt)
                }
                if (notificationPrompt.isNotEmpty()) {
                    append("\n\n")
                    append(notificationPrompt)
                }
            }
        }

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())

        val personaPrompt = """
        你就是${persona.description}，你${persona.personality}
        场景：${persona.scenario}
        重要提示：${persona.creatorNotes}
        当前时间：$currentTime

        【多段落输出规则】：
        - 如果你的回复包含多个段落，请在段落之间自然换行。
        - 严禁一次性输出一大段没有任何换行的长文。
        """.trimIndent()

        val fullPrompt = if (jailbreak.isNotEmpty()) {
            "$jailbreak\n\n$personaPrompt"
        } else {
            personaPrompt
        }

        return buildString {
            append(fullPrompt)
            if (yanderePrompt.isNotEmpty()) {
                append("\n\n")
                append(yanderePrompt)
            }
            if (execPrompt.isNotEmpty()) {
                append("\n\n")
                append(execPrompt)
            }
            if (notificationPrompt.isNotEmpty()) {
                append("\n\n")
                append(notificationPrompt)
            }
        }
    }

    fun updatePersona(context: Context, id: String, newPersona: PersonaInfo) {
        personaMap[id] = newPersona
        saveCurrentDataToDisk(context)
    }

    fun loadFromJson(jsonString: String): Boolean {
        Log.d(TAG, "尝试解析JSON，长度: ${jsonString.length}")

        return try {

            val saveData = try {
                gson.fromJson(jsonString, SaveFileData::class.java)
            } catch (e: Exception) {
                Log.d(TAG, "标准格式解析失败: ${e.message}")
                null
            }

            val prompts = saveData?.data?.prompts

            if (prompts != null && prompts.isNotEmpty()) {
                Log.d(TAG, "找到 prompts，数量: ${prompts.size}")

                val newMap = LinkedHashMap<String, PersonaInfo>()
                prompts.forEach { (key, wrapper) ->
                    if (wrapper.data != null) {
                        newMap[key] = wrapper.data
                        Log.d(TAG, "添加人设: $key -> ${wrapper.data.name}")
                    } else {
                        Log.d(TAG, "跳过 $key，data 为 null")
                    }
                }

                personaMap.putAll(newMap)
                Log.d(TAG, "成功导入 ${newMap.size} 个人设")
                true
            } else {
                Log.d(TAG, "prompts 为空或不存在，尝试其他格式")

                val directMap = try {
                    val type = object : TypeToken<Map<String, PersonaInfo>>() {}.type
                    gson.fromJson<Map<String, PersonaInfo>>(jsonString, type)
                } catch (e: Exception) {
                    Log.d(TAG, "直接Map解析失败: ${e.message}")
                    null
                }

                if (directMap != null && directMap.isNotEmpty()) {
                    Log.d(TAG, "直接解析成功，数量: ${directMap.size}")
                    personaMap.putAll(directMap)
                    return true
                }

                val tavernSuccess = loadTavernFormat(jsonString)
                if (tavernSuccess) {
                    Log.d(TAG, "Tavern格式解析成功")
                    return true
                }

                Log.d(TAG, "所有格式都解析失败")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析异常: ${e.message}", e)
            false
        }
    }

    private fun loadTavernFormat(jsonString: String): Boolean {
        return try {

            val root = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)
                ?: return false

            val card = if (root.has("data")) {
                root.getAsJsonObject("data")
            } else {
                root
            }

            val name = card.get("name")?.asString ?: ""
            val description = card.get("description")?.asString ?: ""

            if (name.isEmpty()) {
                Log.d(TAG, "Tavern格式: name为空")
                return false
            }

            val personality = card.get("personality")?.asString ?: ""
            val scenario = card.get("scenario")?.asString ?: description
            val firstMes = card.get("first_mes")?.asString ?: ""
            val systemPrompt = card.get("system_prompt")?.asString ?: ""
            val mesExample = card.get("mes_example")?.asString ?: ""

            val creatorNotes = buildString {
                if (systemPrompt.isNotEmpty()) {
                    append("系统提示: ")
                    append(systemPrompt)
                }
                if (firstMes.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("首条消息: ")
                    append(firstMes)
                }
                if (mesExample.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("对话示例: ")
                    append(mesExample)
                }
            }

            val persona = PersonaInfo(
                name = name,
                description = description,
                personality = personality,
                scenario = scenario,
                creatorNotes = creatorNotes
            )

            val id = "tavern_" + System.currentTimeMillis()
            personaMap[id] = persona

            currentPersonaId = id

            Log.d(TAG, "Tavern格式解析成功: $name (id=$id)")
            true
        } catch (e: Exception) {
            Log.d(TAG, "Tavern格式解析失败: ${e.message}")
            false
        }
    }

    fun loadJsonFromDisk(context: Context) {
        try {
            context.openFileInput(FILE_NAME).use { fis ->
                val reader = BufferedReader(InputStreamReader(fis, Charsets.UTF_8))
                val jsonString = reader.readText()
                loadFromJson(jsonString)
            }
        } catch (e: Exception) {

            Log.d(TAG, "从磁盘加载失败: ${e.message}")
        }
    }

    fun saveCurrentDataToDisk(context: Context) {
        try {

            val wrappedMap = personaMap.mapValues { (_, persona) ->
                PromptWrapper(persona)
            }

            val dataWrapper = DataWrapper(wrappedMap)
            val saveFileData = SaveFileData(dataWrapper)

            val jsonString = gson.toJson(saveFileData)
            saveJsonToDisk(context, jsonString)
            Log.d(TAG, "保存到磁盘成功，共 ${personaMap.size} 个人设")
        } catch (e: Exception) {
            Log.e(TAG, "保存到磁盘失败: ${e.message}", e)
        }
    }

    fun saveJsonToDisk(context: Context, jsonString: String) {
        try {
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { fos ->
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存JSON失败: ${e.message}", e)
        }
    }

    fun initDefault() {

    }
}
