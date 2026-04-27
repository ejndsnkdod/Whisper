package com.nocturne.whisper.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ProactiveMessageManager(private val context: Context) {

    companion object {
        private const val TAG = "ProactiveMessageManager"

        private const val MIN_INTERVAL_MINUTES = 15L
        private const val MAX_INTERVAL_MINUTES = 240L
        private const val DEFAULT_INTERVAL_MINUTES = 60L

        @Volatile
        private var INSTANCE: ProactiveMessageManager? = null

        fun getInstance(context: Context): ProactiveMessageManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProactiveMessageManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val workManager = WorkManager.getInstance(context)
    private val settingsManager = SettingsManager.getInstance(context)

    fun updateProactiveMessageWork() {
        val settings = settingsManager.getSettings()

        if (settings.isActiveMessageEnabled) {
            scheduleWork(settings.activeMessageIntervalMs)
        } else {
            cancelWork()
        }
    }

    fun enable(intervalMs: Long) {
        scheduleWork(intervalMs)
    }

    fun disable() {
        cancelWork()
    }

    fun updateInterval(intervalMs: Long) {
        val settings = settingsManager.getSettings()
        if (settings.isActiveMessageEnabled) {

            scheduleWork(intervalMs)
        }
    }

    private fun scheduleWork(intervalMs: Long) {

        val intervalMinutes = (intervalMs / 60000).coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

        Log.d(TAG, "Scheduling proactive message work with interval: ${intervalMinutes}min")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ProactiveMessageWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(ProactiveMessageWorker.WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            ProactiveMessageWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.d(TAG, "Proactive message work scheduled successfully")
    }

    private fun cancelWork() {
        Log.d(TAG, "Cancelling proactive message work")
        workManager.cancelUniqueWork(ProactiveMessageWorker.WORK_TAG)
    }

    fun getIntervalMinutes(): Long {
        val settings = settingsManager.getSettings()
        return (settings.activeMessageIntervalMs / 60000).coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    }

    fun minutesToMs(minutes: Long): Long {
        return minutes * 60 * 1000
    }

    fun isEnabled(): Boolean {
        return settingsManager.getSettings().isActiveMessageEnabled
    }

    fun isBackgroundEnabled(): Boolean {
        return settingsManager.getSettings().isBackgroundMessageEnabled
    }
}
