// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.TrendSetting
import app.mauri.sukrara.core.testing.trended
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The treatment lists' rules (#207, docs/37): the glucose then, the LibreView mark shown, the days. */
class TreatmentListTest {
    private val t0 = Instant.parse("2026-01-15T12:00:00Z")

    private fun reading(
        minutes: Long,
        mgdl: Int = 120,
    ) = Reading(t0.plus(Duration.ofMinutes(minutes)), mgdl, null, SourceId.Libre3)

    @Test fun `the glucose then is the nearest reading within 5 minutes, the earlier one on a tie`() {
        val readings = listOf(reading(-10, 100), reading(-3, 110), reading(3, 130), reading(20, 150))
        assertEquals(110, GlucoseAt.of(t0, readings)?.mgdl)
        assertEquals(130, GlucoseAt.of(t0.plusSeconds(60), readings)?.mgdl)
        assertEquals(150, GlucoseAt.of(t0.plus(Duration.ofMinutes(25)), readings)?.mgdl)
        // 8.5 min from either reading, 6 min after the last: nothing says what it was then
        assertNull(GlucoseAt.of(t0.plus(Duration.ofMinutes(11) + Duration.ofSeconds(30)), readings))
        assertNull(GlucoseAt.of(t0.plus(Duration.ofMinutes(26)), readings))
        assertNull(GlucoseAt.of(t0, emptyList()))
    }

    @Test fun `the glucose then carries that reading's own trend`() {
        // rising 2 mg/dL a minute: the trend is the annotated reading's, nothing works one out here
        val readings = (0..30).map { reading(it - 30L, 100 + 2 * it) }.trended(TrendSetting.DEFAULT)
        val then = GlucoseAt.of(t0, readings)
        assertNotNull(then?.trend)
        assertEquals(readings.last(), then)
        assertEquals(readings.last().trend, then?.trend)
    }

    @Test fun `a row shows the LibreView mark only while the entry isn't there, or right after it got there`() {
        assertNull(UploadMark.Uploaded.shown(justUploaded = false))
        assertEquals(UploadMark.Uploaded, UploadMark.Uploaded.shown(justUploaded = true))
        for (m in listOf(UploadMark.Pending, UploadMark.Updating, UploadMark.Stopped)) {
            assertEquals(m, m.shown(justUploaded = false))
        }
        assertNull((null as UploadMark?).shown(justUploaded = true))
    }

    @Test fun `the Treatments page groups entries by the day they happened, newest first`() {
        val zone = ZoneId.of("Europe/Madrid")
        val late = Samples.treatment("late", minutesAgo = 11 * 60 + 30) // 00:30 UTC = 01:30 in Madrid
        val night = Samples.treatment("night", minutesAgo = 12 * 60 + 30) // 23:30 UTC the day before = 00:30 in Madrid
        val before = Samples.treatment("before", minutesAgo = 13 * 60 + 30) // 23:30 in Madrid the day before
        val noon = Samples.treatment("noon", minutesAgo = 5)
        val days = TreatmentDays.of(listOf(before, noon, night, late), zone)
        assertEquals(listOf("2026-01-15", "2026-01-14"), days.map { it.first.toString() })
        assertEquals(listOf("noon", "late", "night"), days[0].second.map { it.id })
        assertEquals(listOf("before"), days[1].second.map { it.id })
    }
}
