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

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    fun execShell(cmd: String): String {
        if (isRootAvailable()) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val error = process.errorStream.bufferedReader().use { it.readText() }
                process.waitFor()
                return if (error.isNotBlank()) "ERR_ROOT: $error" else output.trim()
            } catch (e: Exception) {}
        }

        if (hasPermission()) {
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
                if (error.isNotBlank()) "ERR_SHIZUKU: $error" else output.trim()
            } catch (t: Throwable) {
                "ERR_SYS: ${t.message}"
            }
        }
        return "ERR: Root mati & Shizuku belum diizinkan"
    }

    fun enableFreeform() {
        execShell("settings put global enable_freeform_support 1")
        execShell("settings put global force_resizable_activities 1")
    }

    fun launchFloating(component: String, pkg: String): String {
        enableFreeform()
        
        // Bersihin task lama dulu pake root
        execShell("am force-stop $pkg")
        Thread.sleep(300)
        
        // Tembak pakai am start (tanpa --bounds biar gak error)
        val cmd = "am start --windowingMode 5 --activity-multiple-task -n $component"
        return execShell(cmd)
    }
    
    fun screenOff() = execShell("input keyevent 26")
    fun takeScreenshot() = execShell("input keyevent 120")
}
