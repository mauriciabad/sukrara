// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.MarkShow

/**
 * The display settings Sukrara on the watch follows (the app profile's `ds`), resolved on the phone: the chart's
 * window ([hours], `graph.hours`), the treatment marks per kind ([marks], `graph.marks`), the distribution and % column
 * beside the chart ([side], `graph.side`) and its crown ([crown], `graph.columnCrown`). The unit and the limits are
 * global and travel on their own.
 */
data class GarminDisplay(
    val hours: Int,
    val marks: MarkShow,
    val side: Boolean,
    val crown: Boolean,
) {
    companion object {
        /**
         * THE seam for a watch's own values: today every one is the phone app's own. A watch that becomes a place with
         * its own overrides (docs/38) resolves here, as `GraphConfig.hours(place)` does for the notification and widgets,
         * and the payload keeps carrying the resolved value.
         */
        fun resolve(config: GraphConfig): GarminDisplay = GarminDisplay(config.appHours, config.marks, config.side, config.columnCrown)
    }
}
