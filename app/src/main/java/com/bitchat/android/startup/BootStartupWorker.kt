package com.bitchat.android.startup

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bitchat.android.MeshForegroundService

class BootStartupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val intent = Intent(applicationContext, MeshForegroundService::class.java).apply {
            action = MeshForegroundService.ACTION_USE_BACKGROUND_DELEGATE
        }
        return try {
            ContextCompat.startForegroundService(applicationContext, intent)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

