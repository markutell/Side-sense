package com.custom.sidesense

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {
    fun isBinderAlive() = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
    fun hasPermission() = try {
        isBinderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }
    fun requestPermission(c: Int = 4242) {
        if (isBinderAlive() && !hasPermission()) try { Shizuku.requestPermission(c) } catch (_: Throwable) {}
    }
    fun execShell(cmd: String): String = try {
        val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        val o = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor(); o
    } catch (t: Throwable) { "ERR: ${t.message}" }
    fun enterSplitScreen(pkg: String) =
        execShell("am start --activity-multiple-task --activity-launch-task $pkg")
}
