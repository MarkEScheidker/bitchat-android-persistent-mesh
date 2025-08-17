package com.bitchat.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.bitchat.android.ui.DataManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_USER_UNLOCKED) {
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

                val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                if (hasLocation && hasBackgroundLocation && hasNotifications) {
                    val serviceIntent = Intent(context, MeshForegroundService::class.java).apply {
                        action = MeshForegroundService.ACTION_USE_BACKGROUND_DELEGATE
                    }
                    try {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } catch (_: Exception) {
                        // ignore failures to start service at boot
                    }
                }
            }
        }
    }
}
