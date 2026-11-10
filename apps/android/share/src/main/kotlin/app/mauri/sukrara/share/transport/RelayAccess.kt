// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.transport

import app.mauri.sukrara.protocol.Wire

/**
 * Which relays this launch's [RelayPool]s connect to, whatever relays the protocol names (the default list, an
 * invite's, a stream's). Chosen once per launch by the app's `LaunchPlan`: release builds always get [Public].
 */
sealed interface RelayAccess {
    /** The relays a pool connects to when the protocol names [wanted]. */
    fun connectTo(wanted: List<String>): List<String>

    /** The relays the protocol names that pass its relay-list rules (`Wire.validRelays`; release builds). */
    data object Public : RelayAccess {
        override fun connectTo(wanted: List<String>) = Wire.validRelays(wanted)
    }

    /** No relay at all: a debug build that wasn't pointed anywhere (emulator QA never reaches the public relays). */
    data object Off : RelayAccess {
        override fun connectTo(wanted: List<String>) = emptyList<String>()
    }

    /** Debug builds only: every pool connects to the developer's test relays [urls] (`ws://` allowed) instead. */
    data class Local(val urls: List<String>) : RelayAccess {
        override fun connectTo(wanted: List<String>) = urls
    }
}
