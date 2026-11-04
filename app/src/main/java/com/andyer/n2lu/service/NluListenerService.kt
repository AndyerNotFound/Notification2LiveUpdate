package com.andyer.n2lu.service

import android.app.NotificationManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.andyer.n2lu.data.EventLog
import com.andyer.n2lu.data.LogEvent
import com.andyer.n2lu.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NluListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val suppressedRemovals = ConcurrentHashMap<String, Long>()

    private val prefs: Prefs by lazy { Prefs.get(this) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        log(title = "通知监听已连接", state = "服务")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
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
        val importance = channel?.importance ?: NotificationManager.IMPORTANCE_DEFAULT
        val info = SourceParser.parse(this, sbn, importance, channel?.id) ?: return

        if (settings.isSourceIgnored(info.key)) return
        if (settings.skipAlreadyPromoted && info.isPromoted) return
        if (!ConversionPolicy.shouldConvert(info, settings.triggerMode)) return

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
        if (sbn.packageName == packageName) return
        val suppressedAt = suppressedRemovals.remove(sbn.key)
        val fromReplacement = suppressedAt != null &&
            SystemClock.elapsedRealtime() - suppressedAt < REPLACE_GRACE_MS
        if (fromReplacement) return
        if (!prefs.keepAfterSourceGone) {
            Reposter.removeSource(this, sbn.key)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
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
    }
}
