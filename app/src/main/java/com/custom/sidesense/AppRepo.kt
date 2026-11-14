package com.custom.sidesense

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

object AppRepo {
    data class AppInfo(val pkg: String, val label: String, val icon: Drawable, val component: String)
    
    fun getLaunchableApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0).mapNotNull {
            try {
                // Ambil nama package + activity (contoh: com.app/com.app.MainActivity)
                val comp = "${it.activityInfo.packageName}/${it.activityInfo.name}"
                AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm), comp)
            } catch (_: Throwable) { null }
        }.sortedBy { it.label.lowercase() }
    }
}
