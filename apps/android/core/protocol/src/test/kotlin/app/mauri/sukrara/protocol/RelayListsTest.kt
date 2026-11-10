// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol

import app.mauri.sukrara.protocol.event.RosterCodec
import app.mauri.sukrara.protocol.testing.ContractFiles
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** contracts/protocol/relays/relay-lists.json: which received relay URLs are kept (the Mac runs the same file). */
class RelayListsTest {
    private val fx = ContractFiles.json("protocol/relays/relay-lists.json")

    @Test fun capMatchesTheContract() = assertEquals(fx.getValue("max").jsonPrimitive.int, Wire.MAX_RELAYS)

    @Test fun everyUrl() =
        fx.getValue("urls").jsonArray.forEach {
            val o = it.jsonObject
            val url = o.getValue("url").jsonPrimitive.content
            assertEquals(url, o.getValue("ok").jsonPrimitive.boolean, Wire.isRelayUrl(url))
        }

    @Test fun everyList() =
        fx.getValue("lists").jsonArray.forEach {
            val o = it.jsonObject
            val input = o.getValue("in").jsonArray.map { u -> u.jsonPrimitive.content }
            assertEquals(input.toString(), o.getValue("out").jsonArray.map { u -> u.jsonPrimitive.content }, Wire.validRelays(input))
        }

    @Test fun aRosterKeepsOnlyValidRelays() {
        val device = "a".repeat(64)
        val relays = """["wss://localhost:7447","ws://nos.lol","wss://10.0.2.2","wss://nos.lol"]"""
        val body = """{"v":2,"epoch":1,"devices":["$device"],"relays":$relays}"""
        assertEquals(listOf("wss://nos.lol"), RosterCodec.decode(body).relays)
    }
}
