package com.andyer.n2lu.service

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import com.andyer.n2lu.R

/**
 * Android 16 ships two different promotion rule sets:
 *
 *  - 36.0 (original): a notification is promotable when it is ongoing, has a title, uses a
 *    promotable style and is colorized (`EXTRA_COLORIZED`).
 *  - 36.1 (QPR1): promotion is opt-in. The notification must carry
 *    `EXTRA_REQUEST_PROMOTED_ONGOING`, must not be colorized, and the app must hold
 *    `android.permission.POST_PROMOTED_NOTIFICATIONS`.
 *
 * Rather than guessing from the version code, build a probe notification and ask the platform:
 * `hasPromotableCharacteristics()` is the authoritative client-side check for the rules that are
 * actually active on this device, including the case where the QPR1 feature flag is off.
 */
object PromotionSupport {

    const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    enum class Mode {
        UNSUPPORTED,
        LEGACY_COLORIZED,
        OPT_IN,
    }

    @Volatile
    private var cached: Mode? = null

    fun mode(context: Context): Mode {
        cached?.let { return it }
        val computed = detect(context.applicationContext)
        cached = computed
        return computed
    }

    private fun detect(context: Context): Mode {
        if (Build.VERSION.SDK_INT < 36) return Mode.UNSUPPORTED
        val promotableWithoutColor = runCatching {
            Notification.Builder(context, Reposter.CHANNEL_ID)
                .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_stat_live))
                .setContentTitle("probe")
                .setContentText("probe")
                .setOngoing(true)
                .setStyle(Notification.BigTextStyle().bigText("probe"))
                .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
                .build()
                .hasPromotableCharacteristics()
        }.getOrDefault(false)
        return if (promotableWithoutColor) Mode.OPT_IN else Mode.LEGACY_COLORIZED
    }
}
