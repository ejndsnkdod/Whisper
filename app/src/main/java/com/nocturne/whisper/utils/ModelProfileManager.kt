package com.nocturne.whisper.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nocturne.whisper.data.model.ModelProfile

class ModelProfileManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getProfiles(): List<ModelProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val type = object : TypeToken<List<ModelProfile>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveProfiles(profiles: List<ModelProfile>) {
        prefs.edit().putString(KEY_PROFILES, gson.toJson(profiles)).apply()
    }

    fun addProfile(profile: ModelProfile): Boolean {
        val list = getProfiles().toMutableList()
        if (list.size >= MAX_PROFILES) return false
        list.add(profile)
        saveProfiles(list)
        return true
    }

    fun deleteProfile(id: String) {
        val list = getProfiles().toMutableList()
        list.removeAll { it.id == id }
        saveProfiles(list)
        if (getCurrentProfileId() == id) {
            setCurrentProfileId(list.firstOrNull()?.id ?: "")
        }
    }

    fun getCurrentProfileId(): String =
        prefs.getString(KEY_CURRENT_ID, "") ?: ""

    fun setCurrentProfileId(id: String) {
        prefs.edit().putString(KEY_CURRENT_ID, id).apply()
    }

    fun getCurrentProfile(): ModelProfile? {
        val id = getCurrentProfileId()
        return getProfiles().find { it.id == id }
    }

    companion object {
        private const val PREFS_NAME = "model_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_CURRENT_ID = "current_id"
        const val MAX_PROFILES = 10

        @Volatile
        private var INSTANCE: ModelProfileManager? = null

        fun getInstance(context: Context): ModelProfileManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelProfileManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
