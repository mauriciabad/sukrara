// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.data.settings.Setting
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPlace
import app.mauri.sukrara.graph.GraphSettings
import app.mauri.sukrara.graph.GraphText
import app.mauri.sukrara.graph.LastPlace
import app.mauri.sukrara.graph.MarkShow
import app.mauri.sukrara.graph.PlaceOverride
import app.mauri.sukrara.graph.RingSpec
import app.mauri.sukrara.graph.stats.Band
import app.mauri.sukrara.graph.stats.TirRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Graph pages' edits: each writes one schema setting ([GraphSettings]), so every card surface (home, notification,
 * widgets) redraws from the same [GraphConfig]. The page shows [config]; null until the stored values have loaded.
 */
class GraphViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {
    val config: StateFlow<GraphConfig?> =
        GraphSettings.config(settings).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun <T : Any> set(
        setting: Setting<T>,
        value: T,
    ) {
        viewModelScope.launch { settings.set(setting, value) }
    }

    fun lines(lines: List<Int>) = set(GraphSettings.Lines, GraphText.linesText(lines))

    fun slots(slots: List<RingSpec>) = set(GraphSettings.Slots, GraphText.slotsText(slots))

    fun lastElse(slot: RingSpec) = set(GraphSettings.LastElse, GraphText.slotText(slot))

    fun lastOn(on: Boolean) = set(GraphSettings.Last, on)

    fun lastWhere(place: LastPlace) = set(GraphSettings.LastPlace, place.name)

    fun ringRange(range: TirRange) = set(GraphSettings.RingRange, range.name)

    fun ringOrder(order: List<Band>) = set(GraphSettings.RingOrder, GraphText.ringOrderText(order))

    fun marks(marks: MarkShow) = set(GraphSettings.Marks, marks.text)

    fun placeHours(
        place: GraphPlace,
        hours: Double,
    ) = set(GraphSettings.PlaceHours.getValue(place), hours)

    /** The sensor's time left: the app's, every place follows it unless it has its own. */
    fun sensorTime(on: Boolean) = set(GraphSettings.SensorTime, on)

    /** A place's own sensor time left; null = Same as the app. */
    fun sensorPlace(
        place: GraphPlace,
        own: Boolean?,
    ) = set(GraphSettings.SensorTimePlace.getValue(place), PlaceOverride.of(own).key)

    /** Puts [section]'s settings back to their defaults. */
    fun reset(section: GraphSection) {
        viewModelScope.launch { section.settings.forEach { settings.reset(it) } }
    }
}

/**
 * The groups of the Graph and Metrics pages, each with its own Reset; [All] = everything the Graph page shows (not the
 * ranges, not the metrics), [Metrics] = everything the Metrics page and its pages show.
 */
enum class GraphSection(
    val settings: List<Setting<*>>,
) {
    Ranges(GraphSettings.Bands),
    Lines(listOf(GraphSettings.Lines, GraphSettings.GridHours)),
    Time(listOf(GraphSettings.Hours) + GraphSettings.PlaceHours.values),

    /** The main, second and third metric, the last treatment and what shows while there's none. */
    Beside(listOf(GraphSettings.Slots, GraphSettings.Last, GraphSettings.LastPlace, GraphSettings.LastElse)),

    /** How a time-in-range ring looks: its number, its rewards. */
    TirRings(listOf(GraphSettings.RingRange, GraphSettings.RingMarks)),
    RingOrder(listOf(GraphSettings.RingOrder)),
    BesideGraph(listOf(GraphSettings.Side, GraphSettings.ColumnCrown)),
    Marks(listOf(GraphSettings.Marks)),
    SensorTime(listOf(GraphSettings.SensorTime) + GraphSettings.SensorTimePlace.values),
    All(
        listOf(Lines, Time, BesideGraph, Marks, SensorTime).flatMap { it.settings },
    ),
    Metrics(
        listOf(Beside, TirRings, RingOrder).flatMap { it.settings },
    ),
    ;

    /** Whether [c] differs from the defaults in this section. */
    fun changed(c: GraphConfig): Boolean {
        val d = GraphConfig()
        return when (this) {
            Ranges -> {
                c.bands != d.bands
            }

            Lines -> {
                c.lines != d.lines || c.gridHours != d.gridHours
            }

            Time -> {
                c.appHours != d.appHours || c.placeHours.isNotEmpty()
            }

            Beside -> {
                c.slots != d.slots || c.lastOn != d.lastOn || c.lastWhere != d.lastWhere || c.lastElse != d.lastElse
            }

            TirRings -> {
                c.ringRange != d.ringRange || c.ringMarks != d.ringMarks
            }

            RingOrder -> {
                c.ringOrder != d.ringOrder
            }

            BesideGraph -> {
                c.side != d.side || c.columnCrown != d.columnCrown
            }

            Marks -> {
                c.marks != d.marks
            }

            SensorTime -> {
                c.appSensorTime != d.appSensorTime || c.sensorPlace.isNotEmpty()
            }

            All -> {
                listOf(Lines, Time, BesideGraph, Marks, SensorTime).any { it.changed(c) }
            }

            Metrics -> {
                Beside.changed(c) || TirRings.changed(c) || RingOrder.changed(c)
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private suspend fun SettingsRepository.reset(s: Setting<*>) {
    val setting = s as Setting<Any>
    set(setting, setting.default)
}
