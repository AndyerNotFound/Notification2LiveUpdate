package com.andyer.n2lu.service

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewAckPolicyTest {

    @Test
    fun messageNotificationIsViewable() {
        assertTrue(ViewAckPolicy.isMessageLike(Notification.CATEGORY_MESSAGE, false, false))
        assertTrue(ViewAckPolicy.isMessageLike(null, false, false))
    }

    @Test
    fun ongoingNotificationIsNotViewable() {
        assertFalse(ViewAckPolicy.isMessageLike(Notification.CATEGORY_MESSAGE, true, false))
        assertFalse(ViewAckPolicy.isMessageLike(null, true, false))
    }

    @Test
    fun progressNotificationIsNotViewable() {
        assertFalse(ViewAckPolicy.isMessageLike(Notification.CATEGORY_PROGRESS, false, true))
        assertFalse(ViewAckPolicy.isMessageLike(null, false, true))
    }

    @Test
    fun taskCategoriesAreNotViewable() {
        assertFalse(ViewAckPolicy.isMessageLike(Notification.CATEGORY_NAVIGATION, false, false))
        assertFalse(ViewAckPolicy.isMessageLike(Notification.CATEGORY_TRANSPORT, false, false))
        assertFalse(ViewAckPolicy.isMessageLike(Notification.CATEGORY_CALL, false, false))
    }
}
