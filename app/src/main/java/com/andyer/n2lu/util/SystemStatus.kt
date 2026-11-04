package com.andyer.n2lu.util

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.andyer.n2lu.service.NluListenerService
import com.andyer.n2lu.service.PromotionSupport

data class StatusSnapshot(
    val notificationAccess: Boolean,
    val notificationsEnabled: Boolean,
    val usageAccess: Boolean,
    val promotionSupported: Boolean,
    val promotionAllowed: Boolean,
    val promotionOptIn: Boolean,
    val promotionPermissionDeclared: Boolean,
    val promotionPermissionGranted: Boolean,
    val promotionSettingsAvailable: Boolean,
    val sdkIntFull: Int,
) {
    val sdkInt: Int get() = Build.VERSION.SDK_INT

    val sdkLabel: String
        get() = if (sdkIntFull >= 10_000) {
            "API ${sdkIntFull / 10_000}.${(sdkIntFull % 10_000) / 100}"
        } else {
            "API $sdkInt"
        }
}

enum class PromotionPage {
    PROMOTION,
    APP_NOTIFICATIONS,
    NONE,
}

object SystemStatus {

    private const val ENABLED_LISTENERS = "enabled_notification_listeners"
    const val PROMOTION_PERMISSION = "android.permission.POST_PROMOTED_NOTIFICATIONS"

    fun snapshot(context: Context): StatusSnapshot {
        val application = context.applicationContext
        return StatusSnapshot(
            notificationAccess = hasNotificationAccess(application),
            notificationsEnabled = NotificationManagerCompat.from(application).areNotificationsEnabled(),
            usageAccess = ForegroundApps.hasAccess(application),
            promotionSupported = Build.VERSION.SDK_INT >= 36,
            promotionAllowed = canPromote(application),
            promotionOptIn = PromotionSupport.mode(application) == PromotionSupport.Mode.OPT_IN,
            promotionPermissionDeclared = isPromotionPermissionDeclared(application),
            promotionPermissionGranted = isPromotionPermissionGranted(application),
            promotionSettingsAvailable = isPromotionSettingsAvailable(application),
            sdkIntFull = sdkIntFull(),
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

    fun isPromotionPermissionDeclared(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.contains(PROMOTION_PERMISSION) == true
    }.getOrDefault(false)

    fun isPromotionPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            context.checkSelfPermission(PROMOTION_PERMISSION) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun isPromotionSettingsAvailable(context: Context): Boolean = runCatching {
        context.packageManager.resolveActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS),
            0,
        ) != null
    }.getOrDefault(false)

    fun sdkIntFull(): Int = runCatching {
        Build.VERSION::class.java.getField("SDK_INT_FULL").getInt(null)
    }.getOrDefault(Build.VERSION.SDK_INT * 100)

    fun openNotificationAccess(context: Context): Boolean {
        val component = ComponentName(context, NluListenerService::class.java)
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= 30) {
                add(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            component.flattenToString(),
                        )
                )
            }
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            add(appNotificationIntent(context))
        }
        return startFirst(context, candidates)
    }

    fun openPromotionSettings(context: Context): PromotionPage {
        if (Build.VERSION.SDK_INT >= 36) {
            val promotion = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            if (startFirst(context, listOf(promotion))) return PromotionPage.PROMOTION
        }
        return if (startFirst(context, listOf(appNotificationIntent(context)))) {
            PromotionPage.APP_NOTIFICATIONS
        } else {
            PromotionPage.NONE
        }
    }

    fun openAppNotificationSettings(context: Context): Boolean =
        startFirst(context, listOf(appNotificationIntent(context)))

    fun openUsageAccessSettings(context: Context): Boolean = startFirst(
        context,
        listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)),
    )

    fun openAppDetails(context: Context): Boolean = startFirst(
        context,
        listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:" + context.packageName)),
        ),
    )

    private fun appNotificationIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    private fun startFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolvable = runCatching {
                context.packageManager.resolveActivity(intent, 0) != null
            }.getOrDefault(false)
            if (!resolvable) continue
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }
}
