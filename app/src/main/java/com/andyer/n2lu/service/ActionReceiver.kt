package com.andyer.n2lu.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andyer.n2lu.data.EventLog
import com.andyer.n2lu.data.LogEvent
import com.andyer.n2lu.data.Prefs

class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_IGNORE) return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val prefs = Prefs.get(context)
        prefs.ignoreSource(key)
        Reposter.removeSource(context, key)
        EventLog.add(
            context,
            LogEvent(
                time = System.currentTimeMillis(),
                packageName = "",
                appLabel = "已忽略",
                title = "该通知 30 分钟内不再转换",
                chip = "",
                state = "忽略",
            ),
        )
    }

    companion object {
        const val ACTION_IGNORE = "com.andyer.n2lu.action.IGNORE"
        const val EXTRA_KEY = "source_key"
    }
}
