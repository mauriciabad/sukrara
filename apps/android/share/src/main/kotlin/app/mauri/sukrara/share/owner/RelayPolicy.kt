// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.share.owner.usecase.PublishReadings

/**
 * How readings travel over the internet (Settings › Sharing): [on] = *Over the internet* (schema `sharing.nostr`; off
 * = nothing goes to relays, Bluetooth followers still get every event), [intervalMin] = *Upload interval* (schema
 * `sharing.nostrInterval`; 0 = every reading, else no live events on relays and the current hour's chunk at most every
 * that many minutes). Bluetooth always stays live.
 */
data class RelayPolicy(val on: Boolean = true, val intervalMin: Int = 0) {
    /** Whether [event] goes to the relays now (alerts always do while [on]: followers alert at the same moment). */
    fun toRelays(event: SignedEvent): Boolean = on && !(intervalMin > 0 && event.kind == Wire.KIND_LIVE)

    /** Seconds between two publishes of the current hour's chunk. */
    val chunkEvery: Long get() = if (intervalMin > 0) intervalMin * 60L else PublishReadings.CHUNK_EVERY
}
