package com.custom.sidesense

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object ShizukuHelper {
    // Cek apakah root (su) tersedia
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

    // Eksekusi shell: coba su dulu, kalau gagal baru Shizuku
    fun execShell(cmd: String): String {
        // 1. Coba lewat root (su)
        if (isRootAvailable()) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val error = process.errorStream.bufferedReader().use { it.readText() }
                process.waitFor()
                return if (error.isNotBlank()) "ERR_ROOT: $error" else output.trim()
            } catch (e: Exception) {
                // Gagal, lanjut ke Shizuku
            }
        }

        // 2. Fallback ke Shizuku (jika root tidak ada)
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

    fun enableFreeform() {
        execShell("settings put global enable_freeform_support 1")
        execShell("settings put global force_resizable_activities 1")
    }

    fun launchFloating(component: String, pkg: String): String {
        enableFreeform()
        // Paksa pakai cmd activity + bounds (coba dengan root)
        val cmd = "cmd activity start-activity --windowingMode 5 --activity-new-task --bounds 150,300,850,1200 -n $component"
        return execShell(cmd)
    }

    fun screenOff() = execShell("input keyevent 26")
    fun takeScreenshot() = execShell("input keyevent 120")
}
