// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol.follow

import kotlinx.serialization.Serializable

/**
 * One `leave` on its way to the wearer (PROTOCOL-v2 "Leave"): the signed gift wrap as JSON (never the follower key: the
 * wrap is encrypted and signed by a one-time key), the stream's [relays], when it [expiry]res, and the wearer's name
 * for the person. [accepted] = the hosts of the relays that answered `OK true`; [nearby] = the wearer's phone took it
 * over Bluetooth. Pinned by contracts/protocol/sync/leave.json `outbox`.
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
    private val hosts: Set<String> get() = relays.mapTo(LinkedHashSet(), LeaveOutbox::host)

    /** The wearer can learn it now: a relay keeps it, or the wearer's phone took it over Bluetooth. */
    val told: Boolean get() = accepted.isNotEmpty() || nearby

    /** Nothing more to send: [LeaveOutbox.NEEDED] relays accepted it (all of them when fewer are listed), or it expired. */
    fun done(now: Long): Boolean = now >= expiry || accepted.count { it in hosts } >= minOf(LeaveOutbox.NEEDED, hosts.size)

    /** The relays still to send it to. */
    fun waitingOn(): List<String> = relays.filter { LeaveOutbox.host(it) !in accepted }

    /** [relay] (a host, as relays tag their answers) answered `OK true`. */
    fun accept(relay: String): PendingLeave {
        val host = LeaveOutbox.host(relay)
        return if (host in hosts) copy(accepted = accepted + host) else this
    }

    override fun toString() = "PendingLeave(accepted=${accepted.size}/${relays.size}, nearby=$nearby)"
}

/** The leaves this device still sends (saved as is: a crash or a restart never loses one). */
@Serializable
data class LeaveOutbox(val leaves: List<PendingLeave> = emptyList()) {
    /** Without the ones that are [PendingLeave.done]. */
    fun pruned(now: Long): LeaveOutbox = LeaveOutbox(leaves.filterNot { it.done(now) })

    /** Every leave for [eventId] after [change]. */
    fun update(eventId: String, change: (PendingLeave) -> PendingLeave): LeaveOutbox =
        LeaveOutbox(leaves.map { if (it.eventId == eventId) change(it) else it })

    /** The oldest leave nobody took yet (its wearer's name is what the person is told about), or null. */
    val untold: PendingLeave? get() = leaves.firstOrNull { !it.told }

    companion object {
        /** Relays that must keep a leave before the follower stops sending it (one may drop it). */
        const val NEEDED = 2

        /** A relay URL's host, as the relay pools tag their answers (`wss://nos.lol/` → `nos.lol`). */
        fun host(url: String): String = url.substringAfter("://").substringBefore('/')
    }
}
