// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.graph

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.dialog.LocalGlucoseUnit
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.rows.CardRow
import app.mauri.sukrara.designsystem.rows.DangerRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.MainSwitchRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.RadioRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SplitSwitchRow
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SeeAlso
import app.mauri.sukrara.feature.settings.SeeAlsoLink
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.feature.settings.search.Section
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPalette
import app.mauri.sukrara.graph.GraphSettings
import app.mauri.sukrara.graph.GraphText
import app.mauri.sukrara.graph.LastPlace
import app.mauri.sukrara.graph.RingSpec
import app.mauri.sukrara.graph.SlotKind
import app.mauri.sukrara.graph.stats.Band
import app.mauri.sukrara.graph.stats.Goal
import app.mauri.sukrara.graph.stats.TirRange
import app.mauri.sukrara.core.designsystem.R as DsR

/*
 * Settings › Metrics (docs/39): what sits beside the value on every card. The page lists the things beside the value
 * in the card's order of importance, each a two-target row (the switch shows or hides it, the row opens its page: the
 * stock pattern of a feature with its own options, as each alert), then the time-in-range rings' look on a page of
 * its own. Every page has the live card on top.
 */

/** The metrics with a page of their own: the main, second and third, and the one shown while there's no recent treatment. */
enum class MetricSlot(
    @param:StringRes val title: Int,
    @param:StringRes val show: Int,
    @param:StringRes val footer: Int,
) {
    Main(R.string.st_metric_main, R.string.st_metric_show_main, R.string.st_metric_main_footer),
    Second(R.string.st_metric_second, R.string.st_metric_show_second, R.string.st_metric_other_footer),
    Third(R.string.st_metric_third, R.string.st_metric_show_third, R.string.st_metric_other_footer),
    NoRecent(R.string.st_last_else, R.string.st_last_else_show, R.string.st_last_else_footer),
    ;

    /** This metric's settings in [c]. */
    fun of(c: GraphConfig): RingSpec = if (this == NoRecent) c.lastElse else c.slots[ordinal]

    /** [c]'s metrics with this one replaced by [spec], as [GraphActions] stores them. */
    fun write(
        actions: GraphActions,
        c: GraphConfig,
        spec: RingSpec,
    ) = if (this == NoRecent) actions.lastElse(spec) else actions.slots(c.slots.toMutableList().also { it[ordinal] = spec })

    /** The main metric is never dimmed: it's the one at full strength. */
    val dims: Boolean get() = this != Main

    companion object {
        val SLOTS = listOf(Main, Second, Third)
    }
}

/** [this] showing [kind]: a period that kind can't have becomes its default (a GMI needs a week at least). */
fun RingSpec.withKind(kind: SlotKind): RingSpec {
    val spec = GraphText.windowSpec(kind)
    return copy(kind = kind, windowS = if (spec.valid(windowS.toDouble())) windowS else spec.default.toLong())
}

@DrawableRes
fun metricIcon(kind: SlotKind): Int = if (kind == SlotKind.TIR) DsR.drawable.ds_ic_donut_large else DsR.drawable.ds_ic_speed

@Composable
fun metricKindName(kind: SlotKind): String =
    stringResource(
        when (kind) {
            SlotKind.TIR -> R.string.st_metric_kind_tir
            SlotKind.GMI -> R.string.st_metric_kind_gmi
            SlotKind.AVERAGE -> R.string.st_metric_kind_average
            SlotKind.CV -> R.string.st_metric_kind_cv
        },
    )

/** One line on what a kind is, with its goal (the gauge's tick): "Estimated A1c · goal under 7 %". */
@Composable
private fun metricKindSummary(kind: SlotKind): String =
    when (kind) {
        SlotKind.TIR -> {
            stringResource(R.string.st_metric_kind_tir_summary)
        }

        SlotKind.GMI -> {
            stringResource(R.string.st_metric_kind_gmi_summary)
        }

        SlotKind.AVERAGE -> {
            val unit = LocalGlucoseUnit.current
            val goal = "${unit.text(Goal.AVERAGE_MGDL, LocalConfiguration.current.locales[0])} ${unit.symbol}"
            stringResource(R.string.st_metric_kind_average_summary, goal)
        }

        SlotKind.CV -> {
            stringResource(R.string.st_metric_kind_cv_summary)
        }
    }

/** "Time in range · 1 day", or "Off". */
@Composable
private fun metricSummary(spec: RingSpec): String =
    if (spec.on) {
        stringResource(R.string.st_metric_summary, metricKindName(spec.kind), periodLabeler()(spec.windowS.toDouble()))
    } else {
        stringResource(R.string.st_off)
    }

@Composable
private fun lastWhereName(p: LastPlace): String = stringResource(if (p == LastPlace.END) R.string.st_last_end else R.string.st_last_near)

/** "70–179 mg/dL": the values a time-in-range ring's number counts. */
@Composable
private fun tirSpan(
    r: TirRange,
    c: GraphConfig,
): String {
    val unit = LocalGlucoseUnit.current
    val locale = LocalConfiguration.current.locales[0]
    val top = if (r == TirRange.TIGHT) c.bands.tight else c.bands.high - 1
    return stringResource(R.string.st_tir_span, unit.text(c.bands.low, locale), unit.text(top, locale), unit.symbol)
}

@Composable
private fun tirName(r: TirRange): String = stringResource(if (r == TirRange.TIGHT) R.string.st_tir_tight else R.string.st_tir_in_range)

/**
 * Settings › Metrics: the live card on top; the main, second and third metric; the last treatment and what shows in its
 * place while there's none (greyed while the last treatment is off); the time-in-range rings' page; Reset metrics.
 * [readOnly] = a follower not in Custom (*Settings from*): every switch shows the value in use and can't be flipped;
 * the pages open and show it.
 */
@Composable
fun MetricsContent(
    c: GraphConfig,
    actions: GraphActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    preview: @Composable () -> Unit = {},
) {
    SettingsPage(stringResource(R.string.st_metrics_title), onBack, modifier) {
        PreviewCard(preview)
        SettingsGroup {
            for (slot in MetricSlot.SLOTS) {
                val spec = slot.of(c)
                SplitSwitchRow(
                    stringResource(slot.title),
                    spec.on,
                    { on -> slot.write(actions, c, spec.copy(on = on)) },
                    { actions.openMetric(slot) },
                    summary = metricSummary(spec),
                    icon = metricIcon(spec.kind),
                    switchEnabled = !readOnly,
                )
            }
        }
        SettingsGroup {
            SplitSwitchRow(
                stringResource(R.string.st_last),
                c.lastOn,
                actions.lastOn,
                actions.openLast,
                summary = if (c.lastOn) lastWhereName(c.lastWhere) else stringResource(R.string.st_off),
                icon = DsR.drawable.ds_ic_history,
                switchEnabled = !readOnly,
            )
            // shown in the last treatment's place: nothing to show while that place is off (Google's dependency pattern)
            val other = c.lastElse
            SplitSwitchRow(
                stringResource(R.string.st_last_else),
                other.on,
                { on -> actions.lastElse(other.copy(on = on)) },
                { actions.openMetric(MetricSlot.NoRecent) },
                summary = if (c.lastOn) metricSummary(other) else stringResource(R.string.st_last_else_needs),
                icon = metricIcon(other.kind),
                enabled = c.lastOn,
                switchEnabled = c.lastOn && !readOnly,
            )
        }
        SettingsGroup {
            NavigationRow(
                stringResource(R.string.st_tir_title),
                actions.openRings,
                summary =
                    stringResource(
                        if (c.ringMarks) R.string.st_tir_summary_rewards else R.string.st_tir_summary,
                        tirName(c.ringRange),
                        tirSpan(c.ringRange, c),
                    ),
                icon = DsR.drawable.ds_ic_palette,
            )
        }
        if (GraphSection.Metrics.changed(c) && !readOnly) {
            SettingsGroup {
                DangerRow(
                    stringResource(R.string.st_metrics_reset_all),
                    stringResource(R.string.st_metrics_reset_all_title),
                    stringResource(R.string.st_metrics_reset_all_text),
                    stringResource(R.string.st_reset),
                    { actions.reset(GraphSection.Metrics) },
                    icon = DsR.drawable.ds_ic_restart_alt,
                )
            }
        }
        FooterText(stringResource(R.string.st_metrics_footer))
    }
}

/**
 * A metric's page (Metrics › Main metric, …, No recent treatment): the live card; its main switch; what it shows, one
 * tap each, with what each is and its goal; its period (the kind's own choices: a GMI from a week); dimmed (not the main
 * one). Everything under the switch is greyed while it's off. The no-recent-treatment metric's page is greyed whole
 * while the last treatment is off, and says so.
 */
@Composable
fun MetricContent(
    slot: MetricSlot,
    c: GraphConfig,
    actions: GraphActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    preview: @Composable () -> Unit = {},
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val spec = slot.of(c)
    val period = periodLabeler()
    val placeOff = slot == MetricSlot.NoRecent && !c.lastOn
    val set: (RingSpec) -> Unit = { slot.write(actions, c, it) }
    val editable = spec.on && !placeOff && !readOnly
    SettingsPage(stringResource(slot.title), onBack, modifier) {
        PreviewCard(preview)
        MainSwitchRow(
            stringResource(slot.show),
            spec.on,
            { on -> set(spec.copy(on = on)) },
            enabled = !placeOff,
            readOnly = readOnly,
            note = if (placeOff) stringResource(R.string.st_last_else_needs) else null,
        )
        SettingsGroup(Modifier.selectableGroup(), title = stringResource(R.string.st_metric_shows)) {
            for (kind in SlotKind.entries) {
                RadioRow(
                    metricKindName(kind),
                    spec.kind == kind,
                    { set(spec.withKind(kind)) },
                    summary = metricKindSummary(kind),
                    enabled = editable,
                    icon = metricIcon(kind),
                )
            }
        }
        SettingsGroup {
            ValueRow(
                stringResource(R.string.st_metric_period),
                period(spec.windowS.toDouble()),
                { choosing = true },
                icon = DsR.drawable.ds_ic_schedule,
                enabled = spec.on && !placeOff,
                readOnly = readOnly,
            )
            if (slot.dims) {
                SwitchRow(
                    stringResource(R.string.st_metric_dimmed),
                    spec.faded,
                    { on -> set(spec.copy(faded = on)) },
                    summary = stringResource(R.string.st_metric_dimmed_summary),
                    icon = DsR.drawable.ds_ic_opacity,
                    enabled = spec.on && !placeOff,
                    readOnly = readOnly,
                )
            }
        }
        FooterText(stringResource(slot.footer))
    }
    if (choosing) {
        NumberDialog(
            stringResource(R.string.st_metric_period),
            NumberInput(GraphText.windowSpec(spec.kind)),
            spec.windowS.toDouble(),
            period,
            { choosing = false },
        ) { w -> set(spec.copy(windowS = w.toLong())) }
    }
}

/**
 * Metrics › Last treatment: the live card; its main switch; where it sits (two options on the page: next to the value or
 * at the right end, greyed while it's off); what it shows and for how long.
 */
@Composable
fun LastTreatmentContent(
    c: GraphConfig,
    actions: GraphActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    preview: @Composable () -> Unit = {},
) {
    SettingsPage(stringResource(R.string.st_last), onBack, modifier) {
        PreviewCard(preview)
        MainSwitchRow(stringResource(R.string.st_last_show), c.lastOn, actions.lastOn, readOnly = readOnly)
        SettingsGroup(Modifier.selectableGroup(), title = stringResource(R.string.st_last_where)) {
            for (p in listOf(LastPlace.NEAR_VALUE, LastPlace.END)) {
                RadioRow(lastWhereName(p), c.lastWhere == p, { actions.lastWhere(p) }, enabled = c.lastOn && !readOnly)
            }
        }
        FooterText(stringResource(R.string.st_last_footer))
    }
}

/**
 * Metrics › Time-in-range rings: how every time-in-range ring looks (the metrics and the one shown without a recent
 * treatment): the live card; what its number counts (in range or very good, with the values each counts); the rewards;
 * the colour order (drag; its own Reset); *See also* Readings, where the ranges are set.
 */
@Composable
fun TirRingsContent(
    c: GraphConfig,
    actions: GraphActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    preview: @Composable () -> Unit = {},
) {
    SettingsPage(stringResource(R.string.st_tir_title), onBack, modifier) {
        PreviewCard(preview)
        SettingsGroup(Modifier.selectableGroup(), title = stringResource(R.string.st_tir_number)) {
            for (r in TirRange.entries) {
                RadioRow(tirName(r), c.ringRange == r, { actions.ringRange(r) }, summary = tirSpan(r, c), enabled = !readOnly)
            }
        }
        SettingsGroup {
            SwitchRow(
                stringResource(R.string.st_tir_rewards),
                c.ringMarks,
                { actions.bool(GraphSettings.RingMarks, it) },
                summary = stringResource(R.string.st_tir_rewards_summary),
                icon = DsR.drawable.ds_ic_crown,
                readOnly = readOnly,
            )
        }
        val orderTitle = stringResource(R.string.st_tir_order)
        SettingsGroup(
            title = orderTitle,
            titleAction = { if (!readOnly) ResetIcon(GraphSection.RingOrder, c, orderTitle, actions.reset) },
        ) {
            CardRow { RingOrderList(c, actions.ringOrder, !readOnly) }
        }
        if (!readOnly) FooterText(stringResource(R.string.st_tir_order_footer))
        SeeAlso(
            listOf(
                SeeAlsoLink(
                    stringResource(R.string.st_readings_title),
                    stringResource(R.string.st_overview_readings),
                    Section.Readings,
                    actions.openReadings,
                ),
            ),
        )
    }
}

/** The ring's segments clockwise from 12 o'clock, each with its colour and range. */
@Composable
private fun RingOrderList(
    c: GraphConfig,
    onOrder: (List<Band>) -> Unit,
    editable: Boolean,
) {
    val palette = GraphPalette.of(isSystemInDarkTheme())
    val row: @Composable (Band) -> Unit = { band ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = Color(palette.of(band))
            Canvas(Modifier.padding(end = 12.dp).size(14.dp)) { drawCircle(color) }
            Text(bandName(band, c), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (editable) {
        ReorderList(c.ringOrder, onOrder, rowContent = row)
    } else {
        // the order in use, nothing to drag
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { c.ringOrder.forEach { row(it) } }
    }
}
