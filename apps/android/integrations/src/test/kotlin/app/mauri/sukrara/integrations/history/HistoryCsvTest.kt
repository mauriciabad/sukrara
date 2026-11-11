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
        assertEquals(HistoryCsv.Result.Refused(FileProblem.UnknownDates("yesterday at one")), HistoryCsv.parse(bad, ZoneId.of("UTC")))
        // every time reads, but not in one layout: the first one the best layout can't read is what the error shows
        val mixed = resource("libreview-us.csv").replace("11-08-2026 01:00 PM", "13-08-2026 01:00 PM")
        assertEquals(HistoryCsv.Result.Refused(FileProblem.UnknownDates("13-08-2026 01:00 PM")), HistoryCsv.parse(mixed, ZoneId.of("UTC")))
    }

    private val nicosia = ZoneId.of("Asia/Nicosia")
    private val downloaded = Instant.parse("2026-11-14T13:05:00Z")

    @Test
    fun `issue 176 - LibreView's file with one-digit 12-hour times, midnight and noon, a row without a time`() {
        val h = ok(HistoryCsv.parse(resource("libreview-us-12h.csv"), nicosia, downloaded))
        assertEquals(ImportSource.LibreView, h.source)
        // Nicosia is UTC+2 in November; month first (nothing above 12: the dates that end nearest the download)
        assertEquals(
            listOf("2026-11-09T19:55:00Z", "2026-11-09T20:00:00Z", "2026-11-09T21:55:00Z", "2026-11-09T22:00:00Z", "2026-11-09T22:05:00Z")
                .map(Instant::parse) +
                listOf("2026-11-10T09:55:00Z", "2026-11-10T10:00:00Z").map(Instant::parse),
            h.readings.map { it.t },
        )
        assertEquals(listOf(140, 138, 131, 129, 127, 101, 104), h.readings.map { it.mgdl })
        assertEquals(
            listOf(TreatmentKind.RapidInsulin to 3.5, TreatmentKind.Carbs to 55.0),
            h.treatments.map { it.treatment.kind to it.treatment.amount },
        )
        assertTrue(h.treatments.all { it.treatment.at == Instant.parse("2026-11-10T11:05:00Z") })
        // the note without a time can't be placed
        assertEquals(1, h.skipped)
    }

    @Test
    fun `a Spanish account - day first, a m and p m, mmol per litre`() {
        val h = ok(HistoryCsv.parse(resource("libreview-es-12h.csv"), nicosia, downloaded))
        assertEquals(
            listOf("2026-11-13T07:05:00Z", "2026-11-13T07:10:00Z", "2026-11-13T19:20:00Z").map(Instant::parse),
            h.readings.map { it.t },
        )
        assertEquals(listOf(112, 117, 128), h.readings.map { it.mgdl })
        val long = h.treatments.single().treatment
        assertEquals(TreatmentKind.LongInsulin to 14.0, long.kind to long.amount)
        assertEquals(Instant.parse("2026-11-13T10:15:00Z"), long.at)
    }

    @Test
    fun `a file a spreadsheet saved again - semicolons, slashes, seconds, UTF-16`() {
        val text =
            resource("libreview-eu-mmol.csv")
                .lines()
                .joinToString("\r\n") { line ->
                    HistoryCsv.rows(line).firstOrNull().orEmpty().joinToString(";") { cell ->
                        cell.replace(Regex("""^(\d\d)-(\d\d)-(\d{4}) (\d\d:\d\d)$"""), "$1/$2/$3 $4:00")
                    }
                }
        assertTrue(";" in text && "02/11/2026 08:00:00" in text)
        val h = ok(HistoryCsv.parse(text, ZoneId.of("Europe/Madrid"), downloaded))
        assertEquals(Instant.parse("2026-11-02T07:00:00Z"), h.readings.first().t)
        assertEquals(listOf(112, 117, 106), h.readings.map { it.mgdl })

        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, HistoryCsv.decode(bytes))
        assertEquals(text, HistoryCsv.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray()))
    }

    @Test
    fun `the hour a clock change repeats keeps both passes`() {
        // Nicosia, 2026-10-25: 04:00 goes back to 03:00
        val header = resource("libreview-us.csv").lines()[1]
        val times = listOf("10-25-2026 2:55 AM", "10-25-2026 3:25 AM", "10-25-2026 3:55 AM", "10-25-2026 3:25 AM", "10-25-2026 3:55 AM")
        val text =
            "Glucose Data,Generated on,10-25-2026 9:00 AM UTC,Generated by,Sample Person\r\n$header\r\n" +
                times.joinToString("") { "FreeStyle Libre 3,00000000-0000-0000-0000-00000000000f,$it,0,100,,,,,,,,,,,,,,\r\n" }
        val h = ok(HistoryCsv.parse(text, nicosia, Instant.parse("2026-10-25T09:00:00Z")))
        assertEquals(
            listOf("2026-10-24T23:55:00Z", "2026-10-25T00:25:00Z", "2026-10-25T00:55:00Z", "2026-10-25T01:25:00Z", "2026-10-25T01:55:00Z")
                .map(Instant::parse),
            h.readings.map { it.t },
        )
    }

    @Test
    fun `quoted cells keep commas, quotes and line breaks`() {
        assertEquals(
            listOf(listOf("a", "b, c", "say \"hi\"", "two\nlines"), listOf("x")),
            HistoryCsv.rows("a,\"b, c\",\"say \"\"hi\"\"\",\"two\nlines\"\r\nx\r\n"),
        )
    }
}
