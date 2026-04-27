package com.nocturne.whisper.utils

import android.content.pm.PackageManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

object ExecModeManager {

    private const val TAG = "ExecModeManager"
    private const val DEFAULT_TIMEOUT_SECONDS = 30L
    private val STRING_ARRAY_CLASS = arrayOf<String>().javaClass

    fun exec(command: String): String {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) {
            Log.w(TAG, "exec called with empty command")
            return "命令不能为空"
        }

        Log.d(TAG, "exec requested, command=$trimmedCommand")

        return try {
            val shizukuProcess = createShizukuProcess(trimmedCommand)
            if (shizukuProcess != null) {
                Log.d(TAG, "execution path=shizuku")
                return runProcess(shizukuProcess, "shizuku")
            }

            Log.d(TAG, "execution path=fallback_shell")
            val fallbackProcess = createFallbackProcess(trimmedCommand)
            runProcess(fallbackProcess, "fallback_shell")
        } catch (e: Exception) {
            Log.e(TAG, "exec failed, command=$trimmedCommand", e)
            "执行失败: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun createShizukuProcess(command: String): Process? {
        if (!canUseShizuku()) {
            Log.d(TAG, "shizuku unavailable, will fallback to ProcessBuilder")
            return null
        }

        return try {
            val method: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                STRING_ARRAY_CLASS,
                STRING_ARRAY_CLASS,
                String::class.java
            ).apply {
                isAccessible = true
            }

            method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as? Process
        } catch (e: Exception) {
            Log.w(TAG, "shizuku newProcess invocation failed, will fallback", e)
            null
        }
    }

    private fun canUseShizuku(): Boolean {
        return try {
            val preV11 = Shizuku.isPreV11()
            val binderAlive = Shizuku.pingBinder()
            val permissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            Log.d(
                TAG,
                "shizuku state preV11=$preV11 binderAlive=$binderAlive permissionGranted=$permissionGranted"
            )
            !preV11 && binderAlive && permissionGranted
        } catch (e: Exception) {
            Log.w(TAG, "failed to inspect Shizuku state, will fallback", e)
            false
        }
    }

    private fun createFallbackProcess(command: String): Process {
        return ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
    }

    private fun runProcess(process: Process, path: String): String {
        try {
            Log.d(TAG, "waiting for process, path=$path")
            val finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                Log.w(TAG, "process timeout, path=$path, timeoutSeconds=$DEFAULT_TIMEOUT_SECONDS")
                process.destroyForcibly()
                return "执行超时"
            }

            val exitCode = process.exitValue()
            val stdout = BufferedReader(
                InputStreamReader(process.inputStream, Charsets.UTF_8)
            ).use { reader -> reader.readText().trim() }
            val stderr = BufferedReader(
                InputStreamReader(process.errorStream, Charsets.UTF_8)
            ).use { reader -> reader.readText().trim() }
            val output = buildString {
                if (stdout.isNotBlank()) {
                    append(stdout)
                }
                if (stderr.isNotBlank()) {
                    if (isNotBlank()) append('\n')
                    append(stderr)
                }
            }.trim()

            Log.d(
                TAG,
                "process finished, path=$path exitCode=$exitCode stdout=${stdout.take(800)} stderr=${stderr.take(800)}"
            )

            return when {
                output.isNotEmpty() && exitCode == 0 -> output
                output.isNotEmpty() -> "[exitCode=$exitCode]\n$output"
                else -> "[exitCode=$exitCode]"
            }
        } finally {
            Log.d(TAG, "destroy process, path=$path")
            process.destroy()
        }
    }
}
