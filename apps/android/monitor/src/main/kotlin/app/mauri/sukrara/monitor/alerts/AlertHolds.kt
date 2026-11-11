// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertState
import app.mauri.sukrara.core.model.AlertHold

/**
 * What an app update waits for (#188): the GitHub version never installs while an alert is up or a very low is on,
 * since the install stops Sukrara for a few seconds. Read from the alert runtime's saved state (saved after every
 * input, so it is current with the service dead too) and the hub's live summary, whichever says more.
 */
object AlertHolds {
    fun of(
        machine: AlertState?,
        live: Set<String> = emptySet(),
    ): AlertHold {
        val episodes =
            machine
                ?.local
                ?.episodes
                ?.values
                .orEmpty()
        val mirrored =
            machine
                ?.mirror
                ?.shown
                ?.values
                .orEmpty()
        val showing =
            live +
                episodes.mapNotNull { it.shown?.kind?.wire } +
                mirrored.map { it.kind.wire }
        val veryLow =
            AlertKind.VERY_LOW.wire in showing ||
                episodes.any { it.on && it.kind == AlertKind.VERY_LOW }
        return AlertHold(showing, veryLow)
    }
}
