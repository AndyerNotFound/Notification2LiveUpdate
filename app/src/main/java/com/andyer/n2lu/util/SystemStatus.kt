package com.andyer.n2lu.util

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.andyer.n2lu.service.NluListenerService

data class StatusSnapshot(
    val notificationAccess: Boolean,
    val notificationsEnabled: Boolean,
    val promotionSupported: Boolean,
    val promotionAllowed: Boolean,
) {
    val sdkInt: Int get() = Build.VERSION.SDK_INT
}

object SystemStatus {

    private const val ENABLED_LISTENERS = "enabled_notification_listeners"

    fun snapshot(context: Context): StatusSnapshot {
        val application = context.applicationContext
        return StatusSnapshot(
            notificationAccess = hasNotificationAccess(application),
            notificationsEnabled = NotificationManagerCompat.from(application).areNotificationsEnabled(),
            promotionSupported = Build.VERSION.SDK_INT >= 36,
            promotionAllowed = canPromote(application),
        )
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val flat = runCatching {
            Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS)
        }.getOrNull() ?: return false
        val expected = ComponentName(context, NluListenerService::class.java)
        return flat.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == expected }
    }

    fun canPromote(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        }.getOrDefault(false)
    }

    fun openNotificationAccess(context: Context) {
        val component = ComponentName(context, NluListenerService::class.java)
        val detail = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            null
        }
        val opened = detail != null && runCatching {
            context.startActivity(detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun openPromotionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 36) {
            openAppNotificationSettings(context)
            return
        }
        val opened = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                    .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!opened) openAppNotificationSettings(context)
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
