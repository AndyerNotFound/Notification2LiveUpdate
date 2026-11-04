package com.andyer.n2lu.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val system: Boolean,
)

object AppRepository {

    fun load(context: Context, extraPackages: Set<String>): List<AppEntry> {
        val pm = context.packageManager
        val seen = HashSet<String>()
        val entries = ArrayList<AppEntry>()

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = runCatching { pm.queryIntentActivities(launcherIntent, 0) }.getOrDefault(emptyList())
        for (resolve in activities) {
            val packageName = resolve.activityInfo?.packageName ?: continue
            if (packageName == context.packageName) continue
            if (!seen.add(packageName)) continue
            entries.add(describe(context, pm, packageName, resolve.activityInfo?.applicationInfo?.flags ?: 0))
        }

        for (packageName in extraPackages) {
            if (packageName == context.packageName) continue
            if (!seen.add(packageName)) continue
            val flags = runCatching { pm.getApplicationInfo(packageName, 0).flags }.getOrDefault(0)
            entries.add(describe(context, pm, packageName, flags))
        }

        return entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun describe(
        context: Context,
        pm: PackageManager,
        packageName: String,
        flags: Int,
    ): AppEntry {
        val label = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull() ?: packageName
        val icon = runCatching {
            pm.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
        val system = (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        return AppEntry(packageName, label, icon, system)
    }
}
