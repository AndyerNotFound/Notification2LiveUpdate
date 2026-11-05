package com.andyer.n2lu.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class TriggerMode {
    BANNER,
    BANNER_OR_PROGRESS,
    ALL,
}

enum class ChipStyle {
    AUTO,
    PERCENT,
    APP_NAME,
    TITLE,
}

/**
 * Values live in Compose snapshot state and are mirrored to SharedPreferences, so a write made in
 * any screen recomposes every reader instead of only bumping a revision counter.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences = context.applicationContext
        .getSharedPreferences("n2lu_settings", Context.MODE_PRIVATE)

    var enabled: Boolean by cachedPref(
        read = { sp.getBoolean(KEY_ENABLED, false) },
        write = { putBoolean(KEY_ENABLED, it) },
    )

    var triggerMode: TriggerMode by cachedPref(
        read = { enumOf(sp.getString(KEY_TRIGGER, null), TriggerMode.BANNER_OR_PROGRESS) },
        write = { putString(KEY_TRIGGER, it.name) },
    )

    var allApps: Boolean by cachedPref(
        read = { sp.getBoolean(KEY_ALL_APPS, false) },
        write = { putBoolean(KEY_ALL_APPS, it) },
    )

    var allowedPackages: Set<String> by cachedPref(
        read = { sp.getStringSet(KEY_ALLOWED, null)?.toSet() ?: emptySet() },
        write = { putStringSet(KEY_ALLOWED, LinkedHashSet(it)) },
    )

    var showSourceApp: Boolean by cachedPref(
        read = { sp.getBoolean(KEY_SHOW_SOURCE, true) },
        write = { putBoolean(KEY_SHOW_SOURCE, it) },
    )

    var forwardActions: Boolean by cachedPref(
        read = { sp.getBoolean(KEY_FORWARD_ACTIONS, true) },
        write = { putBoolean(KEY_FORWARD_ACTIONS, it) },
    )

    var keepAfterSourceGone: Boolean by cachedPref(
        read = { sp.getBoolean(KEY_KEEP_AFTER_GONE, false) },
        write = { putBoolean(KEY_KEEP_AFTER_GONE, it) },
    )

    var skipAlreadyPromoted: Boolean by cachedPref(
        read = { sp.getBoolean(KEY_SKIP_PROMOTED, true) },
        write = { putBoolean(KEY_SKIP_PROMOTED, it) },
    )

    var chipStyle: ChipStyle by cachedPref(
        read = { enumOf(sp.getString(KEY_CHIP_STYLE, null), ChipStyle.AUTO) },
        write = { putString(KEY_CHIP_STYLE, it.name) },
    )

    var replaceSource: Boolean by cachedPref(
        read = { sp.getBoolean(KEY_REPLACE_SOURCE, false) },
        write = { putBoolean(KEY_REPLACE_SOURCE, it) },
    )

    var hideWhenSourceForeground: Boolean by cachedPref(
        read = { sp.getBoolean(KEY_HIDE_FOREGROUND, true) },
        write = { putBoolean(KEY_HIDE_FOREGROUND, it) },
    )

    var autoClearOnComplete: Boolean by cachedPref(
        read = { sp.getBoolean(KEY_AUTO_CLEAR, true) },
        write = { putBoolean(KEY_AUTO_CLEAR, it) },
    )

    var dismissAfterViewed: Boolean by cachedPref(
        read = { sp.getBoolean(KEY_DISMISS_VIEWED, true) },
        write = { putBoolean(KEY_DISMISS_VIEWED, it) },
    )

    fun isPackageEnabled(packageName: String): Boolean =
        allApps || allowedPackages.contains(packageName)

    fun setPackageEnabled(packageName: String, allowed: Boolean) {
        val next = allowedPackages.toMutableSet()
        if (allowed) next.add(packageName) else next.remove(packageName)
        allowedPackages = next
    }

    fun selectAll(packages: Collection<String>) {
        allowedPackages = allowedPackages + packages
    }

    fun clearSelection() {
        allowedPackages = emptySet()
    }

    fun ignoreSource(key: String, minutes: Long = 30L) {
        val expiry = System.currentTimeMillis() + minutes * 60_000L
        val next = sp.getStringSet(KEY_IGNORED, null)?.toMutableSet() ?: mutableSetOf()
        next.removeAll { item ->
            item.substringAfterLast('|').toLongOrNull()?.let { it < System.currentTimeMillis() } ?: true
        }
        next.add("$key|$expiry")
        sp.edit().putStringSet(KEY_IGNORED, next).apply()
    }

    fun isSourceIgnored(key: String): Boolean {
        val raw = sp.getStringSet(KEY_IGNORED, null) ?: return false
        val now = System.currentTimeMillis()
        return raw.any { item ->
            val split = item.lastIndexOf('|')
            if (split <= 0) return@any false
            val storedKey = item.substring(0, split)
            val expiry = item.substring(split + 1).toLongOrNull() ?: return@any false
            storedKey == key && expiry > now
        }
    }

    private fun <T> cachedPref(
        read: () -> T,
        write: SharedPreferences.Editor.(T) -> Unit,
    ): ReadWriteProperty<Any?, T> {
        val state = mutableStateOf(read())
        return object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                if (state.value == value) return
                state.value = value
                val editor = sp.edit()
                editor.write(value)
                editor.apply()
            }
        }
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TRIGGER = "trigger_mode"
        private const val KEY_ALL_APPS = "all_apps"
        private const val KEY_ALLOWED = "allowed_packages"
        private const val KEY_SHOW_SOURCE = "show_source_app"
        private const val KEY_FORWARD_ACTIONS = "forward_actions"
        private const val KEY_KEEP_AFTER_GONE = "keep_after_source_gone"
        private const val KEY_SKIP_PROMOTED = "skip_already_promoted"
        private const val KEY_CHIP_STYLE = "chip_style"
        private const val KEY_REPLACE_SOURCE = "replace_source"
        private const val KEY_HIDE_FOREGROUND = "hide_source_foreground"
        private const val KEY_AUTO_CLEAR = "auto_clear_complete"
        private const val KEY_DISMISS_VIEWED = "dismiss_after_viewed"
        private const val KEY_IGNORED = "ignored_sources"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(context).also { instance = it }
        }

        private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
    }
}
