// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.graph

import android.icu.text.ListFormatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.ChoiceDialog
import app.mauri.sukrara.designsystem.dialog.LocalGlucoseUnit
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.rows.CardRow
import app.mauri.sukrara.designsystem.rows.DangerRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.MainSwitchRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.ONE_LINE_SEPARATOR
import app.mauri.sukrara.designsystem.rows.OnOffPlaceDialog
import app.mauri.sukrara.designsystem.rows.PlaceSummary
import app.mauri.sukrara.designsystem.rows.PlaceValue
import app.mauri.sukrara.designsystem.rows.PlaceWords
import app.mauri.sukrara.designsystem.rows.PlacesRow
import app.mauri.sukrara.designsystem.rows.PlacesSwitchRow
import app.mauri.sukrara.designsystem.rows.RadioRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.designsystem.rows.onOffText
import app.mauri.sukrara.designsystem.rows.placeSummary
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SeeAlso
import app.mauri.sukrara.feature.settings.SeeAlsoLink
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.feature.settings.glucoseLabeler
import app.mauri.sukrara.feature.settings.numberLabeler
import app.mauri.sukrara.feature.settings.search.Section
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPalette
import app.mauri.sukrara.graph.GraphPlace
import app.mauri.sukrara.graph.GraphSettings
import app.mauri.sukrara.graph.LastPlace
import app.mauri.sukrara.graph.MarkShow
import app.mauri.sukrara.graph.RingSpec
import app.mauri.sukrara.graph.SlotKind
import app.mauri.sukrara.graph.stats.Band
import app.mauri.sukrara.graph.stats.TirRange
import app.mauri.sukrara.settings.NumberSpec
import app.mauri.sukrara.settings.SettingsSpec
import app.mauri.sukrara.core.designsystem.R as DsR

/** What the Graph pages can do (the ViewModel's edits; no-ops in screenshots). */
class GraphActions(
    val number: (app.mauri.sukrara.core.data.settings.Setting<Double>, Double) -> Unit = { _, _ -> },
    val bool: (app.mauri.sukrara.core.data.settings.Setting<Boolean>, Boolean) -> Unit = { _, _ -> },
    val lines: (List<Int>) -> Unit = {},
    /** The main, second and third metric (each on or off, its kind, period and dimmed). */
    val slots: (List<RingSpec>) -> Unit = {},
    val lastOn: (Boolean) -> Unit = {},
    val lastWhere: (LastPlace) -> Unit = {},
    /** The metric shown in the last treatment's place while there's no recent treatment. */
    val lastElse: (RingSpec) -> Unit = {},
    val ringRange: (TirRange) -> Unit = {},
    val ringOrder: (List<Band>) -> Unit = {},
    val marks: (MarkShow) -> Unit = {},
    /** A place's own time shown (0 = Same as the app). */
    val placeHours: (GraphPlace, Double) -> Unit = { _, _ -> },
    /** The sensor's time left: the app's, and a place's own (null = Same as the app). */
    val sensorTime: (Boolean) -> Unit = {},
    val sensorPlace: (GraphPlace, Boolean?) -> Unit = { _, _ -> },
    /** A setting's page with its places (docs/38): Time shown, Sensor time left. */
    val openPlaces: (PlaceSetting) -> Unit = {},
    val reset: (GraphSection) -> Unit = {},
    val openMarks: () -> Unit = {},
    /** Settings › Readings (the ranges: Graph's *Ranges* row, Time-in-range rings' *See also*). */
    val openReadings: () -> Unit = {},
    /** Metrics' pages: a metric's, the last treatment's, the time-in-range rings'. */
    val openMetric: (MetricSlot) -> Unit = {},
    val openLast: () -> Unit = {},
    val openRings: () -> Unit = {},
)

/** The page's open dialog (one at a time), saved across rotation. */
private sealed interface GraphDialog : java.io.Serializable {
    data object AddLine : GraphDialog

    data object Grid : GraphDialog

    data object AppHours : GraphDialog

    data class PlaceHours(val place: GraphPlace) : GraphDialog

    data class SensorPlace(val place: GraphPlace) : GraphDialog
}

/** A section's Reset icon beside its title, shown only while the section differs from the defaults. */
@Composable
fun ResetIcon(
    section: GraphSection,
    config: GraphConfig,
    title: String,
    onReset: (GraphSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!section.changed(config)) return
    IconButton(onClick = { onReset(section) }, modifier) {
        Icon(painterResource(DsR.drawable.ds_ic_restart_alt), contentDescription = stringResource(R.string.st_reset_section, title))
    }
}

/** The live card on top of a graph page: the app's own panel, drawn by the app (empty in tests without one). */
@Composable
fun PreviewCard(
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp)) { preview() }
}

/** A metric's period in words: "12 h", "1 day", "7 days". */
@Composable
fun periodLabeler(): (Double) -> String {
    val h = stringResource(DsR.string.ds_unit_h)
    val res = LocalResources.current
    return { s ->
        val sec = s.toLong()
        val days = (sec / 86_400L).toInt()
        if (sec % 86_400L == 0L) res.getQuantityString(DsR.plurals.ds_days, days, days) else "${sec / 3_600L} $h"
    }
}

@Composable
fun placeName(p: GraphPlace): String =
    stringResource(
        when (p) {
            GraphPlace.APP -> R.string.st_place_app
            GraphPlace.NOTIFICATION -> R.string.st_place_notification
            GraphPlace.WIDGETS -> R.string.st_place_widgets
        },
    )

/** Where a value applies, as a summary line says it: "in the app", "in the notification", "on widgets". */
@Composable
private fun placeWhere(p: GraphPlace): String =
    stringResource(
        when (p) {
            GraphPlace.APP -> R.string.st_where_app
            GraphPlace.NOTIFICATION -> R.string.st_where_notification
            GraphPlace.WIDGETS -> R.string.st_where_widgets
        },
    )

/** Where a per-place setting applies: the app, the notification, the widgets. */
@DrawableRes
fun placeIcon(p: GraphPlace): Int =
    when (p) {
        GraphPlace.APP -> DsR.drawable.ds_ic_smartphone
        GraphPlace.NOTIFICATION -> DsR.drawable.ds_ic_notifications
        GraphPlace.WIDGETS -> DsR.drawable.ds_ic_widgets
    }

/** How the places' summaries say things in this locale (the platform's list formatter joins places). */
@Composable
private fun placeWords(separator: String = ONE_LINE_SEPARATOR): PlaceWords {
    val locale = LocalConfiguration.current.locales[0]
    val valueWhere = stringResource(R.string.st_value_where)
    return PlaceWords(
        app = stringResource(R.string.st_where_app),
        join = { ListFormatter.getInstance(locale).format(it) },
        valueWhere = { v, w -> String.format(locale, valueWhere, v, w) },
        separator = separator,
    )
}

/** Time shown's summary (#209): the app's value and only the places that differ ([spoken]: hours in full, for TalkBack). */
@Composable
private fun hoursSummary(
    c: GraphConfig,
    spoken: Boolean,
): PlaceSummary {
    val label = numberLabeler()
    val res = LocalResources.current
    val text: (Int) -> String =
        if (spoken) {
            { h -> res.getQuantityString(R.plurals.st_hours_spoken, h, h) }
        } else {
            { h -> label(SettingsSpec.GRAPH_HOURS, h.toDouble()) }
        }
    val places = GraphPlace.OWN.map { PlaceValue(placeWhere(it), c.placeHours[it]) }
    return placeSummary(c.appHours, places, switchRow = false, text = text, words = placeWords(if (spoken) ", " else ONE_LINE_SEPARATOR))
}

/** Sensor time left's summary: its switch shows the app's value, so only the places that differ ("Off on widgets"). */
@Composable
private fun sensorSummary(
    app: Boolean,
    own: Map<GraphPlace, Boolean>,
): PlaceSummary {
    val on = onOffText(true)
    val off = onOffText(false)
    val places = GraphPlace.OWN.map { PlaceValue(placeWhere(it), own[it]) }
    return placeSummary(app, places, switchRow = true, text = { if (it) on else off }, words = placeWords())
}

/** A range's name with its span, a limit in the range beyond it ("High · 180–249"), as the ring-order list says it. */
@Composable
fun bandName(
    band: Band,
    c: GraphConfig,
): String {
    val b = c.bands
    return when (band) {
        Band.VERY_LOW -> stringResource(R.string.st_ring_very_low, b.veryLow)
        Band.LOW -> b.span(band).let { (lo, hi) -> stringResource(R.string.st_ring_low, lo!!, hi!!) }
        Band.TIGHT -> b.span(band).let { (lo, hi) -> stringResource(R.string.st_ring_tight, lo!!, hi!!) }
        Band.TARGET -> b.span(band).let { (lo, hi) -> stringResource(R.string.st_ring_target, lo!!, hi!!) }
        Band.HIGH -> b.span(band).let { (lo, hi) -> stringResource(R.string.st_ring_high, lo!!, hi!!) }
        Band.VERY_HIGH -> stringResource(R.string.st_ring_very_high, b.veryHigh)
    }
}

/**
 * Settings › Graph (the chart only, docs/35): the live card on top, a *Ranges* row that opens them in Readings (they
 * colour the graph; docs/35 §7), then lines across the graph, time shown (the app's value and the places that differ,
 * docs/38), beside the graph, treatments on the graph, the sensor's time left (its home is Sensor end: the same row)
 * and Reset all. Every titled section has its own Reset while it differs from the defaults. [readOnly] = a follower not
 * in Custom (*Settings from*): the mirrored sections show the values in use and can't be changed (no Reset, no Reset
 * all); the places of time shown and sensor time stay this phone's own and editable.
 */
@Composable
fun GraphContent(
    c: GraphConfig,
    actions: GraphActions,
    onBack: () -> Unit,
    readOnly: Boolean = false,
    preview: @Composable () -> Unit = {},
) {
    var dialog by rememberSaveable { mutableStateOf<GraphDialog?>(null) }
    // a mirrored section's Reset only where this phone's own values are in use
    val resetIcon: @Composable (GraphSection, String) -> Unit = { section, title ->
        if (!readOnly) ResetIcon(section, c, title, actions.reset)
    }
    val label = numberLabeler()
    val glucose = glucoseLabeler()
    SettingsPage(stringResource(R.string.st_graph_title), onBack) {
        PreviewCard(preview)
        SettingsGroup {
            NavigationRow(
                stringResource(R.string.st_ranges_title),
                actions.openReadings,
                summary = rangesSummary(c),
                icon = DsR.drawable.ds_ic_dia_range,
            )
        }

        val linesTitle = stringResource(R.string.st_graph_lines)
        SettingsGroup(title = linesTitle, titleAction = { resetIcon(GraphSection.Lines, linesTitle) }) {
            if (c.lines.isNotEmpty()) {
                CardRow { LineChips(c.lines, glucose, !readOnly) { actions.lines(c.lines - it) } }
            }
            val max = SettingsSpec.LINE.maxItems
            ValueRow(
                stringResource(R.string.st_graph_line_add),
                stringResource(if (c.lines.size < max) R.string.st_graph_line_add_summary else R.string.st_graph_line_full, max),
                { dialog = GraphDialog.AddLine },
                icon = DsR.drawable.ds_ic_add,
                enabled = c.lines.size < max,
                readOnly = readOnly,
            )
            ValueRow(stringResource(R.string.st_graph_grid), label(SettingsSpec.GRID_HOURS, c.gridHours.toDouble()), {
                dialog = GraphDialog.Grid
            }, icon = DsR.drawable.ds_ic_bar_chart, readOnly = readOnly)
        }

        // the app's value and only the places that differ (docs/38); its page has the app's values and the places, so it
        // opens for a follower too (the places are this phone's own)
        SettingsGroup {
            PlacesRow(
                stringResource(R.string.st_graph_hours),
                hoursSummary(c, spoken = false),
                hoursSummary(c, spoken = true),
                { actions.openPlaces(PlaceSetting.Hours) },
                icon = DsR.drawable.ds_ic_schedule,
            )
        }

        val besideGraphTitle = stringResource(R.string.st_graph_beside_graph)
        SettingsGroup(
            title = besideGraphTitle,
            titleAction = { resetIcon(GraphSection.BesideGraph, besideGraphTitle) },
        ) {
            SwitchRow(
                stringResource(R.string.st_graph_side),
                c.side,
                { actions.bool(GraphSettings.Side, it) },
                summary = stringResource(R.string.st_graph_side_summary),
                icon = DsR.drawable.ds_ic_align_horizontal_left,
                readOnly = readOnly,
            )
            SwitchRow(
                stringResource(R.string.st_graph_crown),
                c.columnCrown,
                { actions.bool(GraphSettings.ColumnCrown, it) },
                enabled = c.side,
                icon = DsR.drawable.ds_ic_crown,
                readOnly = readOnly,
            )
        }

        SettingsGroup {
            NavigationRow(
                stringResource(R.string.st_marks_title),
                actions.openMarks,
                summary = marksSummary(c.marks),
                icon = DsR.drawable.ds_ic_edit_note,
            )
        }

        SensorTimeGroup(c, actions)

        if (GraphSection.All.changed(c) && !readOnly) {
            SettingsGroup {
                DangerRow(
                    stringResource(R.string.st_graph_reset_all),
                    stringResource(R.string.st_graph_reset_all_title),
                    stringResource(R.string.st_graph_reset_all_text),
                    stringResource(R.string.st_reset),
                    { actions.reset(GraphSection.All) },
                    icon = DsR.drawable.ds_ic_restart_alt,
                )
            }
        }
        FooterText(stringResource(R.string.st_graph_footer))
    }
    GraphDialogs(dialog, c, actions, label) { dialog = null }
}

/**
 * The sensor's time left (`graph.sensorTime` and its places, each phone's own): its home is Settings › Sensor end, and
 * Graph shows the same row (docs/35 §7: one setting, two places, never a copy), in a group of its own.
 */
@Composable
fun SensorTimeGroup(
    c: GraphConfig,
    actions: GraphActions,
) {
    SettingsGroup {
        SensorTimeRow(c.appSensorTime, c.sensorPlace, actions.sensorTime) { actions.openPlaces(PlaceSetting.SensorTime) }
    }
}

/**
 * The sensor time's row, on Graph and on Sensor end (docs/38): the switch is the app's value, the summary names only the
 * places that differ ("Off on widgets"), the text opens its page with the places.
 */
@Composable
fun SensorTimeRow(
    app: Boolean,
    own: Map<GraphPlace, Boolean>,
    setApp: (Boolean) -> Unit,
    openPlaces: () -> Unit,
) {
    PlacesSwitchRow(
        stringResource(R.string.st_graph_sensor),
        app,
        setApp,
        sensorSummary(app, own),
        openPlaces,
        icon = DsR.drawable.ds_ic_hourglass_top,
    )
}

/** The settings the app sets that the notification and the widgets can change (docs/38): each has a page with them. */
enum class PlaceSetting { Hours, SensorTime }

/** The places' group of a setting's page: each place's own value or *Same as the app*, and where else to change it. */
@Composable
private fun PlacesGroup(
    modifier: Modifier = Modifier,
    row: @Composable (GraphPlace) -> Unit,
) {
    SettingsGroup(modifier, title = stringResource(R.string.st_places_group)) {
        for (p in GraphPlace.OWN) row(p)
    }
    FooterText(stringResource(R.string.st_places_footer))
}

/**
 * Settings › Graph › Time shown (docs/38): the live card, the app's time shown as one-tap choices (*Custom…* types one;
 * a custom value is the selected Custom with the value under it), then the notification and the widgets, each its own
 * value or *Same as the app* (the app's value is the selected choice above). [readOnly] = a follower not in Custom: the
 * app's value is the wearer's; the places are this phone's own and stay editable.
 */
@Composable
fun TimeShownContent(
    c: GraphConfig,
    actions: GraphActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    preview: @Composable () -> Unit = {},
) {
    var dialog by rememberSaveable { mutableStateOf<GraphDialog?>(null) }
    val label = numberLabeler()
    val same = stringResource(R.string.st_same_as_app)
    SettingsPage(stringResource(R.string.st_graph_hours), onBack, modifier) {
        PreviewCard(preview)
        SettingsGroup(Modifier.selectableGroup(), title = stringResource(R.string.st_place_app)) {
            val presets = SettingsSpec.GRAPH_HOURS.presets
            for (h in presets) {
                RadioRow(label(SettingsSpec.GRAPH_HOURS, h), c.appHours.toDouble() == h, {
                    actions.number(GraphSettings.Hours, h)
                }, enabled = !readOnly)
            }
            val custom = c.appHours.toDouble() !in presets
            RadioRow(
                stringResource(DsR.string.ds_custom),
                custom,
                { dialog = GraphDialog.AppHours },
                summary = if (custom) label(SettingsSpec.GRAPH_HOURS, c.appHours.toDouble()) else null,
                enabled = !readOnly,
            )
        }
        PlacesGroup { p ->
            ValueRow(
                placeName(p),
                c.placeHours[p]?.let { label(SettingsSpec.PLACE_HOURS, it.toDouble()) } ?: same,
                { dialog = GraphDialog.PlaceHours(p) },
                icon = placeIcon(p),
            )
        }
    }
    GraphDialogs(dialog, c, actions, label) { dialog = null }
}

/**
 * Sensor end › Sensor time left (also from Graph, docs/38): the app's main switch, then the notification and the
 * widgets, each *Same as the app*, *On* or *Off*. Not mirrored: a follower changes all of it.
 */
@Composable
fun SensorTimeContent(
    c: GraphConfig,
    actions: GraphActions,
    onBack: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<GraphDialog?>(null) }
    val same = stringResource(R.string.st_same_as_app)
    SettingsPage(stringResource(R.string.st_graph_sensor), onBack) {
        MainSwitchRow(stringResource(R.string.st_sensor_time_show), c.appSensorTime, actions.sensorTime)
        PlacesGroup { p ->
            ValueRow(
                placeName(p),
                c.sensorPlace[p]?.let { onOffText(it) } ?: same,
                { dialog = GraphDialog.SensorPlace(p) },
                icon = placeIcon(p),
            )
        }
    }
    GraphDialogs(dialog, c, actions, numberLabeler()) { dialog = null }
}

/** "In range 70–179 mg/dL · in Readings": the target range the graph colours with, and where it is set. */
@Composable
private fun rangesSummary(c: GraphConfig): String {
    val unit = LocalGlucoseUnit.current
    val locale = LocalConfiguration.current.locales[0]
    return stringResource(
        R.string.st_graph_ranges_summary,
        unit.text(c.bands.low, locale),
        unit.text(c.bands.high - 1, locale),
        unit.symbol,
    )
}

@Composable
private fun GraphDialogs(
    dialog: GraphDialog?,
    c: GraphConfig,
    actions: GraphActions,
    label: (NumberSpec, Double) -> String,
    close: () -> Unit,
) {
    fun number(spec: NumberSpec) = { v: Double -> label(spec, v) }
    when (dialog) {
        null -> {
            Unit
        }

        GraphDialog.AddLine -> {
            val spec = SettingsSpec.LINE
            val exists = stringResource(R.string.st_graph_line_exists)
            NumberDialog(
                stringResource(R.string.st_graph_line_add),
                NumberInput(spec, reject = { v -> exists.takeIf { v.toInt() in c.lines } }),
                spec.default,
                number(spec),
                close,
            ) { v -> actions.lines((c.lines + v.toInt()).distinct().sorted()) }
        }

        GraphDialog.Grid -> {
            NumberDialog(
                stringResource(R.string.st_graph_grid),
                NumberInput(SettingsSpec.GRID_HOURS),
                c.gridHours.toDouble(),
                number(
                    SettingsSpec.GRID_HOURS,
                ),
                close,
            ) { actions.number(GraphSettings.GridHours, it) }
        }

        // Time shown's Custom…: the number field alone (the presets are the page's own choices)
        GraphDialog.AppHours -> {
            NumberDialog(
                stringResource(R.string.st_graph_hours),
                NumberInput(SettingsSpec.GRAPH_HOURS, presets = emptyList()),
                c.appHours.toDouble(),
                number(SettingsSpec.GRAPH_HOURS),
                close,
            ) { actions.number(GraphSettings.Hours, it) }
        }

        is GraphDialog.PlaceHours -> {
            // 0 = Same as the app, the first choice, with the app's value (docs/38)
            val same = stringResource(R.string.st_same_as_app_value, label(SettingsSpec.GRAPH_HOURS, c.appHours.toDouble()))
            NumberDialog(
                stringResource(if (dialog.place == GraphPlace.NOTIFICATION) R.string.st_hours_notification else R.string.st_hours_widgets),
                NumberInput(SettingsSpec.PLACE_HOURS),
                (c.placeHours[dialog.place] ?: 0).toDouble(),
                { v -> if (v == 0.0) same else label(SettingsSpec.PLACE_HOURS, v) },
                close,
            ) { actions.placeHours(dialog.place, it) }
        }

        is GraphDialog.SensorPlace -> {
            OnOffPlaceDialog(
                stringResource(
                    if (dialog.place == GraphPlace.NOTIFICATION) R.string.st_sensor_time_notification else R.string.st_sensor_time_widgets,
                ),
                stringResource(R.string.st_same_as_app_value, onOffText(c.appSensorTime)),
                c.sensorPlace[dialog.place],
                close,
            ) { actions.sensorPlace(dialog.place, it) }
        }
    }
}

/** The extra lines as input chips; a tap removes one (its × says so to TalkBack). */
@Composable
private fun LineChips(
    lines: List<Int>,
    glucose: (Int) -> String,
    enabled: Boolean,
    onRemove: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (l in lines) {
            val remove = stringResource(R.string.st_graph_line_remove, glucose(l))
            InputChip(
                selected = false,
                onClick = { onRemove(l) },
                label = { Text(glucose(l)) },
                enabled = enabled,
                trailingIcon =
                    if (enabled) {
                        {
                            Icon(
                                painterResource(R.drawable.st_ic_close),
                                contentDescription = remove,
                                Modifier.size(InputChipDefaults.AvatarSize),
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun markKindName(k: String): String =
    stringResource(
        when (k) {
            "rapid" -> R.string.st_marks_rapid
            "long" -> R.string.st_marks_long
            "carbs" -> R.string.st_marks_carbs
            else -> R.string.st_marks_note
        },
    )

/** "Everything", "Carbs: point only" (the default) or "Custom for each kind". */
@Composable
fun marksSummary(m: MarkShow): String {
    val pointOnly =
        MarkShow.KINDS.filter { k ->
            MarkShow.parts(k).size > 1 && MarkShow.parts(k).all { m.has(k, it) == (it == MarkShow.POINT) }
        }
    val full = MarkShow.KINDS.filter { k -> MarkShow.parts(k).all { m.has(k, it) } }
    return when {
        full.size == MarkShow.KINDS.size -> {
            stringResource(R.string.st_marks_summary_all)
        }

        (full + pointOnly).toSet().size == MarkShow.KINDS.size && pointOnly.isNotEmpty() -> {
            stringResource(R.string.st_marks_summary_point, pointOnly.map { markKindName(it) }.joinToString(", "))
        }

        else -> {
            stringResource(R.string.st_marks_summary_custom)
        }
    }
}

/**
 * Settings › Graph › Treatments on the graph: a group per kind, a switch per part that kind has (point, mute line,
 * amount, time), the live card on top. One setting for every graph.
 */
@Composable
fun GraphMarksContent(
    c: GraphConfig,
    actions: GraphActions,
    onBack: () -> Unit,
    readOnly: Boolean = false,
    preview: @Composable () -> Unit = {},
) {
    val title = stringResource(R.string.st_marks_title)
    SettingsPage(title, onBack) {
        PreviewCard(preview)
        MarkGroups(c, actions, readOnly)
        if (GraphSection.Marks.changed(c) && !readOnly) {
            SettingsGroup {
                ValueRow(
                    stringResource(R.string.st_marks_reset),
                    "",
                    { actions.reset(GraphSection.Marks) },
                    icon = DsR.drawable.ds_ic_restart_alt,
                )
            }
        }
        FooterText(stringResource(R.string.st_marks_footer))
    }
}

@Composable
private fun MarkGroups(
    c: GraphConfig,
    actions: GraphActions,
    readOnly: Boolean,
) {
    for (k in MarkShow.KINDS) {
        SettingsGroup(title = markKindName(k)) {
            for (p in MarkShow.parts(k)) {
                val (name, summary) =
                    when (p) {
                        MarkShow.POINT -> stringResource(R.string.st_marks_point) to null
                        MarkShow.LINE -> stringResource(R.string.st_marks_line) to stringResource(R.string.st_marks_line_summary)
                        MarkShow.AMOUNT -> stringResource(R.string.st_marks_amount) to null
                        else -> stringResource(R.string.st_marks_time) to null
                    }
                SwitchRow(
                    name,
                    c.marks.has(k, p),
                    { on -> actions.marks(c.marks.with(k, p, on)) },
                    summary = summary,
                    readOnly = readOnly,
                )
            }
        }
    }
}
