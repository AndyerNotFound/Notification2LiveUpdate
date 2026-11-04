package com.andyer.n2lu.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LogEvent(
    val time: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val chip: String,
    val state: String,
)

object EventLog {

    private const val MAX_EVENTS = 60
    private const val PREFS = "n2lu_log"
    private const val KEY = "events"

    private val _events = MutableStateFlow<List<LogEvent>>(emptyList())
    val events: StateFlow<List<LogEvent>> = _events

    private val lock = Any()
    private var loaded = false

    fun load(context: Context) {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            val raw = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return
            val parsed = runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            LogEvent(
                                time = item.optLong("time"),
                                packageName = item.optString("pkg"),
                                appLabel = item.optString("app"),
                                title = item.optString("title"),
                                chip = item.optString("chip"),
                                state = item.optString("state"),
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
            _events.value = parsed
        }
    }

    fun add(context: Context, event: LogEvent) {
        synchronized(lock) {
            val next = (listOf(event) + _events.value).take(MAX_EVENTS)
            _events.value = next
            persist(context, next)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            _events.value = emptyList()
            persist(context, emptyList())
        }
    }

    private fun persist(context: Context, events: List<LogEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject().apply {
                    put("time", event.time)
                    put("pkg", event.packageName)
                    put("app", event.appLabel)
                    put("title", event.title)
                    put("chip", event.chip)
                    put("state", event.state)
                }
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}
