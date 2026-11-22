// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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
            assertEquals(app.key, s("profile"))
            assertEquals(app.id, s("id"))
            assertEquals(app.beta, s("beta"))
            assertEquals(app.path, s("fetch"))
            assertEquals(app.logs, a.getValue("logs").jsonPrimitive.boolean)
        }
        assertEquals(AuraLink.PORT, file.getValue("port").jsonPrimitive.int)
        assertEquals(GarminApp.ENTRIES, file.getValue("entries").jsonPrimitive.content)
        assertEquals(WatchWrites.MUTE, file.getValue("mute").jsonPrimitive.content)
        assertEquals(WatchWrites.UNDO, file.getValue("undo").jsonPrimitive.content)
        // every id once, lowercase UUIDs
        val ids = GarminApp.entries.flatMap { it.ids }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertEquals(it, it.lowercase()) }
    }
}
