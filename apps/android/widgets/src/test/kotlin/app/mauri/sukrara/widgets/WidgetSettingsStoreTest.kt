// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.graph.PlaceOverrides
import app.mauri.sukrara.graph.stats.TirRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSettingsStoreTest {
    private val backing = InMemoryStateStore(WidgetPlacements())
    private val store = WidgetSettingsStore(backing)

    @Test
    fun `each placement keeps its own settings and a removed one is forgotten`() =
        runTest {
            store.set(1, WidgetOverrides(hours = 3, rings = 0))
            store.set(2, WidgetOverrides(side = false, ringRange = TirRange.TIGHT))
            assertEquals(WidgetOverrides(hours = 3, rings = 0), store.get(1))
            assertEquals(WidgetOverrides(side = false, ringRange = TirRange.TIGHT), store.get(2))
            store.forget(intArrayOf(1))
            assertEquals("a reused id starts clean", WidgetOverrides(), store.get(1))
            assertEquals(
                setOf(2),
                backing.data
                    .first()
                    .byId.keys,
            )
        }

    /** The one number input's formats (placeHours, ringCount): a stored value outside them reads as the nearest valid one. */
    @Test
    fun `a stored value outside its format applies as the nearest valid one`() {
        assertEquals(PlaceOverrides(hours = 7, rings = 1), WidgetOverrides(hours = 7, rings = 1).toPlace())
        assertEquals(48, WidgetOverrides(hours = 90).toPlace().hours)
        assertEquals("0 = same as the app", null, WidgetOverrides(hours = 0).toPlace().hours)
        assertEquals(2, WidgetOverrides(rings = 9).toPlace().rings)
        assertEquals(PlaceOverrides(), WidgetOverrides().toPlace())
    }

    @Test
    fun `all "same as the app" stores nothing`() =
        runTest {
            store.set(5, WidgetOverrides(hours = 6))
            store.set(5, WidgetOverrides())
            assertTrue(
                backing.data
                    .first()
                    .byId
                    .isEmpty(),
            )
        }

    /**
     * WidgetProbe's measurement (#181: Pixel Launcher reports 296×202 dp for a 4×2 it lays out at 244×149): the widget is
     * drawn for the measured size, also for the launcher's near-duplicate reported sizes, per orientation; a resize or
     * removal forgets it, and an unmeasured size draws as reported.
     */
    @Test
    fun `a measured size replaces the reported one until the widget is resized or removed`() =
        runTest {
            assertEquals("unmeasured: as reported", 296f to 202f, backing.data.first().realSize(7, 296f, 202f))
            store.measured(7, MeasuredSize(296f, 201.9f, 244.2f, 148.6f))
            store.measured(7, MeasuredSize(640f, 120f, 560f, 110f))
            val p = backing.data.first()
            assertEquals(244.2f to 148.6f, p.realSize(7, 296f, 202f))
            assertEquals("the launcher's near-duplicate size", 244.2f to 148.6f, p.realSize(7, 296f, 201.1f))
            assertEquals("the other orientation", 560f to 110f, p.realSize(7, 640f, 120f))
            assertEquals("another reported size: not measured", 320f to 170f, p.realSize(7, 320f, 170f))
            assertEquals("another placement", 296f to 202f, p.realSize(8, 296f, 202f))
            store.measured(7, MeasuredSize(296f, 202f, 240f, 150f))
            assertEquals("measured again: the new one", 240f to 150f, backing.data.first().realSize(7, 296f, 202f))
            store.forgetMeasured(7)
            assertEquals(296f to 202f, backing.data.first().realSize(7, 296f, 202f))
            store.measured(7, MeasuredSize(296f, 202f, 240f, 150f))
            store.forget(intArrayOf(7))
            assertTrue(
                backing.data
                    .first()
                    .measured
                    .isEmpty(),
            )
        }

    @Test
    fun `fields are stored under the schema keys of widget overrides`() {
        val json = Json.encodeToString(WidgetOverrides.serializer(), WidgetOverrides(1, 2, TirRange.TIGHT, true, false))
        val keys = Json.parseToJsonElement(json).jsonObject.keys
        val schema =
            Json
                .parseToJsonElement(Contracts.file("settings/settings.json").readText())
                .jsonObject["settings"]
                .toString()
        val entry = Regex("\"widget\\.overrides\".*?\"key\":\\s*\"([^\"]+)\"").find(schema)?.groupValues?.get(1)
        assertEquals(entry?.split(",")?.toSet(), keys)
    }
}
