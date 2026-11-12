// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.feature.settings.RangeSpans.Name
import app.mauri.sukrara.feature.settings.RangeSpans.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** The values a limit makes count, written under it (the Mac's RangeSpansTests are the same cases). */
class RangeSpansTest {
    private val defaults = SchemaPrefs.Bands.map { it.default }

    private fun words(unit: GlucoseUnit = GlucoseUnit.MGDL) =
        SpanWords(
            Name.entries.associateWith { it.name.lowercase() },
            "%1\$s: %2\$s–%3\$s %4\$s",
            "%1\$s: %2\$s %3\$s",
            "%1\$s: up to %2\$s %3\$s",
            "%1\$s: from %2\$s %3\$s",
            unit,
            Locale.ENGLISH,
        )

    @Test fun theRangesAtTheirDefaults() {
        assertEquals(listOf(Span(Name.VERY_LOW, null, 55)), RangeSpans.ofBand(defaults, 0))
        assertEquals(listOf(Span(Name.LOW, 56, 69), Span(Name.IN_RANGE, 70, 179)), RangeSpans.ofBand(defaults, 1))
        assertEquals(listOf(Span(Name.TIGHT, 70, 140)), RangeSpans.ofBand(defaults, 2))
        assertEquals(listOf(Span(Name.HIGH, 180, 249), Span(Name.IN_RANGE, 70, 179)), RangeSpans.ofBand(defaults, 3))
        assertEquals(listOf(Span(Name.VERY_HIGH, 250, 299)), RangeSpans.ofBand(defaults, 4))
        assertEquals(listOf(Span(Name.EXTREMELY_HIGH, 300, null)), RangeSpans.ofBand(defaults, 5))
        assertEquals("low: 56–69 mg/dL\nin_range: 70–179 mg/dL", words().text(RangeSpans.ofBand(defaults, 1)))
        assertEquals("very_low: up to 55 mg/dL", words().text(RangeSpans.ofBand(defaults, 0)))
        assertEquals("extremely_high: from 300 mg/dL", words().text(RangeSpans.ofBand(defaults, 5)))
    }

    @Test fun theAlertsAtTheirDefaults() {
        val s = AlertSettings()
        assertEquals(listOf(Span(Name.VERY_LOW, null, 55)), RangeSpans.ofAlert(AlertKind.VERY_LOW, s))
        assertEquals(listOf(Span(Name.LOW, 56, 69), Span(Name.IN_RANGE, 70, 179)), RangeSpans.ofAlert(AlertKind.LOW, s))
        assertEquals(listOf(Span(Name.HIGH, 180, 249), Span(Name.IN_RANGE, 70, 179)), RangeSpans.ofAlert(AlertKind.HIGH, s))
        assertEquals(listOf(Span(Name.VERY_HIGH, 250, 299)), RangeSpans.ofAlert(AlertKind.VERY_HIGH, s))
        assertEquals(listOf(Span(Name.EXTREMELY_HIGH, 300, null)), RangeSpans.ofAlert(AlertKind.EXTREMELY_HIGH, s))
        assertEquals(emptyList<Span>(), RangeSpans.ofAlert(AlertKind.NO_DATA, s))
    }

    /** A kind turned off no longer bounds its neighbours: with very high off, high reaches extremely high. */
    @Test fun onlyTheKindsTurnedOnBoundEachOther() {
        val s =
            AlertSettings()
                .withRule(AlertKind.VERY_HIGH, AlertSettings().rule(AlertKind.VERY_HIGH).copy(on = false))
                .withRule(AlertKind.VERY_LOW, AlertSettings().rule(AlertKind.VERY_LOW).copy(on = false))
        assertEquals(Span(Name.HIGH, 180, 299), RangeSpans.ofAlert(AlertKind.HIGH, s).first())
        assertEquals(Span(Name.LOW, null, 69), RangeSpans.ofAlert(AlertKind.LOW, s).first())
        // the kind being set counts as on: its own row shows what it would do
        assertEquals(Span(Name.VERY_HIGH, 250, 299), RangeSpans.ofAlert(AlertKind.VERY_HIGH, s).first())
    }

    @Test fun writtenInTheUnitOfTheScreen() {
        assertEquals("low: 3.1–3.8 mmol/L\nin_range: 3.9–9.9 mmol/L", words(GlucoseUnit.MMOL).text(RangeSpans.ofBand(defaults, 1)))
        // an empty range says nothing, a single value once
        assertNull(words().text(listOf(Span(Name.TIGHT, 181, 179))))
        assertEquals("low: 69 mg/dL", words().text(listOf(Span(Name.LOW, 69, 69))))
    }
}
