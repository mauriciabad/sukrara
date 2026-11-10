// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol

import app.mauri.sukrara.protocol.ble.BleFraming
import app.mauri.sukrara.protocol.ble.BleMessages
import app.mauri.sukrara.protocol.testing.ContractFiles
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * contracts/protocol/ble/link-rules.json (PROTOCOL-v2 "Bluetooth link"): frames within one ATT write, the long-write
 * limits, and whose phone an INFO comes from. The wearer's side (long writes, the leave times) is `:share`
 * `NearbyLinkRulesTest`; the Mac runs the same file in `BleLinkRulesTests`.
 */
class BleLinkRulesTest {
    private val fx = ContractFiles.json("protocol/ble/link-rules.json")

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content

    private fun JsonObject.strOrNull(k: String) = get(k)?.takeIf { it != JsonNull }?.jsonPrimitive?.content

    @Test fun everyFrameFitsOneAttWriteOrNotification() {
        fx.getValue("frames").jsonArray.map { it.jsonObject }.forEach { c ->
            val mtu = c.getValue("att_mtu").jsonPrimitive.int
            val frames = BleFraming.frames(7, ByteArray(3000) { it.toByte() }, mtu)
            assertEquals("ATT MTU $mtu", c.getValue("max_frame").jsonPrimitive.int, frames.maxOf { it.size })
        }
    }

    @Test fun longWriteLimitsAreTheReassemblyLimits() {
        val lw = fx.getValue("long_write").jsonObject
        assertEquals(lw.getValue("max_bytes").jsonPrimitive.int, BleFraming.MAX_ATTRIBUTE)
        assertEquals(lw.getValue("idle_s").jsonPrimitive.int * 1000L, BleFraming.IDLE_MS)
    }

    @Test fun whoseInfoItIs() {
        fx.getValue("owner").jsonArray.map { it.jsonObject }.forEach { c ->
            val got =
                BleMessages.owner(
                    c.getValue("info").jsonObject,
                    c.strOrNull("identity")?.let(::PublicKeyHex),
                    c.strOrNull("sid"),
                    c.getValue("nonces").jsonArray.map { it.jsonPrimitive.content },
                )
            assertEquals(c.str("name"), c.str("expect"), got.name.lowercase())
        }
    }
}
