// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.sensor

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The start dialog's choices and checks: never a start ahead of now, never one that has already ended. */
class StartPickTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-11-18T08:30:00Z") // 09:30 in Madrid

    @Test fun `the days offered go back to the first one whose sensor could still be running`() {
        val days = StartPick.days(now, zone, 15 * 1440)
        assertEquals(LocalDate.of(2026, 11, 18), days.first())
        assertEquals(LocalDate.of(2026, 11, 3), days.last())
        assertEquals(16, days.size)
    }

    @Test fun `later than now is refused`() {
        assertEquals(StartPick.Check.Future, StartPick.check(LocalDate.of(2026, 11, 18), 10 * 60, now, zone, 15 * 1440))
    }

    @Test fun `a start that has already ended is refused, with its end`() {
        // 3 Nov 09:00 + 15 days = 18 Nov 09:00, half an hour ago
        val c = StartPick.check(LocalDate.of(2026, 11, 3), 9 * 60, now, zone, 15 * 1440)
        assertEquals(StartPick.Check.Ended(Instant.parse("2026-11-18T08:00:00Z")), c)
    }

    @Test fun `a running start says its end`() {
        val c = StartPick.check(LocalDate.of(2026, 11, 17), 21 * 60 + 15, now, zone, 10 * 1440)
        assertEquals(StartPick.Check.Ok(Instant.parse("2026-11-17T20:15:00Z"), Instant.parse("2026-11-27T20:15:00Z")), c)
    }
}
