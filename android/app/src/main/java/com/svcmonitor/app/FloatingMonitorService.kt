package com.svcmonitor.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FloatingMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager

    private lateinit var iconView: ImageView
    private lateinit var panelView: LinearLayout
    private lateinit var tvState: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLogs: TextView

    private lateinit var spinnerApp: Spinner
    private lateinit var spinnerPreset: Spinner
    private lateinit var etNrs: EditText

    private var appList: List<AppInfo> = emptyList()
    private var selectedApp: AppInfo? = null

    private var fileOffset = 0L
    private var tailBuf = ByteArray(0)
    private var useJsonFallback = false
    private var emptyBinPolls = 0
    private val eventBuffer = ArrayDeque<StatusParser.SvcEvent>(300)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureChannel()
        startForeground(NOTI_ID, buildNotification())

        setupFloatingViews()
        startPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        runCatching { wm.removeView(iconView) }
        runCatching { wm.removeView(panelView) }
    }

    private fun setupFloatingViews() {
        val iconParams = WindowManager.LayoutParams(
            dp(56),
            dp(56),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(160)
        }

        iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_def_app_icon)
            setBackgroundColor(Color.parseColor("#CC1565C0"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { togglePanel() }
            setOnLongClickListener {
                stopSelf()
                true
            }
            setOnTouchListener(DragTouchListener(iconParams))
        }

        val panelParams = WindowManager.LayoutParams(
            dp(360),
            dp(520),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(80)
            y = dp(120)
        }

        panelView = buildPanel().apply {
            visibility = View.GONE
            setOnTouchListener(DragTouchListener(panelParams))
        }

        wm.addView(iconView, iconParams)
        wm.addView(panelView, panelParams)
    }

    private fun buildPanel(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4FFFFFF"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val title = TextView(this).apply {
            text = "SVC Floating Monitor"
            setTextColor(Color.BLACK)
            textSize = 16f
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(title)

        tvState = TextView(this).apply {
            text = "Overlay: Running"
            setTextColor(Color.parseColor("#2E7D32"))
        }
        root.addView(tvState)

        tvStatus = TextView(this).apply {
            text = "Status: loading..."
            setTextColor(Color.DKGRAY)
        }
        root.addView(tvStatus)

        spinnerApp = Spinner(this)
        root.addView(spinnerApp)
        refreshApps()

        spinnerPreset = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@FloatingMonitorService,
                android.R.layout.simple_spinner_dropdown_item,
                StatusParser.presets.map { "${it.name} (${it.description})" }
            )
        }
        root.addView(spinnerPreset)

        etNrs = EditText(this).apply {
            hint = "NR list: 56,63,64"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        root.addView(etNrs)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeBtn("Refresh Apps") { refreshApps() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(makeBtn("Preset") { applyPreset() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(makeBtn("Start") { startMonitoring() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(makeBtn("Stop") { stopMonitoring() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row2)

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(makeBtn("Set NRs") { setNrs() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row3.addView(makeBtn("Clear") {
            scope.launch(Dispatchers.IO) {
                KpmBridge.clear()
                KpmBridge.clearEventFile()
                fileOffset = 0
                tailBuf = ByteArray(0)
                useJsonFallback = false
                emptyBinPolls = 0
                eventBuffer.clear()
                launch(Dispatchers.Main) { renderLogs() }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row3)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.parseColor("#111111"))
        }

        tvLogs = TextView(this).apply {
            text = "No logs yet"
            setTextColor(Color.parseColor("#66FF66"))
            textSize = 11f
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        scroll.addView(tvLogs)
        root.addView(scroll)

        root.addView(makeBtn("Hide") { panelView.visibility = View.GONE })

        return root
    }

    private fun makeBtn(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    private fun refreshApps() {
        appList = AppResolver.getAllApps(this, hideSystemApps = false, onlyLaunchableApps = true)
        spinnerApp.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Select app") + appList.map { "${it.label} (${it.uid})" }
        )
        spinnerApp.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedApp = if (position > 0) appList[position - 1] else null
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                selectedApp = null
            }
        }
    }

    private fun applyPreset() {
        val preset = StatusParser.presets.getOrNull(spinnerPreset.selectedItemPosition) ?: return
        scope.launch(Dispatchers.IO) {
            KpmBridge.preset(preset.id)
            launch(Dispatchers.Main) {
                Toast.makeText(this@FloatingMonitorService, "Preset ${preset.name} applied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setNrs() {
        val nrs = etNrs.text.toString()
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
        if (nrs.isEmpty()) {
            Toast.makeText(this, "Invalid NR list", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch(Dispatchers.IO) {
            KpmBridge.setNrs(nrs)
        }
    }

    private fun startMonitoring() {
        val app = selectedApp
        if (app == null) {
            Toast.makeText(this, "Select target app first", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch(Dispatchers.IO) {
            KpmBridge.setUid(app.uid)
            KpmBridge.enable()
            KpmBridge.clearEventFile()
            fileOffset = 0
            tailBuf = ByteArray(0)
            useJsonFallback = false
            emptyBinPolls = 0
        }
    }

    private fun stopMonitoring() {
        scope.launch(Dispatchers.IO) {
            KpmBridge.disable()
        }
    }

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                pollOnce()
                delay(700)
            }
        }
    }

    private suspend fun pollOnce() {
        val s = kotlinx.coroutines.withContext(Dispatchers.IO) { KpmBridge.status() }
        if (s.success && s.output.isNotEmpty()) {
            val status = StatusParser.parseStatus(s.output)
            tvStatus.text = "Status: ${if (status.enabled) "Running" else "Stopped"}, uid=${status.targetUid}, events=${status.eventsTotal}"

            if (status.enabled) {
                val chunk = kotlinx.coroutines.withContext(Dispatchers.IO) { KpmBridge.readEventFileChunk(fileOffset, 128 * 1024) }
                if (chunk.isNotEmpty()) {
                    fileOffset += chunk.size
                    val merged = ByteArray(tailBuf.size + chunk.size)
                    System.arraycopy(tailBuf, 0, merged, 0, tailBuf.size)
                    System.arraycopy(chunk, 0, merged, tailBuf.size, chunk.size)
                    val parsed = BinEventParser.parse(merged)
                    tailBuf = if (parsed.consumedBytes in 1 until merged.size) {
                        merged.copyOfRange(parsed.consumedBytes, merged.size)
                    } else ByteArray(0)
                    pushEvents(parsed.events)
                    emptyBinPolls = 0
                } else {
                    emptyBinPolls++
                }

                if (emptyBinPolls >= 6) useJsonFallback = true
                if (useJsonFallback) {
                    val dr = kotlinx.coroutines.withContext(Dispatchers.IO) { KpmBridge.drain(128) }
                    if (dr.success && dr.output.isNotEmpty()) {
                        val parsed = StatusParser.parseDrain(dr.output)
                        if (parsed.ok) pushEvents(parsed.events)
                    }
                }
            }
        }
    }

    private fun pushEvents(events: List<StatusParser.SvcEvent>) {
        if (events.isEmpty()) return
        for (e in events) {
            while (eventBuffer.size >= 220) {
                if (eventBuffer.isNotEmpty()) eventBuffer.removeFirst()
            }
            eventBuffer.addLast(e)
        }
        renderLogs()
    }

    private fun renderLogs() {
        if (eventBuffer.isEmpty()) {
            tvLogs.text = "No logs yet"
            return
        }
        val sb = StringBuilder()
        val start = (eventBuffer.size - 160).coerceAtLeast(0)
        var idx = 0
        for (it in eventBuffer) {
            if (idx++ < start) continue
            sb.append('#').append(it.seq)
                .append(' ')
                .append(it.name)
                .append(" pid=")
                .append(it.pid)
                .append(" uid=")
                .append(it.uid)
                .append(' ')
                .append(it.desc)
                .append('\n')
        }
        tvLogs.text = sb.toString()
    }

    private fun togglePanel() {
        panelView.visibility = if (panelView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "SVC Floating Monitor", NotificationManager.IMPORTANCE_LOW)
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("SVC floating monitor")
            .setContentText("Overlay running in background")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var sx = 0
        private var sy = 0
        private var tx = 0f
        private var ty = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = params.x
                    sy = params.y
                    tx = event.rawX
                    ty = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = sx + (event.rawX - tx).toInt()
                    params.y = sy + (event.rawY - ty).toInt()
                    wm.updateViewLayout(v, params)
                }
            }
            return false
        }
    }

    companion object {
        private const val CHANNEL_ID = "svc_floating_monitor"
        private const val NOTI_ID = 1001
    }
}
