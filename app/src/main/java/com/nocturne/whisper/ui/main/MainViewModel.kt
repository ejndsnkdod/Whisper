package com.nocturne.whisper.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nocturne.whisper.utils.SettingsManager

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager.getInstance(application)

    fun getCurrentPersonaId(): String? {
        return settingsManager.getSettings().currentPersonaId
    }

    fun setCurrentPersona(personaId: String) {
        val settings = settingsManager.getSettings()
        settingsManager.saveSettings(settings.copy(currentPersonaId = personaId))
    }
}
