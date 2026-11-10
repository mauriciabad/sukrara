// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.share.transport.RelayAccess

/**
 * The one gate between a launch and the relays it may reach (CLAUDE.md: test runs never use real relays); the Android
 * side of the Mac `LaunchPlan`. Pure: everything it decides on is passed in, so `LaunchPlanTest` covers every case.
 * It exists because a debug build on the emulator connected to the public relays as soon as an identity existed
 * (issue #155).
 *
 * - Release builds: the public relays, always (nothing can point them elsewhere).
 * - Debug builds: no relay, unless a developer pointed them with the debug `RELAYS` command: at a local test relay
 *   (`ws://10.0.2.2:7447` from the emulator, tools/relay), or at the public relays on purpose (`public`).
 */
data class LaunchPlan(
    val relays: RelayAccess,
) {
    companion object {
        /** [relayChoice] = what the debug `RELAYS` command saved (null in release builds and until it runs). */
        fun of(
            debugBuild: Boolean,
            relayChoice: String?,
        ): LaunchPlan =
            if (!debugBuild) {
                LaunchPlan(RelayAccess.Public)
            } else {
                LaunchPlan(relays(relayChoice) ?: RelayAccess.Off)
            }

        /**
         * A `RELAYS` choice: `off` (or nothing) = no relay, `public` = the public relays, else comma-separated
         * `ws://` / `wss://` URLs with a host = only those. Null when a URL isn't one (the command refuses it).
         */
        fun relays(choice: String?): RelayAccess? {
            val text = choice?.trim().orEmpty()
            return when (text.lowercase()) {
                "", "off" -> {
                    RelayAccess.Off
                }

                "public" -> {
                    RelayAccess.Public
                }

                else -> {
                    val urls = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    urls.takeIf { it.isNotEmpty() && it.all(::isRelayUrl) }?.let { RelayAccess.Local(it.distinct()) }
                }
            }
        }

        private fun isRelayUrl(url: String): Boolean {
            val rest =
                when {
                    url.startsWith("ws://") -> url.removePrefix("ws://")
                    url.startsWith("wss://") -> url.removePrefix("wss://")
                    else -> return false
                }
            val host = rest.substringBefore('/').substringBefore(':')
            return host.isNotEmpty() && rest.none { it.isWhitespace() }
        }
    }
}
