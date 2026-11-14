// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.Freshness
import app.mauri.sukrara.graph.GlucoseCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #195: when an alert's notification shows the newest reading again ([AlertLiveValue]): any change of the value, a new
 * arrow, LO / HI, or a new freshness step; never right after it rang. The Mac's `AlertLiveValueTests` check
 * the same cases.
 */
class AlertLiveValueTest {
    private val t = 1_800_000_000L

    private fun at(
        mgdl: Int,
        rate: Double? = -1.5,
        age: Long = 0,
    ) = AlertLiveValue.look(AlertReading(t - age, mgdl, rate), t)

    @Test fun `any change of the value counts and the 10 s guard after a ring`() {
        assertEquals(1, AlertLiveValue.MIN_MOVE_MGDL)
        assertEquals(10L, AlertLiveValue.AFTER_RING_S)
    }

    @Test fun `any change of the value changes it, both ways, the same value doesn't`() {
        assertTrue(AlertLiveValue.changed(at(70), at(71)))
        assertTrue(AlertLiveValue.changed(at(70), at(69)))
        assertFalse(AlertLiveValue.changed(at(70), at(70)))
    }

    @Test fun `a new arrow changes it, also without a move`() {
        assertTrue("↘ to ↓", AlertLiveValue.changed(at(70, -1.5), at(70, -2.5)))
        assertTrue("an arrow to the ring (no rate)", AlertLiveValue.changed(at(70, -1.5), at(70, null)))
        assertFalse("the same arrow, the same value", AlertLiveValue.changed(at(70, -1.2), at(70, -1.8)))
    }

    @Test fun `the reading turning late then stale changes it`() {
        val fresh = at(70, age = 30)
        assertFalse(AlertLiveValue.changed(at(70, age = 0), fresh))
        assertTrue("late: its time is shown", AlertLiveValue.changed(fresh, at(70, age = 120)))
        assertTrue("stale: struck through", AlertLiveValue.changed(at(70, age = 120), at(70, age = 400)))
        assertTrue(at(70, age = 400).struck)
    }

    @Test fun `never in the 10 s after the alert rang`() {
        val shown = at(70)
        val moved = at(80)
        assertFalse(AlertLiveValue.due(shown, moved, rangAt = t - 9, now = t))
        assertTrue(AlertLiveValue.due(shown, moved, rangAt = t - 10, now = t))
        assertTrue(AlertLiveValue.due(shown, moved, rangAt = null, now = t))
        assertFalse("nothing changed", AlertLiveValue.due(shown, at(70), rangAt = null, now = t))
    }

    /** #195 review: the sensor's LO / HI is said in words ("LO · below 40"): a number after it is news, however close. */
    @Test fun `leaving or reaching LO or HI changes it, without a rate too`() {
        assertTrue("LO to 43", AlertLiveValue.changed(at(39, null), at(43, null)))
        assertTrue("43 to LO", AlertLiveValue.changed(at(43, null), at(39, null)))
        assertTrue("HI to 398", AlertLiveValue.changed(at(401, null), at(398, null)))
        assertFalse("LO stays LO", AlertLiveValue.changed(at(39, null), at(39, null)))
    }

    /** #195 review: the clock set back after a ring (the ring now looks in the future) never holds the updates back. */
    @Test fun `a ring in the future after the clock went back doesn't hold updates`() {
        assertTrue(AlertLiveValue.due(at(70), at(80), rangAt = t + 3600, now = t))
    }

    /** The registered glyph surface `alert-notification` (contracts/graph/surfaces): the card's look, asked as itself. */
    @Test fun `the look is the card's, asked as the alert notification surface`() {
        val seen = mutableSetOf<CardSurface>()
        GlucoseCard.faceObserver = { s, _ -> seen += s }
        try {
            val stale = at(70, -2.5, age = 400)
            assertEquals("struck: the ring, not an arrow", "○", stale.arrow)
            assertEquals("↓", at(70, -2.5).arrow)
        } finally {
            GlucoseCard.faceObserver = null
        }
        assertEquals(setOf(CardSurface.ALERT_NOTIFICATION), seen)
    }

    @Test fun `it wakes at the card's next freshness step`() {
        val r = AlertReading(t, 70, -1.5)
        assertEquals(Freshness.nextChangeAt(t, t + 10), AlertLiveValue.nextStepAt(r, t + 10))
        assertTrue(AlertLiveValue.nextStepAt(r, t + 10)!! <= t + 91)
    }
}
