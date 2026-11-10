// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.alerts

import app.mauri.sukrara.core.model.SensorTiming
import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/** The alert engine's sensor window decides the warm-up with THE numbers and rule (contracts/sensor/life.json). */
class SensorTimingFixtureTest {
    private val file = Json.parseToJsonElement(Contracts.file("sensor/life.json").readText()).jsonObject

    @Test
    fun numbersAreTheContracts() {
        assertEquals(file["warmupGraceS"]!!.jsonPrimitive.long, SensorTiming.WARMUP_GRACE_S)
        assertEquals(file["defaultWarmupMin"]!!.jsonPrimitive.int, SensorTiming.DEFAULT_WARMUP_MIN)
        assertEquals(file["defaultWearMin"]!!.jsonPrimitive.int, SensorTiming.DEFAULT_WEAR_MIN)
        assertEquals(SensorTiming.WARMUP_GRACE_S, SensorWindow.WARMUP_GRACE_S)
        assertEquals(SensorTiming.DEFAULT_WARMUP_MIN, SensorWindow.DEFAULT_WARMUP_MIN)
    }

    @Test
    fun windowWarmsUpLikeTheCard() {
        val now = 1_800_000_000L
        for (e in file["cases"]!!.jsonArray) {
            val c = e.jsonObject
            val start = now + c["start"]!!.jsonPrimitive.long
            val warmupEnd = now + c["warmupEnd"]!!.jsonPrimitive.long
            val latest =
                c["latest"]
                    ?.takeIf { it !is JsonNull }
                    ?.jsonPrimitive
                    ?.longOrNull
                    ?.let { now + it }
            val want = c["expect"]!!.jsonObject["warming"]!!.jsonPrimitive.boolean
            val window = SensorWindow(start, start + SensorTiming.DEFAULT_WEAR_MIN * 60L, warmupEnd)
            val name = c["name"]!!.jsonPrimitive.content
            assertEquals(name, want, window.warming(latest, now))
            assertEquals(name, want, SensorTiming.warming(start, warmupEnd, latest, now))
        }
    }
}
