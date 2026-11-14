// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.text.Spanned
import android.text.style.StrikethroughSpan
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertEvent
import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertInput
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.graph.Freshness
import app.mauri.sukrara.monitor.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale

/**
 * #195 (SPEC §1 "Showing an alert"), read back from the shade: the real runtime and the real notifier. An alert up
 * shows the newest reading without sound once it moved 5 mg/dL (never a wiggle, never within 10 s of the ring), its
 * time once late and the value struck through once stale, with the header's age the reading's; one swiped or tapped
 * away stays away until its next ring; a reading back out of range shows the episode's alert again silently, a milder
 * kind replaces a worse one silently; no data takes the level alert away and says when the last reading came; a
 * follower shows the wearer's `show` silently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertLiveNotificationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val rig = AlertRig()
    private val t0 = rig.clock.now().epochSecond
    private var ids = 0
    private val runtime =
        AlertRuntime(
            rig.store,
            rig.treatments,
            AlertNotifier(ctx),
            rig.alarm,
            { },
            { },
            rig.clock,
            newId = { "id${++ids}" },
            zone = { java.time.ZoneOffset.UTC },
        )

    @Before fun setUp() {
        ctx.applicationInfo.icon = android.R.drawable.star_on
        Locale.setDefault(Locale.ENGLISH)
    }

    private fun level(): Notification? = nm.activeNotifications.firstOrNull { it.id == AlertNotifier.idOf(AlertGroup.LEVEL) }?.notification

    private fun noData(): Notification? =
        nm.activeNotifications
            .firstOrNull {
                it.id ==
                    AlertNotifier.idOf(
                        AlertGroup.NO_DATA,
                    )
            }?.notification

    private fun Notification.text(): CharSequence = extras.getCharSequence(Notification.EXTRA_TEXT)!!

    private fun Notification.title(): String = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.silent() = groupAlertBehavior == Notification.GROUP_ALERT_SUMMARY

    /** A reading of [mgdl] at [s] seconds after t0 (the clock moves there). */
    private fun reading(
        mgdl: Int,
        s: Long,
    ) = runBlocking {
        rig.clock.instant = Instant.ofEpochSecond(t0 + s)
        // monitoring started once, at t0 (the hub keeps that time)
        runtime.onState(rig.wearerState(latest = rig.reading(mgdl)).copy(monitoringStartedAt = Instant.ofEpochSecond(t0)))
    }

    private fun tick(s: Long) =
        runBlocking {
            rig.clock.instant = Instant.ofEpochSecond(t0 + s)
            runtime.tick()
        }

    private fun settings(s: AlertSettings = AlertSettings()) = runBlocking { rig.store.setSettings(s) }

    @Test fun `the value follows the readings silently once it moved 5 mg dL, never a wiggle`() {
        settings()
        reading(65, 0)
        assertEquals("65 mg/dL ○", level()!!.text().toString())
        assertTrue(!level()!!.silent())
        reading(62, 60)
        assertEquals("a 3 mg/dL wiggle: left as it is", "65 mg/dL ○", level()!!.text().toString())
        reading(60, 120)
        assertEquals("60 mg/dL ○", level()!!.text().toString())
        assertTrue("updated without sound", level()!!.silent())
        assertEquals("the header's age is the reading's", (t0 + 120) * 1000, level()!!.`when`)
    }

    @Test fun `no update in the 10 s after the alert rang, it comes when the guard ends`() {
        settings()
        reading(65, 0)
        reading(58, 5)
        assertEquals("65 mg/dL ○", level()!!.text().toString())
        assertEquals("it wakes when the guard ends", t0 + 10, rig.alarm.at)
        tick(10)
        assertEquals("58 mg/dL ○", level()!!.text().toString())
    }

    @Test fun `a late reading shows its time, a stale one is struck through, waking for each step`() {
        settings()
        reading(65, 0)
        assertEquals("it wakes at the value's next step", t0 + 91, rig.alarm.at)
        tick(91)
        val time = Freshness.timeText(t0, t0 + 91, Locale.ENGLISH)
        assertEquals("65 mg/dL ○ · $time", level()!!.text().toString())
        tick(301)
        val struck = level()!!.text()
        assertEquals("65 mg/dL ○ · $time", struck.toString())
        val spans = (struck as Spanned).getSpans(0, struck.length, StrikethroughSpan::class.java)
        assertEquals("the value struck through, as the card", 1, spans.size)
        assertEquals("65 mg/dL".length, struck.getSpanEnd(spans[0]))
    }

    @Test fun `tapped or swiped away it stays away until its next ring`() {
        settings()
        reading(65, 0)
        nm.cancel(AlertNotifier.idOf(AlertGroup.LEVEL)) // the tap (auto-cancel) or a swipe
        reading(58, 60)
        assertNull("an update never brings it back", level())
        reading(57, 300) // the low's 5-min repeat rings
        assertNotNull(level())
        assertTrue(!level()!!.silent())
        assertEquals("57 mg/dL ○", level()!!.text().toString())
    }

    @Test fun `a swipe noted by its delete intent keeps the silent show away too`() {
        settings()
        reading(65, 0)
        nm.cancel(AlertNotifier.idOf(AlertGroup.LEVEL))
        runBlocking { runtime.dismissed(AlertKind.LOW.wire) }
        reading(75, 60) // in range: the alert ends on screen
        reading(66, 120) // low again in the episode: shown silently, but not after a swipe
        assertNull(level())
        reading(66, 300)
        assertNotNull("the repeat rings it back", level())
    }

    @Test fun `low again after a moment in range is shown at once without sound`() {
        settings()
        reading(65, 0)
        reading(75, 60)
        assertNull("in range: gone", level())
        reading(66, 120)
        assertNotNull(level())
        assertTrue(level()!!.silent())
        assertEquals("66 mg/dL ○", level()!!.text().toString())
    }

    @Test fun `very low getting better is replaced by the low at once without sound`() {
        settings()
        reading(56, 0)
        reading(55, 60)
        assertEquals(ctx.getString(R.string.alert_headline_very_low), level()!!.title())
        reading(56, 120)
        assertEquals(ctx.getString(R.string.alert_headline_low), level()!!.title())
        assertTrue(level()!!.silent())
    }

    /** #193 + #195: a rising low's quiet takes the alert away; stalled while still low, it is back at once, silently. */
    @Test fun `a rising low that stalls is shown again without sound, also with its repeat set to once`() {
        val s = AlertSettings()
        settings(s.withRule(AlertKind.LOW, s.rule(AlertKind.LOW).copy(repeatMin = 0)))
        listOf(58, 59, 60, 61).forEachIndexed { i, v -> reading(v, i * 60L) }
        assertNull("rising back into range: quiet", level())
        reading(61, 240)
        reading(61, 300)
        assertNotNull("stalled, still low: on screen again", level())
        assertTrue(level()!!.silent())
        assertEquals("61 mg/dL ○", level()!!.text().toString())
    }

    @Test fun `no data takes the low away and says when the last reading came`() {
        val s = AlertSettings()
        settings(s.withRule(AlertKind.NO_DATA, s.rule(AlertKind.NO_DATA).copy(threshold = 10.0)))
        reading(65, 0)
        tick(600)
        assertNull("the low's value isn't now any more", level())
        val time = Freshness.timeText(t0, t0 + 600, Locale.ENGLISH)
        assertEquals("Last reading $time · 65 mg/dL", noData()!!.text().toString())
    }

    @Test fun `a follower shows the wearer's show without sound`() {
        settings()
        runBlocking { runtime.onState(rig.followerState(latest = rig.reading(66))) }
        val f = Firing(AlertKind.LOW, "w1", 1, AlertReading(t0, 66, null), 70.0, t0)
        runBlocking { runtime.process(AlertInput.Remote(AlertEvent.show(f))) }
        assertNotNull(level())
        assertTrue(level()!!.silent())
        assertEquals("66 mg/dL ○", level()!!.text().toString())
    }
}
