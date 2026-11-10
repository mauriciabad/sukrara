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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
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
enum class FileProblem {
    /** Not LibreView's "Glucose Data" layout (any other CSV, a spreadsheet, a picture…). */
    NotGlucoseData,

    /** The layout is right but its times can't be read (a date format we don't know). */
    UnknownDates,
}

/**
 * Reads a file in LibreView's "Glucose Data" layout (contracts/export): Sukrara's own CSV export, the earlier app's
 * *Export data* (the same format) and LibreView's *Download glucose data* file. Columns by position, as Tidepool's
 * LibreView driver reads them, so LibreView's translated headers work too; the unit from the glucose column's header.
 *
 * - Time: Sukrara's `Time (ISO 8601)` column (with the offset) when it has one, else `Device Timestamp` in the
 *   person's time zone, in whichever of LibreView's date layouts every row fits (month or day first: the one that
 *   keeps the file in order).
 * - Record type 0 (historic) and 1 (scan) are readings, LO / HI as the flags; 4 rapid / long insulin (meal + correction
 *   + user change when the rapid column is empty), 5 carbs in grams, 6 a note; the note text goes with its entry.
 *   Strip glucose (finger-stick), ketones, food in servings and non-numeric amounts are counted as skipped: Sukrara
 *   has no record for them. A pen's priming column isn't read (the pen rebuilds its own record at its next read).
 * - Entries get stable ids from what the file says (kind, time, amount, note), so the same file imported again matches
 *   by id; `Entered By` (phone / mac / follower) is kept as the entry's origin.
 */
object HistoryCsv {
    private const val MGDL_PER_MMOL = 18.0182
    private val DATE_LAYOUTS =
        listOf(
            "yyyy-MM-dd HH:mm",
            "dd-MM-yyyy HH:mm",
            "MM-dd-yyyy HH:mm",
            "MM-dd-yyyy hh:mm a",
            "dd/MM/yyyy HH:mm",
            "MM/dd/yyyy HH:mm",
            "MM/dd/yyyy hh:mm a",
            "dd.MM.yyyy HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
        ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    sealed interface Result {
        data class Ok(
            val history: FileHistory,
        ) : Result

        data class Refused(
            val why: FileProblem,
        ) : Result
    }

    fun parse(
        text: String,
        zone: ZoneId,
    ): Result {
        val rows = rows(text.removePrefix("\uFEFF"))
        val headerAt = rows.indexOfFirst { isHeader(it) }
        if (headerAt < 0) return Result.Refused(FileProblem.NotGlucoseData)
        val header = rows[headerAt]
        val mmol = header.getOrNull(4).orEmpty().contains("mmol", ignoreCase = true)
        val ours = header.getOrNull(19).orEmpty().startsWith("Time (ISO") || rows.take(headerAt).any { "Sukrara" in it }
        val source = if (ours) ImportSource.File else ImportSource.LibreView
        val data = rows.drop(headerAt + 1).filter { it.size >= 14 && it[3].trim().toIntOrNull() != null }
        val local = data.filter { iso(it) == null }.map { it[2].trim() }
        val layout = if (local.isEmpty()) null else (layoutFor(local) ?: return Result.Refused(FileProblem.UnknownDates))

        val readings = ArrayList<Reading>()
        val treatments = ArrayList<TreatmentRecord>()
        var skipped = 0
        for (row in data) {
            val at = iso(row) ?: layout?.let { localTime(row[2].trim(), it, zone) }
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

    private fun localTime(
        text: String,
        layout: DateTimeFormatter,
        zone: ZoneId,
    ): Instant? =
        try {
            LocalDateTime.parse(text, layout).atZone(zone).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }

    /**
     * The date layout every row fits; when several do (no day above 12), the one that keeps the most rows in order (a
     * day-month swap scatters them), then the first listed (day before month, as most of the world writes it).
     */
    internal fun layoutFor(times: List<String>): DateTimeFormatter? {
        val fitting =
            DATE_LAYOUTS.mapNotNull { f ->
                val parsed = times.map { t -> runCatching { LocalDateTime.parse(t, f) }.getOrNull() ?: return@mapNotNull null }
                f to parsed.zipWithNext().count { (a, b) -> b.isBefore(a) }
            }
        return fitting.minByOrNull { it.second }?.first
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

    /** RFC 4180 rows: quoted cells may hold commas, quotes (doubled) and line breaks; CRLF or LF. */
    internal fun rows(text: String): List<List<String>> {
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

                    ',' -> {
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
