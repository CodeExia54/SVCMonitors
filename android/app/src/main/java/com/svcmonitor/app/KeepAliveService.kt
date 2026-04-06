package com.svcmonitor.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class KeepAliveService : Service() {

    companion object {
        const val ACTION_START = "com.svcmonitor.app.action.KEEPALIVE_START"
        const val ACTION_STOP = "com.svcmonitor.app.action.KEEPALIVE_STOP"
        private const val CHANNEL_ID = "svc_monitor_keepalive"
        private const val CHANNEL_NAME = "SVC Monitor Keepalive"
        private const val NOTIF_ID = 10031
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        return if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            START_NOT_STICKY
        } else {
            ensureChannel()
            startForeground(NOTIF_ID, buildNotification())
            START_STICKY
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            description = "Keeps SVC monitor alive while monitoring in background."
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return b
            .setContentTitle("SVC Monitor running")
            .setContentText("Background monitoring is active.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }
}

