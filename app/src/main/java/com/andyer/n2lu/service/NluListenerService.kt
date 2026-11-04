package com.andyer.n2lu.service

import android.app.NotificationManager
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

class NluListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: Prefs by lazy { Prefs.get(this) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        EventLog.add(
            this,
            LogEvent(
                time = System.currentTimeMillis(),
                packageName = packageName,
                appLabel = "Notification 2 Live Update",
                title = "通知监听已连接",
                chip = "",
                state = "服务",
            ),
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        EventLog.add(
            this,
            LogEvent(
                time = System.currentTimeMillis(),
                packageName = packageName,
                appLabel = "Notification 2 Live Update",
                title = "通知监听已断开",
                chip = "",
                state = "服务",
            ),
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap) {
        val settings = prefs
        if (!settings.enabled) return
        val sourcePackage = sbn.packageName ?: return
        if (sourcePackage == packageName) return
        if (!settings.isPackageEnabled(sourcePackage)) return

        val ranking = runCatching { rankingMap.getRanking(sbn.key) }.getOrNull()
        val importance = ranking?.channel?.importance ?: NotificationManager.IMPORTANCE_DEFAULT
        val info = SourceParser.parse(this, sbn, importance, ranking?.channel?.id) ?: return

        if (settings.isSourceIgnored(info.key)) return
        if (settings.skipAlreadyPromoted && info.isPromoted) return
        if (!ConversionPolicy.shouldConvert(info, settings.triggerMode)) return

        scope.launch {
            when (val result = Reposter.post(this@NluListenerService, info, settings)) {
                is PostResult.Posted -> EventLog.add(
                    this@NluListenerService,
                    LogEvent(
                        time = System.currentTimeMillis(),
                        packageName = info.packageName,
                        appLabel = info.appLabel,
                        title = info.title.ifBlank { info.text },
                        chip = result.chip,
                        state = if (result.promotable) "已转换 · 可提升" else "已转换",
                    ),
                )
                is PostResult.Failed -> EventLog.add(
                    this@NluListenerService,
                    LogEvent(
                        time = System.currentTimeMillis(),
                        packageName = info.packageName,
                        appLabel = info.appLabel,
                        title = info.title.ifBlank { info.text },
                        chip = "",
                        state = "失败 · " + result.reason,
                    ),
                )
                PostResult.Skipped -> Unit
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: NotificationListenerService.RankingMap) {
        if (sbn.packageName == packageName) return
        if (!prefs.keepAfterSourceGone) {
            Reposter.removeSource(this, sbn.key)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
