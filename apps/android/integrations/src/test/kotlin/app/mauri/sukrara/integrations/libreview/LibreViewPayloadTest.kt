// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.libreview

import app.mauri.sukrara.core.data.libreview.LibreLinkUp
import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/** `contracts/treatments/libreview-fixture.json`: the record numbers and the exact upload bytes. */
class LibreViewPayloadTest {
    private val fx = Json.parseToJsonElement(Contracts.file("treatments/libreview-fixture.json").readText()).jsonObject
    private val entries =
        fx["treatments"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            LvEntry(
                o["id"]!!.jsonPrimitive.content,
                o["k"]!!.jsonPrimitive.content,
                o["a"]!!.jsonPrimitive.double,
                o["t"]!!.jsonPrimitive.long,
                o["n"]!!.jsonPrimitive.content,
                o["u"]!!.jsonPrimitive.long,
                o["del"]!!.jsonPrimitive.boolean,
                o["lv"]!!.jsonPrimitive.long,
            )
        }

    @Test
    fun `record numbers match the contract`() {
        val kinds = entries.associate { it.id to it.k }
        for (r in fx["recordNumbers"]!!.jsonArray) {
            val (id, v, rn) = r.jsonArray.map { it.jsonPrimitive }
            assertEquals(id.content, rn.long, LibreViewPayload.recordNumber(id.content, kinds.getValue(id.content), v.long))
        }
    }

    @Test
    fun `payload matches the contract byte for byte`() {
        val body =
            LibreViewPayload.payload(
                entries,
                fx["deviceId"]!!.jsonPrimitive.content,
                fx["token"]!!.jsonPrimitive.content,
                fx["now"]!!.jsonPrimitive.long,
                ZoneId.of(fx["zone"]!!.jsonPrimitive.content),
            )
        assertEquals(fx["expectedPayload"]!!.jsonPrimitive.content, body)
    }

    /** `contracts/treatments/libreview-glucose-fixture.json` (investigation 41's lv2.py): a Libre 2 read every minute. */
    @Test
    fun `a Libre 2's minute readings become the contract's 5-minute values and bytes`() {
        val g = Json.parseToJsonElement(Contracts.file("treatments/libreview-glucose-fixture.json").readText()).jsonObject
        val serial = g["serial"]!!.jsonPrimitive.content
        assertEquals(true, LibreSerial.libre12(serial))
        for (s in g["notLibre12"]!!.jsonArray) assertEquals(s.toString(), false, LibreSerial.libre12(s.jsonPrimitive.content))
        val readings =
            g["readingsTMgdlLife"]!!.jsonArray.map {
                val (t, mgdl, life) = it.jsonArray.map { v -> v.jsonPrimitive.long }
                LvGlucose(life.toInt(), mgdl.toInt(), t)
            }
        val slots = LibreViewQueue.slots(readings)
        val expected =
            g["expectedSlots"]!!.jsonArray.map {
                val (lc, mgdl, t) = it.jsonArray.map { v -> v.jsonPrimitive.long }
                LvGlucose(lc.toInt(), mgdl.toInt(), t)
            }
        assertEquals(expected, slots)
        assertEquals(slots, LibreViewQueue.slots(readings.reversed()))
        val (clc, cmgdl, ct) = g["expectedCurrent"]!!.jsonArray.map { it.jsonPrimitive.long }
        val current = readings.maxBy { it.epochSec }
        assertEquals(LvGlucose(clc.toInt(), cmgdl.toInt(), ct), current)
        val sensor = LvSensor(serial, g["start"]!!.jsonPrimitive.long, current, slots, sendStart = true)
        assertEquals(g["productType"]!!.jsonPrimitive.long.toInt(), sensor.productType)
        assertEquals(g["wearMin"]!!.jsonPrimitive.long.toInt(), sensor.wearMin)
        val body =
            LibreViewPayload.payload(
                emptyList(),
                g["deviceId"]!!.jsonPrimitive.content,
                g["token"]!!.jsonPrimitive.content,
                g["now"]!!.jsonPrimitive.long,
                ZoneId.of(g["zone"]!!.jsonPrimitive.content),
                sensor,
            )
        assertEquals(g["expectedPayload"]!!.jsonPrimitive.content, body)
        assertEquals("3MH000ABCD7", LibreSerial.active(serial))
        assertEquals("H000ABCDE", LibreSerial.active("0D0H000ABCDE"))
    }

    @Test
    fun `the sensor start record sends the wear length the sensor reports`() {
        fun wear(sensor: LvSensor) =
            Regex("\"wearDuration\":(\\d+)")
                .find(LibreViewPayload.payload(emptyList(), "d", "t", 1_792_083_600, ZoneId.of("UTC"), sensor))
                ?.groupValues
                ?.get(1)
                ?.toInt()
        val current = LvGlucose(1400, 123, 1_792_083_600)
        // a Libre 3 (14 days) and a 3 Plus (15) say their own; one that says none: the family's (Libre 3: 15)
        assertEquals(20160, wear(LvSensor("0D0H000ABCDE", 1_792_000_000, current, sendStart = true, reportedWearMin = 20160)))
        assertEquals(21600, wear(LvSensor("0D0H000ABCDE", 1_792_000_000, current, sendStart = true, reportedWearMin = 21600)))
        assertEquals(21600, wear(LvSensor("0D0H000ABCDE", 1_792_000_000, current, sendStart = true)))
        assertEquals(20160, wear(LvSensor("3MH000ABCDE", 1_792_000_000, current, sendStart = true)))
    }

    @Test
    fun `glucose record numbers come from the serial and the life count`() {
        val h = LibreViewPayload.histor("3MH000ABCD")
        val body =
            LibreViewPayload.payload(
                emptyList(),
                "d",
                "t",
                1_792_083_600,
                ZoneId.of("UTC"),
                LvSensor(
                    "3MH000ABCD",
                    1_792_000_000,
                    LvGlucose(1400, 123, 1_792_083_600, 0.5),
                    listOf(LvGlucose(1395, 120, 1_792_083_300)),
                ),
            )
        assertEquals(true, "\"recordNumber\":${h or 1400L}," in body)
        assertEquals(true, "\"recordNumber\":${h or 1395L}," in body)
        assertEquals(true, "\"trendArrow\":\"Stable\"" in body)
    }

    /**
     * `contracts/treatments/libreview-librelink-fixture.json` (investigation 45's lv45.py): the treatments as LibreLink,
     * beside the official Libre 3 app; the official app's identity from LibreLinkUp's device type, and where it is said.
     */
    @Test
    fun `the LibreLink body, the identity rule and the device type match the contract`() {
        val l = Json.parseToJsonElement(Contracts.file("treatments/libreview-librelink-fixture.json").readText()).jsonObject
        val body =
            LibreViewPayload.linkPayload(
                entries,
                l["deviceId"]!!.jsonPrimitive.content,
                l["token"]!!.jsonPrimitive.content,
                l["now"]!!.jsonPrimitive.long,
                ZoneId.of(l["zone"]!!.jsonPrimitive.content),
            )
        assertEquals(l["expectedPayload"]!!.jsonPrimitive.content, body)
        for (row in l["identities"]!!.jsonArray) {
            val (dtid, official, send) = row.jsonArray.map { it.jsonPrimitive.contentOrNull }
            val identity = LvIdentity.ofDeviceType(dtid?.toInt())
            assertEquals("$dtid", official, identity?.id)
            assertEquals("$dtid", send, identity?.other?.id)
        }
        for (case in l["deviceType"]!!.jsonArray) {
            val c = case.jsonObject
            val data = c["body"]!!.jsonObject["data"]!!.jsonObject
            assertEquals(c["name"].toString(), c["dtid"]!!.jsonPrimitive.intOrNull, LibreLinkUp.deviceType(data))
            assertEquals(c["name"].toString(), c["dtid"]!!.jsonPrimitive.intOrNull, LibreLinkUp.graph(c["body"]!!.jsonObject).deviceType)
        }
    }
}
