// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.protocol.KeyPair
import app.mauri.sukrara.protocol.PublicKeyHex
import app.mauri.sukrara.protocol.SecretKey
import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.event.Rumor
import app.mauri.sukrara.share.OwnerFixtures
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.owner.usecase.ControlEvents
import app.mauri.sukrara.share.owner.usecase.HandleInbox
import app.mauri.sukrara.share.owner.usecase.InboxSignal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wearer's side of contracts/protocol/sync/leave.json (PROTOCOL-v2 "Leave"): an active follower's leave marks it
 * left at when it left (kept between its approval and now) and rotates (keys to the others, no `revoked` notice), a
 * waiting request's leave withdraws it, and everything else (a second copy, a request from the key that left, a
 * stranger, a removed follower, a wrap for another identity) changes nothing.
 */
class LeaveWearerTest {
    private val fixture = Json.parseToJsonElement(Contracts.file("protocol/sync/leave.json").readText()).jsonObject
    private val now = fixture.getValue("now").jsonPrimitive.long
    private val keys =
        fixture.getValue("keys").jsonObject.mapValues { (_, k) ->
            KeyPair(SecretKey.ofHex(k.jsonObject.str("sec"))!!, PublicKeyHex(k.jsonObject.str("pub")))
        }
    private val wearer = fixture.getValue("wearer").jsonObject
    private val events = fixture.getValue("events").jsonObject

    private fun pub(name: String) = keys.getValue(name).public.hex

    private fun at(o: JsonObject, key: String) = now + o.getValue(key).jsonPrimitive.long

    /** The wearer before the steps: the fixture's identity, phone, epoch, followers, waiting request and invites. */
    private fun start(): OwnerState =
        OwnerState(
            identityHex = keys.getValue("I").secret.revealHex(),
            deviceHex = keys.getValue("D").secret.revealHex(),
            sid = fixture.str("sid"),
            epochs = listOf(EpochKey(wearer.getValue("epoch").jsonPrimitive.int, keys.getValue("G1").secret.revealHex())),
            followers =
                wearer.getValue("followers").jsonArray.map { it.jsonObject }.map { f ->
                    FollowerRecord(pub(f.str("f")), f.str("name"), at(f, "since"), revoked = f["revoked"]?.let { at(f, "revoked") })
                },
            writers =
                wearer
                    .getValue("followers")
                    .jsonArray
                    .map { it.jsonObject }
                    .filter { it["writer"]?.jsonPrimitive?.booleanOrNull == true }
                    .map { pub(it.str("f")) }
                    .toSet(),
            pending =
                wearer.getValue("pending").jsonArray.map { it.jsonObject }.map { p ->
                    PendingRequest(pub(p.str("f")), p.str("name"), p.str("token"), "0000", at(p, "at"))
                },
            invites =
                wearer.getValue("invites").jsonArray.map { it.jsonObject }.map { i ->
                    InviteRecord(i.str("t"), at(i, "x"), claimedBy = i["claimedBy"]?.let { pub(it.jsonPrimitive.content) })
                },
        )

    @Test fun theWearerFollowsTheFixture() {
        val handle = HandleInbox(OwnerFixtures.context())
        var s = start()
        val named = keys.entries.associate { (n, k) -> k.public.hex to n }
        wearer.getValue("steps").jsonArray.map { it.jsonObject }.forEach { step ->
            val name = step.str("event")
            val before = s
            val handled = handle(s, SignedEvent.parse(events.getValue(name).toString())!!, now)
            s = handled.step.state
            val verdict =
                when {
                    handled.signal is InboxSignal.FollowerLeft -> "left"
                    before.pending.size > s.pending.size -> "withdrawn"
                    s == before && handled.step.publish.isEmpty() -> "ignored"
                    else -> "changed"
                }
            assertEquals(name, step.str("expect"), verdict)
            assertEquals("$name epoch", step.getValue("epoch").jsonPrimitive.int, s.current.n)
            // keys go to the followers that remain; nobody gets a `revoked` notice for a leave
            val wraps = handled.step.publish.filter { it.kind == Wire.KIND_WRAP }
            val keysTo = step.getValue("keysTo").jsonArray.map { it.jsonPrimitive.content }
            assertEquals("$name keys", keysTo.toSet(), wraps.mapNotNull { named[it.tag("p")] }.toSet())
            wraps.forEach { w ->
                val to = keys.getValue(named.getValue(w.tag("p")!!))
                assertTrue("$name: only key deliveries", OwnerFixtures.open(to, w) is Rumor.KeyDelivery)
            }
        }
        val expected = wearer.getValue("expected").jsonObject
        expected.getValue("followers").jsonArray.map { it.jsonObject }.forEach { e ->
            val f = s.followers.single { it.key == pub(e.str("f")) }
            assertEquals(e.str("f"), at(e, "revoked"), f.revoked)
            assertEquals(e.str("f"), e.getValue("left").jsonPrimitive.boolean, f.left)
        }
        assertEquals(
            expected
                .getValue("writers")
                .jsonArray
                .map { pub(it.jsonPrimitive.content) }
                .toSet(),
            s.writers,
        )
        assertEquals(expected.getValue("pending").jsonArray.size, s.pending.size)
        assertEquals(
            expected
                .getValue("invitesDone")
                .jsonArray
                .map { it.jsonPrimitive.content }
                .toSet(),
            s.invites
                .filter { it.done }
                .map { it.token }
                .toSet(),
        )
        assertEquals(expected.getValue("epoch").jsonPrimitive.int, s.current.n)
        // the backup carries who left, so a restored phone shows it too and never takes that key back
        val backup = ControlEvents.backupPayload(crypto, s)
        assertEquals(
            setOf(pub("F"), pub("W")),
            backup.followers
                .filter { it.left }
                .map { it.key }
                .toSet(),
        )
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
