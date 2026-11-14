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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class EdgeOverlayService : Service() {
    companion object {
        private const val CH = "sidesense_fg"
        private const val NID = 1001
    }
    private lateinit var wm: WindowManager
    private var edgeView: View? = null
    private var panelView: View? = null

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
            .setContentText("Tap strip tepi kanan")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NID, n)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun strip() {
        val v = LayoutInflater.from(this).inflate(R.layout.edge_strip, null)
        val p = WindowManager.LayoutParams(
            dp(10), dp(140),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        v.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) toggle(); true
        }
        try { wm.addView(v, p); edgeView = v } catch (_: Throwable) {}
    }
    private fun toggle() { if (panelView != null) hide() else show() }
    private fun show() {
        val v = LayoutInflater.from(this).inflate(R.layout.panel, null)
        val list = v.findViewById<LinearLayout>(R.id.panel_list)
        v.findViewById<TextView>(R.id.panel_title).text = "Apps"
        v.findViewById<View>(R.id.panel_close).setOnClickListener { hide() }
        val apps = AppRepo.getLaunchableApps(this)
        for (a in apps) {
            val it = LayoutInflater.from(this).inflate(R.layout.item_app, list, false)
            it.findViewById<ImageView>(R.id.item_icon).setImageDrawable(a.icon)
            it.findViewById<TextView>(R.id.item_label).text = a.label
            it.setOnClickListener {
                hide()
                packageManager.getLaunchIntentForPackage(a.pkg)?.let { li ->
                    li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(li) } catch (_: Throwable) {}
                }
            }
            it.setOnLongClickListener {
                hide()
                if (ShizukuHelper.hasPermission()) Thread { ShizukuHelper.enterSplitScreen(a.pkg) }.start()
                true
            }
            list.addView(it)
        }
        val p = WindowManager.LayoutParams(
            dp(300), WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        try { wm.addView(v, p); panelView = v } catch (_: Throwable) {}
    }
    private fun hide() {
        panelView?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        panelView = null
    }
    override fun onDestroy() {
        hide()
        edgeView?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        edgeView = null
        super.onDestroy()
    }
}
