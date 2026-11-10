// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol

import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.protocol.ble.BleMessages
import app.mauri.sukrara.protocol.event.EventFactory
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.event.Rumor
import app.mauri.sukrara.protocol.event.RumorCodec
import app.mauri.sukrara.protocol.follow.FollowResult
import app.mauri.sukrara.protocol.follow.FollowStatus
import app.mauri.sukrara.protocol.follow.FollowerState
import app.mauri.sukrara.protocol.follow.NearbyPair
import app.mauri.sukrara.protocol.follow.StreamFollower
import app.mauri.sukrara.protocol.invite.NearbyPairing
import app.mauri.sukrara.protocol.testing.ContractFiles
import app.mauri.sukrara.protocol.testing.OgsVectors
import app.mauri.sukrara.protocol.testing.OgsVectors.Companion.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * contracts/protocol/ble/nearby-pairing.json (PROTOCOL-v2 §Nearby pairing): commitment, pairing code, offer expiry, the
 * messages, and a follower sequence of real signed gift wraps. The Mac (`NearbyPairingVectorsTests`) reads the same
 * file. Regenerate with `-Psukrara.regen=true` (wraps are re-encrypted; the expectations must not change).
 */
class NearbyPairingVectorsTest {
    private val crypto = SdkTestCrypto()
    private val v = OgsVectors()
    private val f = EventFactory(crypto)
    private val i = v.key("I")
    private val fol = v.key("F")
    private val g1 = v.key("G1")
    private val d = v.key("D")
    private val impostor = throwaway("sukrara/nearby/X")
    private val now = v.now
    private val relays = listOf("wss://relay.example.org")

    private fun throwaway(seed: String) = crypto.keyPair(SecretKey.ofBytes(Hex.sha256(seed.toByteArray()))!!)!!

    private fun hex(seed: String) = Hex.encode(Hex.sha256(seed.toByteArray()))

    private val token = "C_TX2IzrmRf7hg8cATyckQ"
    private val na = hex("sukrara/nearby/na")
    private val nb = hex("sukrara/nearby/nb")

    private fun generate(): JsonObject {
        val commit = NearbyPairing.commit(nb)
        val code = NearbyPairing.code(i.public, fol.public, token, na, nb)
        // a code with a leading zero, so both apps pad it
        val padded =
            generateSequence(0) { it + 1 }
                .map { hex("sukrara/nearby/nb/$it") }
                .first { NearbyPairing.code(i.public, fol.public, token, na, it).startsWith("0") }
        val profile = Profile.of(Json.parseToJsonElement("""{"name":"Alex","avatar":{"v":1,"c":0,"s":"cube","a":45,"n":1497950029}}"""))
        val pairing = BleMessages.Pairing(i.public, relays, token, now + NearbyPairing.OFFER_S, profile, commit)
        val info = Json.parseToJsonElement(BleMessages.info(v.sid, NONCE, pairing, now).decodeToString())
        val body = (info as JsonArray)[1].jsonObject
        val pair = body.getValue("pair").jsonObject

        fun without(key: String) = JsonObject(body + ("pair" to JsonObject(pair - key)))

        fun with(key: String, value: JsonElement) = JsonObject(body + ("pair" to JsonObject(pair + (key to value))))
        val events =
            linkedMapOf(
                "pair-impostor" to f.gift(impostor, fol.public, Rumor.PairNonce(nb), now - 50, now + Wire.TTL_REQUEST),
                "pair-wrong" to f.gift(i, fol.public, Rumor.PairNonce(hex("sukrara/nearby/other")), now - 40, now + Wire.TTL_REQUEST),
                "key-early" to
                    f.gift(
                        i,
                        fol.public,
                        Rumor.KeyDelivery(v.sid, 1, crypto.nsec(g1.secret), listOf(d.public.hex), relays),
                        now - 30,
                        now + Wire.TTL_KEY_DELIVERY,
                    ),
                "pair" to f.gift(i, fol.public, Rumor.PairNonce(nb), now - 20, now + Wire.TTL_REQUEST),
                "pair-again" to f.gift(i, fol.public, Rumor.PairNonce(nb), now - 10, now + Wire.TTL_REQUEST),
            )
        val steps =
            listOf(
                "pair-impostor" to "rejected:wrap-not-from-identity",
                "pair-wrong" to "rejected:pair-commit",
                "key-early" to "ignored:unconfirmed",
                "pair" to "accepted:pair",
                "pair-again" to "ignored:pair-known",
                null to "accepted:key",
            )
        return buildJsonObject {
            put("v", 1)
            put(
                "about",
                "Nearby pairing numeric comparison (PROTOCOL-v2 §Nearby pairing). Throwaway keys from the OGS draft-1 " +
                    "vectors (I wearer, F follower, X impostor). Generated by Android NearbyPairingVectorsTest " +
                    "(-Psukrara.regen=true); read by it and Mac NearbyPairingVectorsTests.",
            )
            put("now", now)
            put(
                "keys",
                buildJsonObject {
                    listOf("I" to i, "F" to fol, "X" to impostor, "D" to d, "G1" to g1).forEach { (n, k) ->
                        put(
                            n,
                            buildJsonObject {
                                put("sec", k.secret.revealHex())
                                put("pub", k.public.hex)
                            },
                        )
                    }
                },
            )
            put("sid", v.sid)
            put("token", token)
            put("na", na)
            put("nb", nb)
            put("commit", commit)
            put("code", code)
            put(
                "codes",
                buildJsonArray {
                    listOf(nb, padded).forEach { n ->
                        add(
                            buildJsonObject {
                                put("i", i.public.hex)
                                put("f", fol.public.hex)
                                put("t", token)
                                put("na", na)
                                put("nb", n)
                                put("c", NearbyPairing.commit(n))
                                put("code", NearbyPairing.code(i.public, fol.public, token, na, n))
                            },
                        )
                    }
                },
            )
            put(
                "offer_expiry",
                buildJsonArray {
                    listOf(now to false, now + 1 to true, now + 600 to true, now + 720 to true, now + 721 to false, now + 86_400 to false)
                        .forEach { (x, ok) ->
                            add(
                                buildJsonObject {
                                    put("x", x)
                                    put("listed", ok)
                                },
                            )
                        }
                },
            )
            put("info", info)
            put(
                "info_not_listed",
                buildJsonObject {
                    put("no-commit", without("c"))
                    put("short-commit", with("c", JsonPrimitive(commit.take(32))))
                    put("too-long", with("x", JsonPrimitive(now + 3600)))
                    put("expired", with("x", JsonPrimitive(now)))
                },
            )
            put("request", Json.parseToJsonElement(RumorCodec.encode(Rumor.Request(token, profile, na))))
            put("pair", Json.parseToJsonElement(RumorCodec.encode(Rumor.PairNonce(nb))))
            put(
                "follower",
                buildJsonObject {
                    put("follower", "F")
                    put("identity", i.public.hex)
                    put(
                        "start",
                        buildJsonObject {
                            put("status", "requested")
                            put("t", token)
                            put("c", commit)
                            put("na", na)
                        },
                    )
                    put("events", JsonObject(events.mapValues { Json.parseToJsonElement(it.value.toJson()) }))
                    put(
                        "steps",
                        buildJsonArray {
                            steps.forEach { (event, expect) ->
                                add(
                                    buildJsonObject {
                                        if (event == null) put("confirm", true) else put("event", event)
                                        put("expect", expect)
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "expected",
                        buildJsonObject {
                            put("status", "active")
                            put("code", code)
                            put("confirmed", true)
                            put("epochs", buildJsonArray { add(JsonPrimitive(1)) })
                        },
                    )
                },
            )
        }
    }

    private fun fixture(): JsonObject {
        if (System.getProperty("sukrara.regen") == "true") {
            val file = File(Contracts.root, "protocol/ble/nearby-pairing.json")
            file.writeText(pretty.encodeToString(JsonElement.serializer(), generate()) + "\n")
        }
        return ContractFiles.json("protocol/ble/nearby-pairing.json")
    }

    @Test fun commitmentAndCode() {
        val fx = fixture()
        assertEquals(fx.str("commit"), NearbyPairing.commit(fx.str("nb")))
        fx.getValue("codes").jsonArray.map { it.jsonObject }.forEach { c ->
            assertEquals(c.str("c"), NearbyPairing.commit(c.str("nb")))
            assertEquals(
                c.str("code"),
                NearbyPairing.code(PublicKeyHex(c.str("i")), PublicKeyHex(c.str("f")), c.str("t"), c.str("na"), c.str("nb")),
            )
        }
        assertEquals(
            fx.str("code"),
            fx
                .getValue("codes")
                .jsonArray[0]
                .jsonObject
                .str("code"),
        )
    }

    @Test fun offerExpiryAndInfo() {
        val fx = fixture()
        val now = fx.str("now").toLong()
        fx.getValue("offer_expiry").jsonArray.map { it.jsonObject }.forEach { c ->
            val x = c.getValue("x").jsonPrimitive.long
            assertEquals("x=$x", c.getValue("listed").jsonPrimitive.boolean, NearbyPairing.offerExpiryOk(x, now))
        }
        val body = (fx.getValue("info") as JsonArray)[1].jsonObject
        val offer = BleMessages.pairing(body, now)!!
        assertEquals(fx.str("commit"), offer.commit)
        assertEquals(fx.str("token"), offer.token)
        assertEquals(
            PublicKeyHex(
                fx
                    .getValue("keys")
                    .jsonObject
                    .getValue("I")
                    .jsonObject
                    .str("pub"),
            ),
            offer.identity,
        )
        // the wearer writes the same INFO
        val nonce = body.str("nonce")
        assertEquals(fx.getValue("info"), Json.parseToJsonElement(BleMessages.info(fx.str("sid"), nonce, offer, now).decodeToString()))
        fx.getValue("info_not_listed").jsonObject.forEach { (why, bad) -> assertNull(why, BleMessages.pairing(bad.jsonObject, now)) }
    }

    @Test fun messages() {
        val fx = fixture()
        val request = RumorCodec.decode(Wire.KIND_TO_OWNER, fx.getValue("request").toString()) as Rumor.Request
        assertEquals(fx.str("na"), request.nonce)
        assertEquals(fx.getValue("request"), Json.parseToJsonElement(RumorCodec.encode(request)))
        val pair = RumorCodec.decode(Wire.KIND_TO_FOLLOWER, fx.getValue("pair").toString()) as Rumor.PairNonce
        assertEquals(fx.str("nb"), pair.nonce)
        assertEquals(fx.getValue("pair"), Json.parseToJsonElement(RumorCodec.encode(pair)))
    }

    /** The follower never uses the wearer's key before the person confirmed the code, and only for the committed nonce. */
    @Test fun followerSequence() {
        val fx = fixture()
        val seq = fx.getValue("follower").jsonObject
        val keys = fx.getValue("keys").jsonObject
        val me = OgsVectors.pair(keys.getValue(seq.str("follower")).jsonObject)
        val start = seq.getValue("start").jsonObject
        val follower =
            StreamFollower(
                crypto,
                FollowerState(
                    follower = me,
                    identity = PublicKeyHex(seq.str("identity")),
                    pair = NearbyPair(start.str("t"), start.str("c"), start.str("na")),
                ),
            )
        assertEquals("requested", start.str("status"))
        follower.requested()
        val events = seq.getValue("events").jsonObject
        val now = fx.str("now").toLong()
        seq.getValue("steps").jsonArray.map { it.jsonObject }.forEach { s ->
            val result: FollowResult? =
                if ("confirm" in s) {
                    follower.confirmPairing()
                } else {
                    follower.onEvent(SignedEvent.parse(events.getValue(s.str("event")).toString())!!, now)
                }
            assertEquals(s.toString(), s.str("expect"), result?.code)
        }
        val expected = seq.getValue("expected").jsonObject
        assertEquals(FollowStatus.Active, follower.state.status)
        assertEquals(
            expected.str("status"),
            follower.state.status.name
                .lowercase(),
        )
        assertEquals(expected.str("code"), follower.state.pair?.code)
        assertEquals(expected.getValue("confirmed").jsonPrimitive.boolean, follower.state.pair?.confirmed)
        assertEquals(
            expected.getValue("epochs").jsonArray.map { it.jsonPrimitive.content.toInt() },
            follower.state.epochs.values
                .map { it.n }
                .sorted(),
        )
    }

    private companion object {
        const val NONCE = "d06523d57ab24aea0bae2e3fe29d950e"
        val pretty = Json { prettyPrint = true }
    }
}
