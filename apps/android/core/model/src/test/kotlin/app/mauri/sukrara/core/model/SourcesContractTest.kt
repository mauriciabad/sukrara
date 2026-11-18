// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.settings.SettingsSpec
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
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** [SourceKind], [SourceSpec] and [ReadingRank] against contracts/sources (sources.json, dedup.json). */
class SourcesContractTest {
    private val sources = Json.parseToJsonElement(Contracts.file("sources/sources.json").readText()).jsonObject
    private val dedup = Json.parseToJsonElement(Contracts.file("sources/dedup.json").readText()).jsonObject

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.strings(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `every Android source declares what the contract says, and only those`() {
        val android =
            sources
                .getValue("sources")
                .jsonArray
                .map { it.jsonObject }
                .filter { "android" in it.strings("platforms") }
        assertEquals(android.map { it.str("id") }, SourceKind.entries.map { it.id })
        for (s in android) {
            val kind = SourceKind.of(s.str("id"))!!
            assertEquals(kind.id, s.strings("persons").toSet(), kind.persons.map { it.id }.toSet())
            assertEquals(kind.id, s.getValue("firstHand").jsonPrimitive.boolean, kind.firstHand)
            assertEquals(kind.id, s.getValue("offline").jsonPrimitive.boolean, kind.offline)
            assertEquals(kind.id, s.str("group"), kind.group.name.lowercase())
            assertEquals(kind.id, s.getValue("delay_s").jsonPrimitive.int, kind.delaySeconds)
            assertEquals(kind.id, s.getValue("backfill").jsonPrimitive.boolean, kind.backfill)
            assertEquals(kind.id, s.strings("readings").toSet(), kind.readings.map { it.id }.toSet())
            assertEquals(kind.id, s.str("life"), kind.life.id)
            // the wear setting: its schema id, stored in the source file under the key the source names
            val wear = (s.getValue("wear") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            val stored = wear?.let { id -> SettingsSpec.SETTINGS.first { it.id == id }.android }
            assertEquals(kind.id, stored, kind.wear?.let { "state:$it" })
        }
        assertEquals(sources.strings("persons"), Person.entries.map { it.id })
    }

    @Test
    fun `each person and source gets the behaviour the contract keys`() {
        var checked = 0
        for (c in sources.getValue("keying").jsonArray.map { it.jsonObject }) {
            val kind = SourceKind.of(c.str("source")) ?: continue // the Mac's own
            val spec = SourceSpec.of(Person.of(c.str("person")), kind)!!
            val name = "${c.str("person")} × ${c.str("source")} (${c.str("settingsFrom")})"
            assertEquals(name, c.getValue("owner").jsonPrimitive.boolean, spec.owner)
            assertEquals(name, c.getValue("stream").jsonPrimitive.boolean, spec.stream)
            val role = spec.alertRole(mirrorsWearer = c.str("settingsFrom") == "wearer")
            assertEquals(name, c.str("alertRole"), ROLES.getValue(role))
            checked++
        }
        assertEquals("every Android pair is keyed", 11, checked)
        for (c in sources.getValue("invalid").jsonArray.map { it.jsonObject }) {
            assertNull(SourceSpec.of(Person.of(c.str("person")), SourceKind.of(c.str("source"))))
        }
    }

    @Test
    fun `every reading id ranks as the contract says`() {
        val ranks = dedup.strings("rank")
        assertEquals(ranks, ReadingRank.Rank.entries.map { it.name.replaceFirstChar(Char::lowercase) })
        for ((id, rank) in dedup.getValue("readingRank").jsonObject) {
            assertEquals(id, rank.jsonPrimitive.content, ReadingRank.of(SourceId(id)).name.replaceFirstChar(Char::lowercase))
        }
        assertEquals(
            dedup
                .getValue("window_s")
                .jsonPrimitive.int
                .toLong(),
            ReadingRank.WINDOW.seconds,
        )
    }

    @Test
    fun `the rank rule keeps one reading per measurement`() {
        for (c in dedup.getValue("cases").jsonArray.map { it.jsonObject }) {
            val stored = c.readings("stored")
            val plan = ReadingRank.plan(stored, c.readings("incoming"))
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

    private companion object {
        val ROLES =
            mapOf(
                SourceAlertRole.Wearer to "WEARER",
                SourceAlertRole.FollowerMirror to "FOLLOWER_MIRROR",
                SourceAlertRole.FollowerOwn to "FOLLOWER_OWN",
            )
    }
}
