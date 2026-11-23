// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * contracts/watchface/payload/apps.json, the registry of Sukrara's Connect IQ apps, is the phone's [GarminApp]; the
 * watch payload's entry in contracts/apps/formats.json (THE shared formats) is the phone's version, port and paths, and
 * every payload of cases.json (which the phone builds exactly, [WatchPayloadFixtureTest]) has its frozen fields.
 */
class GarminAppsTest {
    private val file = Json.parseToJsonElement(Contracts.file("watchface/payload/apps.json").readText()).jsonObject

    @Test
    fun theRegistryIsThePhones() {
        val apps = file.getValue("apps").jsonArray.map { it.jsonObject }
        assertEquals(apps.map { it.getValue("app").jsonPrimitive.content }, GarminApp.entries.map { it.key })
        for ((a, app) in apps.zip(GarminApp.entries)) {
            fun s(k: String) = a.getValue(k).jsonPrimitive.content
            assertEquals(app.profile.key, s("profile"))
            assertEquals(app.isFace, s("type") == "watchface")
            assertEquals(app.id, s("id"))
            assertEquals(app.beta, s("beta"))
            assertEquals(app.profile.path, s("fetch"))
            assertEquals(app.logs, a.getValue("logs").jsonPrimitive.boolean)
            assertEquals(app.store, s("store"))
        }
        assertEquals(GarminLink.PORT, file.getValue("port").jsonPrimitive.int)
        assertEquals(GarminApp.ENTRIES, file.getValue("entries").jsonPrimitive.content)
        assertEquals(WatchWrites.MUTE, file.getValue("mute").jsonPrimitive.content)
        assertEquals(WatchWrites.UNDO, file.getValue("undo").jsonPrimitive.content)
        // every id once (a listing's isn't an app id), lowercase UUIDs
        val ids = GarminApp.entries.flatMap { it.ids + it.store }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertEquals(it, it.lowercase()) }
    }

    @Test
    fun anAppOpensItsListingInThePhonesLanguage() {
        // the Spanish store for Spanish, the English one for any other language
        assertEquals("https://apps.garmin.com/en-US/apps/bae83264-c160-4498-8a4d-492b4ea7d630", GarminApp.Field.storeUrl("en"))
        assertEquals("https://apps.garmin.com/es-ES/apps/bae83264-c160-4498-8a4d-492b4ea7d630", GarminApp.Field.storeUrl("es"))
        assertEquals("https://apps.garmin.com/en-US/apps/bae83264-c160-4498-8a4d-492b4ea7d630", GarminApp.Field.storeUrl("ca"))
        assertEquals("https://apps.garmin.com/es-ES/apps/2859fe48-7e9e-4e52-ae2a-a319fa76fcfc", GarminApp.Aura.storeUrl("es"))
    }

    @Test
    fun theWatchPayloadIsTheRegistrys() {
        val format =
            Json
                .parseToJsonElement(Contracts.file("apps/formats.json").readText())
                .jsonObject
                .getValue("formats")
                .jsonArray
                .map { it.jsonObject }
                .single { it.getValue("format").jsonPrimitive.content == "watch-payload" }
        assertTrue("android" in format.getValue("apps").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(WatchPayload.VERSION, format.getValue("version").jsonPrimitive.int)
        val frozen = format.getValue("frozen").jsonObject
        assertEquals(GarminLink.PORT, frozen.getValue("port").jsonPrimitive.int)
        val paths = WatchProfile.entries.map { it.path } + listOf(GarminApp.ENTRIES, WatchWrites.UNDO, WatchWrites.MUTE)
        assertEquals(frozen.getValue("paths").jsonArray.map { it.jsonPrimitive.content }, paths)
        assertEquals(WatchWrites.UNVERSIONED, frozen.getValue("unversioned").jsonPrimitive.int)
        val fields = frozen.getValue("payload").jsonObject.mapValues { it.value.jsonPrimitive.content }
        val cases = Json.parseToJsonElement(Contracts.file("watchface/payload/cases.json").readText()).jsonObject
        for (list in listOf("cases", "app", "field")) {
            for (c in cases.getValue(list).jsonArray.map { it.jsonObject }) {
                val payload = c.getValue("payload").jsonObject
                for ((path, type) in fields) {
                    val parent = if ('.' in path) payload[path.substringBefore('.')] else payload
                    if (parent is JsonNull) continue
                    val value = (parent as JsonObject)[path.substringAfter('.')]
                    val name = "$list ${c.getValue("name").jsonPrimitive.content}: $path"
                    assertTrue(name, type.split('|').any { t -> value.isA(t) })
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonElement?.isA(type: String) =
        when (type) {
            "integer" -> (this as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull != null
            "string" -> (this as? JsonPrimitive)?.isString == true
            "array" -> this is JsonArray
            "object" -> this is JsonObject
            "null" -> this is JsonNull
            else -> false
        }
}
