package com.bitchat.android

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bitchat.android.mesh.BluetoothMeshDelegate
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.model.BitchatMessage

class PersistentMeshService : Service(), BluetoothMeshDelegate {
    companion object {
        const val CHANNEL_ID = "bitchat_foreground"
        private const val NOTIF_ID = 1
        @Volatile var instance: PersistentMeshService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this  // track current instance for delegate hand-off
        createNotificationChannel()
        Log.d("PersistentMeshService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start or attach to the mesh network
        val mesh = BitchatApplication.meshServiceInstance
        if (mesh == null) {
            // No mesh running yet – start a new one
            BitchatApplication.meshServiceInstance = BluetoothMeshService(applicationContext)
            BitchatApplication.meshServiceInstance!!.startServices()
            // Set this service as delegate to handle callbacks (no UI active)
            BitchatApplication.meshServiceInstance!!.delegate = this
            Log.d("PersistentMeshService", "Started new mesh service in background")
        } else {
            // Mesh already running (likely UI just toggled persistent on)
            // Do NOT override delegate here; delegate will be handed off on UI destroy
            Log.d("PersistentMeshService", "Mesh service already running, continuing in background")
        }

        // Prepare a persistent notification
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)  // reuse app's notification icon
            .setContentTitle("Mesh Network Active")
            .setContentText("BitChat mesh is running in the background.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("PersistentMeshService", "Service destroyed")
        instance = null
        // If the service is being stopped (e.g., user disabled persistent mode),
        // remove the foreground notification. Mesh stopping is handled by Activity if needed.
        stopForeground(true)
    }

    // No binding provided
    override fun onBind(intent: Intent?): IBinder? = null

    /** BluetoothMeshDelegate implementations for background mode **/
    override fun didReceiveMessage(message: BitchatMessage) {
        // Only handle private messages in background. Ignore group messages (ephemeral).
        if (message.isPrivate) {
            val senderID = message.senderPeerID ?: return
            val nickname = message.sender.takeIf { it.isNotEmpty() } ?: senderID
            // Store the message to show later in UI
            val offlineList = BitchatApplication.offlineMessages.getOrPut(senderID) { mutableListOf() }
            offlineList.add(message)
            // Show a notification for the new private message
            try {
                // Use existing NotificationManager for private messages
                val notificationManager = com.bitchat.android.ui.NotificationManager(applicationContext)
                notificationManager.showPrivateMessageNotification(senderID, nickname, message.content)
            } catch (e: Exception) {
                Log.e("PersistentMeshService", "Error showing DM notification: ${e.message}")
            }
            Log.d("PersistentMeshService", "Received private message from $nickname (stored for later)")
        }
        // (Channel/public messages are not stored to maintain ephemerality)
    }

    override fun didUpdatePeerList(peers: List<String>) {
        // No UI to update; optional: log network size
        Log.d("PersistentMeshService", "Peers updated: ${peers.size} connected")
    }

    override fun didReceiveChannelLeave(channel: String, fromPeer: String) {
        // No UI to update in background; ignore
    }

    override fun didReceiveDeliveryAck(ack: com.bitchat.android.model.DeliveryAck) {
        // Optionally handle delivery acknowledgements (not persisted while UI is closed)
        Log.d("PersistentMeshService", "Delivery ACK received for message ${ack.originalMessageID}")
    }

    override fun didReceiveReadReceipt(receipt: com.bitchat.android.model.ReadReceipt) {
        // Optionally handle read receipts (not persisted while UI is closed)
        Log.d("PersistentMeshService", "Read receipt received for message ${receipt.originalMessageID}")
    }

    override fun getNickname(): String? {
        // Provide the stored nickname for outgoing announcements
        val prefs = getSharedPreferences("bitchat_prefs", Context.MODE_PRIVATE)
        return prefs.getString("nickname", null) ?: "anon"
    }

    override fun isFavorite(peerID: String): Boolean {
        // In background, assume no favorites (to be safe, don't cache store-and-forward)
        return false
    }

    /** Create a low-priority notification channel for the persistent service */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Keeps the mesh network active in background"
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
