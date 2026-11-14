package com.custom.sidesense

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> refresh() }

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        toast(if (grantResult == PackageManager.PERMISSION_GRANTED) "Shizuku granted" else "Shizuku denied")
        refresh()
    }
    private val binderOk = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }
    private val binderDead = Shizuku.OnBinderDeadListener { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btn_notif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else toast("Tidak perlu")
        }
        findViewById<Button>(R.id.btn_shizuku).setOnClickListener {
            when {
                !ShizukuHelper.isBinderAlive() -> toast("Shizuku service belum jalan")
                ShizukuHelper.hasPermission() -> toast("Shizuku sudah diizinkan")
                else -> ShizukuHelper.requestPermission()
            }
            refresh()
        }
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            ContextCompat.startForegroundService(this, Intent(this, EdgeOverlayService::class.java))
            toast("Service start")
        }
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopService(Intent(this, EdgeOverlayService::class.java))
            toast("Service stop")
        }

        try { Shizuku.addRequestPermissionResultListener(permListener) } catch (_: Throwable) {}
        try { Shizuku.addBinderReceivedListener(binderOk) } catch (_: Throwable) {}
        try { Shizuku.addBinderDeadListener(binderDead) } catch (_: Throwable) {}
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderReceivedListener(binderOk) } catch (_: Throwable) {}
        try { Shizuku.removeBinderDeadListener(binderDead) } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun refresh() {
        if (!this::status.isInitialized) return
        val ov = Settings.canDrawOverlays(this)
        val nf = if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true
        status.text = buildString {
            appendLine("Overlay   : ${if (ov) "OK" else "BELUM"}")
            appendLine("Notifikasi: ${if (nf) "OK" else "BELUM"}")
            appendLine("Binder    : ${if (ShizukuHelper.isBinderAlive()) "alive" else "mati"}")
            appendLine("Shizuku   : ${if (ShizukuHelper.hasPermission()) "OK" else "BELUM"}")
        }
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
