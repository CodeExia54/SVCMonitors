package moe.fuqiuluo.mamu.service

import android.util.Log
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
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val WS_TAG = "SvcWebSocketRelay"

class SvcWebSocketRelay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var enabled = false

    @Volatile
    private var wsUrl = ""

    @Volatile
    private var deviceTag = ""

    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private val connected = AtomicBoolean(false)
    private var backoffMs = 1500L

    fun start(url: String, tag: String) {
        wsUrl = url
        deviceTag = tag
        enabled = true
        connectNow()
        startHeartbeat()
    }

    fun stop() {
        enabled = false
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        reconnectJob = null
        heartbeatJob = null
        connected.set(false)
        webSocket?.close(1000, "relay stop")
        webSocket = null
    }

    fun updateStatus(isFloatingActive: Boolean, keepAlive: Boolean) {
        if (!enabled || !connected.get()) return
        val payload = JSONObject()
            .put("type", "svc_status")
            .put("floating_active", isFloatingActive)
            .put("keep_alive", keepAlive)
            .put("ts", System.currentTimeMillis())
        webSocket?.send(payload.toString())
    }

    private fun connectNow() {
        if (!enabled || wsUrl.isBlank()) return
        val req = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.set(true)
                backoffMs = 1500L
                Log.i(WS_TAG, "connected: $wsUrl")
                val hello = JSONObject()
                    .put("type", "hello")
                    .put("device_tag", deviceTag)
                    .put("ts", System.currentTimeMillis())
                webSocket.send(hello.toString())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connected.set(false)
                webSocket.close(code, reason)
                if (enabled) scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected.set(false)
                if (enabled) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected.set(false)
                Log.w(WS_TAG, "failure: ${t.message}")
                if (enabled) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            if (enabled) connectNow()
        }
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive && enabled) {
                if (connected.get()) {
                    val hb = JSONObject()
                        .put("type", "heartbeat")
                        .put("device_tag", deviceTag)
                        .put("ts", System.currentTimeMillis())
                    webSocket?.send(hb.toString())
                }
                delay(10_000L)
            }
        }
    }

    fun release() {
        stop()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        scope.cancel()
    }
}
