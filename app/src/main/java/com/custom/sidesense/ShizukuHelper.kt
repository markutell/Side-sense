package com.custom.sidesense

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object ShizukuHelper {
    fun isBinderAlive(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
    fun hasPermission(): Boolean = try {
        isBinderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun requestPermission(requestCode: Int = 4242) {
        if (isBinderAlive() && !hasPermission()) try { Shizuku.requestPermission(requestCode) } catch (_: Throwable) {}
    }

    fun execShell(cmd: String): String {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as ShizukuRemoteProcess
            try { process.outputStream.close() } catch (_: Throwable) {}
            var output = ""; var error = ""
            val t1 = Thread { output = process.inputStream.bufferedReader().use { it.readText() } }
            val t2 = Thread { error = process.errorStream.bufferedReader().use { it.readText() } }
            t1.start(); t2.start(); t1.join(); t2.join()
            process.waitFor(); process.destroy()
            // Kalau ada error, kita balikin teks error-nya
            if (error.isNotBlank()) "ERR: $error" else output.trim()
        } catch (t: Throwable) { "ERR_SYS: ${t.message}" }
    }

    fun enableFreeform() {
        execShell("settings put global enable_freeform_support 1")
        execShell("settings put global force_resizable_activities 1")
    }

    // Gunakan Package Name (pkg), jangan Activity Name
    fun launchFloating(pkg: String): String {
        enableFreeform()
        // Pakai monkey untuk nge-launch (lebih tahan banting daripada am start -n)
        // Lalu tembak pakai multiple-task + bounds
        val cmd = "am start --windowingMode 5 --activity-multiple-task --bounds 150,300,850,1200 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $pkg"
        return execShell(cmd)
    }
    
    fun screenOff() = execShell("input keyevent 26")
    fun takeScreenshot() = execShell("input keyevent 120")
}
