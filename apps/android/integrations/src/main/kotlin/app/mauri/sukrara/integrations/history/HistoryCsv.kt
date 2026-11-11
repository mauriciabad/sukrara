// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.history

import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.HistoryImport
import app.mauri.sukrara.core.model.storage.ImportSource
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/** What a glucose file holds, ready for the import path. */
data class FileHistory(
    /** [ImportSource.File] for a Sukrara export (this app's or the earlier app's), [ImportSource.LibreView] for LibreView's. */
    val source: ImportSource,
    val readings: List<Reading>,
    val treatments: List<TreatmentRecord>,
    /** Rows that are nothing Sukrara keeps (finger-stick and ketone checks, food in servings, non-numeric amounts). */
    val skipped: Int,
)

/** Why a file can't be imported. */
sealed interface FileProblem {
    /** Not LibreView's "Glucose Data" layout (any other CSV, a spreadsheet, a picture…). */
    data object NotGlucoseData : FileProblem

    /** The layout is right but its times can't be read: [example] is the first time that couldn't (as the file has it). */
    data class UnknownDates(
        val example: String,
    ) : FileProblem
}

/**
 * Reads a file in LibreView's "Glucose Data" layout (contracts/export): Sukrara's own CSV export, the earlier app's
 * *Export data* (the same format) and LibreView's *Download glucose data* file. Columns by position, as Tidepool's
 * LibreView driver reads them, so LibreView's translated headers work too; the unit from the glucose column's header.
 *
 * - Time: Sukrara's `Time (ISO 8601)` column (with the offset) when it has one, else `Device Timestamp` in the
 *   person's time zone, in whatever layout LibreView wrote it ([FileDates]: day, month or year first, 12 or 24 hours,
 *   one- or two-digit hours, seconds or not); a row with no time is left out. In the hour a clock change repeats, the
 *   second pass gets the later offset.
 * - Comma-separated; a file a spreadsheet saved again with `;` or tabs is read too. UTF-8 or UTF-16 ([decode]).
 * - Record type 0 (historic) and 1 (scan) are readings, LO / HI as the flags; 4 rapid / long insulin (meal + correction
 *   + user change when the rapid column is empty), 5 carbs in grams, 6 a note; the note text goes with its entry.
 *   Strip glucose (finger-stick), ketones, food in servings and non-numeric amounts are counted as skipped: Sukrara
 *   has no record for them. A pen's priming column isn't read (the pen rebuilds its own record at its next read).
 * - Entries get stable ids from what the file says (kind, time, amount, note), so the same file imported again matches
 *   by id; `Entered By` (phone / mac / follower) is kept as the entry's origin.
 */
object HistoryCsv {
    private const val MGDL_PER_MMOL = 18.0182

    sealed interface Result {
        data class Ok(
            val history: FileHistory,
        ) : Result

        data class Refused(
            val why: FileProblem,
        ) : Result
    }

    /** Reads [text]. [now] (when known) settles a file whose dates fit both day first and month first: see [FileDates]. */
    fun parse(
        text: String,
        zone: ZoneId,
        now: Instant? = null,
    ): Result {
        val body = text.removePrefix("\uFEFF")
        val (rows, headerAt) =
            SEPARATORS
                .asSequence()
                .map { sep -> rows(body, sep).let { r -> r to r.indexOfFirst { isHeader(it) } } }
                .firstOrNull { it.second >= 0 }
                ?: return Result.Refused(FileProblem.NotGlucoseData)
        val header = rows[headerAt]
        val mmol = header.getOrNull(4).orEmpty().contains("mmol", ignoreCase = true)
        val ours = header.getOrNull(19).orEmpty().startsWith("Time (ISO") || rows.take(headerAt).any { "Sukrara" in it }
        val source = if (ours) ImportSource.File else ImportSource.LibreView
        val data = rows.drop(headerAt + 1).filter { it.size >= 14 && it[3].trim().toIntOrNull() != null }
        val times = localTimes(data, zone, now) ?: return Result.Refused(FileProblem.UnknownDates(firstUnread(data, now, zone)))

        val readings = ArrayList<Reading>()
        val treatments = ArrayList<TreatmentRecord>()
        var skipped = 0
        for ((i, row) in data.withIndex()) {
            val at = iso(row) ?: times[i]
            if (at == null) {
                skipped++
                continue
            }
            when (row[3].trim().toInt()) {
                0, 1 -> {
                    val mgdl = glucose(row[if (row[3].trim() == "0") 4 else 5], mmol)
                    if (mgdl == null) skipped++ else readings += Reading(at, mgdl, row.getOrNull(20)?.let(::number), source.readings)
                }

                4 -> {
                    val rapid =
                        number(row[7])
                            ?: listOf(16, 17, 18).mapNotNull { row.getOrNull(it)?.let(::number) }.takeIf { it.isNotEmpty() }?.sum()
                    val long = number(row[12])
                    val made =
                        listOfNotNull(
                            rapid?.takeIf { it > 0 }?.let { entry(source, TreatmentKind.RapidInsulin, it, at, row) },
                            long?.takeIf { it > 0 }?.let { entry(source, TreatmentKind.LongInsulin, it, at, row) },
                        )
                    if (made.isEmpty()) skipped++ else treatments += made
                }

                5 -> {
                    val grams = number(row[9])?.takeIf { it > 0 }
                    if (grams == null) skipped++ else treatments += entry(source, TreatmentKind.Carbs, grams, at, row)
                }

                6 -> {
                    if (note(row).isBlank()) skipped++ else treatments += entry(source, TreatmentKind.Note, 0.0, at, row)
                }

                else -> {
                    skipped++
                }
            }
        }
        return Result.Ok(FileHistory(source, readings, treatments, skipped))
    }

    /** The column-name row: `Device`, `Serial Number`, `Device Timestamp`, `Record Type`, … in any language (by shape). */
    private fun isHeader(row: List<String>): Boolean =
        row.size >= 19 && row[3].trim().toIntOrNull() == null && row[3].isNotBlank() &&
            row.getOrNull(4).orEmpty().let { it.contains("mg/dL", ignoreCase = true) || it.contains("mmol", ignoreCase = true) }

    private fun iso(row: List<String>): Instant? =
        row.getOrNull(19)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                OffsetDateTime.parse(it).toInstant()
            } catch (e: DateTimeParseException) {
                null
            }
        }

    /**
     * Each row's instant from its `Device Timestamp` (null: an ISO row, or no time), all in the one layout the file
     * fits; null when some time can't be read in it.
     */
    private fun localTimes(
        data: List<List<String>>,
        zone: ZoneId,
        now: Instant?,
    ): List<Instant?>? {
        val parts = data.map { row -> if (iso(row) != null || row[2].isBlank()) null else (FileDates.split(row[2]) ?: return null) }
        val cells = parts.filterNotNull()
        if (cells.isEmpty()) return parts.map { null }
        val order = FileDates.orderFor(cells, now?.atZone(zone)?.toLocalDateTime()) ?: return null
        val local = parts.map { p -> p?.let { FileDates.local(it, order) } }
        val oldestFirst = FileDates.oldestFirst(local.filterNotNull())
        // per device (its serial): the clock change's repeated hour follows that device's own order
        val previous = HashMap<String, Instant>()
        return local.mapIndexed { i, t ->
            t?.let { FileDates.instant(it, zone, previous[data[i][1]], oldestFirst).also { at -> previous[data[i][1]] = at } }
        }
    }

    /** The first time the file's best-fitting layout can't read (what the error shows). */
    private fun firstUnread(
        data: List<List<String>>,
        now: Instant?,
        zone: ZoneId,
    ): String {
        val cells = data.filter { iso(it) == null && it[2].isNotBlank() }.map { it[2] }
        cells.firstOrNull { FileDates.split(it) == null }?.let { return it.trim() }
        val parts = cells.map { FileDates.split(it)!! }
        val best = FileDates.Order.entries.maxBy { FileDates.fits(parts, it) }
        return cells
            .zip(parts)
            .firstOrNull { FileDates.local(it.second, best) == null }
            ?.first
            ?.trim()
            ?: cells.firstOrNull()?.trim().orEmpty()
    }

    /** A glucose cell: a number (mg/dL, or mmol/L with `.` or `,`), or LO / HI; null when it's empty or not a value. */
    private fun glucose(
        cell: String,
        mmol: Boolean,
    ): Int? {
        val c = cell.trim()
        if (c.equals("LO", ignoreCase = true)) return GlucoseRange.LO // card-ok: reads a file's LO cell, never shown
        if (c.equals("HI", ignoreCase = true)) return GlucoseRange.HI // card-ok: reads a file's HI cell, never shown
        val v = number(c) ?: return null
        return (if (mmol) v * MGDL_PER_MMOL else v).roundToInt()
    }

    private fun number(cell: String): Double? =
        cell
            .trim()
            .replace(',', '.')
            .takeIf { it.isNotEmpty() }
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() }

    /** The Notes column without the export's formula guard (a leading `'` before = + - @). */
    private fun note(row: List<String>): String {
        val n = row[13]
        return (if (n.length > 1 && n[0] == '\'' && n[1] in "=+-@\t\r") n.substring(1) else n).trim()
    }

    private fun entry(
        source: ImportSource,
        kind: TreatmentKind,
        amount: Double,
        at: Instant,
        row: List<String>,
    ): TreatmentRecord {
        val note = note(row)
        val rounded = if (kind == TreatmentKind.Carbs) Math.round(amount).toDouble() else Math.round(amount * 100) / 100.0
        val origin =
            when (row.getOrNull(21)?.trim()) {
                "mac" -> TreatmentOrigin.Mac
                "follower" -> TreatmentOrigin.Follower
                else -> TreatmentOrigin.Phone
            }
        val id = HistoryImport.treatmentId(source, "${kind.name}|${at.epochSecond}|$rounded|$note")
        return TreatmentRecord(Treatment(id, kind, rounded, at, note, at, false, origin), importedFrom = source)
    }

    /** What the cells may be separated by: LibreView's comma, then what spreadsheets save (semicolon, tab). */
    private val SEPARATORS = listOf(',', ';', '\t')

    /**
     * A chosen file's text: UTF-16 when it starts with that byte-order mark (a spreadsheet's "Unicode text"), else UTF-8
     * (its mark dropped too).
     */
    fun decode(bytes: ByteArray): String =
        when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> bytes.decodeToString().removePrefix("\uFEFF")
        }

    /** RFC 4180 rows: quoted cells may hold [sep], quotes (doubled) and line breaks; CRLF or LF. */
    internal fun rows(
        text: String,
        sep: Char = ',',
    ): List<List<String>> {
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"' && i + 1 < text.length && text[i + 1] == '"') {
                    cell.append('"')
                    i++
                } else if (c == '"') {
                    quoted = false
                } else {
                    cell.append(c)
                }
            } else {
                when (c) {
                    '"' -> {
                        quoted = true
                    }

                    sep -> {
                        row += cell.toString()
                        cell.clear()
                    }

                    '\r' -> {}

                    '\n' -> {
                        row += cell.toString()
                        cell.clear()
                        out += row
                        row = ArrayList()
                    }

                    else -> {
                        cell.append(c)
                    }
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            out += row
        }
        return out
    }
}
