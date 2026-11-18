// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [Link] and [ReadingRank]'s fence against contracts/sources/link.json (`pick`, `behind`, `leave`, `defaults`, `fence`). */
class LinkContractTest {
    private val link = Json.parseToJsonElement(Contracts.file("sources/link.json").readText()).jsonObject

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.cases(key: String) = getValue(key).jsonArray.map { it.jsonObject }

    private fun enumId(name: String) = name.replaceFirstChar(Char::lowercase)

    @Test
    fun `picking a source asks what the contract says`() {
        var checked = 0
        for (c in link.cases("pick")) {
            // a source this app doesn't have (the Mac's Juggluco on the network) is the Mac's case
            val picked = SourceKind.of(c.str("picked")) ?: continue
            val linked = Link.Linked.entries.single { enumId(it.name) == c.str("linked") }
            val asks = c.getValue("asks").jsonPrimitive.contentOrNull
            assertEquals(c.str("name"), asks, Link.pick(linked, picked)?.name?.let(::enumId))
            checked++
        }
        assertEquals("every Android pick case", 8, checked)
    }

    @Test
    fun `a follow found behind another source at start is asked about while live`() {
        val cases = link.cases("behind")
        assertEquals(3, cases.size)
        for (c in cases) {
            val linked = Link.Linked.entries.single { enumId(it.name) == c.str("linked") }
            val asks = c.getValue("asks").jsonPrimitive.contentOrNull
            assertEquals(c.str("name"), asks, Link.behind(linked)?.name?.let(::enumId))
        }
    }

    @Test
    fun `leaving asks only what the contract says, and keeps the settings it says`() {
        for (c in link.cases("leave")) {
            val plan =
                Link.leave(
                    wearerSettings = c.str("settingsFrom") == "wearer",
                    theirs = Link.Theirs.entries.single { enumId(it.name) == c.str("theirs") },
                    history = c.getValue("history").jsonPrimitive.boolean,
                    personChange = c.getValue("personChange").jsonPrimitive.boolean,
                )
            val name = c.str("name")
            assertEquals(name, c.getValue("questions").jsonArray.map { it.jsonPrimitive.content }, plan.questions.map { enumId(it.name) })
            val result = c.getValue("result").jsonPrimitive.contentOrNull
            assertEquals(name, result, plan.result?.name?.let(::enumId))
        }
    }

    @Test
    fun `the preselected answers keep their settings and the history`() {
        val defaults = link.getValue("defaults").jsonObject
        assertEquals(defaults.str("settings"), enumId(Link.DEFAULT_SETTINGS.name))
        assertEquals(defaults.str("history") == "keep", Link.DEFAULT_KEEP_HISTORY)
    }

    @Test
    fun `the next source continues the kept history behind the fence`() {
        val cases = link.cases("fence")
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            val stored = c.readings("stored")
            val fence =
                Instant.ofEpochSecond(
                    c
                        .getValue("fence_s")
                        .jsonPrimitive.int
                        .toLong(),
                )
            val plan = ReadingRank.plan(stored, c.readings("incoming"), fence)
            val kept =
                (stored - plan.remove.toSet()).filterNot { s -> plan.upsert.any { it.t == s.t && it.source == s.source } } + plan.upsert
            assertEquals(c.str("name"), c.readings("kept").key(), kept.key())
        }
    }

    private fun JsonObject.readings(key: String) =
        (getValue(key) as JsonArray).map {
            val r = it.jsonArray
            Reading(
                Instant.ofEpochSecond(r[0].jsonPrimitive.int.toLong()),
                r[2].jsonPrimitive.int,
                null,
                SourceId(r[1].jsonPrimitive.content),
            )
        }

    private fun List<Reading>.key() =
        sortedWith(
            compareBy({ it.t }, { it.source.id }),
        ).map { Triple(it.t.epochSecond, it.source.id, it.mgdl) }
}
