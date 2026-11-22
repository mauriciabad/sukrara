// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.model.AlertControls
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.integrations.localserver.HttpRequest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.time.Instant

/**
 * contracts/watchface/payload entries.json and mutes.json on Android's endpoint: the signature, the send time, the id and
 * the kind are checked here and nothing reaches the phone's path when one fails; the rest goes to the phone's entry path
 * (its own rules: WatchEntryTest in :feature:treatments) with the fields as sent, and its outcome comes back as the
 * contract's answer. Mutes run the alert runtime's own actions, each id once.
 */
class WatchWritesTest {
    private val entries = Json.parseToJsonElement(Contracts.file("watchface/payload/entries.json").readText()).jsonObject
    private val mutes = Json.parseToJsonElement(Contracts.file("watchface/payload/mutes.json").readText()).jsonObject
    private val now = entries.getValue("now").jsonPrimitive.long
    private val clock = FakeClock(Instant.ofEpochSecond(now))
    private val key =
        entries
            .getValue("key")
            .jsonPrimitive.content
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

    /** The phone's path as the case's answer says it ended; records what it was handed. */
    private class Port : WatchEntries {
        var outcome: WatchLogged = WatchLogged.Off
        val got = ArrayList<WatchEntry>()
        override val open = flowOf(true)

        override suspend fun add(e: WatchEntry): WatchLogged {
            got += e
            return outcome
        }
    }

    private fun request(
        path: String,
        c: JsonObject,
    ): HttpRequest {
        val body = c.getValue("body").jsonObject
        val form = c.getValue("encoding").jsonPrimitive.content == "form"
        val text =
            if (form) {
                body.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v.jsonPrimitive.content, "UTF-8") }
            } else {
                body.toString()
            }
        val type = if (form) "application/x-www-form-urlencoded" else "application/json"
        return HttpRequest("POST", path, emptyMap(), mapOf("content-type" to type), body = text)
    }

    private fun treatment(
        e: JsonObject?,
        id: String,
    ): Treatment? {
        e ?: return null
        val at = Instant.ofEpochSecond(e.getValue("t").jsonPrimitive.long)
        return Treatment(
            id,
            KINDS.getValue(e.getValue("k").jsonPrimitive.content),
            e.getValue("a").jsonPrimitive.double,
            at,
            modifiedAt = at,
            origin = TreatmentOrigin.Phone,
        )
    }

    @Test
    fun everyEntryCase() {
        for (c in entries.getValue("cases").jsonArray.map { it.jsonObject }) {
            val name = c.getValue("name").jsonPrimitive.content
            val answer = c.getValue("answer").jsonObject
            val r = answer.getValue("r").jsonPrimitive.content
            val body = c.getValue("body").jsonObject
            val id = body.getValue("id").jsonPrimitive.content
            // the signed text is the body's fields in the contract's order
            val signed = listOf("id", "k", "a", "t", "s", "f").joinToString("|") { body.getValue(it).jsonPrimitive.content }
            assertEquals(name, c.getValue("signed").jsonPrimitive.content, signed)
            val port = Port()
            port.outcome =
                when (r) {
                    "stored" -> {
                        WatchLogged.Stored(treatment(answer.getValue("e").jsonObject, id)!!)
                    }

                    "known" -> {
                        WatchLogged.Known(treatment(answer["e"]?.takeIf { it !is JsonNull }?.jsonObject, id))
                    }

                    "pen" -> {
                        answer.getValue("pen").jsonObject.let {
                            WatchLogged.Pen(it.getValue("t").jsonPrimitive.long, it.getValue("a").jsonPrimitive.double)
                        }
                    }

                    "amount" -> {
                        WatchLogged.Amount
                    }

                    "when" -> {
                        WatchLogged.When
                    }

                    "off" -> {
                        WatchLogged.Off
                    }

                    else -> {
                        WatchLogged.Off
                    }
                }
            val writes = WatchWrites({ key }, port, null, clock)
            val a = writes.answer(request(WatchWrites.ENTRIES, c))
            assertEquals(name, "200 OK", a.status)
            assertJson(name, answer, Json.parseToJsonElement(a.body))
            if (r in setOf("sig", "clock", "id", "kind")) {
                assertTrue("$name: never reaches the phone's path", port.got.isEmpty())
            } else {
                val e = port.got.single()
                assertEquals(name, id, e.id)
                assertEquals(name, KINDS.getValue(body.getValue("k").jsonPrimitive.content), e.kind)
                assertEquals(name, body.getValue("a").jsonPrimitive.content, e.amount)
                assertEquals(name, body.getValue("t").jsonPrimitive.long, e.at.epochSecond)
                assertEquals(name, body.getValue("f").jsonPrimitive.content == "1", e.force)
            }
        }
    }

    @Test
    fun noKeyOnThePhoneSignsNothing() {
        val c = entries.getValue("cases").jsonArray[0].jsonObject
        val port = Port()
        val a = WatchWrites({ null }, port, null, clock).answer(request(WatchWrites.ENTRIES, c))
        assertTrue(a.body.contains("\"r\":\"sig\""))
        assertTrue(port.got.isEmpty())
    }

    @Test
    fun notARequestIsABadRequest() {
        val w = WatchWrites({ key }, Port(), null, clock)
        assertEquals(
            "400 Bad Request",
            w
                .answer(
                    HttpRequest(
                        "POST",
                        WatchWrites.ENTRIES,
                        emptyMap(),
                        mapOf("content-type" to "application/json"),
                        "{",
                    ),
                ).status,
        )
        assertEquals("400 Bad Request", w.answer(HttpRequest("POST", WatchWrites.ENTRIES, emptyMap(), emptyMap(), """{"id":"x"}""")).status)
        assertEquals("404 Not Found", w.answer(HttpRequest("POST", "sukrara/other", emptyMap(), emptyMap(), "")).status)
    }

    @Test
    fun everyMuteCaseInOrder() {
        val calls = ArrayList<String>()
        val controls =
            object : AlertControls {
                override suspend fun mute(
                    kind: String,
                    minutes: Int,
                ) {
                    calls += "mute($kind,$minutes)"
                }

                override suspend fun unmute(kind: String) {
                    calls += "unmute($kind)"
                }

                override suspend fun remindLess(kind: String) = error("not a watch action")

                override suspend fun remindAsUsual(kind: String) = error("not a watch action")

                override suspend fun muteAll(minutes: Int?) {
                    calls += "muteAll($minutes)"
                }

                override suspend fun unmuteAll() {
                    calls += "unmuteAll()"
                }
            }
        val writes = WatchWrites({ key }, Port(), controls, clock)
        for (c in mutes.getValue("cases").jsonArray.map { it.jsonObject }) {
            val name = c.getValue("name").jsonPrimitive.content
            val body = c.getValue("body").jsonObject
            val signed = listOf("id", "do", "side", "min", "s").joinToString("|") { body.getValue(it).jsonPrimitive.content }
            assertEquals(name, c.getValue("signed").jsonPrimitive.content, signed)
            calls.clear()
            val a = writes.answer(request(WatchWrites.MUTE, c))
            assertEquals(name, "200 OK", a.status)
            assertJson(name, c.getValue("answer"), Json.parseToJsonElement(a.body))
            val call = c["calls"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
            assertEquals(name, listOfNotNull(call), calls)
        }
    }

    @Test
    fun theKeyIsMadeOnceAndKept() =
        runBlocking {
            val secure = InMemorySecureStore()
            val k = WatchKey(secure).get()!!
            assertEquals(32, k.size)
            assertTrue(k.contentEquals(WatchKey(secure).get()!!))
            assertFalse(WatchKey(InMemorySecureStore()).get()!!.contentEquals(k))
            assertNull(WatchKey(UnreadableStore).get())
        }

    /** A store whose secret exists but can't be opened: never replaced by a new key. */
    private object UnreadableStore : app.mauri.sukrara.core.model.storage.SecureStore {
        override suspend fun read(name: String): ByteArray = throw IllegalStateException("locked")

        override suspend fun write(
            name: String,
            bytes: ByteArray,
        ) = error("must not write")

        override suspend fun delete(name: String) = Unit
    }

    /** Same JSON, numbers by value. */
    private fun assertJson(
        where: String,
        expected: JsonElement,
        actual: JsonElement,
    ) {
        when (expected) {
            is JsonObject -> {
                val o = actual as? JsonObject ?: throw AssertionError("$where: $actual")
                assertEquals("$where: keys", expected.keys, o.keys)
                expected.forEach { (k, v) -> assertJson("$where.$k", v, o.getValue(k)) }
            }

            is JsonPrimitive -> {
                if (expected is JsonNull || expected.isString || expected.booleanOrNull != null) {
                    assertEquals(where, expected, actual)
                } else {
                    assertEquals(where, expected.double, (actual as JsonPrimitive).double, 1e-9)
                }
            }

            else -> {
                assertEquals(where, expected.jsonArray, actual.jsonArray)
            }
        }
    }

    private companion object {
        val KINDS =
            mapOf(
                "rapid" to TreatmentKind.RapidInsulin,
                "long" to TreatmentKind.LongInsulin,
                "carbs" to TreatmentKind.Carbs,
                "note" to TreatmentKind.Note,
            )
    }
}
