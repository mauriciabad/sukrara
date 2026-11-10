// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.Wire

/** What the wearer's sharing sent since the app started (Settings › Advanced › Statistics): counts only. */
data class SentCounts(
    /** Live events, one per reading. */
    val live: Int = 0,
    /** Hour chunks (an hour of readings, sent again while that hour fills and on a reconnect). */
    val chunks: Int = 0,
) {
    /** These counts plus [events] (readings' events: live ones and chunks). */
    fun plus(events: List<SignedEvent>): SentCounts =
        copy(
            live = live + events.count { it.kind == Wire.KIND_LIVE },
            chunks = chunks + events.count { it.kind == Wire.KIND_DATA },
        )
}
