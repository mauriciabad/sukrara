// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.alerts

import app.mauri.sukrara.core.model.TrendRate

/**
 * The readings an engine keeps for the quiets' clear move ([TrendRate.clear], SPEC §2 "Clear move in the readings"):
 * kept with the episodes (`AlertMemory.recent`), so a restart or the alarm with the service dead judges the same.
 */
object Recent {
    /**
     * [recent] (ascending by time) with [r] in it, keeping only what a window can use: the readings taken within
     * [TrendRate.CLEAR_WINDOW_S] of the newest one; a reading of the same time replaces the one there.
     */
    fun keep(
        recent: List<AlertReading>,
        r: AlertReading,
    ): List<AlertReading> {
        val all = (recent.filter { it.t != r.t } + r).sortedBy { it.t }
        val newest = all.last().t
        return all.filter { newest - it.t < TrendRate.CLEAR_WINDOW_S }
    }
}
