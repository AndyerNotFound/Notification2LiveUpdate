package com.andyer.n2lu.util

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process

object ForegroundApps {

    private const val WINDOW_MS = 30_000L

    @Volatile
    private var cached: String? = null

    @Suppress("DEPRECATION")
    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    fun currentPackage(context: Context): String? {
        if (!hasAccess(context)) return null
        val power = context.getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) return null
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard != null && keyguard.isKeyguardLocked) return null
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return cached
        val now = System.currentTimeMillis()
        val events = runCatching { manager.queryEvents(now - WINDOW_MS, now) }.getOrNull() ?: return cached
        val event = UsageEvents.Event()
        var latest: String? = null
        var seen = false
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    latest = event.packageName
                    seen = true
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN -> {
                    latest = null
                    seen = true
                }
            }
        }
        if (seen) cached = latest
        return cached
    }
}
