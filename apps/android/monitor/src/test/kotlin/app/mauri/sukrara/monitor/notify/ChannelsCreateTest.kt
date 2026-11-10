// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What Android gets from [Channels.create] for the sensor countdowns (#148). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChannelsCreateTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    @Test
    fun `the countdown channel shows on the lock screen silently and replaces the low-importance one`() {
        nm.createNotificationChannel(NotificationChannel("sensor.countdown", "old", NotificationManager.IMPORTANCE_LOW))
        Channels.create(ctx)
        val ch = nm.getNotificationChannel(Channels.SENSOR_COUNTDOWN)
        assertNotNull(ch)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, ch.importance)
        assertNull(ch.sound)
        assertFalse(ch.shouldVibrate())
        assertFalse(ch.canShowBadge())
        assertNull("the old channel is deleted", nm.getNotificationChannel("sensor.countdown"))
    }
}
