// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.history

import app.mauri.sukrara.integrations.history.FileDates.Order
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The local times of a glucose file in every layout LibreView writes them (its languages and countries) and spreadsheets
 * save them in (issue #176: `11-10-2026 9:15 PM` was refused). Synthetic times only.
 */
class FileDatesTest {
    private fun read(
        cell: String,
        order: Order,
    ): LocalDateTime? = FileDates.split(cell)?.let { FileDates.local(it, order) }

    private val nineFifteenPm = LocalDateTime.of(2026, 11, 10, 21, 15)

    @Test
    fun `every layout a LibreView account or a spreadsheet writes`() {
        val cases =
            listOf(
                // US English: month first, 12 hours, one-digit hour (the owner's error)
                "11-10-2026 9:15 PM" to Order.MonthDayYear,
                "11-10-2026 09:15 PM" to Order.MonthDayYear,
                "11/10/2026 9:15:00 PM" to Order.MonthDayYear,
                "11-10-2026 9:15 pm" to Order.MonthDayYear,
                "11-10-2026 9:15PM" to Order.MonthDayYear,
                // Europe: day first, 24 hours
                "10-11-2026 21:15" to Order.DayMonthYear,
                "10/11/2026 21:15:00" to Order.DayMonthYear,
                "10.11.2026 21:15" to Order.DayMonthYear,
                "10.11.26 21:15" to Order.DayMonthYear,
                "10.11.2026 21.15" to Order.DayMonthYear,
                "2026-11-10 21 h 15" to Order.YearMonthDay,
                // day first with a 12-hour clock: Spanish, Greek
                "10-11-2026 9:15 p. m." to Order.DayMonthYear,
                "10-11-2026 9:15 p.m." to Order.DayMonthYear,
                "10/11/2026 9:15 μ.μ." to Order.DayMonthYear,
                // year first: ISO, Tidepool's `YYYY-MM-DD hh:mm A`, East Asia (the marker before the time)
                "2026-11-10 21:15" to Order.YearMonthDay,
                "2026-11-10T21:15:00" to Order.YearMonthDay,
                "2026-11-10 09:15 PM" to Order.YearMonthDay,
                "2026/11/10 21:15" to Order.YearMonthDay,
                "2026. 11. 10. 오후 9:15" to Order.YearMonthDay,
                "2026/11/10 午後9:15" to Order.YearMonthDay,
                // no-break and narrow no-break spaces (what newer formatters put before AM / PM)
                "11-10-2026\u00A09:15\u202FPM" to Order.MonthDayYear,
            )
        for ((cell, order) in cases) assertEquals(cell, nineFifteenPm, read(cell, order))
    }

    @Test
    fun `midnight and noon on a 12-hour clock`() {
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 5), read("11-10-2026 12:05 AM", Order.MonthDayYear))
        assertEquals(LocalDateTime.of(2026, 11, 10, 12, 5), read("11-10-2026 12:05 PM", Order.MonthDayYear))
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 5), read("10-11-2026 0:05", Order.DayMonthYear))
    }

    @Test
    fun `what isn't a date and a time, or no such day or hour`() {
        for (cell in listOf("yesterday at one", "10-11-2026", "21:15", "", "10-11-2026 9:15 xyz", "2026-11-10T21:15:00Z")) {
            assertNull(cell, FileDates.split(cell))
        }
        assertNull(read("31-02-2026 10:00", Order.DayMonthYear))
        assertNull(read("10-11-2026 24:00", Order.DayMonthYear))
        assertNull(read("11-10-2026 13:15 PM", Order.MonthDayYear))
        assertNull(read("10-13-2026 10:00", Order.DayMonthYear))
        assertNull("a four-digit first number is a year", read("2026-11-10 10:00", Order.DayMonthYear))
    }

    private fun order(
        cells: List<String>,
        now: LocalDateTime? = null,
    ) = FileDates.orderFor(cells.map { FileDates.split(it)!! }, now)

    @Test
    fun `the file decides which number is the day`() {
        // a day above 12 settles it
        assertEquals(Order.MonthDayYear, order(listOf("11-09-2026 9:55 PM", "11-13-2026 7:30 AM")))
        assertEquals(Order.DayMonthYear, order(listOf("09-11-2026 21:55", "13-11-2026 07:30")))
        // none above 12: the order that keeps the rows in time order (oldest or newest first)
        val dayFirst = listOf("11-01-2026 08:00", "12-01-2026 08:00", "01-02-2026 08:00", "02-02-2026 08:00")
        assertEquals(Order.DayMonthYear, order(dayFirst))
        assertEquals(Order.DayMonthYear, order(dayFirst.reversed()))
        val monthFirst = listOf("01-01-2026 08:00", "01-02-2026 08:00", "02-01-2026 08:00", "02-02-2026 08:00")
        assertEquals(Order.MonthDayYear, order(monthFirst))
        // in order either way: the file was just downloaded, so its dates end near now and never ahead of it
        val both = listOf("11-09-2026 9:55 PM", "11-10-2026 1:05 PM")
        assertEquals(Order.MonthDayYear, order(both, LocalDateTime.of(2026, 11, 14, 15, 0)))
        assertEquals(Order.DayMonthYear, order(both, LocalDateTime.of(2026, 10, 12, 15, 0)))
        // nothing else to go by: day first
        assertEquals(Order.DayMonthYear, order(both))
        // no order fits every cell
        assertNull(order(listOf("13-11-2026 08:00", "11-13-2026 08:00")))
    }

    @Test
    fun `the hour a clock change repeats isn't folded onto the first pass`() {
        val zone = ZoneId.of("Asia/Nicosia")
        // 2026-10-25: 04:00 EEST goes back to 03:00 EET, so 03:00-03:59 happens twice
        val local = listOf("02:55", "03:25", "03:55", "03:25", "03:55", "04:10").map { LocalDateTime.parse("2026-10-25T$it") }
        val want =
            listOf("23:55", "00:25", "00:55", "01:25", "01:55", "02:10").mapIndexed { i, t ->
                Instant.parse("2026-10-${if (i == 0) 24 else 25}T$t:00Z")
            }
        var previous: Instant? = null
        val oldestFirst = local.map { FileDates.instant(it, zone, previous, true).also { at -> previous = at } }
        assertEquals(want, oldestFirst)
        previous = null
        val newestFirst = local.reversed().map { FileDates.instant(it, zone, previous, false).also { at -> previous = at } }
        assertEquals(want.reversed(), newestFirst)
        assertEquals(true, FileDates.oldestFirst(local))
        assertEquals(false, FileDates.oldestFirst(local.reversed()))
    }

    @Test
    fun `the layout without the numbers, for the log and a report`() {
        assertEquals("00-00-0000 0:00 PM", FileDates.shape("11-10-2026 9:15 PM"))
        assertEquals("00.00.0000 00:00 μ.μ.", FileDates.shape(" 10.11.2026 21:15 μ.μ. "))
    }
}
