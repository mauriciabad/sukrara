// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.LockAppearance
import app.mauri.sukrara.core.model.LockBackground
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** The lock-screen widget's settings (#66) are the schema's: its options in its order, its defaults, an unknown value. */
class LockWidgetSettingsTest {
    private fun entry(id: String): JsonObject =
        Json
            .parseToJsonElement(Contracts.file("settings/settings.json").readText())
            .jsonObject["settings"]!!
            .jsonArray
            .map { it.jsonObject }
            .single { it["id"]!!.jsonPrimitive.content == id }

    private fun values(id: String) = entry(id)["values"]!!.jsonArray.map { it.jsonPrimitive.content }

    private fun default(id: String) = entry(id)["default"]!!.jsonPrimitive.content

    @Test fun `background and appearance offer the schema's values, with its defaults`() {
        assertEquals(values("lock.background"), LockBackground.entries.map { it.key })
        assertEquals(default("lock.background"), LockBackground.DEFAULT.key)
        assertEquals(default("lock.background"), SurfaceSettings.LockWidgetBackground.default)
        assertEquals(values("lock.appearance"), LockAppearance.entries.map { it.key })
        assertEquals(default("lock.appearance"), LockAppearance.DEFAULT.key)
        assertEquals(default("lock.appearance"), SurfaceSettings.LockWidgetAppearance.default)
    }

    @Test fun `on by default with frosted glass, an unknown stored value reads as the default`() =
        runTest {
            val settings = InMemorySettingsRepository()
            assertEquals(true, settings.flow(SurfaceSettings.LockWidget).first())
            assertEquals(LockLook(LockBackground.FROSTED, LockAppearance.AUTO), SurfaceSettings.lockLook(settings).first())
            settings.set(SurfaceSettings.LockWidgetBackground, "card")
            settings.set(SurfaceSettings.LockWidgetAppearance, "dark")
            assertEquals(LockLook(LockBackground.CARD, LockAppearance.DARK), SurfaceSettings.lockLook(settings).first())
            assertEquals(LockBackground.DEFAULT, LockBackground.of("blur"))
            assertEquals(LockAppearance.DEFAULT, LockAppearance.of(null))
        }
}
