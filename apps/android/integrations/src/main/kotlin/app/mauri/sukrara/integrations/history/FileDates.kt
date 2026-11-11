// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.history

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The local times of a glucose file (`Device Timestamp`) in whatever layout LibreView wrote them, which follows the
 * account's language and country (or a spreadsheet app that saved the file again): the date's three numbers in any
 * order a country uses (day / month / year, month / day / year, year / month / day), separated by `-`, `/`, `.` or
 * spaces, a two- or four-digit year; then hours and minutes (seconds and fractions optional), one or two digits for
 * the hour, a 24-hour clock or a 12-hour one with its AM / PM in any of the ways languages write it (`PM`, `pm`,
 * `p.m.`, `p. m.`, `μ.μ.`, `午後`, …), before or after the time. Issue #176: a LibreView file whose 12-hour times have
 * one-digit hours (`11-10-2026 9:15 PM`) wasn't read at all.
 *
 * Which number is the day isn't written in the cell: the file decides. The order every cell fits wins; when several
 * do (no day above 12 anywhere), the one that keeps the rows in order (a day / month swap scatters them), then the one
 * whose dates aren't in the future and end nearest now (the file was just downloaded), then day first.
 */
internal object FileDates {
    /** Which of a cell's three date numbers is the day, the month and the year. */
    enum class Order { DayMonthYear, MonthDayYear, YearMonthDay }

    /** One cell's numbers as written ([pm] null: a 24-hour clock). */
    data class Parts(
        val a: Int,
        val aDigits: Int,
        val b: Int,
        val c: Int,
        val cDigits: Int,
        val hour: Int,
        val minute: Int,
        val sec: Int,
        val pm: Boolean?,
    )

    private val DATE = Regex("""^(\d{1,4})\s*[-/.]\s*(\d{1,2})\s*[-/.]\s*(\d{1,4})\.?""")

    /** `21:15`, `21.15` (Finnish, Danish), `21 h 15` (French Canada); seconds and a fraction optional. */
    private val TIME = Regex("""(?<!\d)(\d{1,2})(?:[:.]| ?h ?)(\d{2})(?:[:.](\d{2})(?:[.,]\d{1,9})?)?(?!\d)""")

    /** What languages write for AM and PM, lower case, without dots and spaces (CLDR's day periods of LibreView's languages). */
    private val AM = setOf("am", "a", "πμ", "午前", "上午", "오전", "ص", "vorm", "fm", "de")
    private val PM = setOf("pm", "p", "μμ", "午後", "下午", "오후", "م", "nachm", "em", "du")

    /** Spaces a cell may hold besides the plain one (no-break, narrow no-break, thin). */
    private val SPACES = Regex("[\\s\u00A0\u202F\u2009]+")

    /** Splits a cell into its numbers; null when it isn't a date and a time. */
    fun split(cell: String): Parts? {
        val text = cell.replace(SPACES, " ").trim()
        val date = DATE.find(text) ?: return null
        val rest = text.substring(date.range.last + 1)
        val time = TIME.find(rest) ?: return null
        val marker =
            (rest.substring(0, time.range.first) + " " + rest.substring(time.range.last + 1))
                .lowercase()
                .filterNot { it.isWhitespace() || it == '.' || it == ',' }
                .removePrefix("t") // ISO's date-time separator
        val pm =
            when (marker) {
                "" -> null
                in AM -> false
                in PM -> true
                else -> return null
            }
        val (a, b, c) = date.destructured
        val (h, m, s) = time.destructured
        return Parts(
            a.toInt(),
            a.length,
            b.toInt(),
            c.toInt(),
            c.length,
            h.toInt(),
            m.toInt(),
            s.ifEmpty { "0" }.toInt(),
            pm,
        )
    }

    /** The cell's local time read in [order]; null when it doesn't fit (no such day, month or hour). */
    fun local(
        p: Parts,
        order: Order,
    ): LocalDateTime? {
        val (year, month, day) =
            when (order) {
                Order.YearMonthDay -> if (p.aDigits == 4 && p.cDigits <= 2) Triple(p.a, p.b, p.c) else return null
                Order.DayMonthYear -> if (p.aDigits <= 2 && p.cDigits in YEAR_DIGITS) Triple(year(p), p.b, p.a) else return null
                Order.MonthDayYear -> if (p.aDigits <= 2 && p.cDigits in YEAR_DIGITS) Triple(year(p), p.a, p.b) else return null
            }
        val hour =
            when (p.pm) {
                null -> p.hour.takeIf { it in 0..23 }
                false -> p.hour.takeIf { it in 0..12 }?.let { if (it == 12) 0 else it }
                true -> p.hour.takeIf { it in 1..12 }?.let { if (it == 12) 12 else it + 12 }
            } ?: return null
        if (p.minute > 59 || p.sec > 59) return null
        return try {
            LocalDate.of(year, month, day).atTime(hour, p.minute, p.sec)
        } catch (e: DateTimeException) {
            null
        }
    }

    /** A two-digit year is this century's (CGM data is). */
    private fun year(p: Parts) = if (p.cDigits == 2) 2000 + p.c else p.c

    private val YEAR_DIGITS = setOf(2, 4)

    /**
     * The order every cell fits, or null when none does. [now] (the phone's local time) breaks a tie between orders that
     * all fit: see the class.
     */
    fun orderFor(
        cells: List<Parts>,
        now: LocalDateTime?,
    ): Order? {
        val fitting =
            Order.entries.mapNotNull { o ->
                val times = cells.map { local(it, o) ?: return@mapNotNull null }
                o to times
            }
        if (fitting.size <= 1) return fitting.singleOrNull()?.first
        return fitting
            .minWith(
                compareBy<Pair<Order, List<LocalDateTime>>>(
                    { disorder(it.second) },
                    { future(it.second, now) },
                    { distance(it.second, now) },
                    { it.first.ordinal },
                ),
            ).first
    }

    /** How many cells [order] reads (the best order's misfits are what the error shows). */
    fun fits(
        cells: List<Parts>,
        order: Order,
    ): Int = cells.count { local(it, order) != null }

    /** Steps against the file's direction: a file in time order (oldest or newest first) has few, a day / month swap many. */
    private fun disorder(times: List<LocalDateTime>): Int {
        val steps = times.zipWithNext()
        val back = steps.count { (a, b) -> b.isBefore(a) }
        val forward = steps.count { (a, b) -> b.isAfter(a) }
        return minOf(back, forward)
    }

    /** Cells more than a day ahead of [now] (a time zone apart is fine). */
    private fun future(
        times: List<LocalDateTime>,
        now: LocalDateTime?,
    ): Int = if (now == null) 0 else times.count { it.isAfter(now.plusDays(1)) }

    /** How far the newest cell is from [now], in minutes. */
    private fun distance(
        times: List<LocalDateTime>,
        now: LocalDateTime?,
    ): Long {
        if (now == null) return 0
        val newest = times.max()
        return kotlin.math.abs(
            java.time.Duration
                .between(newest, now)
                .toMinutes(),
        )
    }

    /**
     * The instant of [local] in [zone]. In the hour a clock change repeats, the offset follows the file's direction from
     * the device's [previous] reading (oldest first: the second pass is the later offset), so that hour isn't folded
     * onto the first one.
     */
    fun instant(
        local: LocalDateTime,
        zone: ZoneId,
        previous: Instant?,
        oldestFirst: Boolean,
    ): Instant {
        val earlier = ZonedDateTime.ofLocal(local, zone, null).withEarlierOffsetAtOverlap().toInstant()
        val later = ZonedDateTime.ofLocal(local, zone, null).withLaterOffsetAtOverlap().toInstant()
        if (earlier == later || previous == null) return if (oldestFirst) earlier else later
        return if (oldestFirst) {
            if (earlier >= previous) earlier else later
        } else {
            if (later <= previous) later else earlier
        }
    }

    /** Whether the times run oldest first (most steps forward). */
    fun oldestFirst(times: List<LocalDateTime>): Boolean {
        val steps = times.zipWithNext()
        return steps.count { (a, b) -> !b.isBefore(a) } >= steps.count { (a, b) -> b.isBefore(a) }
    }

    /** The cell's layout without its numbers (digits → `0`): for the log and a problem report, never a reading's time. */
    fun shape(cell: String): String =
        cell
            .trim()
            .map { if (it.isDigit()) '0' else it }
            .joinToString("")
            .take(40)
}
