// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import app.mauri.sukrara.share.ble.NearbyServerCore
import app.mauri.sukrara.share.ble.NearbyStatus
import app.mauri.sukrara.share.owner.FollowerRecord

/**
 * How readings travel on the wearer's sharing page: *Over the internet* ([internet]) and its upload interval
 * ([intervalMin], 0 = every reading), the Bluetooth switch ([on]), what the Bluetooth server says ([status]), the
 * follower devices connected now whose identity proof named a follower ([links]) and whether *Pair nearby* is open
 * ([pairingUntil]).
 */
data class NearbyUi(
    val on: Boolean = true,
    val status: NearbyStatus = NearbyStatus.Stopped,
    val links: List<NearbyServerCore.Named> = emptyList(),
    /** *Pair nearby* is open until this epoch second (null = closed). */
    val pairingUntil: Long? = null,
    val internet: Boolean = true,
    val intervalMin: Double = 0.0,
)

/**
 * Settings › Sharing › Nearby devices, like Android's Connected devices: the follower devices connected now
 * (*Connected*, oldest link first), then the followers paired with *Pair nearby* that aren't (*Disconnected*, by name).
 * Only active followers: a link whose proof named nobody, or a removed follower, is never listed.
 */
object NearbyDevices {
    /** One row: [device] = `phone` / `mac` from the identity proof while connected, null for a paired one out of range. */
    data class Row(val follower: FollowerRecord, val connected: Boolean, val device: String? = null)

    fun rows(
        followers: List<FollowerRecord>,
        paired: Set<String>,
        links: List<NearbyServerCore.Named>,
    ): List<Row> {
        val active = followers.filter { it.active }.associateBy { it.key }
        val connected = links.sortedBy { it.sinceMs }.mapNotNull { l -> active[l.follower]?.let { Row(it, true, l.device) } }
        val linked = connected.map { it.follower.key }.toSet()
        val away =
            paired
                .mapNotNull { active[it] }
                .filter { it.key !in linked }
                .sortedBy { it.name.lowercase() }
                .map { Row(it, false) }
        return connected + away
    }
}
