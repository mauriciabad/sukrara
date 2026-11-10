// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.text.Spanned
import android.text.style.StrikethroughSpan
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.LiveNotificationContent
import app.mauri.sukrara.monitor.notify.NotificationPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/**
 * [LiveNotification.build] (#82): what Android gets for the glucose notification, which is also the monitoring
 * service's foreground notification. [LiveNotificationContentTest][app.mauri.sukrara.monitor.notify.LiveNotificationContentTest]
 * covers what it says; this pins how it is posted: the ongoing, silent status of the glucose channel, outside every
 * group, put back when swiped away, the late value struck through, colorized only with a value, and the content's
 * actions as notification actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveNotificationBuildTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-01-15T12:00:00Z")
    private val live = LiveNotification(ctx)
    private val card = NotificationPrefs(chip = false, numberIcon = true)

    private fun state(
        mgdl: Int? = 138,
        ageS: Long = 30,
        sensor: SensorState = SensorState.Active(null),
    ): GlucoseState {
        val reading = mgdl?.let { Reading(now.minusSeconds(ageS), it, 1.2, SourceId.Libre3) }
        return GlucoseState.Initial.copy(
            role = Role.Wearer,
            latest = reading,
            recent = listOfNotNull(reading),
            sensor = sensor,
            monitoringStartedAt = now - Duration.ofHours(1),
        )
    }

    private fun build(
        state: GlucoseState,
        prefs: NotificationPrefs = card,
    ): Notification = live.build(LiveNotificationContent.from(state, now, LiveNotification::face, prefs), now)

    private fun title(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TITLE)

    private fun struck(text: CharSequence?) =
        (text as? Spanned)?.getSpans(0, text.length, StrikethroughSpan::class.java)?.isNotEmpty() == true

    @Test fun `the ongoing silent status of the glucose channel, outside every group, put back when swiped`() {
        for (s in listOf(state(), state(mgdl = null), state(ageS = 6 * 60, sensor = SensorState.Ended))) {
            val n = build(s)
            assertEquals(Channels.GLUCOSE, n.channelId)
            assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue("one sound at most, never per reading", n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            assertEquals(Notification.CATEGORY_STATUS, n.category)
            // a second grouped or ungrouped notification would be bundled with it (NotificationGroups)
            assertNull(n.group)
            assertNotNull("a tap opens something", n.contentIntent)
            assertTrue("a swipe tells the app, which posts it again", shadowOf(n.deleteIntent).isBroadcast)
        }
    }

    @Test fun `a fresh value is the plain title without a time, a late one is struck through at its reading's time`() {
        val fresh = build(state())
        assertFalse(struck(title(fresh)))
        assertFalse(fresh.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))

        val late = build(state(ageS = 8 * 60))
        assertTrue(struck(title(late)))
        assertTrue(late.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertEquals(now.minusSeconds(8 * 60).toEpochMilli(), late.`when`)
    }

    @Test fun `chip off with a value is the colorized card with the number icon, without its pictures as text`() {
        val n = build(state())
        assertTrue(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(Icon.TYPE_BITMAP, n.smallIcon.type)
        // no card frame drawn yet (the service's first post): text, never an old picture
        assertNull(n.contentView)
        assertNull(n.bigContentView)
    }

    @Test fun `without a value it stays text, uncoloured, with its state's icon`() {
        for (s in listOf(state(mgdl = null), state(ageS = 25 * 60), state(ageS = 6 * 60, sensor = SensorState.Ended))) {
            val n = build(s)
            assertFalse(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
            assertEquals(Icon.TYPE_RESOURCE, n.smallIcon.type)
        }
    }

    @Test fun `the content's actions are the notification's, each with somewhere to go`() {
        val ended = build(state(ageS = 6 * 60, sensor = SensorState.Ended))
        assertEquals(listOf(ctx.getString(R.string.monitor_action_replace_sensor)), ended.actions.map { it.title.toString() })
        assertTrue(shadowOf(ended.actions.single().actionIntent).isActivity)

        val waiting = build(state(mgdl = null))
        assertEquals(listOf(ctx.getString(R.string.monitor_action_open_app)), waiting.actions.map { it.title.toString() })
    }
}
