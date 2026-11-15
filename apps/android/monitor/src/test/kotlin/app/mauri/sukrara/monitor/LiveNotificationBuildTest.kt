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
import app.mauri.sukrara.core.model.NotificationIcon
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.monitor.notify.CardFrame
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.LiveNotificationContent
import app.mauri.sukrara.monitor.notify.NotificationCard
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
 * group, put back when swiped away, the late value struck through, first in the shade in every state (in the range
 * colour only with a value and *Coloured background*), and the content's actions as notification actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveNotificationBuildTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-01-15T12:00:00Z")
    private val live = LiveNotification(ctx)
    private val card = NotificationPrefs(chip = false, icon = NotificationIcon.ValueTrend)

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

    /**
     * System UI's first section of the shade (AOSP `ColorizedFgsCoordinator.isRichOngoing`, the service makes it a
     * foreground service): a promoted Live Update, a colorized foreground service, or a call. Anything else sorts with
     * every other app's notifications by time and sinks under newer ones (the owner, 2026-11-15: "now it is at the bottom").
     */
    private fun firstInShade(n: Notification) =
        n.extras.getBoolean(Notification.EXTRA_COLORIZED) || n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING)

    /** The shade's own colour (`materialColorSurfaceContainerHigh`): System UI doesn't tint a row in it. */
    private fun shade(dark: Boolean = false) = NotificationCard.surface(ctx, dark)

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

    /** #94: the value on the lock screen by default; with the setting off, private behind "Sukrara · Monitoring". */
    @Test fun `the lock screen shows the value unless the person turned it off`() {
        for (s in listOf(state(), state(mgdl = null), state(ageS = 6 * 60, sensor = SensorState.Ended))) {
            assertEquals(Notification.VISIBILITY_PUBLIC, build(s).visibility)
            val private = build(s, card.copy(lockScreen = false))
            assertEquals(Notification.VISIBILITY_PRIVATE, private.visibility)
            val public = private.publicVersion!!
            assertEquals(ctx.getString(R.string.monitor_notif_public_title), title(public).toString())
            val text = public.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            assertEquals(ctx.getString(R.string.monitor_notif_public_text), text)
            assertEquals(Channels.GLUCOSE, public.channelId)
        }
    }

    @Test fun `first in the shade in every state, with every choice of notification, drawn or not`() {
        val states =
            listOf(
                state(),
                state(mgdl = 50),
                state(mgdl = 300),
                state(mgdl = null),
                state(ageS = 8 * 60),
                state(ageS = 25 * 60),
                state(ageS = 6 * 60, sensor = SensorState.Ended),
            )
        // every choice of notification (a Live Update that Android won't promote included: Robolectric has no 36.1)
        val choices =
            NotificationIcon.entries.flatMap { icon ->
                listOf(true, false).flatMap { chip ->
                    listOf(true, false).flatMap { promotion ->
                        listOf(true, false).flatMap { colorize ->
                            listOf(true, false).map { lock ->
                                NotificationPrefs(chip, colorize = colorize, promotionAllowed = promotion, icon = icon, lockScreen = lock)
                            }
                        }
                    }
                }
            } + NotificationPrefs(chip = true, rootValue = true, promotionAllowed = true)
        for (s in states) {
            for (prefs in choices) {
                val n = live.build(LiveNotificationContent.from(s, now, LiveNotification::face, prefs), now)
                assertTrue("$prefs ${s.latest} ${s.sensor}", firstInShade(n))
            }
            // the card drawn, in both themes and both colours
            for (colorize in listOf(true, false)) {
                for (dark in listOf(false, true)) {
                    val content = LiveNotificationContent.from(s, now, LiveNotification::face, card.copy(colorize = colorize))
                    val n = live.build(content, now, CardFrame(s, emptyList(), GraphConfig(), dark = dark))
                    assertTrue("drawn, colorize=$colorize dark=$dark ${s.latest}", firstInShade(n))
                }
            }
        }
    }

    @Test fun `without the range colour it is in the shade's own colour, of the theme it is drawn for`() {
        val content = LiveNotificationContent.from(state(), now, LiveNotification::face, card)
        assertEquals(shade(), live.build(content, now).color)
        assertEquals(shade(dark = true), live.build(content, now, CardFrame(state(), emptyList(), GraphConfig(), dark = true)).color)
        assertEquals(shade(dark = false), live.build(content, now, CardFrame(state(), emptyList(), GraphConfig(), dark = false)).color)
        val ranged = build(state(), card.copy(colorize = true)).color
        assertTrue("the range colour when chosen", ranged != shade() && ranged != shade(dark = true))
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

    @Test fun `chip off with a value is the card with the number icon, without its pictures as text`() {
        val n = build(state())
        assertEquals("the system's colour by default", shade(), n.color)
        assertEquals(Icon.TYPE_BITMAP, n.smallIcon.type)
        // no card frame drawn yet (the service's first post): text, never an old picture
        assertNull(n.contentView)
        assertNull(n.bigContentView)
    }

    @Test fun `without a value it stays text, in the shade's colour, with its state's icon`() {
        for (s in listOf(state(mgdl = null), state(ageS = 25 * 60), state(ageS = 6 * 60, sensor = SensorState.Ended))) {
            val n = build(s, card.copy(colorize = true))
            assertEquals("never a range colour without a value", shade(), n.color)
            assertEquals(Icon.TYPE_RESOURCE, n.smallIcon.type)
        }
    }

    @Test fun `the content's actions are the notification's, each with somewhere to go`() {
        val ended = build(state(ageS = 6 * 60, sensor = SensorState.Ended))
        assertEquals(listOf(ctx.getString(R.string.monitor_action_replace_sensor)), ended.actions.map { it.title.toString() })
        assertTrue(shadowOf(ended.actions.single().actionIntent).isActivity)

        // no Open Sukrara (the owner, 2026-11-14): the tap already opens
        for (s in listOf(state(), state(mgdl = null))) assertTrue(build(s).actions.orEmpty().isEmpty())
    }
}
