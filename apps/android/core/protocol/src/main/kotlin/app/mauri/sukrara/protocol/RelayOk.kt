// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * A relay's NIP-01 answer to an event we sent, `["OK", <event id>, <true|false>, <message>, …]` (PROTOCOL-v2 "Relay
 * answers", contracts/protocol/relays/relay-answers.json `answers`; Mac `RelayOk`). [accepted] is the third element
 * when it is a JSON boolean (anything else: false); [message] the fourth when it is a string (old relays leave it out).
 */
data class RelayOk(val eventId: String, val accepted: Boolean, val message: String = "") {
    /**
     * The relay keeps the event: `true` whatever the message (empty, `duplicate:`, `pow:` …), or `false` with
     * `duplicate:` — it already holds that exact id (NIP-20 says such a relay MUST answer `true`; some answer `false`).
     * Never any other `false`.
     */
    val kept: Boolean get() = accepted || message.startsWith(DUPLICATE)

    /** NIP-42: the relay takes the event only from an authenticated client: AUTH, then send it again. */
    val authRequired: Boolean get() = !accepted && message.startsWith(AUTH_REQUIRED)

    companion object {
        private const val DUPLICATE = "duplicate:"
        private const val AUTH_REQUIRED = "auth-required:"

        /** The answer in a relay message already parsed as [frame]; null when it isn't an `OK` with a string id. */
        fun of(frame: JsonArray): RelayOk? {
            fun str(i: Int) = (frame.getOrNull(i) as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (str(0) != "OK") return null
            val id = str(1) ?: return null
            val accepted = (frame.getOrNull(2) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: false
            return RelayOk(id, accepted, str(3).orEmpty())
        }

        /** The answer in the relay message [text]; null when it isn't one. */
        fun parse(text: String): RelayOk? = runCatching { Json.parseToJsonElement(text) as? JsonArray }.getOrNull()?.let(::of)
    }
}
