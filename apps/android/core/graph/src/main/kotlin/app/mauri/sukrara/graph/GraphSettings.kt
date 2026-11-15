// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.data.settings.Setting
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.TirRange
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * THE source of [GraphConfig]: the `graph.*` settings of the schema, read by schema id (store and key from the
 * generated `SettingsSpec`). Home, widgets and every other card surface draw from [config]; the settings pages write
 * these same [Setting]s. The structured ones are stored in their wire text form ([GraphText]); a stored value that
 * doesn't parse reads as the default.
 */
object GraphSettings {
    /** The six ranges, lowest first (veryLow, low, tight, high, veryHigh, extremelyHigh). */
    val Bands: List<Setting<Double>> =
        listOf("veryLow", "low", "tight", "high", "veryHigh", "extremelyHigh").map { SchemaSettings.number("graph.band.$it") }

    val Hours = SchemaSettings.number("graph.hours")
    val GridHours = SchemaSettings.number("graph.gridHours")
    val Side = SchemaSettings.bool("graph.side", default = GraphConfig().side)
    val ColumnCrown = SchemaSettings.bool("graph.columnCrown", default = GraphConfig().columnCrown)
    val RingMarks = SchemaSettings.bool("graph.ringMarks", default = GraphConfig().ringMarks)
    val RingRange = SchemaSettings.text("graph.ringRange", GraphConfig().ringRange.name, TirRange.entries.map { it.name }.toSet())

    /** The last treatment beside the value, and where it sits (schema option `lastPlace`: next to the value or the end). */
    val Last = SchemaSettings.bool("graph.last", default = GraphConfig().lastOn)
    val LastPlace =
        SchemaSettings.text("graph.lastPlace", GraphConfig().lastWhere.name, SettingsSpec.OPTIONS.getValue("lastPlace").toSet())

    /** The main, second and third metric (on, what, period, dimmed), [GraphText.slotsText]. */
    val Slots = SchemaSettings.text("graph.slots", GraphText.slotsText(GraphConfig.DEFAULT_SLOTS))

    /** The ring's segments clockwise from 12 o'clock, [GraphText.ringOrderText]. */
    val RingOrder = SchemaSettings.text("graph.ringOrder", GraphText.ringOrderText(RingSpec.DEFAULT_RING_ORDER))

    /** The extra lines across the chart (mg/dL), [GraphText.linesText]. */
    val Lines = SchemaSettings.text("graph.lines", GraphText.linesText(GraphConfig.DEFAULT_LINES))

    /** The last-treatment slot's metric while there's no recent treatment (on or off), [GraphText.slotText]. */
    val LastElse = SchemaSettings.text("graph.lastElse", GraphText.slotText(GraphConfig.DEFAULT_LAST_ELSE))

    /** Treatments on the graph, [MarkShow.text]. */
    val Marks = SchemaSettings.text("graph.marks", MarkShow.DEFAULT.text)

    /** The glucose unit every number is written in (`general.unit`, schema option `unit`). */
    val GlucoseUnitSetting =
        SchemaSettings.text(
            "general.unit",
            GlucoseUnit.MGDL.key,
            SettingsSpec.OPTIONS.getValue("unit").toSet(),
        )

    /** Each place's own window in hours (0 = the app's, [Hours]); only the places that can differ ([GraphPlace.OWN]). */
    val PlaceHours: Map<GraphPlace, Setting<Double>> =
        GraphPlace.OWN.associateWith { SchemaSettings.numberAt("graph.hours.place", placeIndex(it)) }

    /** Whether the app's card shows the sensor's time left: every place follows it unless it has its own. */
    val SensorTime = SchemaSettings.bool("graph.sensorTime", default = GraphConfig().appSensorTime)

    /** Each place's own sensor time left ([PlaceOverride]: Same follows [SensorTime]). */
    val SensorTimePlace: Map<GraphPlace, Setting<String>> =
        GraphPlace.OWN.associateWith {
            SchemaSettings.textAt("graph.sensorTime.place", placeIndex(it), PlaceOverride.SAME.key, PlaceOverride.keys)
        }

    /** A place's key in a per-place setting's comma list: its position in the schema's `places.android`, by name. */
    private fun placeIndex(p: GraphPlace): Int = GraphPlace.OWN_NAMES.indexOf(p.name).also { require(it >= 0) { "$p has no own value" } }

    /** The person's graph settings, a new [GraphConfig] on every change (ranges always in order: [Bands.sane]). */
    fun config(settings: SettingsRepository): Flow<GraphConfig> {
        val bands = combine(Bands.map(settings::flow)) { v -> bandsOf(v.map(Double::toInt)) }
        val numbers =
            combine(settings.flow(Hours), settings.flow(GridHours), settings.flow(Last)) { h, g, last ->
                Triple(h.toInt(), g.toInt(), last)
            }
        val switches =
            combine(settings.flow(Side), settings.flow(ColumnCrown), settings.flow(RingMarks)) { side, crown, marks ->
                Triple(side, crown, marks)
            }
        val enums = combine(settings.flow(RingRange), settings.flow(LastPlace)) { range, last -> range to last }
        val texts = combine(listOf(Slots, RingOrder, Lines, LastElse, Marks).map(settings::flow)) { it.toList() }
        val hours =
            combine(GraphPlace.OWN.map { settings.flow(PlaceHours.getValue(it)) }) { v ->
                GraphPlace.OWN
                    .zip(v.map(Double::toInt))
                    .filter { it.second > 0 }
                    .toMap()
            }
        val sensorOwn =
            combine(GraphPlace.OWN.map { settings.flow(SensorTimePlace.getValue(it)) }) { v ->
                GraphPlace.OWN
                    .zip(v)
                    .mapNotNull { (p, o) -> PlaceOverride.of(o).on?.let { p to it } }
                    .toMap()
            }
        val sensor = combine(settings.flow(SensorTime), sensorOwn) { app, own -> app to own }
        val places = combine(hours, sensor, settings.flow(GlucoseUnitSetting)) { h, s, u -> Triple(h, s, GlucoseUnit.of(u)) }
        return combine(bands, numbers, switches, combine(enums, texts) { e, t -> e to t }, places) {
            b,
            (appHours, grid, last),
            (side, crown, marks),
            (e, t),
            (placeHours, sensor, unit),
            ->
            GraphConfig(
                bands = b,
                appHours = appHours,
                placeHours = placeHours,
                gridHours = grid,
                slots = GraphText.parseSlots(t[0]) ?: GraphConfig.DEFAULT_SLOTS,
                ringOrder = GraphText.parseRingOrder(t[1]) ?: RingSpec.DEFAULT_RING_ORDER,
                side = side,
                columnCrown = crown,
                ringMarks = marks,
                lines = GraphText.parseLines(t[2]),
                ringRange = TirRange.valueOf(e.first),
                lastOn = last,
                lastWhere =
                    app.mauri.sukrara.graph.LastPlace
                        .valueOf(e.second),
                lastElse = GraphText.parseSlot(t[3]) ?: GraphConfig.DEFAULT_LAST_ELSE,
                appSensorTime = sensor.first,
                sensorPlace = sensor.second,
                marks = MarkShow.parse(t[4]),
                unit = unit,
            )
        }.distinctUntilChanged()
    }

    private fun bandsOf(v: List<Int>) = Bands(v[0], v[1], v[2], v[3], v[4], v[5]).sane()
}
