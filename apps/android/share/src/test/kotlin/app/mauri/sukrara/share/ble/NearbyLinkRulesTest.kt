// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.ble

import app.mauri.sukrara.protocol.PublicKeyHex
import app.mauri.sukrara.protocol.testing.ContractFiles
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.follower.FollowerEngine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PROTOCOL-v2 "Bluetooth link" on this app's side (contracts/protocol/ble/link-rules.json; the frames and owner cases
 * are `:core:protocol` `BleLinkRulesTest`): how long a follower leaves a phone alone, how many HELLO nonces it keeps,
 * and an unexecuted long write dropped after its idle time like a reassembly buffer (#115). The long write is the
 * Mac's HELLO from contracts/protocol/ble/default-mtu.json.
 */
class NearbyLinkRulesTest {
    private val rules = ContractFiles.json("protocol/ble/link-rules.json")
    private val mtuFx = ContractFiles.json("protocol/ble/default-mtu.json")
    private val mtu = mtuFx.getValue("att_mtu").jsonPrimitive.int
    private val idleMs =
        rules
            .getValue("long_write")
            .jsonObject
            .getValue("idle_s")
            .jsonPrimitive.long * 1000
    private var nowMs = 1_792_087_200_000L
    private val core =
        NearbyServerCore(
            crypto,
            object : NearbyServerCore.Host {
                override fun sid() = mtuFx.getValue("sid").jsonPrimitive.content

                override fun followers(): Collection<PublicKeyHex> = listOf(PublicKeyHex(mtuFx.getValue("follower").jsonPrimitive.content))
            },
        ) { nowMs }

    private val parts =
        mtuFx
            .getValue("long_write")
            .jsonObject
            .getValue("prepared")
            .jsonArray
            .map { it.jsonObject }

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun prepare(peer: String, part: JsonObject) =
        core
            .write(
                peer,
                hex(part.str("value")),
                toRx = true,
                prepared = true,
                offset = part.getValue("offset").jsonPrimitive.int,
                mtu = mtu,
            ).first

    @Test fun aFollowerLeavesAPhoneAloneForTheSpecsTimesAndKeepsItsLastNonces() {
        val leave = rules.getValue("leave_s").jsonObject
        assertEquals(leave.getValue("other_wearer").jsonPrimitive.long, FollowerEngine.LEAVE_OTHER_PHONE_S)
        assertEquals(leave.getValue("nothing_to_pair").jsonPrimitive.long, FollowerEngine.NEARBY_IDLE_S)
        assertEquals(rules.getValue("nonces_kept").jsonPrimitive.int, NearbyLink.NONCES)
    }

    @Test fun anUnexecutedLongWriteIsDroppedAfterTheIdleTime() {
        core.connected("mac", mtu)
        core.subscribed("mac", true)
        parts.forEach { assertTrue(prepare("mac", it)) }
        nowMs += idleMs + 1
        // the whole HELLO was queued, but the late Execute Write finds nothing: no frame, the link isn't named
        assertEquals(true to emptyList<NearbyServerCore.Effect>(), core.execute("mac", commit = true))
        assertTrue(core.named().isEmpty())
    }

    @Test fun aStaleQueueDoesNotBlockTheNextLongWrite() {
        core.connected("mac", mtu)
        core.subscribed("mac", true)
        parts.take(5).forEach { assertTrue(prepare("mac", it)) }
        nowMs += idleMs + 1
        // the dropped parts are gone: the whole long write from offset 0 is a fresh queue and names the link
        parts.forEach { assertTrue(prepare("mac", it)) }
        val (ok, _) = core.execute("mac", commit = true)
        assertTrue(ok)
        assertEquals(listOf(mtuFx.getValue("follower").jsonPrimitive.content), core.named().map { it.follower })
    }

    @Test fun partsWithinTheIdleTimeStillMakeOneFrame() {
        core.connected("mac", mtu)
        core.subscribed("mac", true)
        parts.forEach {
            assertTrue(prepare("mac", it))
            nowMs += idleMs // each part restarts the wait
        }
        core.execute("mac", commit = true)
        assertEquals(1, core.named().size)
    }
}
