// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol.follow

import app.mauri.sukrara.protocol.RelayOk
import app.mauri.sukrara.protocol.Wire
import kotlinx.serialization.Serializable

/**
 * One `leave` on its way to the wearer (PROTOCOL-v2 "Leave"): the signed gift wrap as JSON (never the follower key: the
 * wrap is encrypted and signed by a one-time key), the stream's [relays], when it [expiry]res, and the wearer's name for
 * the person. [accepted] = the relays that keep it ([Wire.relayKey] of the URL whose connection answered); [nearby] =
 * the wearer's phone took it over Bluetooth. Pinned by contracts/protocol/sync/leave.json `outbox`.
 *
 * `to` in the rules = the relays it goes to: the valid ones of [relays] ([Wire.validRelays], what a relay pool
 * connects to), or a debug build's test relays standing in for them (the sender's `relaysFor`).
 */
@Serializable
data class PendingLeave(
    val wrapJson: String,
    val eventId: String,
    val relays: List<String>,
    val expiry: Long,
    val wearer: String = "",
    val accepted: Set<String> = emptySet(),
    val nearby: Boolean = false,
) {
    /** The wearer can learn it now: a relay keeps it, or the wearer's phone took it over Bluetooth. */
    val told: Boolean get() = accepted.isNotEmpty() || nearby

    /** Nothing more to send: [LeaveOutbox.NEEDED] of [to] keep it (every one when fewer), or it expired. */
    fun done(
        now: Long,
        to: List<String> = Wire.validRelays(relays),
    ): Boolean {
        if (now >= expiry) return true
        val keys = to.mapTo(HashSet(), Wire::relayKey)
        return keys.isNotEmpty() && accepted.count { it in keys } >= minOf(LeaveOutbox.NEEDED, keys.size)
    }

    /** The relays of [to] still to send it to. */
    fun waitingOn(to: List<String> = Wire.validRelays(relays)): List<String> = to.filter { Wire.relayKey(it) !in accepted }

    /**
     * The relay at [url] (the URL its connection was opened with) answered [ok]: it keeps this leave when the answer
     * names this leave's id, says the relay keeps it ([RelayOk.kept]: `true`, or `false` with `duplicate:`), and the
     * relay is one of [to]. Anything else changes nothing.
     */
    fun answered(
        url: String,
        ok: RelayOk,
        to: List<String> = Wire.validRelays(relays),
    ): PendingLeave {
        val key = Wire.relayKey(url)
        val ours = ok.eventId == eventId && ok.kept && to.any { Wire.relayKey(it) == key }
        return if (ours && key !in accepted) copy(accepted = accepted + key) else this
    }

    override fun toString() = "PendingLeave(accepted=${accepted.size}/${relays.size}, nearby=$nearby)"
}

/** The leaves this device still sends (saved as is: a crash or a restart never loses one). */
@Serializable
data class LeaveOutbox(val leaves: List<PendingLeave> = emptyList()) {
    /** Without the ones that are [PendingLeave.done]; [to] = the relays a leave's list goes to. */
    fun pruned(
        now: Long,
        to: (List<String>) -> List<String> = Wire::validRelays,
    ): LeaveOutbox = LeaveOutbox(leaves.filterNot { it.done(now, to(it.relays)) })

    /** Every leave after the relay at [url] answered [ok] ([PendingLeave.answered]). */
    fun answered(
        url: String,
        ok: RelayOk,
        to: (List<String>) -> List<String> = Wire::validRelays,
    ): LeaveOutbox = LeaveOutbox(leaves.map { it.answered(url, ok, to(it.relays)) })

    /** Every leave for [eventId] after [change]. */
    fun update(eventId: String, change: (PendingLeave) -> PendingLeave): LeaveOutbox =
        LeaveOutbox(leaves.map { if (it.eventId == eventId) change(it) else it })

    /** The oldest leave nobody took yet (its wearer's name is what the person is told about), or null. */
    val untold: PendingLeave? get() = leaves.firstOrNull { !it.told }

    companion object {
        /** Relays that must keep a leave before the follower stops sending it (one may drop it). */
        const val NEEDED = 2
    }
}
