// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model.storage

import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import java.time.Duration
import java.util.TreeSet
import java.util.UUID
import kotlin.math.abs

/**
 * Where imported history came from (Settings › Share with apps › Import history). Its readings are stored under
 * [readings] (one source per origin, [SourceId.Imported]); its entries carry it as [TreatmentRecord.importedFrom].
 * Imported history is never uploaded anywhere (LibreView, Nightscout, Tidepool, Health Connect): it came from one of
 * those services or from an app that already sent it there, and a second copy would count twice.
 */
enum class ImportSource(
    val id: String,
) {
    /** LibreView's *Download glucose data* file. */
    LibreView("libreview"),

    /** The Tidepool account (its data API). */
    Tidepool("tidepool"),

    /** A Sukrara export (this app's, or the earlier app's *Export data*), contracts/export. */
    File("file"),
    ;

    val readings: SourceId get() = SourceId("import-$id")

    companion object {
        fun of(id: String?): ImportSource? = entries.firstOrNull { it.id == id }
    }
}

/**
 * THE import path's rules, shared by every importer (LibreView, Tidepool, a file) and pure so they can be property
 * tested. Nothing is ever counted twice, and an import run twice changes nothing:
 *
 * - **Readings**: one measurement is one reading. An imported reading closer than [SAME_READING] (either way) to a
 *   stored one, whatever its source, or to one kept earlier in the same import, is the same measurement and is
 *   dropped: what is stored wins (first import wins), and the phone's own sensor wins over every import (its readings
 *   remove the imported ones within [SAME_READING] when they arrive later: `ReadingsRepository.upsert`). Several
 *   devices that uploaded the same reading (LibreView lists each) keep one.
 * - **Entries**: an entry whose id is stored is that entry (an origin id, e.g. a Sukrara entry sent to Tidepool and read
 *   back, keeps its id). Otherwise it is the same as a stored (tombstones included: a deleted entry stays deleted) or
 *   already imported entry of the same kind within [SAME_TREATMENT_S] with the same amount (insulin within
 *   [INSULIN_UNITS], carbs within [CARB_GRAMS]: a file rounds them) or the same note text: the Nightscout linking rule
 *   (`NsDocs.sameTreatment`) with the file's rounding.
 */
object HistoryImport {
    /** Two readings this close (either way) are the same measurement. */
    val SAME_READING: Duration = Duration.ofMinutes(2)

    /** How far apart (s) two entries may be and still be the same one (as Nightscout linking). */
    const val SAME_TREATMENT_S = 120L

    /** Insulin amounts this close are the same (a file writes tenths of a unit). */
    const val INSULIN_UNITS = 0.05

    /** Carbs this close are the same (a file writes whole grams). */
    const val CARB_GRAMS = 0.5

    /** How far back an import reaches: the history the phone keeps (readings are kept 90 days). */
    val PERIOD: Duration = Duration.ofDays(90)

    /**
     * Imported readings are at least this old: alerts ignore readings older than 15 minutes (contracts/alerts SPEC), so
     * an import never rings, and the card never shows an imported value as the current one.
     */
    val NEWEST_AGE: Duration = Duration.ofMinutes(15)

    /**
     * The readings of [incoming] to store next to [stored] (every stored reading around their times, any source): LO /
     * HI normalised, implausible values dropped, each one more than [SAME_READING] away from every stored reading and
     * every one kept before it. Oldest first.
     */
    fun readings(
        stored: List<Reading>,
        incoming: List<Reading>,
    ): List<Reading> {
        val tolerance = SAME_READING.toMillis()
        val taken = TreeSet<Long>()
        stored.forEach { taken += it.t.toEpochMilli() }
        val out = ArrayList<Reading>()
        for (r in incoming.mapNotNull(GlucoseRange::normalise).sortedWith(compareBy({ it.t }, { it.source.id }, { it.mgdl }))) {
            val t = r.t.toEpochMilli()
            val near = taken.ceiling(t - tolerance)
            if (near != null && near <= t + tolerance) continue
            taken += t
            out += r
        }
        return out
    }

    /**
     * The entries of [incoming] to store next to [stored] (every stored entry around their times and every stored one
     * with one of their ids, tombstones included): not stored by id, and not the same as a stored or earlier kept
     * entry ([same]). Oldest first.
     */
    fun treatments(
        stored: List<TreatmentRecord>,
        incoming: List<TreatmentRecord>,
    ): List<TreatmentRecord> {
        val ids = stored.mapTo(HashSet()) { it.treatment.id }
        val pool = stored.mapTo(ArrayList()) { it.treatment }
        val out = ArrayList<TreatmentRecord>()
        for (r in incoming.sortedWith(compareBy({ it.treatment.at }, { it.treatment.id }))) {
            val t = r.treatment
            if (t.id in ids || pool.any { same(it, t) }) continue
            ids += t.id
            pool += t
            out += r
        }
        return out
    }

    /** [a] and [b] are one entry: the same kind within [SAME_TREATMENT_S], the same amount or note. */
    fun same(
        a: Treatment,
        b: Treatment,
    ): Boolean {
        if (a.kind != b.kind || abs(a.at.epochSecond - b.at.epochSecond) > SAME_TREATMENT_S) return false
        return when (a.kind) {
            TreatmentKind.RapidInsulin, TreatmentKind.LongInsulin -> abs(a.amount - b.amount) <= INSULIN_UNITS + 1e-9
            TreatmentKind.Carbs -> abs(a.amount - b.amount) <= CARB_GRAMS + 1e-9
            TreatmentKind.Note -> a.note.trim() == b.note.trim()
        }
    }

    /**
     * A stable id for an imported entry from its origin [key] (the service's own id, or what the file says about it): the
     * same entry imported again gets the same id. A name-based UUID, lowercase, as the treatments contract wants.
     */
    fun treatmentId(
        source: ImportSource,
        key: String,
    ): String = UUID.nameUUIDFromBytes("sukrara-import|${source.id}|$key".toByteArray()).toString()
}

/**
 * THE import path (Import history): every importer stores through it, so [HistoryImport]'s rules hold whatever the
 * origin. Each call is one transaction (an import interrupted half way keeps what it stored; running it again adds only
 * what is missing).
 */
interface HistoryImportStore {
    /** Stores [HistoryImport.readings] of [batch] against everything stored around it; returns how many were added. */
    suspend fun importReadings(batch: List<Reading>): Int

    /** Stores [HistoryImport.treatments] of [batch] against everything stored around it; returns how many were added. */
    suspend fun importTreatments(batch: List<TreatmentRecord>): Int
}
