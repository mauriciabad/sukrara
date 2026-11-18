// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.integrations.nightscout.NsSiteProblem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The Nightscout source's pure rules against contracts/sources/nightscout (the cases the Mac passes too). */
class NightscoutSourceContractTest {
    private fun load(name: String) = Json.parseToJsonElement(Contracts.file("sources/nightscout/$name").readText()).jsonObject

    private fun JsonElement.textOrNull() = takeIf { it != JsonNull }?.jsonPrimitive?.contentOrNull

    @Test
    fun `entries become the readings the contract lists`() {
        for (c in load("entries.json").getValue("cases").jsonArray.map { it.jsonObject }) {
            val got = NsEntries.parse(c.getValue("body").toString(), c.getValue("device").textOrNull())
            val want =
                c.getValue("readings").jsonArray.map { r ->
                    val a = r.jsonArray
                    Triple(a[0].jsonPrimitive.long, a[1].jsonPrimitive.int, a[2].jsonPrimitive.doubleOrNull)
                }
            assertEquals(c.getValue("name").jsonPrimitive.content, want, got.map { Triple(it.t.epochSecond, it.mgdl, it.sensorRate) })
        }
    }

    @Test
    fun `the sensor's life comes from the site's newest Sensor Start or Change, as the contract says`() {
        val life = load("sensor-life.json")
        val now = Instant.ofEpochSecond(life.getValue("now").jsonPrimitive.long)
        for (c in life.getValue("cases").jsonArray.map { it.jsonObject }) {
            val name = c.getValue("name").jsonPrimitive.content
            val sensor = NsSensorLife.of(c.getValue("site").jsonArray.map { it.jsonObject }, now)
            val expect = c.getValue("expect").takeIf { it != JsonNull }?.jsonObject
            if (expect == null) {
                assertEquals(name, null, sensor)
                continue
            }
            requireNotNull(sensor) { name }
            assertEquals(name, expect.getValue("id").jsonPrimitive.content, sensor.id)
            assertEquals(name, expect.getValue("start").jsonPrimitive.long, sensor.start.epochSecond)
            assertEquals(name, expect.getValue("warmupEnd").jsonPrimitive.long, sensor.warmupEnd.epochSecond)
            assertEquals(name, expect.getValue("end").jsonPrimitive.long, sensor.end.epochSecond)
            val latest =
                c
                    .getValue("latest")
                    .takeIf { it != JsonNull }
                    ?.jsonPrimitive
                    ?.long
                    ?.let(Instant::ofEpochSecond)
            val state =
                when (sensor.state(latest, now)) {
                    is SensorState.WarmingUp -> "warming"
                    is SensorState.Active -> "active"
                    SensorState.Ended -> "ended"
                    else -> "other"
                }
            assertEquals(name, expect.getValue("state").jsonPrimitive.content, state)
        }
    }

    @Test
    fun `addresses parse as the contract says`() {
        val sites = load("sites.json")
        for (c in sites.getValue("parse").jsonArray.map { it.jsonObject }) {
            val input = c.getValue("input").jsonPrimitive.content
            val s = NightscoutSite.parse(input)
            val problem =
                when (s.problem) {
                    null -> null
                    NsSiteProblem.Url -> "url"
                    NsSiteProblem.Https -> "https"
                }
            assertEquals(input, c.getValue("root").jsonPrimitive.content, s.root)
            assertEquals(input, c.getValue("token").textOrNull(), s.token)
            assertEquals(input, c.getValue("problem").textOrNull(), problem)
        }
        for (c in sites.getValue("same").jsonArray.map { it.jsonArray }) {
            val (a, b) = c[0].jsonPrimitive.content to c[1].jsonPrimitive.content
            assertEquals("$a ~ $b", c[2].jsonPrimitive.boolean, NightscoutSite.same(a, b))
        }
    }

    @Test
    fun `the source asks again when the contract says`() {
        val poll = load("poll.json")
        assertEquals(poll.getValue("min_s").jsonPrimitive.long, NsPoll.MIN_S)
        assertEquals(poll.getValue("upload_lag_s").jsonPrimitive.long, NsPoll.UPLOAD_LAG_S)
        assertEquals(poll.getValue("backoff_s").jsonArray.map { it.jsonPrimitive.long }, NsPoll.BACKOFF_S)
        assertEquals(poll.getValue("refused_s").jsonPrimitive.long, NsPoll.REFUSED_S)
        for (c in poll.getValue("cases").jsonArray.map { it.jsonObject }) {
            val got =
                NsPoll.waitS(
                    now = c.getValue("now").jsonPrimitive.long,
                    newest =
                        c
                            .getValue("newest")
                            .takeIf { it != JsonNull }
                            ?.jsonPrimitive
                            ?.longOrNull,
                    previous =
                        c
                            .getValue("previous")
                            .takeIf { it != JsonNull }
                            ?.jsonPrimitive
                            ?.longOrNull,
                    failures = c.getValue("failures").jsonPrimitive.int,
                    retryAfterS = c["retryAfter_s"]?.jsonPrimitive?.longOrNull,
                    refused = c["refused"]?.jsonPrimitive?.booleanOrNull == true,
                )
            assertEquals(c.getValue("name").jsonPrimitive.content, c.getValue("wait_s").jsonPrimitive.long, got)
        }
    }

    @Test
    fun `treatments sync both ways as the contract says`() {
        val t = load("treatments.json")
        val window = t.getValue("window").jsonArray.map { Instant.ofEpochSecond(it.jsonPrimitive.long) }
        val device = t.getValue("device").jsonPrimitive.content
        for (c in t.getValue("cases").jsonArray.map { it.jsonObject }) {
            val name = c.getValue("name").jsonPrimitive.content
            val local =
                c.getValue("local").jsonArray.map { it.jsonObject }.map { l ->
                    Treatment(
                        id = l.getValue("id").jsonPrimitive.content,
                        kind = kind(l.getValue("k").jsonPrimitive.content),
                        amount = l.getValue("a").jsonPrimitive.double,
                        at = Instant.ofEpochSecond(l.getValue("t").jsonPrimitive.long),
                        note = l.getValue("n").jsonPrimitive.content,
                        modifiedAt = Instant.ofEpochMilli(l.getValue("u").jsonPrimitive.long),
                        deleted = l.getValue("del").jsonPrimitive.boolean,
                        origin = TreatmentOrigin.Phone,
                    )
                }
            val led = c.getValue("ledger").jsonObject
            val ledger =
                NsSyncLedger(
                    pulled =
                        led
                            .getValue("pulled")
                            .jsonArray
                            .map { it.jsonPrimitive.content }
                            .toSet(),
                    pushed = led.getValue("pushed").jsonObject.mapValues { it.value.jsonPrimitive.long },
                )
            val plan =
                NsTreatmentSync.plan(
                    window[0],
                    window[1],
                    c.getValue("site").jsonArray.map { it.jsonObject },
                    local,
                    ledger,
                    device,
                )
            val expect = c.getValue("expect").jsonObject
            val upsert =
                expect.getValue("upsert").jsonArray.map { it.jsonObject }.map { e ->
                    SyncEntry(
                        e.getValue("id").jsonPrimitive.content,
                        kind(e.getValue("k").jsonPrimitive.content),
                        e.getValue("a").jsonPrimitive.double,
                        Instant.ofEpochSecond(e.getValue("t").jsonPrimitive.long),
                        e.getValue("n").jsonPrimitive.content,
                    )
                }
            assertEquals(name, upsert, plan.upsert)
            assertEquals(name, expect.ids("delete"), plan.delete)
            assertEquals(name, expect.ids("push"), plan.push)
            assertEquals(name, expect.ids("remove"), plan.remove)
        }
    }

    private fun JsonObject.ids(k: String) = getValue(k).jsonArray.map { it.jsonPrimitive.content }

    private fun kind(k: String) =
        when (k) {
            "rapid" -> TreatmentKind.RapidInsulin
            "long" -> TreatmentKind.LongInsulin
            "carbs" -> TreatmentKind.Carbs
            else -> TreatmentKind.Note
        }
}
