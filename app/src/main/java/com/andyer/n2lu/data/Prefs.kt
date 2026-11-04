package com.andyer.n2lu.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

object PrefsBus {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    fun bump() {
        _revision.value = _revision.value + 1
    }
}

class Prefs private constructor(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("n2lu_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = edit { putBoolean(KEY_ENABLED, value) }

    var triggerMode: TriggerMode
        get() = enumOf(sp.getString(KEY_TRIGGER, null), TriggerMode.BANNER_OR_PROGRESS)
        set(value) = edit { putString(KEY_TRIGGER, value.name) }

    var allApps: Boolean
        get() = sp.getBoolean(KEY_ALL_APPS, false)
        set(value) = edit { putBoolean(KEY_ALL_APPS, value) }

    var allowedPackages: Set<String>
        get() = sp.getStringSet(KEY_ALLOWED, null)?.toSet() ?: emptySet()
        set(value) = edit { putStringSet(KEY_ALLOWED, value.toMutableSet()) }

    var showSourceApp: Boolean
        get() = sp.getBoolean(KEY_SHOW_SOURCE, true)
        set(value) = edit { putBoolean(KEY_SHOW_SOURCE, value) }

    var keepAfterSourceGone: Boolean
        get() = sp.getBoolean(KEY_KEEP_AFTER_GONE, false)
        set(value) = edit { putBoolean(KEY_KEEP_AFTER_GONE, value) }

    var skipAlreadyPromoted: Boolean
        get() = sp.getBoolean(KEY_SKIP_PROMOTED, true)
        set(value) = edit { putBoolean(KEY_SKIP_PROMOTED, value) }

    var chipStyle: ChipStyle
        get() = enumOf(sp.getString(KEY_CHIP_STYLE, null), ChipStyle.AUTO)
        set(value) = edit { putString(KEY_CHIP_STYLE, value.name) }

    var replaceSource: Boolean
        get() = sp.getBoolean(KEY_REPLACE_SOURCE, false)
        set(value) = edit { putBoolean(KEY_REPLACE_SOURCE, value) }

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
        next.removeAll { item -> item.substringAfterLast('|').toLongOrNull()?.let { it < System.currentTimeMillis() } ?: true }
        next.add("$key|$expiry")
        edit { putStringSet(KEY_IGNORED, next) }
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

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        val editor = sp.edit()
        editor.block()
        editor.apply()
        PrefsBus.bump()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TRIGGER = "trigger_mode"
        private const val KEY_ALL_APPS = "all_apps"
        private const val KEY_ALLOWED = "allowed_packages"
        private const val KEY_SHOW_SOURCE = "show_source_app"
        private const val KEY_KEEP_AFTER_GONE = "keep_after_source_gone"
        private const val KEY_SKIP_PROMOTED = "skip_already_promoted"
        private const val KEY_CHIP_STYLE = "chip_style"
        private const val KEY_REPLACE_SOURCE = "replace_source"
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
