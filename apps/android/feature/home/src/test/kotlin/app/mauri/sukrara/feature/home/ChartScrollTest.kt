// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import androidx.compose.runtime.saveable.SaverScope
import app.mauri.sukrara.graph.ChartView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's chart scroll and zoom: the window stays inside the history and 2 h…31 d. */
class ChartScrollTest {
    private val now = 1_800_000_000L
    private val day = 86_400L
    private val default = 6 * 3600L
    private val first = now - 31 * day

    @Test fun scrollsBackAndStopsAtTheEdges() {
        val s = ChartScroll()
        assertFalse(s.away)
        assertEquals(-3600.0, s.by(-3600.0, first + default, now), 0.0)
        assertEquals(now - 3600, s.endS)
        assertTrue(s.away)
        // forward past now: only the hour back is moved, then it follows now again
        assertEquals(3600.0, s.by(7200.0, first + default, now), 0.0)
        assertNull(s.endS)
        // back past the oldest reading: stops with the oldest reading at the left edge
        s.by(-40.0 * day, first + default, now)
        assertEquals(first + default, s.endS)
    }

    @Test fun zoomsWithinTwoHoursAndTheHistory() {
        val s = ChartScroll()
        s.zoom(100.0, 1.0, default, first, now)
        assertEquals(ChartView.MIN_SPAN_S, s.spanS)
        s.zoom(0.0001, 1.0, default, first, now)
        assertEquals(31 * day, s.spanS)
        // a short history: never wider than it (but never narrower than the place's own window)
        val short = ChartScroll()
        short.zoom(0.0001, 1.0, default, now - 3 * day, now)
        assertEquals(3 * day, short.spanS)
        val tiny = ChartScroll()
        tiny.zoom(0.0001, 1.0, default, now - 3600, now)
        assertNull(tiny.spanS)
    }

    @Test fun zoomKeepsTheMomentUnderTheFingers() {
        val s = ChartScroll(endS = now - day)
        val anchor = 0.25
        val at = (now - day) - default * (1 - anchor)
        s.zoom(2.0, anchor, default, first, now)
        val span = s.span(default)
        assertEquals(default / 2, span)
        assertEquals(at, (s.endS!! - span * (1 - anchor)), 1.0)
    }

    @Test fun talkBackStepsDoubleAndHalve() {
        val s = ChartScroll()
        s.step(out = true, default, first, now)
        assertEquals(2 * default, s.spanS)
        s.step(out = false, default, first, now)
        assertNull(s.spanS) // back at the place's own length: not zoomed
        assertFalse(s.away)
    }

    @Test fun setBackToNowEndsTheScroll() {
        val s = ChartScroll(endS = now - day, spanS = 7 * day)
        s.set(now.toDouble(), default.toDouble(), default, now)
        assertFalse(s.away)
        assertEquals(default, s.span(default))
    }

    @Test fun canZoomOnlyWhileAStepChangesTheWindow() {
        val s = ChartScroll()
        assertTrue(s.canZoom(out = true, default, first, now))
        assertTrue(s.canZoom(out = false, default, first, now))
        s.zoom(100.0, 1.0, default, first, now)
        assertFalse(s.canZoom(out = false, default, first, now))
        assertTrue(s.canZoom(out = true, default, first, now))
        // a history as long as the place's window: nothing to zoom out to
        assertFalse(ChartScroll().canZoom(out = true, default, now - default, now))
    }

    private fun ChartScroll.restored(keep: Boolean): ChartScroll {
        val saver = ChartScroll.saver { keep }
        return saver.restore(with(saver) { SaverScope { true }.save(this@restored) }!!)!!
    }

    @Test fun aRotationKeepsTheExactWindow() {
        val s = ChartScroll()
        s.by(-3600.5, first + default, now)
        s.zoom(2.0, 0.5, default, first, now)
        val back = s.restored(keep = true)
        assertEquals(s.endS, back.endS)
        assertEquals(s.spanS, back.spanS)
        // the exact (sub-second) end carries on: the same step lands on the same second
        assertEquals(s.by(-0.5, first + default, now), back.by(-0.5, first + default, now), 0.0)
        assertEquals(s.endS, back.endS)
        // following now and not zoomed stays so
        assertFalse(ChartScroll().restored(keep = true).away)
    }

    @Test fun leavingHomeOpensAtNow() {
        val s = ChartScroll(endS = now - day, spanS = 7 * day)
        assertFalse(s.restored(keep = false).away)
    }

    /** #95: a double tap goes from the place's window to 3 h around the tapped moment, and from 3 h back. */
    @Test fun aDoubleTapIsThreeHoursAndBack() {
        val twelve = 12 * 3600L
        val s = ChartScroll()
        assertTrue(s.closeLookIn(twelve))
        // at the right edge (now): 3 h up to now, still following now
        val (end, span) = s.closeLook(1.0, twelve, first, now)!!
        assertEquals(now.toDouble(), end, 0.0)
        assertEquals(3 * 3600.0, span, 0.0)
        s.set(end, span, twelve, now)
        assertEquals(3 * 3600L, s.spanS)
        assertFalse(s.closeLookIn(twelve))
        // in the middle: the tapped moment stays under the finger
        val (end2, span2) = s.closeLook(0.5, twelve, first, now)!!
        assertEquals(twelve.toDouble(), span2, 0.0)
        assertEquals(now.toDouble(), end2, 0.0) // the window can't pass now: it follows now again
        s.set(end2, span2, twelve, now)
        assertFalse(s.away)
        // zoomed to a day by a pinch: the close look again
        s.set(now.toDouble(), day.toDouble(), twelve, now)
        assertEquals(3 * 3600.0, s.closeLook(0.0, twelve, first, now)!!.second, 0.0)
        // a place that shows 3 h or less: only back to its own length, nothing from there
        assertFalse(ChartScroll().closeLookIn(2 * 3600L))
        assertNull(ChartScroll().closeLook(1.0, 2 * 3600L, first, now))
        assertNull(ChartScroll().closeLook(1.0, 3 * 3600L, first, now))
    }

    @Test fun limits() {
        assertEquals(ChartView.MIN_SPAN_S.toDouble() to (31 * day).toDouble(), ChartScroll.spanLimits(default, first - day, now))
        // a place showing 1 h: zooming in never goes below it
        assertEquals(3600.0, ChartScroll.spanLimits(3600, first, now).first, 0.0)
    }
}
