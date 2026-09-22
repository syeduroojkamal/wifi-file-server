package org.foss.wififileserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class ServerService : Service() {

    private var server: FileServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVER) {
            sendStatus(BROADCAST_SERVER_STOPPED)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra("PORT", 8080) ?: 8080
        val address = intent?.getStringExtra(EXTRA_SERVER_ADDRESS)

        if (server == null) {
            server = FileServer(applicationContext, port)
            try {
                server?.start()
                val notification = buildNotification(address, port)
                startForeground(NOTIFICATION_ID, notification)
                sendStatus(BROADCAST_SERVER_STARTED)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start file server", e)
                sendStatus(
                    BROADCAST_SERVER_FAILED,
                    e.message ?: "Unable to start the server"
                )
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi File Server — FOSS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background file sharing service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(address: String?, port: Int): Notification {
        val stopIntent = Intent(this, ServerService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = "${address ?: "Server"}:$port - Tap to stop"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi File Server Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun sendStatus(action: String, errorMessage: String? = null) {
        val intent = Intent(action).setPackage(packageName)
        if (errorMessage != null) {
            intent.putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "ServerService"
        const val CHANNEL_ID = "file_server_channel"
        const val NOTIFICATION_ID = 101
        const val BROADCAST_SERVER_STARTED = "org.foss.wififileserver.SERVER_STARTED"
        const val BROADCAST_SERVER_FAILED = "org.foss.wififileserver.SERVER_FAILED"
        const val BROADCAST_SERVER_STOPPED = "org.foss.wififileserver.SERVER_STOPPED"
        const val EXTRA_ERROR_MESSAGE = "ERROR_MESSAGE"
        const val EXTRA_SERVER_ADDRESS = "SERVER_ADDRESS"
        const val ACTION_STOP_SERVER = "org.foss.wififileserver.STOP_SERVER"
    }
}
