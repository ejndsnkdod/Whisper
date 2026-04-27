package com.nocturne.whisper

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.nocturne.whisper.utils.ChatSessionManager
import com.nocturne.whisper.utils.ProactiveMessageManager
import com.nocturne.whisper.utils.SettingsManager

class WhisperApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val settingsManager = SettingsManager.getInstance(this)
        val settings = settingsManager.getSettings()

        if (settings.isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        ChatSessionManager.init(this)

        ProactiveMessageManager.getInstance(this).updateProactiveMessageWork()
    }
}
