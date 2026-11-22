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
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.MarkShow
import app.mauri.sukrara.graph.RingSpec
import app.mauri.sukrara.graph.SlotKind
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.GlucoseMetrics
import app.mauri.sukrara.graph.stats.TimeInRange
import app.mauri.sukrara.graph.stats.TirRange
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
        val profiles = mapOf("cases" to GarminApp.Aura, "app" to GarminApp.App, "field" to GarminApp.Field)
        for ((list, app) in profiles) {
            val cases = file.getValue(list).jsonArray
            assertTrue(cases.size >= if (app == GarminApp.Aura) 10 else 3)
            for (c in cases.map { it.jsonObject }) {
                val name = "$list: " + c.getValue("name").jsonPrimitive.content
                val payload = build(c.getValue("phone").jsonObject, app)
                val expected = c.getValue("payload")
                assertSame(name, expected, payload)
                // through the wire format and back, as the watch reads it
                assertSame("$name (encoded)", expected, MonkeyCDecode.decode(MonkeyC.encode(payload)))
            }
        }
    }

    @Test
    fun theAppsDayFitsItsBackground() {
        // the app's full day (288 slots, the day's entries, every metric) with a heavy day of 64 entries and the key: well
        // under Background.exit's ~8 KB, for a background of 64 KB
        val phone =
            file
                .getValue("app")
                .jsonArray[0]
                .jsonObject
                .getValue("phone")
                .jsonObject
        val heavy =
            JsonObject(
                phone +
                    (
                        "treatments" to
                            JsonArray(
                                (1..64).map { k ->
                                    JsonObject(
                                        mapOf(
                                            "id" to JsonPrimitive("6f1c2b9a-0d3e-4c5f-8a7b-%012d".format(k)),
                                            "k" to JsonPrimitive(listOf("rapid", "carbs", "long")[k % 3]),
                                            "a" to JsonPrimitive(4.5 + k),
                                            "t" to JsonPrimitive(-1200L * k),
                                        ),
                                    )
                                },
                            )
                    ),
            )
        val p = build(heavy, GarminApp.App) + ("key" to "0".repeat(64))
        val size = MonkeyC.encode(p).size
        assertTrue("$size bytes", size < 6 * 1024)
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
                GarminApp.Aura,
            )
        assertTrue(MonkeyC.encode(full).size < 1024)
    }

    private fun build(
        p: JsonObject,
        profile: GarminApp,
    ): Map<String, Any?> {
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
                    deleted = t["del"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }
        val bands =
            p["bands"]?.jsonArray?.map { it.jsonPrimitive.int }?.let { Bands(it[0], it[1], it[2], it[3], it[4], it[5]) } ?: Bands()
        val firing =
            p["alerts"]
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.toSet()
                .orEmpty()
        return AuraPayload.of(
            profile,
            PayloadInputs(
                state.copy(alerts = state.alerts.copy(firingKinds = firing)),
                treatments,
                Instant.ofEpochSecond(now),
                GlucoseUnit.entries.first { it.key == (p["unit"]?.jsonPrimitive?.content ?: "mgdl") },
                bands,
                p["countdownHours"]?.jsonPrimitive?.int ?: 8,
                p["tir"]?.jsonPrimitive?.doubleOrNull,
                Instant.ofEpochSecond(now + (p["dayEnd"]?.jsonPrimitive?.long ?: 86_400)),
                life(p.getValue("sensor").jsonObject),
                graph(p, bands),
                p["metrics"]
                    ?.jsonObject
                    .orEmpty()
                    .map { (w, m) -> w.toLong() to metrics(m.jsonObject) }
                    .toMap(),
                p["open"]?.jsonPrimitive?.booleanOrNull ?: false,
            ),
        )
    }

    /** The person's metric and chart settings as the case gives them (README "The app only"). */
    private fun graph(
        p: JsonObject,
        bands: Bands,
    ): GraphConfig {
        fun spec(s: JsonObject) =
            RingSpec(
                s.getValue("w").jsonPrimitive.long,
                kind = SLOTS.getValue(s.getValue("k").jsonPrimitive.content),
                faded = s.getValue("dim").jsonPrimitive.booleanOrNull!!,
            )
        val lastElse = p["lastElse"]?.takeIf { it !is JsonNull }?.jsonObject?.let(::spec)
        val marks =
            p["marks"]?.jsonObject?.let { m ->
                MarkShow.parse(m.entries.joinToString(",") { (k, v) -> "$k:" + v.jsonArray.joinToString("+") { it.jsonPrimitive.content } })
            } ?: MarkShow.DEFAULT
        return GraphConfig(
            bands = bands,
            appHours = p["graphHours"]?.jsonPrimitive?.int ?: 12,
            slots = p["slots"]?.jsonArray?.map { spec(it.jsonObject) } ?: GraphConfig.DEFAULT_SLOTS,
            ringRange = if (p["ringRange"]?.jsonPrimitive?.content == "tight") TirRange.TIGHT else TirRange.IN_RANGE,
            side = p["side"]?.jsonPrimitive?.booleanOrNull ?: true,
            columnCrown = p["crown"]?.jsonPrimitive?.booleanOrNull ?: true,
            lastOn = p["lastPlace"]?.jsonPrimitive?.content != "off",
            lastElse = lastElse ?: GraphConfig.DEFAULT_LAST_ELSE.copy(on = false),
            marks = marks,
        )
    }

    /** A window's report numbers: shares per band, coverage, mean and SD (one reading stands for "has data"). */
    private fun metrics(m: JsonObject): GlucoseMetrics {
        val b = m.getValue("tir").jsonArray.map { it.jsonPrimitive.double }
        val cov = m.getValue("coverage").jsonPrimitive.double
        return GlucoseMetrics(
            TimeInRange(b[0], b[1], b[2], b[3], b[4], b[5], cov),
            m.getValue("mean").jsonPrimitive.double,
            m.getValue("sd").jsonPrimitive.double,
            if (cov > 0) 1 else 0,
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
        val SLOTS = mapOf("tir" to SlotKind.TIR, "gmi" to SlotKind.GMI, "avg" to SlotKind.AVERAGE, "cv" to SlotKind.CV)

        val KINDS =
            mapOf(
                "rapid" to TreatmentKind.RapidInsulin,
                "long" to TreatmentKind.LongInsulin,
                "carbs" to TreatmentKind.Carbs,
                "note" to TreatmentKind.Note,
            )
    }
}
