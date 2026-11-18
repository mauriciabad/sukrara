// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol

import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.protocol.event.EventFactory
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.event.Rumor
import app.mauri.sukrara.protocol.event.RumorCodec
import app.mauri.sukrara.protocol.follow.LeaveOutbox
import app.mauri.sukrara.protocol.follow.PendingLeave
import app.mauri.sukrara.protocol.testing.ContractFiles
import app.mauri.sukrara.protocol.testing.OgsVectors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
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
 * contracts/protocol/sync/leave.json (PROTOCOL-v2 "Leave"): the `leave` body and its lifetime, real gift wraps every
 * app opens (who sealed them, to whom, when), and the follower's outbox rule (told / done). The wearer's side
 * (`wearer`) is replayed by `:share` `LeaveWearerTest`; the Mac runs the rest (`LeaveFixtureTests`). Regenerate with
 * `-Psukrara.regen=true` (the wraps are made again with fresh randomness; the rest must not change).
 */
class LeaveFixtureTest {
    private val crypto = SdkTestCrypto()
    private val v = OgsVectors()
    private val f = EventFactory(crypto)
    private val now = v.now

    private fun throwaway(seed: String) = crypto.keyPair(SecretKey.ofBytes(Hex.sha256(seed.toByteArray()))!!)!!

    private val keys: Map<String, KeyPair> =
        linkedMapOf(
            "I" to v.key("I"),
            "D" to v.key("D"),
            "G1" to v.key("G1"),
            "F" to v.key("F"),
            "W" to v.key("W"),
            "F2" to throwaway("sukrara/sync/F2"),
            "F3" to throwaway("sukrara/sync/F3"),
            "X" to throwaway("sukrara/leave/X"),
            "I2" to throwaway("sukrara/leave/I2"),
        )

    /** One wrap of the fixture: [from] seals [rumor] at [at] to [to]. */
    private data class Wrap(val name: String, val from: String, val to: String, val rumor: Rumor, val at: Long)

    private val wraps =
        listOf(
            Wrap("leave-F", "F", "I", Rumor.Leave, now - 600),
            Wrap("leave-F-again", "F", "I", Rumor.Leave, now - 300),
            Wrap("request-F", "F", "I", Rumor.Request("t-new", Profile("Mom", null)), now - 250),
            Wrap("leave-F2", "F2", "I", Rumor.Leave, now - 200),
            Wrap("leave-X", "X", "I", Rumor.Leave, now - 150),
            Wrap("leave-F3", "F3", "I", Rumor.Leave, now - 120),
            Wrap("leave-F-other", "F", "I2", Rumor.Leave, now - 100),
            // W's clock runs behind: its leave says before it was approved (the wearer keeps its approval day)
            Wrap("leave-W", "W", "I", Rumor.Leave, now - 3700),
        )

    private fun ttl(r: Rumor) = if (r == Rumor.Leave) Wire.TTL_LEAVE else Wire.TTL_REQUEST

    private fun generate(): JsonObject =
        buildJsonObject {
            put("v", 1)
            put("about", "A follower's leave (PROTOCOL-v2 \"Leave\", contracts/protocol/sync/README.md). Throwaway keys, synthetic data.")
            put(
                "keys",
                buildJsonObject {
                    keys.forEach { (n, k) ->
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
            put("identity", keys.getValue("I").public.hex)
            put("now", now)
            put("body", Json.parseToJsonElement(RumorCodec.encode(Rumor.Leave)))
            put("ttl", Wire.TTL_LEAVE)
            put(
                "events",
                buildJsonObject {
                    wraps.forEach { w ->
                        val ev = f.gift(keys.getValue(w.from), keys.getValue(w.to).public, w.rumor, w.at, w.at + ttl(w.rumor))
                        put(w.name, Json.parseToJsonElement(ev.toJson()))
                    }
                },
            )
            put(
                "wraps",
                buildJsonObject {
                    wraps.forEach { w ->
                        put(
                            w.name,
                            buildJsonObject {
                                put("from", w.from)
                                put("to", w.to)
                                put("type", w.rumor.type)
                                put("at", w.at)
                                put("expiration", w.at + ttl(w.rumor))
                            },
                        )
                    }
                },
            )
            put("wearer", WEARER)
            put("outbox", OUTBOX)
        }

    @Test fun theFixtureHoldsWhatEveryAppMakes() {
        if (System.getProperty("sukrara.regen") == "true") {
            File(Contracts.root, "protocol/sync/leave.json").writeText(pretty.encodeToString(JsonElement.serializer(), generate()) + "\n")
        }
        val fx = ContractFiles.json("protocol/sync/leave.json")
        assertEquals(fx.getValue("body"), Json.parseToJsonElement(RumorCodec.encode(Rumor.Leave)))
        assertEquals(fx.getValue("ttl").jsonPrimitive.long, Wire.TTL_LEAVE)
        assertEquals(Rumor.Leave, RumorCodec.decode(Wire.KIND_TO_OWNER, fx.getValue("body").toString()))
        val fixtureKeys =
            fx.getValue("keys").jsonObject.mapValues { (_, k) ->
                KeyPair(SecretKey.ofHex(k.jsonObject.str("sec"))!!, PublicKeyHex(k.jsonObject.str("pub")))
            }
        val events = fx.getValue("events").jsonObject
        fx.getValue("wraps").jsonObject.forEach { (name, meta) ->
            val m = meta.jsonObject
            val ev = SignedEvent.parse(events.getValue(name).toString())!!
            assertEquals(name, true, crypto.verify(ev))
            assertEquals(name, Wire.KIND_WRAP, ev.kind)
            assertEquals(name, fixtureKeys.getValue(m.str("to")).public.hex, ev.tag("p"))
            assertEquals(
                name,
                m
                    .getValue("expiration")
                    .jsonPrimitive.long
                    .toString(),
                ev.tag("expiration"),
            )
            // only the recipient opens it; the sender is the seal's signer
            if (m.str("to") != "I") assertNull(name, crypto.unwrap(fixtureKeys.getValue("I").secret, ev))
            val opened = crypto.unwrap(fixtureKeys.getValue(m.str("to")).secret, ev)!!
            assertEquals(name, fixtureKeys.getValue(m.str("from")).public, opened.sender)
            assertEquals(name, Wire.KIND_TO_OWNER, opened.kind)
            assertEquals(name, m.getValue("at").jsonPrimitive.long, opened.createdAt)
            assertEquals(name, m.str("type"), RumorCodec.decode(opened.kind, opened.content)?.type)
        }
    }

    /**
     * The follower's outbox: an answer counts for the relay whose connection it came on, when it names the leave and
     * says the relay keeps it (`true`, or `false` with `duplicate:`); told once a relay or the wearer's phone took it,
     * done at two of the valid relays (or every one), or expired.
     */
    @Test fun theOutboxIsDoneOnlyWhenTwoRelaysKeepIt() {
        val fx = ContractFiles.json("protocol/sync/leave.json")
        val outbox = fx.getValue("outbox").jsonObject
        val ttl = fx.getValue("ttl").jsonPrimitive.long
        val made = fx.getValue("now").jsonPrimitive.long
        val id = outbox.str("id")
        outbox.getValue("runs").jsonArray.forEach { r ->
            val run = r.jsonObject
            val name = run.str("name")
            val relays = (run["relays"] ?: outbox.getValue("relays")).jsonArray.map { it.jsonPrimitive.content }
            var leave = PendingLeave("{}", id, relays, made + ttl, "Ana", nearby = run["nearby"]?.jsonPrimitive?.booleanOrNull ?: false)
            run.getValue("answers").jsonArray.forEach {
                val answer = it.jsonObject
                RelayOk.parse(answer.getValue("ok").toString())?.let { ok -> leave = leave.answered(answer.str("from"), ok) }
            }
            val at = made + (run["after"]?.jsonPrimitive?.long ?: 0)
            assertEquals("$name told", run.getValue("told").jsonPrimitive.boolean, leave.told)
            assertEquals("$name done", run.getValue("done").jsonPrimitive.boolean, leave.done(at))
            assertEquals("$name waiting", run.getValue("waiting").jsonArray.map { it.jsonPrimitive.content }, leave.waitingOn())
            assertEquals(name, if (leave.done(at)) emptyList() else listOf(leave), LeaveOutbox(listOf(leave)).pruned(at).leaves)
        }
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private companion object {
        val pretty = Json { prettyPrint = true }

        /** The wearer's side (run by `:share` `LeaveWearerTest`): who it knows, what each wrap does, what it ends with. */
        val WEARER: JsonObject =
            Json
                .parseToJsonElement(
                    """
                    {
                      "epoch": 1,
                      "followers": [
                        {"f": "F", "name": "Mom", "since": -86400},
                        {"f": "W", "name": "Dad", "since": -3600, "writer": true},
                        {"f": "F3", "name": "Old phone", "since": -172800, "revoked": -90000}
                      ],
                      "pending": [{"f": "F2", "name": "Ana", "token": "t2", "at": -500}],
                      "invites": [{"t": "t2", "x": 80000, "claimedBy": "F2"}, {"t": "t-new", "x": 80000}],
                      "steps": [
                        {"event": "leave-F", "expect": "left", "epoch": 2, "keysTo": ["W"]},
                        {"event": "leave-F-again", "expect": "ignored", "epoch": 2, "keysTo": []},
                        {"event": "request-F", "expect": "ignored", "epoch": 2, "keysTo": []},
                        {"event": "leave-F2", "expect": "withdrawn", "epoch": 2, "keysTo": []},
                        {"event": "leave-X", "expect": "ignored", "epoch": 2, "keysTo": []},
                        {"event": "leave-F3", "expect": "ignored", "epoch": 2, "keysTo": []},
                        {"event": "leave-F-other", "expect": "ignored", "epoch": 2, "keysTo": []},
                        {"event": "leave-W", "expect": "left", "epoch": 3, "keysTo": []}
                      ],
                      "expected": {
                        "followers": [
                          {"f": "F", "revoked": -600, "left": true},
                          {"f": "W", "revoked": -3600, "left": true},
                          {"f": "F3", "revoked": -90000, "left": false}
                        ],
                        "writers": [],
                        "pending": [],
                        "invitesDone": ["t2"],
                        "epoch": 3
                      }
                    }
                    """.trimIndent(),
                ).jsonObject

        /**
         * The follower's outbox rule: a leave ([id]) sent to `relays`, the relays' NIP-01 answers (`from`: the URL its
         * connection was opened with; `ok`: what the relay sent), [after] seconds since it was made.
         */
        val OUTBOX: JsonObject =
            Json
                .parseToJsonElement(
                    """
                    {
                      "id": "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5",
                      "relays": ["wss://a.example/", "wss://B.example", "wss://c.example:4848", "wss://c.example/inbox"],
                      "runs": [
                        {"name": "online", "answers": [
                          {"from": "wss://a.example/", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]},
                          {"from": "wss://B.example", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, "duplicate: already have this event"]}],
                         "told": true, "done": true, "waiting": ["wss://c.example:4848", "wss://c.example/inbox"]},
                        {"name": "one-relay", "answers": [
                          {"from": "wss://a.example/", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]}],
                         "told": true, "done": false, "waiting": ["wss://B.example", "wss://c.example:4848", "wss://c.example/inbox"]},
                        {"name": "duplicate-only", "answers": [
                          {"from": "wss://c.example:4848", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, "duplicate: already have this event"]}],
                         "told": true, "done": false, "waiting": ["wss://a.example/", "wss://B.example", "wss://c.example/inbox"]},
                        {"name": "duplicate-false", "answers": [
                          {"from": "wss://c.example/inbox", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", false, "duplicate: already have this event"]},
                          {"from": "wss://a.example/", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true]}],
                         "told": true, "done": true, "waiting": ["wss://B.example", "wss://c.example:4848"]},
                        {"name": "spelled-another-way", "answers": [
                          {"from": "wss://A.example:443", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]},
                          {"from": "wss://b.example/", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]}],
                         "told": true, "done": true, "waiting": ["wss://c.example:4848", "wss://c.example/inbox"]},
                        {"name": "another-port-or-path-is-another-relay", "answers": [
                          {"from": "wss://c.example", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]},
                          {"from": "wss://c.example:4849", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]},
                          {"from": "wss://c.example/outbox", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]}],
                         "told": false, "done": false, "waiting": ["wss://a.example/", "wss://B.example", "wss://c.example:4848", "wss://c.example/inbox"]},
                        {"name": "refused", "answers": [
                          {"from": "wss://a.example/", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", false, "blocked: you are banned from posting here"]},
                          {"from": "wss://B.example", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", false, "rate-limited: slow down there chief", "1000"]},
                          {"from": "wss://c.example:4848", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", false, "auth-required: we only accept events from registered users"]},
                          {"from": "wss://c.example/inbox", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", "true", ""]}],
                         "told": false, "done": false, "waiting": ["wss://a.example/", "wss://B.example", "wss://c.example:4848", "wss://c.example/inbox"]},
                        {"name": "another-event", "answers": [
                          {"from": "wss://a.example/", "ok": ["OK", "13d9861f4ff3374f7a53252dc5a4e7623b44455d5d915651f6f9041060d70164", true, ""]},
                          {"from": "wss://B.example", "ok": ["NOTICE", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5"]}],
                         "told": false, "done": false, "waiting": ["wss://a.example/", "wss://B.example", "wss://c.example:4848", "wss://c.example/inbox"]},
                        {"name": "offline", "answers": [], "told": false, "done": false,
                         "waiting": ["wss://a.example/", "wss://B.example", "wss://c.example:4848", "wss://c.example/inbox"]},
                        {"name": "nearby-only", "nearby": true, "answers": [], "told": true, "done": false,
                         "waiting": ["wss://a.example/", "wss://B.example", "wss://c.example:4848", "wss://c.example/inbox"]},
                        {"name": "a-relay-not-listed", "answers": [
                          {"from": "wss://x.example", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]},
                          {"from": "wss://a.example/", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]}],
                         "told": true, "done": false, "waiting": ["wss://B.example", "wss://c.example:4848", "wss://c.example/inbox"]},
                        {"name": "one-relay-stream", "relays": ["wss://a.example"], "answers": [
                          {"from": "wss://a.example", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]}],
                         "told": true, "done": true, "waiting": []},
                        {"name": "only-valid-relays-count", "relays": ["wss://a.example", "ws://b.example", "wss://localhost:7447"], "answers": [
                          {"from": "wss://a.example", "ok": ["OK", "cc44fab9b469c92ca7617fafb12a87ccdc1d682b4ac47f1fd7cec7dfb04db8e5", true, ""]}],
                         "told": true, "done": true, "waiting": []},
                        {"name": "no-valid-relay", "relays": ["ws://b.example"], "answers": [], "told": false, "done": false, "waiting": []},
                        {"name": "expired", "answers": [], "after": 31536000, "told": false, "done": true,
                         "waiting": ["wss://a.example/", "wss://B.example", "wss://c.example:4848", "wss://c.example/inbox"]}
                      ]
                    }
                    """.trimIndent(),
                ).jsonObject
    }
}
