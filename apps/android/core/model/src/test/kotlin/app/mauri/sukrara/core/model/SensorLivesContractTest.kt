// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [SensorLives] against every case of contracts/sources/sensor-life.json (the Mac runs the same: SensorLivesTests). */
class SensorLivesContractTest {
    private val doc = Json.parseToJsonElement(Contracts.file("sources/sensor-life.json").readText()).jsonObject
    private val now = doc.getValue("now").jsonPrimitive.long

    private fun JsonObject.rel(key: String): Long? =
        get(key)
            ?.takeIf { it != JsonNull }
            ?.jsonPrimitive
            ?.long
            ?.let { now + it }

    @Test
    fun `every case gives the life and state the contract expects`() {
        val cases = doc.getValue("cases").jsonArray.map { it.jsonObject }
        for (c in cases) {
            val name = c.getValue("name").jsonPrimitive.content
            val kind = SourceKind.of(c.getValue("source").jsonPrimitive.content)
            // the Mac's Juggluco on the network says what Android's Juggluco on the phone says
            val life = kind?.life ?: SourceLife.Start
            val said =
                c["said"]?.takeIf { it != JsonNull }?.jsonObject?.let {
                    SaidSensor(it.getValue("id").jsonPrimitive.content, now + it.getValue("start").jsonPrimitive.long)
                }
            val span =
                SensorLives.of(
                    life,
                    Person.of(c.getValue("person").jsonPrimitive.content),
                    said,
                    c.getValue("wearMin").jsonPrimitive.int,
                    c.rel("manualStart"),
                    now,
                )
            val expect = c["expect"]?.takeIf { it != JsonNull }?.jsonObject
            if (expect == null) {
                assertNull(name, span)
                continue
            }
            requireNotNull(span) { name }
            assertEquals(name, expect.getValue("id").jsonPrimitive.content, span.id)
            assertEquals(name, expect.rel("start"), span.startS)
            assertEquals(name, expect.rel("warmupEnd"), span.warmupEndS)
            assertEquals(name, expect.rel("end"), span.endS)
            assertEquals(name, expect.getValue("from").jsonPrimitive.content, span.from.name.lowercase())
            val state =
                when (span.state(c.rel("latest"), now)) {
                    SensorState.Ended -> "ended"
                    is SensorState.WarmingUp -> "warming"
                    is SensorState.Active -> "active"
                    else -> "?"
                }
            assertEquals(name, expect.getValue("state").jsonPrimitive.content, state)
        }
        assertEquals("every case ran", 19, cases.size)
    }

    @Test
    fun `the Mac's Juggluco on the network is a start-only source too`() {
        val sources = Json.parseToJsonElement(Contracts.file("sources/sources.json").readText()).jsonObject
        val lan =
            sources.getValue("sources").jsonArray.map { it.jsonObject }.first {
                it.getValue("id").jsonPrimitive.content ==
                    "lanJuggluco"
            }
        assertEquals(SourceLife.Start.id, lan.getValue("life").jsonPrimitive.content)
    }
}
