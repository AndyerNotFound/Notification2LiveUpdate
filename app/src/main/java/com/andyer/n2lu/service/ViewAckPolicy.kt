package com.andyer.n2lu.service

import android.app.Notification

object ViewAckPolicy {

    fun isMessageLike(category: String?, ongoing: Boolean, hasProgress: Boolean): Boolean {
        if (ongoing || hasProgress) return false
        return when (category) {
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_MISSED_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_SYSTEM -> false
            else -> true
        }
    }
}
