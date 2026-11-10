// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.history

import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.ImportSource
import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor

/**
 * Reading glucose files back (Import history): every `contracts/export` case's file (what this app and the earlier app
 * write) comes back as the readings and entries it was written from, and LibreView's own files (synthetic fixtures in
 * test resources, US and European layouts) are read by position, whatever the language of their headers.
 */
class HistoryCsvTest {
    private val cases =
        File(Contracts.root, "export")
            .listFiles { f -> f.isFile && f.extension == "json" }
            .orEmpty()
            .flatMap {
                Json
                    .parseToJsonElement(it.readText())
                    .jsonObject["cases"]!!
                    .jsonArray
                    .map { c -> c.jsonObject }
            }

    private fun resource(name: String) = javaClass.getResource("/history/$name")!!.readText()

    private fun ok(r: HistoryCsv.Result) = (r as HistoryCsv.Result.Ok).history

    private fun tenths(x: Double) = floor(abs(x) * 10 + 0.5) / 10 * (if (x < 0) -1 else 1)

    @Test
    fun `every export contract case reads back as what it was written from`() {
        check(cases.size >= 7)
        for (c in cases) {
            val name = c["name"]!!.jsonPrimitive.content
            val from = c["from"]!!.jsonPrimitive.long
            val to = c["to"]!!.jsonPrimitive.long
            val mmol = c["mmol"]?.jsonPrimitive?.boolean == true
            val text = c["expect"]!!.jsonArray.joinToString("") { it.jsonPrimitive.content + "\r\n" }
            val h = ok(HistoryCsv.parse(text, ZoneId.of(c["zone"]!!.jsonPrimitive.content)))
            assertEquals(name, ImportSource.File, h.source)

            // readings: the 5-minute rows the writer kept
            var last: Long? = null
            val rows =
                c["readings"]!!.jsonArray.map { it.jsonObject }.sortedBy { it["t"]!!.jsonPrimitive.long }.filter { r ->
                    val t = r["t"]!!.jsonPrimitive.long
                    (t in from until to && (last == null || t - last!! >= 280)).also { if (it) last = t }
                }
            assertEquals(name, rows.map { it["t"]!!.jsonPrimitive.long }, h.readings.map { it.t.epochSecond })
            for ((r, read) in rows.zip(h.readings)) {
                val sgv = r["sgv"]!!.jsonPrimitive.int
                assertTrue("$name: $sgv vs ${read.mgdl}", abs(sgv - read.mgdl) <= (if (mmol) 1 else 0))
                val rate = r["rate"]?.jsonPrimitive?.double
                assertEquals(name, rate?.let(::tenths), read.rateMgdlPerMin)
                assertEquals(ImportSource.File.readings, read.source)
            }

            // entries: every live one in the period, rounded as the file writes them, with its note and origin
            val live =
                c["treatments"]!!
                    .jsonArray
                    .map { it.jsonObject }
                    .filter { it["del"]?.jsonPrimitive?.boolean != true && it["t"]!!.jsonPrimitive.long in from until to }
            val read = h.treatments.sortedWith(compareBy({ it.treatment.at }, { it.treatment.kind.name }))
            val want = live.sortedWith(compareBy({ it["t"]!!.jsonPrimitive.long }, { kind(it).name }))
            assertEquals(name, want.size, read.size)
            for ((w, r) in want.zip(read)) {
                val t = r.treatment
                assertEquals(name, w["t"]!!.jsonPrimitive.long, t.at.epochSecond)
                assertEquals(name, kind(w), t.kind)
                val a = w["a"]?.jsonPrimitive?.double ?: 0.0
                val expected =
                    if (t.kind ==
                        TreatmentKind.Carbs
                    ) {
                        Math.round(a).toDouble()
                    } else if (t.kind == TreatmentKind.Note) {
                        0.0
                    } else {
                        tenths(a)
                    }
                assertEquals(name, expected, t.amount, 1e-9)
                assertEquals(
                    name,
                    w["n"]
                        ?.jsonPrimitive
                        ?.content
                        .orEmpty()
                        .trim(),
                    t.note,
                )
                assertEquals(name, origin(w), t.origin)
                assertEquals(name, ImportSource.File, r.importedFrom)
            }
        }
    }

    private fun kind(o: JsonObject) =
        when (o["k"]!!.jsonPrimitive.content) {
            "rapid" -> TreatmentKind.RapidInsulin
            "long" -> TreatmentKind.LongInsulin
            "carbs" -> TreatmentKind.Carbs
            else -> TreatmentKind.Note
        }

    private fun origin(o: JsonObject) =
        when (o["o"]?.jsonPrimitive?.content) {
            "mac" -> TreatmentOrigin.Mac
            "follower" -> TreatmentOrigin.Follower
            else -> TreatmentOrigin.Phone
        }

    @Test
    fun `LibreView's US file - month first with AM and PM, scans, every insulin column, servings and strips skipped`() {
        val zone = ZoneId.of("America/New_York")
        val h = ok(HistoryCsv.parse(resource("libreview-us.csv"), zone))
        assertEquals(ImportSource.LibreView, h.source)
        assertEquals(Instant.parse("2026-11-08T18:00:00Z"), h.readings.first().t)
        assertEquals(listOf(112, 118, 121, 118, 126, 98, GlucoseRange.LO), h.readings.map { it.mgdl })
        assertTrue(h.readings.all { it.source == ImportSource.LibreView.readings })
        val entries = h.treatments.map { Triple(it.treatment.kind, it.treatment.amount, it.treatment.note) }
        assertEquals(
            listOf(
                Triple(TreatmentKind.RapidInsulin, 4.5, "lunch"),
                Triple(TreatmentKind.RapidInsulin, 4.5, "lunch"),
                Triple(TreatmentKind.Carbs, 60.0, ""),
                Triple(TreatmentKind.Note, 0.0, "felt low, ate a snack"),
                Triple(TreatmentKind.RapidInsulin, 4.0, ""),
                Triple(TreatmentKind.LongInsulin, 14.0, ""),
            ),
            entries,
        )
        // the same entry from two devices has one id: the import keeps one
        assertEquals(h.treatments[0].treatment.id, h.treatments[1].treatment.id)
        // a finger-stick and food in servings: nothing Sukrara keeps
        assertEquals(2, h.skipped)
    }

    @Test
    fun `LibreView's European file - day first, mmol per litre with decimal commas, Spanish headers`() {
        val h = ok(HistoryCsv.parse(resource("libreview-eu-mmol.csv"), ZoneId.of("Europe/Madrid")))
        assertEquals(ImportSource.LibreView, h.source)
        assertEquals(Instant.parse("2026-11-02T07:00:00Z"), h.readings.first().t)
        assertEquals(listOf(112, 117, 106), h.readings.map { it.mgdl })
        assertEquals(
            2.5,
            h.treatments
                .single()
                .treatment.amount,
            1e-9,
        )
    }

    @Test
    fun `a file that isn't a glucose file, or whose dates can't be read, is refused`() {
        assertEquals(HistoryCsv.Result.Refused(FileProblem.NotGlucoseData), HistoryCsv.parse("name,amount\nrent,800\n", ZoneId.of("UTC")))
        assertEquals(HistoryCsv.Result.Refused(FileProblem.NotGlucoseData), HistoryCsv.parse("", ZoneId.of("UTC")))
        val bad = resource("libreview-us.csv").replace("11-08-2026 01:00 PM", "yesterday at one")
        assertEquals(HistoryCsv.Result.Refused(FileProblem.UnknownDates), HistoryCsv.parse(bad, ZoneId.of("UTC")))
    }

    @Test
    fun `quoted cells keep commas, quotes and line breaks`() {
        assertEquals(
            listOf(listOf("a", "b, c", "say \"hi\"", "two\nlines"), listOf("x")),
            HistoryCsv.rows("a,\"b, c\",\"say \"\"hi\"\"\",\"two\nlines\"\r\nx\r\n"),
        )
    }
}
