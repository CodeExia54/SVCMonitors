package com.svcmonitor.app

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
import com.svcmonitor.app.db.SvcEventDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SvcBgRelay"
private const val CH_ID = "svc_bg_relay"
private const val NOTI_ID = 10102

class BackgroundRelayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var relayJob: Job? = null
    private var lastSeq = 0L
    private val wsConnected = AtomicBoolean(false)

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var ws: WebSocket? = null
    private var wsReconnectJob: Job? = null
    private var wsBackoff = 1500L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (relayJob == null) startLoop()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTI_ID, buildNotification("Background relay running"))
        startLoop()
    }

    private fun startLoop() {
        if (relayJob?.isActive == true) return
        relayJob = scope.launch {
            val dao = SvcEventDb.get(applicationContext).dao()
            while (isActive) {
                val prefs = getSharedPreferences("svcmon_prefs", MODE_PRIVATE)
                val wsEnabled = prefs.getBoolean("svc_ws_enabled", false)
                val wsUrl = prefs.getString("svc_ws_url", "ws://127.0.0.1:8080/ws") ?: ""
                val wsDevice = prefs.getString("svc_ws_device", "svc-device") ?: "svc-device"
                if (wsEnabled) {
                    ensureWs(wsUrl, wsDevice)
                    val events = dao.afterSeq(lastSeq, 200)
                    if (events.isNotEmpty()) {
                        lastSeq = events.last().seq
                        sendEvents(wsDevice, events.map {
                            JSONObject()
                                .put("seq", it.seq)
                                .put("name", it.name)
                                .put("nr", it.nr)
                                .put("pid", it.pid)
                                .put("uid", it.uid)
                                .put("comm", it.comm)
                                .put("ret", it.ret)
                                .put("desc", it.desc)
                        })
                    }
                } else {
                    closeWs()
                }
                delay(1500L)
            }
        }
    }

    private fun ensureWs(url: String, device: String) {
        if (wsConnected.get()) return
        if (url.isBlank()) return
        if (ws != null) return
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                wsConnected.set(true)
                wsBackoff = 1500L
                webSocket.send(
                    JSONObject()
                        .put("type", "hello")
                        .put("device", device)
                        .put("source", "svc-android-bg")
                        .toString()
                )
                Log.i(TAG, "ws open: $url")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                wsConnected.set(false)
                ws = null
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                wsConnected.set(false)
                ws = null
                Log.w(TAG, "ws fail: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (wsReconnectJob?.isActive == true) return
        wsReconnectJob = scope.launch {
            delay(wsBackoff)
            wsBackoff = (wsBackoff * 2).coerceAtMost(30_000L)
            ws = null
        }
    }

    private fun sendEvents(device: String, items: List<JSONObject>) {
        if (!wsConnected.get()) return
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        val payload = JSONObject()
            .put("type", "events")
            .put("device", device)
            .put("count", items.size)
            .put("events", arr)
            .put("ts", System.currentTimeMillis())
        ws?.send(payload.toString())
    }

    private fun closeWs() {
        wsConnected.set(false)
        ws?.close(1000, "disabled")
        ws = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(CH_ID, "SVC Background Relay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("SVC Background Relay")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        relayJob?.cancel()
        wsReconnectJob?.cancel()
        closeWs()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        scope.cancel()
    }
}
