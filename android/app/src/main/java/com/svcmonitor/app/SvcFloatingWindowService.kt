package com.svcmonitor.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.svcmonitor.app.db.SvcEventDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FLOAT_CH = "svc_float_overlay"
private const val FLOAT_NOTI_ID = 10103

class SvcFloatingWindowService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager
    private var iconView: TextView? = null
    private var panelView: View? = null
    private var panelVisible = false
    private var appList: List<AppInfo> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(FLOAT_NOTI_ID, buildNotification())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission required for floating window", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingViews()
        startLogUpdater()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun createFloatingViews() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        val iconParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
        }

        val icon = TextView(this).apply {
            text = "SVC"
            textSize = 16f
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xCC1565C0.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        makeDraggable(icon, iconParams)
        icon.setOnClickListener { togglePanel() }
        wm.addView(icon, iconParams)
        iconView = icon

        val panel = buildPanel()
        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = 260
        }
        panel.visibility = View.GONE
        wm.addView(panel, panelParams)
        panelView = panel
    }

    private fun buildPanel(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xF2191C22.toInt())
        }
        val title = TextView(this).apply {
            text = "SVC Floating Monitor"
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(title)

        val tvTarget = TextView(this).apply {
            tag = "target_view"
            textSize = 13f
            setTextColor(0xFFFFCC80.toInt())
            text = "Target UID: all (-1)"
            setPadding(0, 8, 0, 6)
        }
        root.addView(tvTarget)

        val etSearch = EditText(this).apply {
            hint = "Search app/process"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFB0BEC5.toInt())
        }
        root.addView(etSearch)

        val spinner = Spinner(this).apply { tag = "proc_spinner" }
        root.addView(spinner)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnSelect = Button(this).apply {
            text = "Select App"
            setOnClickListener {
                val item = spinner.selectedItem as? String ?: return@setOnClickListener
                val uid = item.substringAfterLast("uid=", "-1").substringBefore(")").toIntOrNull() ?: -1
                scope.launch(Dispatchers.IO) {
                    val r = KpmBridge.setUid(uid)
                    withContext(Dispatchers.Main) {
                        if (r.success) {
                            tvTarget.text = if (uid >= 0) "Target UID: $uid" else "Target UID: all (-1)"
                            Toast.makeText(this@SvcFloatingWindowService, "Target updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@SvcFloatingWindowService, "Set UID failed: ${r.error}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        val btnStart = Button(this).apply {
            text = "Start"
            setOnClickListener {
                scope.launch(Dispatchers.IO) {
                    val r = KpmBridge.enable()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SvcFloatingWindowService, if (r.success) "Monitoring enabled" else "Enable failed: ${r.error}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val btnStop = Button(this).apply {
            text = "Stop"
            setOnClickListener {
                scope.launch(Dispatchers.IO) {
                    val r = KpmBridge.disable()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SvcFloatingWindowService, if (r.success) "Monitoring disabled" else "Disable failed: ${r.error}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val btnOpen = Button(this).apply {
            text = "Open App"
            setOnClickListener {
                val i = Intent(this@SvcFloatingWindowService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(i)
            }
        }
        row.addView(btnSelect)
        row.addView(btnStart)
        row.addView(btnStop)
        row.addView(btnOpen)
        root.addView(row)

        setupProcessSelector(etSearch, spinner)

        val tvLog = TextView(this).apply {
            tag = "log_view"
            setTextColor(0xFFD7E3FC.toInt())
            textSize = 12f
            setPadding(0, 14, 0, 0)
            text = "Loading logs..."
        }
        val sv = ScrollView(this).apply { addView(tvLog) }
        root.addView(sv)
        return root
    }

    private fun setupProcessSelector(etSearch: EditText, spinner: Spinner) {
        appList = AppResolver.getAllApps(this, hideSystemApps = false, onlyLaunchableApps = true)

        fun refresh(q: String) {
            val list = if (q.isBlank()) appList else appList.filter {
                it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
            val labels = if (list.isEmpty()) {
                listOf("All apps (uid=-1)")
            } else {
                listOf("All apps (uid=-1)") + list.map { "${it.label} (${it.packageName}, uid=${it.uid})" }
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinner.adapter = adapter
        }

        refresh("")
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refresh(s?.toString().orEmpty().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun togglePanel() {
        panelVisible = !panelVisible
        panelView?.visibility = if (panelVisible) View.VISIBLE else View.GONE
    }

    private fun startLogUpdater() {
        val dao = SvcEventDb.get(applicationContext).dao()
        val tv = panelView?.findViewWithTag<TextView>("log_view") ?: return
        scope.launch {
            while (isActive) {
                val events = withContext(Dispatchers.IO) { dao.latest(30) }.asReversed()
                val text = if (events.isEmpty()) {
                    "No events yet."
                } else {
                    buildString {
                        for (e in events) {
                            append("#").append(e.seq).append(" ")
                                .append(e.name.ifBlank { "nr=${e.nr}" })
                                .append(" pid=").append(e.pid)
                                .append(" uid=").append(e.uid)
                                .append('\n')
                            if (e.desc.isNotBlank()) append("  ").append(e.desc.take(120)).append('\n')
                        }
                    }
                }
                tv.text = text
                delay(1200L)
            }
        }
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    wm.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(FLOAT_CH, "SVC Floating Window", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, FLOAT_CH)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("SVC Floating Overlay")
            .setContentText("Running with live events")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try { panelView?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { iconView?.let { wm.removeView(it) } } catch (_: Exception) {}
    }
}
