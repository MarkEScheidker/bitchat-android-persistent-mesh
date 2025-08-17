package com.bitchat.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.bitchat.android.ui.DataManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val dm = DataManager(context.applicationContext)
            if (dm.isPersistentNetworkEnabled() && dm.isStartOnBootEnabled()) {
                val hasLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                if (hasLocation) {
                    val serviceIntent = Intent(context, MeshForegroundService::class.java).apply {
                        action = MeshForegroundService.ACTION_USE_BACKGROUND_DELEGATE
                    }
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }
}
