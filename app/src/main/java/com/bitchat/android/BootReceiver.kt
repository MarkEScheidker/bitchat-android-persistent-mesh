package com.bitchat.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            try {
                val prefs = context.getSharedPreferences("bitchat_prefs", Context.MODE_PRIVATE)
                val persistent = prefs.getBoolean("persistent_mode", false)
                val startOnBoot = prefs.getBoolean("start_on_boot", false)
                if (persistent && startOnBoot) {
                    Log.d("BootReceiver", "Starting PersistentMeshService on boot")
                    val serviceIntent = Intent(context, PersistentMeshService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start service on boot: ${e.message}")
            }
        }
    }
}
