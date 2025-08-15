package com.bitchat.android

import android.app.Application
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.model.BitchatMessage

/**
 * Main application class for bitchat Android
 */
class BitchatApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize any global services or configurations
        // For now, keep it simple
    }

    companion object {
        @Volatile var meshServiceInstance: BluetoothMeshService? = null
        @Volatile var offlineMessages: MutableMap<String, MutableList<BitchatMessage>> = mutableMapOf()
    }
}
