// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol

import app.mauri.sukrara.protocol.testing.ContractFiles
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** contracts/protocol/relays/relay-answers.json: which relay a URL names and what an `OK` says (the Mac runs it too). */
class RelayAnswersTest {
    private val fx = ContractFiles.json("protocol/relays/relay-answers.json")

    @Test fun everyUrlNamesItsRelay() =
        fx.getValue("keys").jsonArray.forEach {
            val o = it.jsonObject
            val url = o.getValue("url").jsonPrimitive.content
            assertEquals(url, o.getValue("key").jsonPrimitive.content, Wire.relayKey(url))
        }

    @Test fun everyAnswer() =
        fx.getValue("answers").jsonArray.forEach {
            val o = it.jsonObject
            val frame = o.getValue("frame").toString()
            val ok = RelayOk.parse(frame)
            val id =
                o
                    .getValue("id")
                    .takeIf { e -> e != JsonNull }
                    ?.jsonPrimitive
                    ?.content
            assertEquals(frame, id, ok?.eventId)
            assertEquals(frame, o.getValue("kept").jsonPrimitive.boolean, ok?.kept ?: false)
            assertEquals(frame, o.getValue("auth").jsonPrimitive.boolean, ok?.authRequired ?: false)
        }
}
