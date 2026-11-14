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
 * #195: when an alert's notification shows the newest reading again ([AlertLiveValue]): a move of 5 mg/dL or more, a
 * new arrow, or a new freshness step; never a wiggle, never right after it rang. The Mac's `AlertLiveValueTests` check
 * the same cases.
 */
class AlertLiveValueTest {
    private val t = 1_800_000_000L

    private fun at(
        mgdl: Int,
        rate: Double? = -1.5,
        age: Long = 0,
    ) = AlertLiveValue.look(AlertReading(t - age, mgdl, rate), t)

    @Test fun `the threshold is 5 mg dL and the 10 s guard after a ring`() {
        assertEquals(5, AlertLiveValue.MIN_MOVE_MGDL)
        assertEquals(10L, AlertLiveValue.AFTER_RING_S)
    }

    @Test fun `a move under 5 mg dL changes nothing, 5 or more does, both ways`() {
        assertFalse(AlertLiveValue.changed(at(70), at(74)))
        assertFalse(AlertLiveValue.changed(at(70), at(66)))
        assertTrue(AlertLiveValue.changed(at(70), at(75)))
        assertTrue(AlertLiveValue.changed(at(70), at(65)))
    }

    @Test fun `a new arrow changes it, also without a move`() {
        assertTrue("↘ to ↓", AlertLiveValue.changed(at(70, -1.5), at(70, -2.5)))
        assertTrue("an arrow to the ring (no rate)", AlertLiveValue.changed(at(70, -1.5), at(70, null)))
        assertFalse("the same arrow", AlertLiveValue.changed(at(70, -1.2), at(71, -1.8)))
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
        assertFalse("nothing changed", AlertLiveValue.due(shown, at(72), rangAt = null, now = t))
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
