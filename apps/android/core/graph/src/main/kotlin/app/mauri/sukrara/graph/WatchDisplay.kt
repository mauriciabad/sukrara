// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.graph.stats.Band
import app.mauri.sukrara.graph.stats.TirRange
import app.mauri.sukrara.settings.SettingsSpec

/**
 * One watch's own values for Sukrara on the watch (settings.json `apps.garmin.watch`, contracts/watchface/app SPEC §11:
 * the watch is a place, docs/38); null = Same as the app. The unit, the limits and the alerts are never per watch.
 */
data class WatchDisplay(
    /** The main, second and third metric (`graph.slots`), each its own or null (Same as the app). */
    val slots: List<RingSpec?> = SAME_SLOTS,
    /** The last treatment beside the value (`graph.last`). */
    val lastOn: Boolean? = null,
    /** No recent treatment (`graph.lastElse`). */
    val lastElse: RingSpec? = null,
    val ringOrder: List<Band>? = null,
    /** The number in a time-in-range ring (`graph.ringRange`). */
    val ringRange: TirRange? = null,
    /** The rings' rewards (`graph.ringMarks`). */
    val ringMarks: Boolean? = null,
    /** The chart's time (`graph.hours`). */
    val hours: Int? = null,
    /** Treatments on the chart (`graph.marks`). */
    val marks: MarkShow? = null,
    /** The distribution and % column beside the chart (`graph.side`). */
    val side: Boolean? = null,
    /** The period the Numbers page opens on (days; the phone's report card starts at 14). */
    val reportDays: Int? = null,
) {
    /** Whether every value follows the app. */
    val same: Boolean get() = this == WatchDisplay()

    /** How many values are this watch's own (each metric counts as one). */
    val ownCount: Int
        get() =
            slots.count { it != null } +
                listOf(lastOn, lastElse, ringOrder, ringRange, ringMarks, hours, marks, side, reportDays).count { it != null }

    /** [c] as this watch shows it: each own value in place of the app's. */
    fun applyTo(c: GraphConfig): GraphConfig =
        c.copy(
            slots = c.slots.mapIndexed { i, s -> slots.getOrNull(i) ?: s },
            lastOn = lastOn ?: c.lastOn,
            lastElse = lastElse ?: c.lastElse,
            ringOrder = ringOrder ?: c.ringOrder,
            ringRange = ringRange ?: c.ringRange,
            ringMarks = ringMarks ?: c.ringMarks,
            appHours = hours ?: c.appHours,
            marks = marks ?: c.marks,
            side = side ?: c.side,
        )

    /** [slot] (0 main, 1 second, 2 third) as its own [spec], or Same as the app (null). */
    fun withSlot(
        slot: Int,
        spec: RingSpec?,
    ) = copy(slots = slots.toMutableList().also { it[slot] = spec })

    companion object {
        /** Every metric Same as the app. */
        val SAME_SLOTS: List<RingSpec?> = listOf(null, null, null)

        /** The report periods the watch can open on (days), as the phone's report offers them. */
        val REPORT_DAYS: List<Int> = SettingsSpec.OPTIONS.getValue("reportPeriod").map(String::toInt)

        /** The phone's report card starts here: the app's own value. */
        const val REPORT_START = 14
    }
}
