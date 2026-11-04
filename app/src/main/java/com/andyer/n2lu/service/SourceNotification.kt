package com.andyer.n2lu.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.andyer.n2lu.data.TriggerMode

data class SourceInfo(
    val key: String,
    val packageName: String,
    val id: Int,
    val tag: String?,
    val title: String,
    val text: String,
    val appLabel: String,
    val smallIcon: Icon?,
    val progress: Int?,
    val progressMax: Int,
    val indeterminate: Boolean,
    val chronometer: Boolean,
    val chronometerCountDown: Boolean,
    val whenMillis: Long,
    val showWhen: Boolean,
    val category: String?,
    val isOngoing: Boolean,
    val isPromoted: Boolean,
    val isGroupSummary: Boolean,
    val hasMediaSession: Boolean,
    val channelImportance: Int,
    val channelId: String?,
    val postTime: Long,
) {
    val hasProgress: Boolean
        get() = indeterminate || (progress != null && progressMax > 0)

    val hasContent: Boolean
        get() = title.isNotBlank() || text.isNotBlank()
}

object SourceParser {

    fun parse(
        context: Context,
        sbn: StatusBarNotification,
        channelImportance: Int,
        channelId: String?,
    ): SourceInfo? {
        val notification = sbn.notification ?: return null
        val extras: Bundle = notification.extras ?: Bundle.EMPTY

        val title = firstText(
            extras,
            Notification.EXTRA_TITLE,
            Notification.EXTRA_CONVERSATION_TITLE,
            Notification.EXTRA_TITLE_BIG,
        )
        val text = firstText(
            extras,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
        )
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val hasProgressExtra = extras.containsKey(Notification.EXTRA_PROGRESS) && (progressMax > 0 || indeterminate)
        val progress = if (hasProgressExtra) extras.getInt(Notification.EXTRA_PROGRESS, 0) else null

        val appLabel = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrNull() ?: sbn.packageName

        val flags = notification.flags
        val promoted = Build.VERSION.SDK_INT >= 36 &&
            (flags and Notification.FLAG_PROMOTED_ONGOING) != 0

        return SourceInfo(
            key = sbn.key,
            packageName = sbn.packageName,
            id = sbn.id,
            tag = sbn.tag,
            title = title,
            text = text,
            appLabel = appLabel,
            smallIcon = notification.smallIcon,
            progress = progress,
            progressMax = progressMax,
            indeterminate = indeterminate,
            chronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false),
            chronometerCountDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false),
            whenMillis = notification.`when`,
            showWhen = extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true),
            category = notification.category,
            isOngoing = sbn.isOngoing || (flags and Notification.FLAG_ONGOING_EVENT) != 0,
            isPromoted = promoted,
            isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
            channelImportance = channelImportance,
            channelId = channelId,
            postTime = sbn.postTime,
        )
    }

    private fun firstText(extras: Bundle, vararg keys: String): String {
        for (key in keys) {
            val value = extras.getCharSequence(key)
            if (!value.isNullOrBlank()) return value.toString().trim()
        }
        return ""
    }
}

object ConversionPolicy {

    private val skippedPackages = setOf("android", "com.android.systemui")

    private val skippedCategories = setOf(
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_MISSED_CALL,
        Notification.CATEGORY_ALARM,
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_SYSTEM,
    )

    fun shouldConvert(info: SourceInfo, mode: TriggerMode): Boolean {
        if (info.packageName in skippedPackages) return false
        if (!info.hasContent) return false
        if (info.isGroupSummary) return false
        if (info.hasMediaSession) return false
        if (info.category != null && info.category in skippedCategories) return false
        val isBanner = info.channelImportance >= NotificationManager.IMPORTANCE_HIGH
        return when (mode) {
            TriggerMode.BANNER -> isBanner
            TriggerMode.BANNER_OR_PROGRESS ->
                isBanner || (info.isOngoing && info.hasProgress)
            TriggerMode.ALL -> true
        }
    }
}
