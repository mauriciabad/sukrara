// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.ReadingTrend
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.TreatmentQuiet
import app.mauri.sukrara.core.model.TrendFrom
import app.mauri.sukrara.core.model.TrendRate
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.integrations.CurrentSensor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * contracts/watchface/payload/cases.json on Android: each phone state builds exactly its payload ([AuraPayload]), and
 * the payload survives [MonkeyC] (decoded as the watch reads it); the bytes Garmin's own serializer wrote decode to the
 * same payload.
 */
class AuraPayloadFixtureTest {
    private val file = Json.parseToJsonElement(Contracts.file("watchface/payload/cases.json").readText()).jsonObject
    private val now = file.getValue("now").jsonPrimitive.long

    @Test
    fun everyCase() {
        val cases = file.getValue("cases").jsonArray
        assertTrue(cases.size >= 10)
        for (c in cases.map { it.jsonObject }) {
            val name = c.getValue("name").jsonPrimitive.content
            val payload = build(c.getValue("phone").jsonObject)
            val expected = c.getValue("payload")
            assertSame(name, expected, payload)
            // through the wire format and back, as the watch reads it
            assertSame("$name (encoded)", expected, MonkeyCDecode.decode(MonkeyC.encode(payload)))
        }
    }

    @Test
    fun garminsOwnBytes() {
        // a version-1 payload as Garmin wrote it: the serialization doesn't change with the payload's version
        val bytes = file["bytes"]?.takeIf { it !is JsonNull }?.jsonObject ?: return
        val hex = bytes.getValue("hex").jsonPrimitive.content
        val raw = ByteArray(hex.length / 2) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
        assertSame("Garmin's bytes", bytes.getValue("payload"), MonkeyCDecode.decode(raw))
    }

    @Test
    fun theFormatIsSmall() {
        // a full payload (three hours of history, a meal, every field) stays far under a Connect IQ background's limits
        val full =
            build(
                file
                    .getValue("cases")
                    .jsonArray[0]
                    .jsonObject
                    .getValue("phone")
                    .jsonObject,
            )
        assertTrue(MonkeyC.encode(full).size < 1024)
    }

    private fun build(p: JsonObject): Map<String, Any?> {
        fun at(e: JsonElement?) = Instant.ofEpochSecond(now + e!!.jsonPrimitive.long)
        val readings =
            p["readings"]?.jsonArray.orEmpty().map { it.jsonObject }.map { r ->
                val rate = r["rate"]?.jsonPrimitive?.doubleOrNull
                Reading(
                    at(r["t"]),
                    r.getValue("mgdl").jsonPrimitive.int,
                    null,
                    SourceId.Libre3,
                    rate?.let {
                        ReadingTrend(
                            it,
                            r["band"]?.jsonPrimitive?.intOrNull ?: TrendRate.plainBand(it),
                            TrendFrom.Readings,
                            null,
                        )
                    },
                )
            }
        val monitoring = p["monitoring"]?.jsonPrimitive?.booleanOrNull ?: true
        val quiet =
            p["quiet"]?.jsonObject?.let { q ->
                TreatmentQuiet(q["rapidS"]?.jsonPrimitive?.longOrNull, q["carbsS"]?.jsonPrimitive?.longOrNull)
            } ?: TreatmentQuiet()
        val state =
            GlucoseState(
                spec = SourceSpec.YourSensor,
                latest = readings.maxByOrNull { it.t },
                recent = readings.sortedBy { it.t },
                sensor = sensor(p.getValue("sensor").jsonObject),
                source = app.mauri.sukrara.core.model.SourceStatus.NotStarted,
                alerts = AlertUiState(status = status(p["status"]?.jsonObject), treatmentQuiet = quiet),
                link = null,
                monitoringStartedAt = if (monitoring) Instant.ofEpochSecond(now - 86_400) else null,
            )
        val treatments =
            p["treatments"]?.jsonArray.orEmpty().map { it.jsonObject }.map { t ->
                Treatment(
                    id = t.getValue("id").jsonPrimitive.content,
                    kind = KINDS.getValue(t.getValue("k").jsonPrimitive.content),
                    amount = t.getValue("a").jsonPrimitive.double,
                    at = at(t["t"]),
                    modifiedAt = at(t["t"]),
                    origin = TreatmentOrigin.Phone,
                )
            }
        val bands =
            p["bands"]?.jsonArray?.map { it.jsonPrimitive.int }?.let { Bands(it[0], it[1], it[2], it[3], it[4], it[5]) } ?: Bands()
        return AuraPayload.of(
            state,
            treatments,
            Instant.ofEpochSecond(now),
            GlucoseUnit.entries.first { it.key == (p["unit"]?.jsonPrimitive?.content ?: "mgdl") },
            bands,
            p["countdownHours"]?.jsonPrimitive?.int ?: 8,
            p["tir"]?.jsonPrimitive?.doubleOrNull,
            Instant.ofEpochSecond(now + (p["dayEnd"]?.jsonPrimitive?.long ?: 86_400)),
            life(p.getValue("sensor").jsonObject),
        )
    }

    /** The sensor screen's dates (`start` and its life's `end`, or `lifeEnd` for a warm-up), when the case gives them. */
    private fun life(s: JsonObject): CurrentSensor? {
        val start = s["start"]?.jsonPrimitive?.long ?: return null
        val end = (s["lifeEnd"] ?: s["end"])?.jsonPrimitive?.long ?: return null
        return CurrentSensor("3MH00SYNTH1", Instant.ofEpochSecond(now + start), Instant.ofEpochSecond(now + end))
    }

    private fun sensor(s: JsonObject): SensorState {
        fun at(k: String) = Instant.ofEpochSecond(now + s.getValue(k).jsonPrimitive.long)
        return when (s.getValue("state").jsonPrimitive.content) {
            "active" -> SensorState.Active(s["end"]?.let { at("end") })
            "warmup" -> SensorState.WarmingUp(at("ready"))
            "ended" -> SensorState.Ended
            else -> SensorState.None
        }
    }

    private fun status(s: JsonObject?): CardStatus {
        s ?: return CardStatus.None

        fun until(): Long = s.getValue("until").jsonPrimitive.let { if (it.content == "never") CardStatus.NEVER else now + it.long }

        fun low() = s.getValue("low").jsonPrimitive.booleanOrNull!!
        return when (s.getValue("k").jsonPrimitive.content) {
            "needed" -> {
                CardStatus.Needed(low())
            }

            "taken" -> {
                CardStatus.Taken(
                    KINDS.getValue(s.getValue("kind").jsonPrimitive.content),
                    now + s.getValue("at").jsonPrimitive.long,
                    until(),
                )
            }

            "paused" -> {
                CardStatus.Paused(low(), until())
            }

            "muted" -> {
                CardStatus.Muted(until())
            }

            else -> {
                error("status ${s["k"]}")
            }
        }
    }

    /** [expected] (the fixture's JSON) and [actual] (Monkey C values) say the same: numbers by value, maps by key. */
    private fun assertSame(
        where: String,
        expected: JsonElement,
        actual: Any?,
    ) {
        when (expected) {
            is JsonNull -> {
                assertEquals("$where: null", null, actual)
            }

            is JsonObject -> {
                val m = actual as? Map<*, *> ?: throw AssertionError("$where: expected a map, got $actual")
                assertEquals("$where: keys", expected.keys, m.keys.map { it as String }.toSet())
                expected.forEach { (k, v) -> assertSame("$where.$k", v, m[k]) }
            }

            is JsonArray -> {
                val l = actual as? List<*> ?: throw AssertionError("$where: expected a list, got $actual")
                assertEquals("$where: size", expected.size, l.size)
                expected.forEachIndexed { i, v -> assertSame("$where[$i]", v, l[i]) }
            }

            is JsonPrimitive -> {
                when {
                    expected.isString -> {
                        assertEquals(where, expected.content, actual)
                    }

                    expected.booleanOrNull != null -> {
                        assertEquals(where, expected.booleanOrNull, actual)
                    }

                    expected.content.contains('.') -> {
                        assertTrue("$where: $actual is not a Float", actual is Float)
                        assertEquals(where, expected.double, (actual as Float).toDouble(), 1e-4)
                    }

                    else -> {
                        assertTrue("$where: $actual is not an Int", actual is Int)
                        assertEquals(where, expected.long, (actual as Int).toLong())
                    }
                }
            }
        }
    }

    private companion object {
        val KINDS =
            mapOf(
                "rapid" to TreatmentKind.RapidInsulin,
                "long" to TreatmentKind.LongInsulin,
                "carbs" to TreatmentKind.Carbs,
                "note" to TreatmentKind.Note,
            )
    }
}
