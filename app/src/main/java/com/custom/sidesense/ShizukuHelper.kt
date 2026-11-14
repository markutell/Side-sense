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
        if (isBinderAlive() && !hasPermission()) {
            try { Shizuku.requestPermission(requestCode) } catch (_: Throwable) {}
        }
    }

    /**
     * Jalankan shell command via Shizuku pakai reflection,
     * karena Shizuku.newProcess() private di API 13.x.
     */
    fun execShell(cmd: String): String {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true

            val process = method.invoke(
                null,
                arrayOf("sh", "-c", cmd),
                null,
                null
            ) as ShizukuRemoteProcess

            // Tutup stdin biar nggak nge-hang
            try { process.outputStream.close() } catch (_: Throwable) {}

            // Baca stdout & stderr di thread terpisah biar nggak deadlock
            var output = ""
            var error = ""
            val t1 = Thread {
                output = process.inputStream.bufferedReader().use { it.readText() }
            }
            val t2 = Thread {
                error = process.errorStream.bufferedReader().use { it.readText() }
            }
            t1.start(); t2.start()
            t1.join(); t2.join()

            process.waitFor()
            process.destroy()

            if (error.isNotBlank()) "ERR: $error" else output.trim()
        } catch (t: Throwable) {
            "ERR: ${t.message}"
        }
    }

    fun enterSplitScreen(pkg: String): String =
        execShell("am start --activity-multiple-task --activity-launch-task $pkg")
}
