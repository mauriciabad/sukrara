// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.sensorend

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.NotificationGroups
import app.mauri.sukrara.monitor.notify.NotificationIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The two sensor countdowns as Android gets them (Robolectric, API 36.0: no promotion, the plain ongoing post). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SensorCountdownNotificationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val presenter by lazy { AndroidSensorEndPresenter(ctx) }
    private val now = 1_800_000_000L

    @Before fun setUp() {
        ctx.applicationInfo.icon = android.R.drawable.star_on
        val main = ComponentName(ctx.packageName, "app.mauri.sukrara.MainActivity")
        val pm = shadowOf(ctx.packageManager)
        pm.addActivityIfNotPresent(main)
        pm.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        Channels.create(ctx) // as the app does: the silent countdown channel posts at once
    }

    private fun posted(notice: SensorNotice) = shadowOf(nm).getNotification(AndroidSensorEndPresenter.id(notice))

    /**
     * #156: a countdown and the glucose notification both ungrouped end up in Android's automatic bundle, so the
     * countdowns are in Sukrara's info group with its summary; the glucose notification stays the one ungrouped.
     */
    @Test fun `both countdowns are in the info group with its summary`() {
        presenter.countdown(end = now + 5 * 3600, hours = 8, now = now, promote = false)
        presenter.warmup(start = now - 600, warmupEnd = now + 3000, now = now, waiting = false)
        val info = NotificationGroups.Group.INFO
        assertEquals(info.key, posted(SensorNotice.COUNTDOWN).group)
        assertEquals(info.key, posted(SensorNotice.WARMUP).group)
        val summary = shadowOf(nm).getNotification(NotificationIds.INFO_SUMMARY)
        assertEquals(info.key, summary.group)
        assertTrue(shadowOf(nm).allNotifications.none { it.group == null })

        presenter.cancel(SensorNotice.COUNTDOWN)
        presenter.cancel(SensorNotice.WARMUP)
        // no child left: the summary goes too (alone it would count as ungrouped)
        assertFalse(shadowOf(nm).activeNotifications.any { it.id == NotificationIds.INFO_SUMMARY })
    }

    /** #140: the end countdown's bar fills toward the end, and it goes by itself at the end (never a timer below zero). */
    @Test fun `the end countdown fills toward the end and times out at it`() {
        val now = System.currentTimeMillis() / 1000
        val end = now + 6 * 3600
        presenter.countdown(end, hours = 8, now = now, promote = false)
        val n = posted(SensorNotice.COUNTDOWN)
        assertTrue(kotlin.math.abs(n.timeoutAfter - 6 * 3600_000L) < 60_000)
        val style = Notification.Builder.recoverBuilder(ctx, n).style as Notification.ProgressStyle
        assertEquals(250, style.progress) // 2 h of 8 gone
    }

    @Test fun `the warm-up countdown has no timeout`() {
        presenter.warmup(start = now - 600, warmupEnd = now + 3000, now = now, waiting = false)
        assertEquals(0L, posted(SensorNotice.WARMUP).timeoutAfter)
    }
}
