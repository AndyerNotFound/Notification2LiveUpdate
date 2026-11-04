package com.andyer.n2lu.service

import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import com.andyer.n2lu.R
import com.andyer.n2lu.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object DemoPoster {

    private const val DEMO_KEY = "n2lu|demo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context): PostResult {
        val appContext = context.applicationContext
        val settings = Prefs.get(appContext)
        val first = Reposter.post(appContext, demoInfo(appContext, 0), settings)
        scope.launch {
            for (step in 1..20) {
                delay(600)
                Reposter.post(appContext, demoInfo(appContext, step), settings)
            }
            delay(4000)
            Reposter.removeSource(appContext, DEMO_KEY)
        }
        return first
    }

    fun stop(context: Context) {
        Reposter.removeSource(context.applicationContext, DEMO_KEY)
    }

    private fun demoInfo(context: Context, step: Int): SourceInfo {
        val progress = (step * 100 / 20).coerceIn(0, 100)
        val now = System.currentTimeMillis()
        return SourceInfo(
            key = DEMO_KEY,
            packageName = context.packageName,
            id = 990_001,
            tag = "demo",
            title = "正在转换横幅通知",
            text = "演示进度 $progress% ，完成后会自动消失",
            appLabel = context.getString(R.string.app_name),
            smallIcon = Icon.createWithResource(context, R.drawable.ic_stat_demo),
            progress = progress,
            progressMax = 100,
            indeterminate = false,
            chronometer = false,
            chronometerCountDown = false,
            whenMillis = now,
            showWhen = false,
            category = android.app.Notification.CATEGORY_PROGRESS,
            isOngoing = true,
            isPromoted = false,
            isGroupSummary = false,
            hasMediaSession = false,
            channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            channelId = null,
            postTime = now,
        )
    }
}
