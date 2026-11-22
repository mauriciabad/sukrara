// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.MarkShow
import app.mauri.sukrara.graph.WatchDisplay

/**
 * The display settings Sukrara on one watch follows, resolved on the phone: [graph] = the app's settings with that
 * watch's own values in place ([WatchDisplay], docs/38: the metrics `m`, `else`, `ro` come from it), the chart's
 * window ([hours], `graph.hours`), the treatment marks per kind ([marks], `graph.marks`), the distribution and % column
 * beside the chart ([side], `graph.side`) and its crown ([crown], `graph.columnCrown`), the period the Numbers page
 * opens on ([reportDays], `repD`). The unit and the limits are global and travel on their own.
 */
data class GarminDisplay(
    val graph: GraphConfig,
    val hours: Int,
    val marks: MarkShow,
    val side: Boolean,
    val crown: Boolean,
    val reportDays: Int,
) {
    companion object {
        /** THE one place a watch's values resolve: [own] over the app's [config] (nothing own = the app's). */
        fun resolve(
            config: GraphConfig,
            own: WatchDisplay = WatchDisplay(),
        ): GarminDisplay {
            val g = own.applyTo(config)
            return GarminDisplay(g, g.appHours, g.marks, g.side, g.columnCrown, own.reportDays ?: WatchDisplay.REPORT_START)
        }
    }
}
