// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [TrendRate] against contracts/trend: the same cases the Mac's TrendRateTests run. */
class TrendRateTest {
    private fun load(name: String): JsonObject = Json.parseToJsonElement(Contracts.file("trend/$name").readText()).jsonObject

    private fun JsonElement.doubleOrNull(): Double? = takeIf { it != JsonNull }?.jsonPrimitive?.double

    private fun JsonElement.intOrNull(): Int? = takeIf { it != JsonNull }?.jsonPrimitive?.int

    /** rate.json: every reading's rate, where it came from, its band and what the fast alerts judge. */
    @Test fun theRateCases() {
        val doc = load("rate.json")
        val constants = doc.getValue("constants").jsonObject
        assertEquals(
            TrendSetting.entries.map { it.id },
            constants.getValue("settings").jsonArray.map { it.jsonPrimitive.content },
        )
        val taus = constants.getValue("tauS").jsonObject
        TrendSetting.entries.filter { it.tauS != null }.forEach { assertEquals(taus.getValue(it.id).jsonPrimitive.long, it.tauS) }
        assertEquals(TrendSetting.DEFAULT.id, constants.getValue("default").jsonPrimitive.content)
        assertEquals(TrendRate.MAX_STEP_S, constants.getValue("maxStepS").jsonPrimitive.long)
        assertEquals(TrendRate.HYSTERESIS, constants.getValue("hysteresis").jsonPrimitive.double, 0.0)
        assertEquals(TrendRate.ALERT_HOLD_S, constants.getValue("alertHoldS").jsonPrimitive.long)
        assertEquals(TrendRate.DOUBLE_HOLD_S, constants.getValue("doubleHoldS").jsonPrimitive.long)
        val cases = doc.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.size > 30)
        for (c in cases) {
            val name = c.getValue("name").jsonPrimitive.content
            val setting = TrendSetting.entries.first { it.id == c.getValue("trend").jsonPrimitive.content }
            val readings =
                c.getValue("readings").jsonArray.map {
                    val p = it.jsonArray
                    val t = Instant.ofEpochSecond(p[0].jsonPrimitive.long)
                    val sensor = p[2].doubleOrNull()
                    when (val v = p[1].jsonPrimitive.content) {
                        "LO" -> Reading.lo(t, SourceId.Libre3)
                        "HI" -> Reading.hi(t, SourceId.Libre3)
                        else -> Reading(t, v.toInt(), sensor, SourceId.Libre3)
                    }
                }
            val got = TrendRate.annotate(readings, setting)
            assertEquals("$name: every reading, ascending", readings.size, got.size)
            assertEquals(name, got.sortedBy { it.t }, got)
            val byT = got.groupBy { it.t.epochSecond }
            val expect = c.getValue("expect").jsonArray.map { it.jsonArray }
            assertEquals("$name: one expectation per time", byT.size, expect.size)
            for (e in expect) {
                val t = e[0].jsonPrimitive.long
                val at = "$name at ${t - readings.minOf { it.t.epochSecond }} s"
                val trends = byT.getValue(t).map { it.trend }.distinct()
                assertEquals("$at: the same trend for the same time", 1, trends.size)
                val tr = trends.single()
                val rate = e[1].doubleOrNull()
                if (rate == null) {
                    assertNull(at, tr)
                    assertEquals(at, "none", e[2].jsonPrimitive.content)
                    continue
                }
                assertNotNull(at, tr)
                assertEquals("$at rate", rate, tr!!.rate, 1e-9)
                assertEquals("$at from", e[2].jsonPrimitive.content, tr.from.name.lowercase())
                assertEquals("$at band", e[3].intOrNull(), tr.band)
                val held = e[4].doubleOrNull()
                if (held == null) assertNull("$at held", tr.held) else assertEquals("$at held", held, tr.held!!, 1e-9)
                // the rate a reading shows is its trend's
                assertEquals(at, tr.rate, byT.getValue(t).first().rate)
            }
        }
    }

    /** bands.json: plain bands, the drawn angle and band sequences (hysteresis, the doubled arrow's hold). */
    @Test fun theBandCases() {
        val doc = load("bands.json")
        for (p in doc.getValue("plain").jsonArray.map { it.jsonObject }) {
            val rate = p.getValue("rate").jsonPrimitive.double
            assertEquals("plain $rate", p.getValue("band").jsonPrimitive.int, TrendRate.plainBand(rate))
        }
        for (a in doc.getValue("angles").jsonArray.map { it.jsonObject }) {
            val rate = a.getValue("rate").jsonPrimitive.double
            assertEquals("deg $rate", a.getValue("deg").jsonPrimitive.double, TrendRate.deg(rate), 1e-6)
        }
        for (s in doc.getValue("sequences").jsonArray.map { it.jsonObject }) {
            val name = s.getValue("name").jsonPrimitive.content
            val points = s.getValue("points").jsonArray.map { it.jsonArray }
            val got = TrendRate.bands(points.map { it[0].jsonPrimitive.long }, points.map { it[1].doubleOrNull() })
            assertEquals(name, (s.getValue("bands") as JsonArray).map { it.intOrNull() }, got)
        }
    }

    /** clear.json: the quiets' clear-move rule (#193), unchanged. */
    @Test fun theClearCases() {
        val cases = load("clear.json").getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.size > 40)
        for (c in cases) {
            val name = c.getValue("name").jsonPrimitive.content
            val readings =
                c.getValue("readings").jsonArray.map {
                    val p = it.jsonArray
                    object : TrendPoint {
                        override val t = p[0].jsonPrimitive.long
                        override val mgdl = p[1].jsonPrimitive.int
                    }
                }
            val got = TrendRate.clear(readings, c.getValue("now").jsonPrimitive.long, c.getValue("up").jsonPrimitive.boolean)
            val want = c.getValue("slope").doubleOrNull()
            if (want == null) {
                assertNull("$name (${c.getValue("why").jsonPrimitive.content})", got)
            } else {
                assertNotNull(name, got)
                assertEquals(name, want, got!!, 1e-9)
            }
        }
    }

    @Test fun theSettingReadsItsStoredValue() {
        assertEquals(TrendSetting.Quick, TrendSetting.of("quick"))
        assertEquals(TrendSetting.Sensor, TrendSetting.of("sensor"))
        assertEquals(TrendSetting.Balanced, TrendSetting.of(null))
        assertEquals(TrendSetting.Balanced, TrendSetting.of("whatever"))
    }
}
