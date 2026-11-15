// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

/**
 * How the wearer's readings reach a follower's device now (the follower's home, the Mac panel: docs/FEATURES.md
 * "Sharing over Bluetooth"). Both paths run side by side; Bluetooth names the path while it is connected, as a reading
 * over it doesn't wait for the internet. The Mac's `LinkPath` makes the same choice.
 */
enum class LinkPath {
    /** Connected over Bluetooth to the wearer's phone (its INFO proved it is theirs). */
    Nearby,

    /** Not nearby, and at least one relay is connected. */
    Internet,

    /** Neither: no relay connected and no Bluetooth link, so nothing new can arrive. */
    Offline,
    ;

    companion object {
        /** [relaysConnected] null = not known (the follow isn't running): no path to show, unless Bluetooth is up. */
        fun of(
            nearby: Boolean,
            relaysConnected: Int?,
        ): LinkPath? =
            when {
                nearby -> Nearby
                relaysConnected == null -> null
                relaysConnected > 0 -> Internet
                else -> Offline
            }
    }
}
