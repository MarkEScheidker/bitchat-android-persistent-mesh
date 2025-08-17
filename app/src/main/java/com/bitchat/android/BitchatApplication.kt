package com.bitchat.android

import android.app.Application
import com.bitchat.android.mesh.BluetoothMeshService

/**
 * Main application class for bitchat Android
 */
class BitchatApplication : Application() {
    val meshService: BluetoothMeshService by lazy { BluetoothMeshService(this) }

    override fun onCreate() {
        super.onCreate()

        // Initialize any global services or configurations
    }
}
