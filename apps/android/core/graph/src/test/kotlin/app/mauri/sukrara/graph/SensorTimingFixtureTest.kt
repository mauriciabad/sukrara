// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SensorTiming
import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

/** The card's badge decides the warm-up with THE numbers and rule (contracts/sensor/life.json), as the alerts do. */
class SensorTimingFixtureTest {
    private val file = Json.parseToJsonElement(Contracts.file("sensor/life.json").readText()).jsonObject

    @Test
    fun badgeWarmsUpLikeTheAlerts() {
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
            val info = SensorInfo(start, start + SensorTiming.DEFAULT_WEAR_MIN * 60L, warmupEnd)
            assertEquals(c["name"]!!.jsonPrimitive.content, want, SensorLife.warming(info, latest, now))
        }
    }

    /** The hub's states get the defaults: a warm-up starts the default warm-up before its end; an active sensor never warms. */
    @Test
    fun hubStatesUseTheDefaults() {
        val ready = Instant.ofEpochSecond(1_800_000_000L)
        val warming = SensorState.WarmingUp(ready).cardInfo()!!
        assertEquals(ready.epochSecond - SensorTiming.DEFAULT_WARMUP_MIN * 60L, warming.startS)
        assertEquals(warming.startS + SensorTiming.DEFAULT_WEAR_MIN * 60L, warming.endS)
        val active = SensorState.Active(ready).cardInfo()!!
        assertEquals(ready.epochSecond - SensorTiming.DEFAULT_WEAR_MIN * 60L, active.startS)
        assertFalse(SensorLife.warming(active, latestT = ready.epochSecond - 60, now = ready.epochSecond))
    }
}
