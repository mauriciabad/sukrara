// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.designsystem.dialog.LocalGlucoseUnit
import java.util.Locale

/**
 * The whole mg/dL values a limit makes count, as a range or alert row says them under its number ("Low: 56–69 mg/dL",
 * "In range: 70–179 mg/dL"; the Mac's RangeSpans is the same). Every limit is the first value of the range it names,
 * inclusive (SPEC §1, contracts/graph/range/bands.json), so the number alone decides: nothing to choose.
 */
object RangeSpans {
    enum class Name { VERY_LOW, LOW, TIGHT, IN_RANGE, HIGH, VERY_HIGH, EXTREMELY_HIGH }

    /** One range: its name and its lowest and highest values (null = open-ended). */
    data class Span(
        val name: Name,
        val lo: Int?,
        val hi: Int?,
    )

    /**
     * The ranges the Ranges page's limit [index] sets ([limits] = very low, low, very good, high, very high, extremely
     * high, mg/dL): its own range, and in range for the low and high limits that bound it.
     */
    fun ofBand(
        limits: List<Double>,
        index: Int,
    ): List<Span> {
        val (vl, l, t, h, vh) = limits.map { it.toInt() }
        val eh = limits[5].toInt()
        val inRange = Span(Name.IN_RANGE, l + 1, h - 1)
        return when (index) {
            0 -> listOf(Span(Name.VERY_LOW, null, vl))
            1 -> listOf(Span(Name.LOW, vl + 1, l), inRange)
            2 -> listOf(Span(Name.TIGHT, l + 1, minOf(t, h - 1)))
            3 -> listOf(Span(Name.HIGH, h, vh - 1), inRange)
            4 -> listOf(Span(Name.VERY_HIGH, vh, eh - 1))
            else -> listOf(Span(Name.EXTREMELY_HIGH, eh, null))
        }
    }

    /**
     * The values [kind] alerts at under [s] (the kinds turned on bound each other; [kind] counts as on, it is the one
     * being set) and, for low and high, the values no level alert matches. Empty for a kind without a glucose range.
     */
    fun ofAlert(
        kind: AlertKind,
        s: AlertSettings,
    ): List<Span> {
        fun on(k: AlertKind) = k == kind || s.rule(k).on

        fun th(k: AlertKind) = s.rule(k).threshold.toInt()
        val lows = listOf(AlertKind.VERY_LOW, AlertKind.LOW).filter(::on)
        val highs = listOf(AlertKind.HIGH, AlertKind.VERY_HIGH, AlertKind.EXTREMELY_HIGH).filter(::on)
        val inRange = Span(Name.IN_RANGE, lows.maxOfOrNull(::th)?.plus(1), highs.minOfOrNull(::th)?.minus(1))

        // the next kind on the way out bounds this one: low above very low, high under very high …
        fun above(vararg ks: AlertKind) = ks.filter(::on).minOfOrNull(::th)?.minus(1)
        return when (kind) {
            AlertKind.VERY_LOW -> listOf(Span(Name.VERY_LOW, null, th(kind)))
            AlertKind.LOW -> listOf(Span(Name.LOW, AlertKind.VERY_LOW.takeIf(::on)?.let { th(it) + 1 }, th(kind)), inRange)
            AlertKind.HIGH -> listOf(Span(Name.HIGH, th(kind), above(AlertKind.VERY_HIGH, AlertKind.EXTREMELY_HIGH)), inRange)
            AlertKind.VERY_HIGH -> listOf(Span(Name.VERY_HIGH, th(kind), above(AlertKind.EXTREMELY_HIGH)))
            AlertKind.EXTREMELY_HIGH -> listOf(Span(Name.EXTREMELY_HIGH, th(kind), null))
            else -> emptyList()
        }
    }
}

/** The words a span is written with, resolved once so a dialog can write a typed value's spans as it changes. */
class SpanWords(
    private val names: Map<RangeSpans.Name, String>,
    private val between: String,
    private val one: String,
    private val upTo: String,
    private val from: String,
    private val unit: GlucoseUnit,
    private val locale: Locale,
) {
    /** One line per non-empty span: "Low: 56–69 mg/dL", "Very low: up to 55 mg/dL", "Extremely high: from 300 mg/dL". */
    fun text(spans: List<RangeSpans.Span>): String? =
        spans
            .mapNotNull { s ->
                val name = names.getValue(s.name)

                fun n(v: Int) = unit.text(v, locale)
                when {
                    s.lo == null && s.hi == null -> null
                    s.lo == null -> upTo.format(name, n(s.hi!!), unit.symbol)
                    s.hi == null -> from.format(name, n(s.lo), unit.symbol)
                    s.lo > s.hi -> null
                    s.lo == s.hi -> one.format(name, n(s.lo), unit.symbol)
                    else -> between.format(name, n(s.lo), n(s.hi), unit.symbol)
                }
            }.joinToString("\n")
            .ifEmpty { null }
}

@Composable
fun spanWords(): SpanWords =
    SpanWords(
        mapOf(
            RangeSpans.Name.VERY_LOW to stringResource(R.string.st_kind_very_low),
            RangeSpans.Name.LOW to stringResource(R.string.st_kind_low),
            RangeSpans.Name.TIGHT to stringResource(R.string.st_span_tight),
            RangeSpans.Name.IN_RANGE to stringResource(R.string.st_span_in_range),
            RangeSpans.Name.HIGH to stringResource(R.string.st_kind_high),
            RangeSpans.Name.VERY_HIGH to stringResource(R.string.st_kind_very_high),
            RangeSpans.Name.EXTREMELY_HIGH to stringResource(R.string.st_kind_extremely_high),
        ),
        stringResource(R.string.st_span_between),
        stringResource(R.string.st_span_one),
        stringResource(R.string.st_span_up_to),
        stringResource(R.string.st_span_from),
        LocalGlucoseUnit.current,
        LocalConfiguration.current.locales[0],
    )

/** A row's value with the spans it sets under it: "69 mg/dL\nLow: 56–69 mg/dL\nIn range: 70–179 mg/dL". */
fun withSpans(
    value: String,
    spans: String?,
): String = if (spans == null) value else "$value\n$spans"
