package com.custom.sidesense

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EdgeOverlayService : Service() {
    companion object {
        private const val CH = "sidesense_fg"
        private const val NID = 1001
    }
    private lateinit var wm: WindowManager
    private var edgeView: View? = null
    private var panelView: View? = null
    private var backdropView: View? = null

    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        fg()
        if (edgeView == null) strip()
        return START_STICKY
    }
    private fun fg() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            nm.createNotificationChannel(NotificationChannel(CH, "SideSense", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("SideSense Lite aktif")
            .setContentText("Double tap strip untuk buka")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NID, n)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    
    private fun strip() {
        val v = LayoutInflater.from(this).inflate(R.layout.edge_strip, null)
        val p = WindowManager.LayoutParams(
            dp(8), dp(120),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(12) 
        }
        
        var lastClickTime = 0L
        v.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 300) {
                    toggle()
                    lastClickTime = 0
                } else {
                    lastClickTime = currentTime
                }
            }
            true
        }
        try { wm.addView(v, p); edgeView = v } catch (_: Throwable) {}
    }

    private fun toggle() { if (panelView != null) hide() else show() }

    private fun show() {
        if (panelView != null) return
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels

        val backdrop = View(this).apply {
            setBackgroundColor(0x22000000)
            setOnClickListener { hide() }
        }
        val backdropParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try { wm.addView(backdrop, backdropParams); backdropView = backdrop } catch (_: Throwable) {}

        val v = LayoutInflater.from(this).inflate(R.layout.panel, null)
        v.findViewById<View>(R.id.panel_close).setOnClickListener { hide() }
        
        v.findViewById<Button>(R.id.btn_power).setOnClickListener {
            hide()
            if (ShizukuHelper.hasPermission()) Thread { ShizukuHelper.screenOff() }.start()
        }
        v.findViewById<Button>(R.id.btn_screenshot).setOnClickListener {
            hide()
            if (ShizukuHelper.hasPermission()) Thread { ShizukuHelper.takeScreenshot() }.start()
        }

        val recycler = v.findViewById<RecyclerView>(R.id.recycler_apps)
        recycler.layoutManager = GridLayoutManager(this, 4)
        val apps = AppRepo.getLaunchableApps(this)
        
        recycler.adapter = AppAdapter(apps, 
            onClick = { app -> 
                hide()
                if (ShizukuHelper.hasPermission()) {
                    // Panggil pake Package Name (app.pkg)
                    Thread { 
                        val result = ShizukuHelper.launchFloating(app.pkg)
                        // Kalau ada tulisan ERR, berarti gagal. Tampilkan ke layar!
                        if (result.startsWith("ERR")) {
                            runOnUiThread { Toast.makeText(this@EdgeOverlayService, result, Toast.LENGTH_LONG).show() }
                        }
                    }.start()
                } else {
                    packageManager.getLaunchIntentForPackage(app.pkg)?.let { li ->
                        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { startActivity(li) } catch (_: Throwable) {}
                    }
                }
            },
            onLongClick = { app -> 
                hide()
                packageManager.getLaunchIntentForPackage(app.pkg)?.let { li ->
                    li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(li) } catch (_: Throwable) {}
                }
            }
        )

        val initWidth = (screenWidth * 0.75f).toInt()
        val initHeight = (screenHeight * 0.65f).toInt()
        val startX = screenWidth - initWidth - dp(16)
        val startY = (screenHeight - initHeight) / 2

        val panelParams = WindowManager.LayoutParams(
            initWidth, initHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
            if (Build.VERSION.SDK_INT >= 31) {
                setBlurBehindRadius(dp(25))
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            }
        }

        val header = v.findViewById<View>(R.id.panel_header)
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f

        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = panelParams.x
                    initY = panelParams.y
                    touchX = e.rawX
                    touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = initX + (e.rawX - touchX).toInt()
                    panelParams.y = initY + (e.rawY - touchY).toInt()
                    try { wm.updateViewLayout(v, panelParams) } catch (_: Throwable) {}
                    true
                }
                else -> false
            }
        }

        val resize = v.findViewById<View>(R.id.resize_handle)
        var initW = 0
        var initH = 0
        
        resize.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initW = panelParams.width
                    initH = panelParams.height
                    touchX = e.rawX
                    touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.width = (initW + (e.rawX - touchX)).toInt()
                    panelParams.height = (initH + (e.rawY - touchY)).toInt()
                    if (panelParams.width < dp(200)) panelParams.width = dp(200)
                    if (panelParams.height < dp(250)) panelParams.height = dp(250)
                    try { wm.updateViewLayout(v, panelParams) } catch (_: Throwable) {}
                    true
                }
                else -> false
            }
        }

        try { 
            wm.addView(v, panelParams); panelView = v 
            v.alpha = 0f
            v.animate().alpha(1f).setDuration(200).start()
        } catch (_: Throwable) {}
    }

    private fun hide() {
        if (panelView != null) {
            panelView?.animate()
                ?.alpha(0f)
                ?.setDuration(150)
                ?.withEndAction {
                    backdropView?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
                    backdropView = null
                    panelView?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
                    panelView = null
                }?.start()
        } else {
            backdropView?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
            backdropView = null
            panelView?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
            panelView = null
        }
    }
    
    override fun onDestroy() {
        hide()
        edgeView?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        edgeView = null
        super.onDestroy()
    }
}
