// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model.storage

import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.Random
import kotlin.math.abs

/**
 * THE import path's rules ([HistoryImport]) as properties over many random histories (seeded: a failure names its
 * seed and replays): nothing is ever counted twice, nothing stored is lost, every imported measurement / entry is
 * represented, and running an import again adds nothing. Synthetic data only.
 */
class HistoryImportTest {
    private val t0 = Instant.parse("2026-10-01T08:00:00Z")
    private val tol = HistoryImport.SAME_READING.toMillis()

    // MARK: readings

    private fun reading(
        sec: Long,
        mgdl: Int,
        source: SourceId,
    ) = Reading(t0.plusSeconds(sec), mgdl, null, source)

    /** A random history: the sensor's own runs (1-minute or 5-minute) and earlier imports, any order. */
    private fun storedReadings(r: Random): List<Reading> {
        val out = ArrayList<Reading>()
        repeat(r.nextInt(4)) {
            val start = r.nextInt(6 * 3600).toLong()
            val step = if (r.nextBoolean()) 60L else 300L
            repeat(r.nextInt(40)) { i -> out += reading(start + i * step + r.nextInt(5), 60 + r.nextInt(250), SourceId.Libre3) }
        }
        // an earlier import, already deduplicated
        val earlier =
            (
                0 until
                    r.nextInt(
                        60,
                    )
            ).map { reading(r.nextInt(6 * 3600).toLong(), 60 + r.nextInt(250), ImportSource.Tidepool.readings) }
        return HistoryImport.readings(out, earlier).plus(out).distinctBy { it.t to it.source }
    }

    /** A random batch from one service: 5- or 15-minute history, scans, the same reading from a second device, LO / HI. */
    private fun incomingReadings(r: Random): List<Reading> {
        val source = ImportSource.entries[r.nextInt(ImportSource.entries.size)].readings
        val start = r.nextInt(3 * 3600).toLong()
        val step = if (r.nextBoolean()) 300L else 900L
        val out = ArrayList<Reading>()
        repeat(r.nextInt(80)) { i ->
            val t = start + i * step + r.nextInt(30)
            out += reading(t, 20 + r.nextInt(480), source)
            if (r.nextInt(5) == 0) out += reading(t + r.nextInt(240) - 120, 60 + r.nextInt(250), source) // scan / 2nd device
        }
        return out.shuffled(r)
    }

    private fun near(
        a: Reading,
        b: Reading,
    ) = abs(a.t.toEpochMilli() - b.t.toEpochMilli()) <= tol

    @Test fun `readings - never two within two minutes, nothing stored lost, everything represented, idempotent`() {
        repeat(500) { seed ->
            val r = Random(seed.toLong())
            val stored = storedReadings(r)
            val incoming = incomingReadings(r)
            val added = HistoryImport.readings(stored, incoming)
            val result = stored + added
            for (a in added) {
                assertTrue("seed $seed: $a duplicates", result.none { it !== a && near(it, a) })
                assertTrue("seed $seed: an imported reading is stored as its source", a.source in SourceId.Imported)
            }
            for (i in incoming.mapNotNull(GlucoseRange::normalise)) {
                assertTrue("seed $seed: $i not represented", result.any { near(it, i) })
            }
            assertEquals("seed $seed: a second run adds nothing", emptyList<Reading>(), HistoryImport.readings(result, incoming))
            assertEquals("seed $seed: the order of the batch doesn't matter", added, HistoryImport.readings(stored, incoming.shuffled(r)))
        }
    }

    @Test fun `readings - the stored one wins, LO and HI are the flags, nonsense is dropped`() {
        val stored = listOf(reading(0, 120, SourceId.Libre3))
        val incoming =
            listOf(
                reading(90, 125, ImportSource.LibreView.readings),
                reading(-120, 118, ImportSource.LibreView.readings),
                reading(300, 25, ImportSource.LibreView.readings),
                reading(600, 450, ImportSource.LibreView.readings),
                reading(900, 0, ImportSource.LibreView.readings),
            )
        val added = HistoryImport.readings(stored, incoming)
        assertEquals(listOf(GlucoseRange.LO, GlucoseRange.HI), added.map { it.mgdl })
        assertEquals(listOf(300L, 600L), added.map { it.t.epochSecond - t0.epochSecond })
    }

    @Test fun `every import source has its own reading source and none is the phone's sensor`() {
        assertEquals(SourceId.Imported, ImportSource.entries.map { it.readings }.toSet())
        assertTrue(SourceId.Imported.none { it in SourceId.Wearer || it in SourceId.Follower })
        assertTrue(SourceId.Imported.all { it in SourceId.of(Role.Wearer) })
        assertTrue(SourceId.Imported.all { it in SourceId.notOf(Role.Follower) })
    }

    // MARK: treatments

    private fun entry(
        id: String,
        kind: TreatmentKind,
        amount: Double,
        sec: Long,
        note: String = "",
        deleted: Boolean = false,
        from: ImportSource? = null,
    ) = TreatmentRecord(
        Treatment(id, kind, amount, t0.plusSeconds(sec), note, t0.plusSeconds(sec), deleted, TreatmentOrigin.Phone),
        importedFrom = from,
    )

    private fun randomEntry(
        r: Random,
        id: String,
        from: ImportSource?,
    ): TreatmentRecord {
        val kind = TreatmentKind.entries[r.nextInt(TreatmentKind.entries.size)]
        val amount =
            when (kind) {
                TreatmentKind.RapidInsulin, TreatmentKind.LongInsulin -> (1 + r.nextInt(80)) / 4.0
                TreatmentKind.Carbs -> (5 + r.nextInt(60)).toDouble()
                TreatmentKind.Note -> 0.0
            }
        val note = if (kind == TreatmentKind.Note) listOf("walk", "pizza", "sick day").random(r) else ""
        return entry(id, kind, amount, r.nextInt(6 * 3600).toLong(), note, deleted = r.nextInt(8) == 0, from = from)
    }

    private fun <T> List<T>.random(r: Random) = this[r.nextInt(size)]

    /** The same entry as a service or a file has it: rounded, a little off in time, maybe its stored id (an origin id). */
    private fun copyOf(
        r: Random,
        e: TreatmentRecord,
        from: ImportSource,
        n: Int,
    ): TreatmentRecord {
        val t = e.treatment
        val rounded =
            when (t.kind) {
                TreatmentKind.Carbs -> Math.round(t.amount).toDouble()
                TreatmentKind.Note -> 0.0
                else -> Math.round(t.amount * 10) / 10.0
            }
        val id = if (r.nextInt(3) == 0) t.id else HistoryImport.treatmentId(from, "copy-$n")
        return entry(id, t.kind, rounded, t.at.epochSecond - t0.epochSecond + r.nextInt(120) - 60, " ${t.note} ", from = from)
    }

    @Test fun `entries - never the same twice, tombstones stay, everything represented, idempotent`() {
        repeat(500) { seed ->
            val r = Random(seed.toLong())
            val stored = (0 until r.nextInt(30)).map { randomEntry(r, "s$it", null) }.distinctBy { it.treatment.id }
            val from = ImportSource.entries.random(r)
            val copies = stored.filter { r.nextBoolean() }.mapIndexed { i, e -> copyOf(r, e, from, i) }
            val fresh = (0 until r.nextInt(30)).map { randomEntry(r, HistoryImport.treatmentId(from, "new-$it"), from).withDeleted(false) }
            val twice =
                fresh.filter { r.nextInt(4) == 0 }.mapIndexed {
                    i,
                    e,
                    ->
                    e.withId(HistoryImport.treatmentId(from, "second-device-$i"))
                }
            val incoming = (copies + fresh + twice).shuffled(r)

            val added = HistoryImport.treatments(stored, incoming)
            val result = stored + added
            val ids = stored.map { it.treatment.id }.toSet()
            for (a in added) {
                assertTrue("seed $seed: ${a.treatment.id} was stored", a.treatment.id !in ids)
                assertTrue(
                    "seed $seed: ${a.treatment} duplicates",
                    result.none { it !== a && HistoryImport.same(it.treatment, a.treatment) },
                )
                assertEquals("seed $seed: marked with its origin", from, a.importedFrom)
            }
            for (i in incoming) {
                assertTrue(
                    "seed $seed: ${i.treatment} not represented",
                    result.any { it.treatment.id == i.treatment.id || HistoryImport.same(it.treatment, i.treatment) },
                )
            }
            // a copy of a stored entry (deleted or not) never comes back as a new one
            assertTrue("seed $seed: a copy was added", copies.none { c -> c in added })
            assertEquals("seed $seed: a second run adds nothing", emptyList<TreatmentRecord>(), HistoryImport.treatments(result, incoming))
            assertEquals("seed $seed: the order of the batch doesn't matter", added, HistoryImport.treatments(stored, incoming.shuffled(r)))
        }
    }

    private fun TreatmentRecord.withDeleted(d: Boolean) = copy(treatment = treatment.copy(deleted = d))

    private fun TreatmentRecord.withId(id: String) = copy(treatment = treatment.copy(id = id))

    @Test fun `entries - the Nightscout linking rule with a file's rounding`() {
        val stored = entry("a", TreatmentKind.RapidInsulin, 2.25, 0)

        fun same(other: TreatmentRecord) = HistoryImport.same(stored.treatment, other.treatment)
        assertTrue(same(entry("b", TreatmentKind.RapidInsulin, 2.3, 120)))
        assertTrue(!same(entry("b", TreatmentKind.RapidInsulin, 2.3, 121)))
        assertTrue(!same(entry("b", TreatmentKind.RapidInsulin, 2.4, 0)))
        assertTrue(!same(entry("b", TreatmentKind.LongInsulin, 2.25, 0)))
        val carbs = entry("c", TreatmentKind.Carbs, 44.6, 0)
        assertTrue(HistoryImport.same(carbs.treatment, entry("d", TreatmentKind.Carbs, 45.0, -60).treatment))
        val note = entry("n", TreatmentKind.Note, 0.0, 0, "walk")
        assertTrue(HistoryImport.same(note.treatment, entry("m", TreatmentKind.Note, 0.0, 30, " walk ").treatment))
        assertTrue(!HistoryImport.same(note.treatment, entry("m", TreatmentKind.Note, 0.0, 30, "run").treatment))
    }

    @Test fun `an imported entry's id is stable, a lowercase UUID, and differs per origin`() {
        val a = HistoryImport.treatmentId(ImportSource.File, "rapid|1792067696|2.5|")
        assertEquals(a, HistoryImport.treatmentId(ImportSource.File, "rapid|1792067696|2.5|"))
        assertTrue(a != HistoryImport.treatmentId(ImportSource.LibreView, "rapid|1792067696|2.5|"))
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(a))
        assertTrue(Duration.ofDays(90) == HistoryImport.PERIOD)
    }
}
