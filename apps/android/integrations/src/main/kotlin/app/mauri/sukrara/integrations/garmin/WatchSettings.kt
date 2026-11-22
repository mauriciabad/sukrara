// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.graph.GraphText
import app.mauri.sukrara.graph.MarkShow
import app.mauri.sukrara.graph.WatchDisplay
import app.mauri.sukrara.graph.stats.TirRange
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One watch's own values as stored (settings.json `apps.garmin.watch`), each field under its schema key, the compound
 * ones in the app's own text formats (`graph.slots`, `graph.lastElse`, `graph.ringOrder`, `graph.marks`); null = Same
 * as the app.
 */
@Serializable
data class WatchOverrides(
    @SerialName("watchSlots") val slots: String? = null,
    @SerialName("watchLast") val last: Boolean? = null,
    @SerialName("watchLastElse") val lastElse: String? = null,
    @SerialName("watchRingOrder") val ringOrder: String? = null,
    @SerialName("watchRingRange") val ringRange: TirRange? = null,
    @SerialName("watchRingMarks") val ringMarks: Boolean? = null,
    @SerialName("watchHours") val hours: Int? = null,
    @SerialName("watchMarks") val marks: String? = null,
    @SerialName("watchSide") val side: Boolean? = null,
    @SerialName("watchReportDays") val reportDays: Int? = null,
) {
    /** As the watch gets them: a stored value that no longer parses or is out of its format follows the app. */
    fun toDisplay() =
        WatchDisplay(
            slots = slots?.let(GraphText::parseSlots),
            lastOn = last,
            lastElse = lastElse?.let(GraphText::parseSlot),
            ringOrder = ringOrder?.let(GraphText::parseRingOrder),
            ringRange = ringRange,
            ringMarks = ringMarks,
            hours = hours?.takeIf { it > 0 && SettingsSpec.GRAPH_HOURS.valid(it.toDouble()) },
            marks = marks?.let(MarkShow::parse),
            side = side,
            reportDays = reportDays?.takeIf { it in WatchDisplay.REPORT_DAYS },
        )

    companion object {
        fun of(d: WatchDisplay) =
            WatchOverrides(
                d.slots?.let(GraphText::slotsText),
                d.lastOn,
                d.lastElse?.let(GraphText::slotText),
                d.ringOrder?.let(GraphText::ringOrderText),
                d.ringRange,
                d.ringMarks,
                d.hours,
                d.marks?.text,
                d.side,
                d.reportDays,
            )
    }
}

/** Every watch's own values by Garmin Connect's id for it (the typed state file `watches`). */
@Serializable
data class GarminWatches(
    val byId: Map<Long, WatchOverrides> = emptyMap(),
)

/** Reads and writes [GarminWatches]; a watch whose values all follow the app has no entry. */
class WatchSettingsStore(
    private val store: StateStore<GarminWatches>,
) {
    /** Each watch's own values; a watch without any isn't listed. */
    val displays: Flow<Map<Long, WatchDisplay>> =
        store.data.map { w -> w.byId.mapValues { it.value.toDisplay() }.filterValues { !it.same } }

    suspend fun set(
        watch: Long,
        display: WatchDisplay,
    ) = store.update { w ->
        w.copy(byId = if (display.same) w.byId - watch else w.byId + (watch to WatchOverrides.of(display)))
    }
}
