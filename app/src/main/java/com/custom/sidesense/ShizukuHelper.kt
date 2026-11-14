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
            if (error.isNotBlank()) "ERR: $error" else output.trim()
        } catch (t: Throwable) { "ERR_SYS: ${t.message}" }
    }

    fun enableFreeform() {
        execShell("settings put global enable_freeform_support 1")
        execShell("settings put global force_resizable_activities 1")
    }

    fun launchFloating(component: String, pkg: String): String {
        enableFreeform()
        // Paksa pakai cmd activity + windowingMode freeform
        // (Kita coba tanpa bounds dulu biar sistem yang nentuin ukuran default)
        val cmd = "cmd activity start-activity --windowingMode 5 --activity-new-task -n $component"
        return execShell(cmd)
    }
    
    fun screenOff() = execShell("input keyevent 26")
    fun takeScreenshot() = execShell("input keyevent 120")
}
