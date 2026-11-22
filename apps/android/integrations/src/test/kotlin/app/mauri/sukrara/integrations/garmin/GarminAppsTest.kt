// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** contracts/watchface/payload/apps.json, the registry of Sukrara's Connect IQ apps, is the phone's [GarminApp]. */
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
            // the store listing's id, null until it is published (#247)
            assertEquals(app.store, a.getValue("store").jsonPrimitive.contentOrNull)
        }
        assertEquals(GarminLink.PORT, file.getValue("port").jsonPrimitive.int)
        assertEquals(GarminApp.ENTRIES, file.getValue("entries").jsonPrimitive.content)
        assertEquals(WatchWrites.MUTE, file.getValue("mute").jsonPrimitive.content)
        assertEquals(WatchWrites.UNDO, file.getValue("undo").jsonPrimitive.content)
        // every id once, lowercase UUIDs
        val ids = GarminApp.entries.flatMap { it.ids }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertEquals(it, it.lowercase()) }
    }

    @Test
    fun aListingLinksOnlyOnceItHasAnId() {
        val none = ConnectIqStore(emptyMap())
        assertEquals(emptyList<GarminApp>(), none.apps)
        assertNull(none.url(GarminApp.Aura, "en"))
        val one = ConnectIqStore(mapOf(GarminApp.Field to "abc-123"))
        assertEquals(listOf(GarminApp.Field), one.apps)
        assertNull(one.url(GarminApp.Aura, "en"))
        assertEquals("https://apps.garmin.com/en-US/apps/abc-123", one.url(GarminApp.Field, "en"))
        assertEquals("https://apps.garmin.com/es-ES/apps/abc-123", one.url(GarminApp.Field, "es"))
        // the registry's own: only the apps with a store id
        assertEquals(GarminApp.entries.filter { it.store != null }, ConnectIqStore().apps)
    }
}
