// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.MarkShow
import app.mauri.sukrara.graph.RingSpec
import app.mauri.sukrara.graph.SlotKind
import app.mauri.sukrara.graph.WatchDisplay
import app.mauri.sukrara.graph.stats.TirRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A watch's own values (settings.json `apps.garmin.watch`): stored under the schema's keys, resolved in one place. */
class WatchSettingsTest {
    private val own =
        WatchDisplay(
            slots = listOf(null) + GraphConfig.DEFAULT_SLOTS.drop(1).map { it.copy(on = false) },
            lastOn = false,
            lastElse = GraphConfig.DEFAULT_LAST_ELSE.copy(on = false),
            ringOrder = RingSpec.DEFAULT_RING_ORDER.reversed(),
            ringRange = TirRange.TIGHT,
            ringMarks = false,
            hours = 6,
            marks = MarkShow.parse("rapid:point,long:point,carbs:point,note:point"),
            side = false,
            reportDays = 30,
        )

    private class Memory<T>(
        start: T,
    ) : StateStore<T> {
        override val data = MutableStateFlow(start)

        override suspend fun update(transform: (T) -> T) {
            data.value = transform(data.value)
        }
    }

    @Test
    fun theFieldsAreTheSchemasKeys() {
        val json = Json.encodeToString(WatchOverrides.serializer(), WatchOverrides.of(own))
        val schema = Json.parseToJsonElement(Contracts.file("settings/settings.json").readText()).jsonObject
        val row =
            schema.getValue("settings").jsonArray.map { it.jsonObject }.single {
                it.getValue("id").jsonPrimitive.content ==
                    "apps.garmin.watch"
            }
        val keys =
            row
                .getValue("android")
                .jsonObject
                .getValue("key")
                .jsonPrimitive.content
                .split(",")
        assertEquals(keys.toSet(), Json.parseToJsonElement(json).jsonObject.keys)
    }

    @Test
    fun storedAndReadBackTheSame() =
        runBlocking {
            val store = WatchSettingsStore(Memory(GarminWatches()))
            store.set(7, own, "Descent Mk3i 43mm")
            store.set(8, WatchDisplay(hours = 3), "Venu 3")
            assertEquals(mapOf(7L to own, 8L to WatchDisplay(hours = 3)), store.displays.first())
            assertEquals(mapOf(7L to "Descent Mk3i 43mm", 8L to "Venu 3"), store.names.first())
            // every value back to Same as the app: the watch has no entry, nor its name
            store.set(8, WatchDisplay())
            assertEquals(setOf(7L), store.displays.first().keys)
            assertEquals(setOf(7L), store.names.first().keys)
        }

    @Test
    fun aStoredValueOutOfItsFormatFollowsTheApp() {
        val d = WatchOverrides(slots = "nonsense", hours = 400, reportDays = 5).toDisplay()
        assertTrue(d.same)
        // one part that doesn't parse: that metric follows the app, the others keep theirs
        val one = WatchOverrides(slots = "nonsense,GMI:1209600:1:1,").toDisplay()
        assertEquals(listOf(null, RingSpec(1_209_600, kind = SlotKind.GMI, faded = true), null), one.slots)
    }

    @Test
    fun theOnePlaceResolvesEveryValue() {
        val app = GraphConfig()
        val same = GarminDisplay.resolve(app)
        assertEquals(app, same.graph)
        assertEquals(WatchDisplay.REPORT_START, same.reportDays)
        val d = GarminDisplay.resolve(app, own)
        assertEquals(6, d.hours)
        assertEquals(false, d.side)
        assertEquals(30, d.reportDays)
        assertEquals(own.marks, d.marks)
        assertEquals(TirRange.TIGHT, d.graph.ringRange)
        assertEquals(own.ringOrder, d.graph.ringOrder)
        assertEquals(false, d.graph.lastOn)
        // the main metric follows the app, the second and third are off on this watch
        assertEquals(listOf(app.slots[0].windowS), d.graph.rings().map { it.windowS })
        // never per watch: the unit and the limits stay the app's
        assertEquals(app.unit, d.graph.unit)
        assertEquals(app.bands, d.graph.bands)
    }
}
