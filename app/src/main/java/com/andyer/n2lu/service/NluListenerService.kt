package com.andyer.n2lu.service

import android.app.NotificationManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.andyer.n2lu.data.EventLog
import com.andyer.n2lu.data.LogEvent
import com.andyer.n2lu.data.Prefs
import com.andyer.n2lu.util.ForegroundApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NluListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val suppressedRemovals = ConcurrentHashMap<String, Long>()

    private val autoClearJobs = ConcurrentHashMap<String, Job>()

    private val viewedSince = ConcurrentHashMap<String, Long>()

    @Volatile
    private var foregroundJob: Job? = null

    private val prefs: Prefs by lazy { Prefs.get(this) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        log(title = "通知监听已连接", state = "服务")
        ensureForegroundWatcher()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        foregroundJob?.cancel()
        foregroundJob = null
        viewedSince.clear()
        suppressedRemovals.clear()
        log(title = "通知监听已断开", state = "服务")
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
    ) {
        val settings = prefs
        if (!settings.enabled) return
        val sourcePackage = sbn.packageName ?: return
        if (sourcePackage == packageName) return
        if (!settings.isPackageEnabled(sourcePackage)) return

        val ranking = NotificationListenerService.Ranking()
        val hasRanking = runCatching { rankingMap.getRanking(sbn.key, ranking) }.getOrDefault(false)
        val channel = if (hasRanking) ranking.channel else null
        val importance = if (hasRanking) ranking.importance else NotificationManager.IMPORTANCE_DEFAULT
        val info = SourceParser.parse(this, sbn, importance, channel?.id) ?: return

        if (ConversionPolicy.isDisqualified(info)) {
            if (!info.hasContent || info.isGroupSummary) dropStaleCard(info)
            return
        }
        if (settings.isSourceIgnored(info.key)) return
        if (settings.skipAlreadyPromoted && info.isPromoted) return
        if (!ConversionPolicy.shouldConvert(info, settings.triggerMode)) return

        ensureForegroundWatcher()
        val completed = settings.autoClearOnComplete &&
            info.key != DemoPoster.DEMO_KEY &&
            CompletionPolicy.isComplete(
                info.progress,
                info.progressMax,
                info.indeterminate,
                info.chronometerCountDown,
                info.title,
                info.text,
            )

        scope.launch {
            when (val result = Reposter.post(this@NluListenerService, info, settings)) {
                is PostResult.Posted -> {
                    if (settings.replaceSource) {
                        suppressedRemovals[info.key] = SystemClock.elapsedRealtime()
                        runCatching { cancelNotification(info.key) }
                    }
                    log(
                        packageName = info.packageName,
                        appLabel = info.appLabel,
                        title = info.title.ifBlank { info.text },
                        chip = result.chip,
                        state = if (result.promotable) "已转换 · 可提升" else "已转换",
                    )
                    if (completed) scheduleAutoClear(info) else cancelAutoClear(info.key)
                }
                is PostResult.Failed -> log(
                    packageName = info.packageName,
                    appLabel = info.appLabel,
                    title = info.title.ifBlank { info.text },
                    state = "失败 · " + result.reason,
                )
                else -> Unit
            }
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
    ) {
        handleRemoved(sbn, reason = null)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        reason: Int,
    ) {
        handleRemoved(sbn, reason)
    }

    private fun handleRemoved(sbn: StatusBarNotification, reason: Int?) {
        if (sbn.packageName == packageName) return
        val suppressedAt = suppressedRemovals.remove(sbn.key)
        val fromReplacement = suppressedAt != null &&
            SystemClock.elapsedRealtime() - suppressedAt < REPLACE_GRACE_MS
        if (fromReplacement) return
        val userHandled = reason == NotificationListenerService.REASON_CLICK ||
            reason == NotificationListenerService.REASON_CANCEL ||
            reason == NotificationListenerService.REASON_CANCEL_ALL ||
            reason == NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED
        if (!userHandled && prefs.keepAfterSourceGone) return
        val info = Reposter.sourceInfo(sbn.key)
        viewedSince.remove(sbn.key)
        cancelAutoClear(sbn.key)
        Reposter.removeSource(this, sbn.key)
        if (info == null) return
        val state = when (reason) {
            NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED -> "所属分组被清除 · 自动清除"
            else -> if (userHandled) "用户已处理 · 自动清除" else "源通知消失 · 自动清除"
        }
        log(
            packageName = info.packageName,
            appLabel = info.appLabel,
            title = info.title.ifBlank { info.text },
            state = state,
        )
    }

    override fun onDestroy() {
        autoClearJobs.clear()
        viewedSince.clear()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureForegroundWatcher() {
        if (foregroundJob?.isActive == true) return
        foregroundJob = scope.launch {
            while (isActive) {
                val active = Reposter.hasRecords()
                if (active) {
                    val settings = prefs
                    val tracking = settings.hideWhenSourceForeground || settings.dismissAfterViewed
                    val foreground = if (tracking) {
                        ForegroundApps.currentPackage(this@NluListenerService)
                    } else {
                        null
                    }
                    Reposter.applyForeground(
                        this@NluListenerService,
                        if (settings.hideWhenSourceForeground) foreground else null,
                    )
                    trackViewed(foreground)
                } else {
                    viewedSince.clear()
                }
                delay(if (active) ACTIVE_POLL_MS else IDLE_POLL_MS)
            }
        }
    }

    private fun trackViewed(foreground: String?) {
        val settings = prefs
        if (!settings.dismissAfterViewed) return
        if (foreground == null) {
            viewedSince.clear()
            return
        }
        val candidates = Reposter.dismissableInForeground(foreground, packageName)
        val dismissed = HashSet<String>()
        val now = SystemClock.elapsedRealtime()
        val iterator = viewedSince.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val candidate = candidates.firstOrNull { it.key == entry.key }
            if (candidate == null) {
                iterator.remove()
                continue
            }
            if (now - entry.value < VIEW_DWELL_MS) continue
            iterator.remove()
            dismissed += candidate.key
            cancelAutoClear(candidate.key)
            if (Reposter.removeCompleted(this, candidate.key)) {
                log(
                    packageName = candidate.packageName,
                    appLabel = candidate.appLabel,
                    title = candidate.title.ifBlank { candidate.text },
                    state = "已查看 · 自动关闭",
                )
            }
        }
        for (candidate in candidates) {
            if (candidate.key !in dismissed) {
                viewedSince.putIfAbsent(candidate.key, now)
            }
        }
    }

    private fun dropStaleCard(info: SourceInfo) {
        val previous = Reposter.sourceInfo(info.key) ?: return
        cancelAutoClear(info.key)
        viewedSince.remove(info.key)
        Reposter.removeSource(this, info.key)
        log(
            packageName = previous.packageName,
            appLabel = previous.appLabel,
            title = previous.title.ifBlank { previous.text },
            state = "源通知已失效 · 已清除",
        )
    }

    private fun scheduleAutoClear(info: SourceInfo) {
        if (autoClearJobs[info.key]?.isActive == true) return
        autoClearJobs[info.key] = scope.launch {
            delay(AUTO_CLEAR_DELAY_MS)
            val removed = Reposter.removeCompleted(this@NluListenerService, info.key)
            autoClearJobs.remove(info.key)
            if (!removed) return@launch
            log(
                packageName = info.packageName,
                appLabel = info.appLabel,
                title = info.title.ifBlank { info.text },
                chip = "完成",
                state = "已完成 · 自动清除",
            )
        }
    }

    private fun cancelAutoClear(key: String) {
        autoClearJobs.remove(key)?.cancel()
    }

    private fun log(
        packageName: String = this.packageName,
        appLabel: String = "Notification 2 Live Update",
        title: String,
        chip: String = "",
        state: String,
    ) {
        EventLog.add(
            this,
            LogEvent(
                time = System.currentTimeMillis(),
                packageName = packageName,
                appLabel = appLabel,
                title = title,
                chip = chip,
                state = state,
            ),
        )
    }

    companion object {
        private const val REPLACE_GRACE_MS = 5_000L
        private const val AUTO_CLEAR_DELAY_MS = 8_000L
        private const val VIEW_DWELL_MS = 6_000L
        private const val ACTIVE_POLL_MS = 1_500L
        private const val IDLE_POLL_MS = 2_000L
    }
}
