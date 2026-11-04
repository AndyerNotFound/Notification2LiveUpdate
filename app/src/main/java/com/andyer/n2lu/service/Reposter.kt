package com.andyer.n2lu.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.andyer.n2lu.R
import com.andyer.n2lu.data.Prefs
import androidx.core.app.NotificationManagerCompat
import com.andyer.n2lu.util.ForegroundApps
import com.andyer.n2lu.util.IconLoader
import com.andyer.n2lu.util.SystemStatus

sealed interface PostResult {
    data class Posted(val promotable: Boolean, val chip: String) : PostResult
    data object Skipped : PostResult
    data class Failed(val reason: String) : PostResult
}

object Reposter {

    const val CHANNEL_ID = "n2lu_live_updates"
    private const val TAG_PREFIX = "n2lu:"
    private const val MIN_INTERVAL_MS = 200L
    private const val MAX_TRACKED = 128
    private const val STALE_GRACE_MS = 5_000L
    private const val ACCENT = 0xFF14B8A6.toInt()

    private val signatures = HashMap<String, String>()
    private val lastPostAt = HashMap<String, Long>()
    private val records = HashMap<String, Record>()
    private val clearedSignatures = HashMap<String, String>()
    private val lock = Any()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_live_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_live_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun post(context: Context, info: SourceInfo, prefs: Prefs): PostResult {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return PostResult.Failed("通知权限未开启")
        }
        val chip = ChipText.build(info, prefs.chipStyle)
        val signature = buildString {
            append(info.title).append('|')
            append(info.text).append('|')
            append(info.progress ?: -1).append('|')
            append(info.progressMax).append('|')
            append(info.indeterminate).append('|')
            append(chip).append('|')
            append(prefs.showSourceApp).append('|')
            append(prefs.keepAfterSourceGone)
        }
        synchronized(lock) {
            if (clearedSignatures[info.key] == signature) return PostResult.Skipped
            clearedSignatures.remove(info.key)
            val last = lastPostAt[info.key] ?: 0L
            if (signatures[info.key] == signature && SystemClock.elapsedRealtime() - last < MIN_INTERVAL_MS) {
                return PostResult.Skipped
            }
            if (signatures.size > MAX_TRACKED) {
                signatures.clear()
                lastPostAt.clear()
            }
        }
        return try {
            val built = build(context, info, prefs, chip)
            val manager = context.getSystemService(NotificationManager::class.java)
                ?: return PostResult.Failed("无 NotificationManager")
            val hidden = shouldHideForForeground(context, info, prefs)
            if (!hidden) manager.notify(tagOf(info.key), idOf(info.key), built.notification)
            synchronized(lock) {
                signatures[info.key] = signature
                lastPostAt[info.key] = SystemClock.elapsedRealtime()
                if (records.size > MAX_TRACKED) records.clear()
                records[info.key] = Record(info.packageName, built.notification, hidden, SystemClock.elapsedRealtime())
            }
            PostResult.Posted(built.promotable, chip)
        } catch (t: Throwable) {
            PostResult.Failed(t.javaClass.simpleName + ": " + (t.message ?: "unknown"))
        }
    }

    fun removeSource(context: Context, key: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.cancel(tagOf(key), idOf(key)) }
        synchronized(lock) {
            signatures.remove(key)
            lastPostAt.remove(key)
            records.remove(key)
            clearedSignatures.remove(key)
        }
    }

    fun removeCompleted(context: Context, key: String) {
        val signature = synchronized(lock) { signatures[key] }
        synchronized(lock) {
            if (signature != null) clearedSignatures[key] = signature
            signatures.remove(key)
            lastPostAt.remove(key)
            records.remove(key)
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.cancel(tagOf(key), idOf(key)) }
    }

    fun hasRecord(key: String): Boolean = synchronized(lock) { records.containsKey(key) }

    fun hasRecords(): Boolean = synchronized(lock) { records.isNotEmpty() }

    fun applyForeground(context: Context, foreground: String?) {
        val needed = synchronized(lock) { foreground != null || records.values.any { it.hidden } }
        if (!needed) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val activeTags = runCatching {
            manager.activeNotifications.mapNotNull { it.tag }.toSet()
        }.getOrNull()
        val toHide = ArrayList<String>()
        val toShow = ArrayList<Pair<String, Notification>>()
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val changes = ArrayList<Pair<String, Record>>()
            val iterator = records.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val key = entry.key
                val record = entry.value
                if (record.sourcePackage == context.packageName) continue
                val stale = !record.hidden && activeTags != null &&
                    now - record.postedAt > STALE_GRACE_MS && tagOf(key) !in activeTags
                if (stale) {
                    iterator.remove()
                    signatures.remove(key)
                    lastPostAt.remove(key)
                    continue
                }
                val hide = foreground != null && record.sourcePackage == foreground
                if (hide == record.hidden) continue
                changes += key to record.copy(hidden = hide)
                if (hide) toHide += key else toShow += key to record.notification
            }
            for ((key, record) in changes) {
                records[key] = record
            }
        }
        for (key in toHide) {
            runCatching { manager.cancel(tagOf(key), idOf(key)) }
        }
        for ((key, notification) in toShow) {
            runCatching { manager.notify(tagOf(key), idOf(key), notification) }
        }
    }

    private fun shouldHideForForeground(context: Context, info: SourceInfo, prefs: Prefs): Boolean {
        if (!prefs.hideWhenSourceForeground) return false
        if (info.packageName == context.packageName) return false
        return ForegroundApps.currentPackage(context) == info.packageName
    }

    fun isPromoted(context: Context, key: String): Boolean? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        val posted = runCatching {
            manager.activeNotifications.firstOrNull { it.tag == tagOf(key) && it.id == idOf(key) }
        }.getOrNull() ?: return null
        return (posted.notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.activeNotifications
                .filter { it.tag?.startsWith(TAG_PREFIX) == true }
                .forEach { manager.cancel(it.tag, it.id) }
        }
        synchronized(lock) {
            signatures.clear()
            lastPostAt.clear()
            records.clear()
            clearedSignatures.clear()
        }
    }

    private class Built(val notification: Notification, val promotable: Boolean)

    private data class Record(
        val sourcePackage: String,
        val notification: Notification,
        val hidden: Boolean,
        val postedAt: Long,
    )

    private fun build(context: Context, info: SourceInfo, prefs: Prefs, chip: String): Built {
        val title = info.title.ifBlank { info.appLabel }
        val text = info.text.ifBlank { info.appLabel }
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(IconLoader.smallIcon(context, info.smallIcon, R.drawable.ic_stat_live))
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(null)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(ACCENT)

        when (PromotionSupport.mode(context)) {
            PromotionSupport.Mode.OPT_IN -> builder.addExtras(
                Bundle().apply { putBoolean(PromotionSupport.EXTRA_REQUEST_PROMOTED_ONGOING, true) }
            )
            PromotionSupport.Mode.LEGACY_COLORIZED -> builder.setColorized(true)
            PromotionSupport.Mode.UNSUPPORTED -> Unit
        }

        if (prefs.showSourceApp) builder.setSubText(info.appLabel)
        if (info.chronometer) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(info.chronometerCountDown)
                .setWhen(if (info.whenMillis > 0L) info.whenMillis else System.currentTimeMillis())
                .setShowWhen(true)
        } else {
            builder.setShowWhen(info.showWhen)
                .setWhen(if (info.whenMillis > 0L) info.whenMillis else System.currentTimeMillis())
        }

        IconLoader.appIcon(context, info.packageName)?.let { builder.setLargeIcon(it) }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(info.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(
                context,
                info.packageName.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentIntent(pending)
        }

        val ignoreIntent = Intent(context, ActionReceiver::class.java)
            .setAction(ActionReceiver.ACTION_IGNORE)
            .putExtra(ActionReceiver.EXTRA_KEY, info.key)
        val ignorePending = PendingIntent.getBroadcast(
            context,
            info.key.hashCode(),
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(
            Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_stat_live),
                "忽略",
                ignorePending,
            ).build()
        )

        if (Build.VERSION.SDK_INT >= 36) {
            applyRichContent(builder, info, chip)
        } else if (info.hasProgress) {
            builder.setProgress(
                info.progressMax.coerceAtLeast(1),
                (info.progress ?: 0).coerceIn(0, info.progressMax.coerceAtLeast(1)),
                info.indeterminate,
            )
        }

        val notification = builder.build()
        val promotable = Build.VERSION.SDK_INT >= 36 &&
            SystemStatus.canPromote(context) &&
            runCatching { notification.hasPromotableCharacteristics() }.getOrDefault(false)
        return Built(notification, promotable)
    }

    @RequiresApi(36)
    private fun applyRichContent(builder: Notification.Builder, info: SourceInfo, chip: String) {
        builder.setShortCriticalText(chip)
        if (info.hasProgress) {
            val max = info.progressMax.coerceAtLeast(1)
            val style = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressSegments(
                    listOf(Notification.ProgressStyle.Segment(max).setColor(ACCENT))
                )
            if (info.indeterminate) {
                style.setProgressIndeterminate(true)
            } else {
                style.setProgress((info.progress ?: 0).coerceIn(0, max))
            }
            builder.setStyle(style)
        } else {
            builder.setStyle(Notification.BigTextStyle().bigText(info.text.ifBlank { info.title }))
        }
    }

    private fun tagOf(key: String): String = TAG_PREFIX + key

    private fun idOf(key: String): Int {
        val hash = key.hashCode() and 0x7FFFFFFF
        return hash % 90_000 + 10_000
    }
}
