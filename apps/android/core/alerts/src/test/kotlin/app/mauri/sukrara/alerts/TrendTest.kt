// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.alerts

import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Trend] and the two trend quiets of [Rules] (SPEC §2 "Trend from the readings", #193). */
class TrendTest {
    /** contracts/alerts/trend.json: the same cases the Mac's TrendTests run. */
    @Test fun theSharedCases() {
        val cases =
            Json
                .parseToJsonElement(Contracts.file("alerts/trend.json").readText())
                .jsonObject
                .getValue("cases")
                .jsonArray
        assertTrue(cases.size > 40)
        for (c in cases.map { it.jsonObject }) {
            val name = c.getValue("name").jsonPrimitive.content
            val readings =
                c.getValue("readings").jsonArray.map {
                    val p = it.jsonArray
                    AlertReading(p[0].jsonPrimitive.long, p[1].jsonPrimitive.int)
                }
            val got = Trend.clear(readings, c.getValue("now").jsonPrimitive.long, c.getValue("up").jsonPrimitive.boolean)
            val want =
                c
                    .getValue("slope")
                    .takeIf { it != JsonNull }
                    ?.jsonPrimitive
                    ?.double
            if (want == null) {
                assertNull("$name (${c.getValue("why").jsonPrimitive.content})", got)
            } else {
                assertNotNull(name, got)
                assertEquals(name, want, got!!, 1e-9)
            }
        }
    }

    private val t0 = 1_762_100_000L

    private fun run(vararg mgdl: Int, step: Long = 60) = mgdl.mapIndexed { i, v -> AlertReading(t0 + i * step, v) }

    @Test fun keepHoldsTheWindowAscendingAndReplacesTheSameTime() {
        var kept = emptyList<AlertReading>()
        for (r in run(60, 61, 62, 63, 64, 65, 66)) kept = Trend.keep(kept, r)
        // only the readings within 5 min of the newest: the one exactly 5 min before is out
        assertEquals(run(60, 61, 62, 63, 64, 65, 66).drop(2), kept)
        // a late (backfilled) reading fills its place; one of the same time replaces it
        kept = Trend.keep(kept.filter { it.t != t0 + 240 }, AlertReading(t0 + 240, 64))
        assertEquals((2..6).map { t0 + it * 60 }, kept.map { it.t })
        kept = Trend.keep(kept, AlertReading(t0 + 240, 70))
        assertEquals(70, kept.single { it.t == t0 + 240 }.mgdl)
        // a reading far older than the window is dropped at once
        assertEquals(kept, Trend.keep(kept, AlertReading(t0 - 3600, 50)))
    }

    @Test fun aWholeNumberSlopeIsExact() {
        assertEquals(1.0, Trend.slope(run(61, 62, 63, 64, 65)), 0.0)
        assertEquals(0.5, Trend.slope(run(56, 56, 57, 57, 58)), 0.0)
        assertEquals(0.0, Trend.slope(listOf(AlertReading(t0, 60))), 0.0)
    }

    private val s = AlertSettings()

    @Test fun risingBackIntoRangePredictsFromTheSlope() {
        val r = run(61, 62, 63, 64, 65)
        val now = r.last().t
        assertTrue(Rules.risingBackIntoRange(AlertKind.LOW, r, s, now)) // 65 + 20 = 85
        // exactly at the low threshold is in range (low is strictly below it)
        assertTrue(Rules.risingBackIntoRange(AlertKind.LOW, r, s.copy(risingLow = QuietRule(true, 5)), now)) // 70
        assertFalse(Rules.risingBackIntoRange(AlertKind.LOW, r, s.copy(risingLow = QuietRule(true, 4)), now)) // 69
        assertFalse(Rules.risingBackIntoRange(AlertKind.LOW, r, s.copy(risingLow = QuietRule(false, 20)), now))
        // very low only on the wearer's own phone; never a high or a rate kind
        val vl = run(46, 47, 48, 49, 50)
        assertTrue(Rules.risingBackIntoRange(AlertKind.VERY_LOW, vl, s, vl.last().t))
        assertFalse(Rules.risingBackIntoRange(AlertKind.VERY_LOW, vl, s.copy(wearersOwn = false), vl.last().t))
        assertFalse(Rules.risingBackIntoRange(AlertKind.HIGH, r, s, now))
        assertFalse(Rules.risingBackIntoRange(AlertKind.FALLING_FAST, r, s, now))
        // too old: the readings stopped
        assertFalse(Rules.risingBackIntoRange(AlertKind.LOW, r, s, now + Trend.MAX_AGE_S + 1))
    }

    @Test fun withLowOffRangeStartsAboveVeryLow() {
        val off = s.withRule(AlertKind.LOW, s.rule(AlertKind.LOW).copy(on = false))
        // 50 + 1 × 5 = 55: still very low (at or below 55); × 6 = 56: in range
        val r = run(46, 47, 48, 49, 50)
        assertFalse(Rules.risingBackIntoRange(AlertKind.VERY_LOW, r, off.copy(risingLow = QuietRule(true, 5)), r.last().t))
        assertTrue(Rules.risingBackIntoRange(AlertKind.VERY_LOW, r, off.copy(risingLow = QuietRule(true, 6)), r.last().t))
    }

    @Test fun fallingBackIntoRangeMirrorsIt() {
        val r = run(200, 199, 198, 197, 196)
        val now = r.last().t
        assertTrue(Rules.fallingBackIntoRange(AlertKind.HIGH, r, s, now)) // 196 − 20 = 176
        assertTrue(Rules.fallingBackIntoRange(AlertKind.VERY_HIGH, r, s, now))
        assertFalse(Rules.fallingBackIntoRange(AlertKind.EXTREMELY_HIGH, r, s, now))
        // the high threshold is high (180 alerts): 196 − 16 = 180 is not back
        assertFalse(Rules.fallingBackIntoRange(AlertKind.HIGH, r, s.copy(fallingHigh = QuietRule(true, 16)), now))
        assertTrue(Rules.fallingBackIntoRange(AlertKind.HIGH, r, s.copy(fallingHigh = QuietRule(true, 17)), now))
        assertFalse(Rules.fallingBackIntoRange(AlertKind.HIGH, r, s.copy(fallingHigh = QuietRule(false, 20)), now))
        // the sensor's rate plays no part: a rising rate on falling readings still quiets
        assertTrue(Rules.fallingBackIntoRange(AlertKind.HIGH, r.map { it.copy(rate = 2.0) }, s, now))
        assertFalse(Rules.fallingBackIntoRange(AlertKind.HIGH, run(200, 200, 200, 200, 200).map { it.copy(rate = -3.0) }, s, now))
    }
}
