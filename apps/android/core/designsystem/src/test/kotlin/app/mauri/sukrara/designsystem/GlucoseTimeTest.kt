// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem

import app.mauri.sukrara.core.model.storage.glucoseMinutes
import app.mauri.sukrara.designsystem.time.GlucoseTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * One wording for how much glucose there is: the delete-history dialog counted every reading (5,276) while the export
 * counted its 5-minute rows (225); both now say the glucose time, counted in 5-minute stretches.
 */
class GlucoseTimeTest {
    private val t0 = Instant.parse("2026-11-18T08:00:00Z")

    @Test fun aMinutesReadingsAndAFiveMinuteSourcesSayTheSame() {
        val everyMinute = (0 until 120).map { t0.plusSeconds(it * 60L) }
        val everyFive = (0 until 24).map { t0.plusSeconds(it * 300L + 17) }
        assertEquals(120L, glucoseMinutes(everyMinute.asSequence()))
        assertEquals(120L, glucoseMinutes(everyFive.asSequence()))
        // a gap counts nothing
        assertEquals(10L, glucoseMinutes(sequenceOf(t0, t0.plusSeconds(3600))))
    }

    @Test fun minutesThenHoursThenDays() {
        assertEquals(GlucoseTime.Unit.Minutes to 45L, GlucoseTime.of(45))
        assertEquals(GlucoseTime.Unit.Hours to 19L, GlucoseTime.of(18 * 60 + 45))
        assertEquals(GlucoseTime.Unit.Hours to 47L, GlucoseTime.of(47 * 60))
        assertEquals(GlucoseTime.Unit.Days to 4L, GlucoseTime.of(88 * 60))
    }
}
