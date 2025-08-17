package com.bitchat.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bitchat.android.mesh.BluetoothMeshDelegate
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryAck
import com.bitchat.android.model.ReadReceipt
import com.bitchat.android.ui.DataManager
import com.bitchat.android.ui.NotificationManager as PMNotificationManager

class MeshForegroundService : Service() {
    private lateinit var meshService: BluetoothMeshService
    private lateinit var dataManager: DataManager
    private lateinit var notificationManager: PMNotificationManager
    private val backgroundDelegate = object : BluetoothMeshDelegate {
        override fun didReceiveMessage(message: BitchatMessage) {
            if (message.isPrivate) {
                message.senderPeerID?.let { sender ->
                    dataManager.savePendingPrivateMessage(message)
                    notificationManager.showPrivateMessageNotification(sender, message.sender, message.content)
                }
            }
        }
        override fun didUpdatePeerList(peers: List<String>) {}
        override fun didReceiveChannelLeave(channel: String, fromPeer: String) {}
        override fun didReceiveDeliveryAck(ack: DeliveryAck) {}
        override fun didReceiveReadReceipt(receipt: ReadReceipt) {}
        override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? = null
        override fun getNickname(): String? = null
        override fun isFavorite(peerID: String): Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as BitchatApplication
        meshService = app.meshService
        dataManager = DataManager(applicationContext)
        notificationManager = PMNotificationManager(applicationContext)
        notificationManager.setAppBackgroundState(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hasLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocation) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_START -> {
                if (!meshService.isRunning()) {
                    meshService.startServices()
                }
            }
            ACTION_USE_BACKGROUND_DELEGATE -> {
                meshService.delegate = backgroundDelegate
                if (!meshService.isRunning()) {
                    meshService.startServices()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (meshService.delegate === backgroundDelegate) {
            meshService.delegate = null
        }
    }

    private fun createNotification(): Notification {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Mesh Network", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.keep_network_active_background))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.bitchat.android.action.START"
        const val ACTION_USE_BACKGROUND_DELEGATE = "com.bitchat.android.action.BACKGROUND_DELEGATE"
        private const val CHANNEL_ID = "mesh_foreground"
        private const val NOTIFICATION_ID = 1
    }
}

