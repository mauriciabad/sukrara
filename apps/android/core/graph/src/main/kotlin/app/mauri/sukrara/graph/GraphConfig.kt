// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.graph.stats.Band
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.TirRange
import app.mauri.sukrara.settings.SettingsSpec

/**
 * Where a chart is shown. The app's own values are the shared ones ([GraphConfig.appHours], [GraphConfig.appSensorTime]);
 * only the places in [OWN] can have their own (docs/38).
 */
enum class GraphPlace {
    APP,
    NOTIFICATION,
    WIDGETS,
    ;

    companion object {
        /**
         * The places that can differ from the app, in page order: the schema's `places.android` of `graph.hours.place`
         * and `graph.sensorTime.place` (GraphSettingsTest reads it), whose comma list of keys holds one per place.
         */
        val OWN_NAMES = listOf("NOTIFICATION", "WIDGETS")

        val OWN: List<GraphPlace> = OWN_NAMES.map(::valueOf)
    }
}

/**
 * A place's own value of a switch the app sets (schema option `placeOverride`, docs/38): [SAME] follows the app,
 * [ON] / [OFF] are the place's own. [on] = the place's own value, null = the app's.
 */
enum class PlaceOverride(
    val key: String,
    val on: Boolean?,
) {
    SAME("same", null),
    ON("on", true),
    OFF("off", false),
    ;

    companion object {
        val keys: Set<String> = entries.map { it.key }.toSet()

        /** A stored name; unknown = [SAME]. */
        fun of(key: String): PlaceOverride = entries.firstOrNull { it.key == key } ?: SAME

        /** A place's own value (null = the app's) as stored. */
        fun of(on: Boolean?): PlaceOverride = entries.first { it.on == on }
    }
}

/**
 * Everything the graph and slot settings decide (contracts/settings/settings.json `graph.*`), with the schema's
 * defaults. The settings repository stores each field under its own schema key and builds this; the renderer only
 * reads it ([rings], [graphStyle], [lastElseRing]).
 */
data class GraphConfig(
    val bands: Bands = Bands(),
    /** The app's time shown (`graph.hours`): the shared one, every place without its own follows it. */
    val appHours: Int = SettingsSpec.GRAPH_HOURS.default.toInt(),
    /** The places in [GraphPlace.OWN] with their own time shown (`graph.hours.place`); absent = [appHours]. */
    val placeHours: Map<GraphPlace, Int> = emptyMap(),
    /** The main, second and third metric (`graph.slots`), each on or off ([RingSpec.on]); the first one on is the main. */
    val slots: List<RingSpec> = DEFAULT_SLOTS,
    val ringRange: TirRange = TirRange.IN_RANGE,
    val ringOrder: List<Band> = RingSpec.DEFAULT_RING_ORDER,
    /** The distribution and % column beside the chart. */
    val side: Boolean = true,
    val columnCrown: Boolean = true,
    /** The rings' rewards: laurel from 90 %, flowers from 95 %, crown at 100 % ([TirMark]). */
    val ringMarks: Boolean = true,
    val lines: List<Int> = DEFAULT_LINES,
    val gridHours: Int = SettingsSpec.GRID_HOURS.default.toInt(),
    /** The last treatment beside the value (`graph.last`), and where it sits while on (`graph.lastPlace`: never OFF). */
    val lastOn: Boolean = true,
    val lastWhere: LastPlace = LastPlace.NEAR_VALUE,
    /** What the last-treatment slot shows while there's no recent treatment, shown while [RingSpec.on]. */
    val lastElse: RingSpec = DEFAULT_LAST_ELSE,
    /** Whether the app's card shows the sensor's time left (`graph.sensorTime`): every place without its own follows it. */
    val appSensorTime: Boolean = true,
    /** The places in [GraphPlace.OWN] with their own (`graph.sensorTime.place` On / Off); absent = [appSensorTime]. */
    val sensorPlace: Map<GraphPlace, Boolean> = emptyMap(),
    val marks: MarkShow = MarkShow.DEFAULT,
    /**
     * The glucose unit every number is written in (`general.unit`, not mirrored: each device keeps its own;
     * contracts/settings/units.json). Not a `graph.*` setting, but every card surface draws from this config.
     */
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
) {
    fun sensorTime(p: GraphPlace) = sensorPlace[p] ?: appSensorTime

    /** The place's window; 0 in the schema means "same as the app". */
    fun hours(p: GraphPlace) = placeHours[p]?.takeIf { it > 0 } ?: appHours

    /** Where a card puts the last treatment: [lastWhere] while it's on, else [LastPlace.OFF]. */
    val lastPlace: LastPlace get() = if (lastOn) lastWhere else LastPlace.OFF

    /** [lastElse] with the ring settings applied; null while it's off. */
    fun lastElseRing() = lastElse.takeIf { it.on }?.copy(range = ringRange, order = ringOrder, marks = ringMarks)

    /** The metrics that are on, main first, with the ring settings applied ([range]: a widget's own). */
    fun rings(range: TirRange = ringRange) =
        slots.take(3).filter { it.on }.map { it.copy(range = range, order = ringOrder, marks = ringMarks) }

    fun graphStyle(hours: Int) =
        GraphStyle(
            windowS = hours * 3600L,
            bands = bands,
            side = side,
            lines = lines,
            gridHours = gridHours,
            crown = columnCrown,
            marks = marks,
            unit = unit,
        )

    /** The app's home panel: the app's window and sensor time, the rings and last-treatment slot. */
    fun appPanelStyle() =
        CardStyle(
            graph = graphStyle(appHours),
            rings = rings(),
            last = lastPlace,
            lastElse = lastElseRing(),
            sensorTime = appSensorTime,
            bands = bands,
            unit = unit,
        )

    companion object {
        /** The chart's extra lines: 100 mg/dL unless the user changes them. */
        val DEFAULT_LINES = listOf(SettingsSpec.LINE.default.toInt())

        /** Today and the last week as time in range; the third metric (off by default) the 14-day GMI. */
        val DEFAULT_SLOTS =
            listOf(RingSpec(86_400L), RingSpec(7 * 86_400L), RingSpec(14 * 86_400L, kind = SlotKind.GMI, on = false))

        /** The last-treatment slot's fallback: time in range over 30 days, faded like a second slot. */
        val DEFAULT_LAST_ELSE = RingSpec(30 * 86_400L)
    }
}
