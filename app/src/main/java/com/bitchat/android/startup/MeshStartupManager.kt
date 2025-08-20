package com.bitchat.android.startup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bitchat.android.BitchatApplication
import com.bitchat.android.MeshForegroundService
import com.bitchat.android.mesh.BluetoothPermissionManager

object MeshStartupManager {
    private const val BOOT_START_WORK = "mesh_boot_start_work"

    fun startInBackground(context: Context) {
        val appContext = context.applicationContext

        val dm = com.bitchat.android.ui.DataManager(appContext)
        if (!dm.isPersistentNetworkEnabled() || !dm.isStartOnBootEnabled()) return

        val permissionManager = BluetoothPermissionManager(appContext)
        if (!permissionManager.hasBluetoothPermissions()) return

        if (!isLocationEnabled(appContext)) return

        if (!isBluetoothEnabled(appContext)) return

        val serviceIntent = Intent(appContext, MeshForegroundService::class.java).apply {
            action = MeshForegroundService.ACTION_USE_BACKGROUND_DELEGATE
        }

        try {
            ContextCompat.startForegroundService(appContext, serviceIntent)
        } catch (_: Exception) {
            enqueueWorkFallback(appContext)
        }
    }

    private fun enqueueWorkFallback(context: Context) {
        val work = OneTimeWorkRequestBuilder<BootStartupWorker>()
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BOOT_START_WORK,
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    private fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        return manager.adapter?.isEnabled == true
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            val gps = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
            val net = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
            gps || net
        }
    }
}

