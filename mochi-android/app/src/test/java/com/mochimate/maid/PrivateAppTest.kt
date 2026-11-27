package com.mochimate.maid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateAppTest {
    @Test fun messagingAppsArePrivate() {
        listOf(
            "jp.naver.line.android", "com.google.android.gm", "com.discord",
            "com.instagram.android", "com.twitter.android", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "com.whatsapp",
        ).forEach { assertTrue(it, MochiNotificationListener.isPrivateApp(it)) }
    }

    @Test fun paymentAndClockAreNotPrivate() {
        listOf(
            "jp.ne.paypay.android.app", "com.sec.android.app.clockpackage",
            "com.google.android.deskclock", "com.google.android.calendar",
        ).forEach { assertFalse(it, MochiNotificationListener.isPrivateApp(it)) }
    }
}
