// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.readings

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * The typed state file `history.json`: the kept history's fence (epoch s; 0 = none), the last reading of the source
 * before the one in use (a source change for the same person, a Sukrara link left with *Keep it*). The next source's
 * backfill fills gaps behind it and never replaces a kept reading (contracts/sources/link.json `fence`,
 * `ReadingRank.plan`). A damaged file reads as no fence: the rank rule alone, as before.
 */
@Serializable
data class HistoryFence(
    val fenceS: Long = 0,
) {
    val at: Instant? get() = fenceS.takeIf { it > 0 }?.let(Instant::ofEpochSecond)

    companion object {
        fun at(at: Instant?) = HistoryFence(at?.epochSecond ?: 0)
    }
}
