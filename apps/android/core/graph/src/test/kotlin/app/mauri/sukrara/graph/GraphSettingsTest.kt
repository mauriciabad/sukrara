// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.graph.stats.GlucosePoint
import app.mauri.sukrara.graph.stats.TirRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [GraphSettings.config]: the person's `graph.*` settings reach every card surface's style, and the drawn card. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraphSettingsTest {
    private val settings = InMemorySettingsRepository()
    private val now = 1_800_000_000L

    @Before
    fun setUp() = RoundedFont.init(RuntimeEnvironment.getApplication())

    @Test fun defaultsAreTheSchemas() =
        runTest {
            assertEquals(GraphConfig(), GraphSettings.config(settings).first())
        }

    @Test fun everyReadKeyLandsInTheConfig() =
        runTest {
            settings.set(GraphSettings.Hours, 6.0)
            settings.set(GraphSettings.GridHours, 2.0)
            settings.set(GraphSettings.Slots, "TIR:86400:1:1,TIR:604800:1:0,GMI:1209600:1:0")
            settings.set(GraphSettings.Last, false)
            settings.set(GraphSettings.Side, false)
            settings.set(GraphSettings.ColumnCrown, false)
            settings.set(GraphSettings.RingMarks, false)
            settings.set(GraphSettings.RingRange, "TIGHT")
            settings.set(GraphSettings.LastPlace, "END")
            val c = GraphSettings.config(settings).first()
            assertEquals(6, c.hours(GraphPlace.APP))
            assertEquals(6, c.hours(GraphPlace.WIDGETS))
            assertEquals(2, c.gridHours)
            assertEquals(1, c.rings().size)
            assertEquals(LastPlace.OFF, c.lastPlace)
            assertFalse(c.side || c.columnCrown || c.ringMarks)
            assertEquals(TirRange.TIGHT, c.ringRange)
            assertEquals("the place is kept while it's off", LastPlace.END, c.lastWhere)
        }

    @Test fun theStructuredKeysLandInTheConfig() =
        runTest {
            settings.set(GraphSettings.Slots, "GMI:86400:0:1,AVERAGE:604800:1:0,CV:2592000:0")
            settings.set(GraphSettings.RingOrder, "VERY_HIGH,HIGH,TARGET,TIGHT,LOW,VERY_LOW")
            settings.set(GraphSettings.Lines, "120,80")
            settings.set(GraphSettings.LastElse, "AVERAGE:259200:0:0")
            settings.set(GraphSettings.Marks, "rapid:point,long:point,carbs:point+amount,note:")
            settings.set(GraphSettings.PlaceHours.getValue(GraphPlace.NOTIFICATION), 3.0)
            settings.set(GraphSettings.SensorTimePlace.getValue(GraphPlace.WIDGETS), "off")
            val c = GraphSettings.config(settings).first()
            assertEquals(listOf(SlotKind.GMI, SlotKind.AVERAGE, SlotKind.CV), c.slots.map { it.kind })
            assertFalse(c.slots[0].faded)
            assertEquals("a GMI covers a week at least", 7 * 86_400L, c.slots[0].windowS)
            assertEquals(listOf(true, false, true), c.slots.map { it.on })
            assertEquals(listOf(SlotKind.GMI, SlotKind.CV), c.rings().map { it.kind })
            assertEquals(
                "VERY_HIGH,HIGH,TARGET,TIGHT,LOW,VERY_LOW",
                GraphText.ringOrderText(c.ringOrder),
            )
            assertEquals(listOf(80, 120), c.lines)
            assertEquals(RingSpec(259_200L, kind = SlotKind.AVERAGE, faded = false, on = false), c.lastElse)
            assertEquals(null, c.lastElseRing())
            assertTrue(c.marks.has("carbs", MarkShow.AMOUNT))
            assertFalse(c.marks.has("rapid", MarkShow.LINE))
            assertEquals(3, c.hours(GraphPlace.NOTIFICATION))
            assertEquals(c.appHours, c.hours(GraphPlace.APP))
            assertFalse(c.sensorTime(GraphPlace.WIDGETS))
            assertTrue(c.sensorTime(GraphPlace.APP))
        }

    /** #209: the app's time shown is the shared one; a place follows it (0) or has its own. The app has no own value. */
    @Test fun theAppUsesTheSharedTimeShownAndAPlaceItsOwn() =
        runTest {
            settings.set(GraphSettings.Hours, 6.0)
            settings.set(GraphSettings.PlaceHours.getValue(GraphPlace.WIDGETS), 24.0)
            var c = GraphSettings.config(settings).first()
            assertEquals(listOf(6, 6, 24), GraphPlace.entries.map(c::hours))
            assertEquals(6L * 3600, c.appPanelStyle().graph?.windowS)
            assertEquals(mapOf(GraphPlace.WIDGETS to 24), c.placeHours)
            settings.set(GraphSettings.PlaceHours.getValue(GraphPlace.WIDGETS), 0.0)
            c = GraphSettings.config(settings).first()
            assertEquals(emptyMap<GraphPlace, Int>(), c.placeHours)
            assertEquals(6, c.hours(GraphPlace.WIDGETS))
            assertFalse(
                "the app has no place key",
                GraphPlace.APP in GraphSettings.PlaceHours || GraphPlace.APP in GraphSettings.SensorTimePlace,
            )
        }

    /** The places that can differ are the schema's `places.android`, by name, each with its own key in that order. */
    @Test fun thePlacesAreTheSchemas() {
        val schema = Json.parseToJsonElement(Contracts.file("settings/settings.json").readText()).jsonObject
        val settings = schema.getValue("settings").jsonArray.map { it.jsonObject }
        for (id in listOf("graph.hours.place", "graph.sensorTime.place")) {
            val s = settings.single { it.getValue("id").jsonPrimitive.content == id }
            val places =
                s
                    .getValue("places")
                    .jsonObject
                    .getValue("android")
                    .jsonArray
                    .map { it.jsonPrimitive.content }
            assertEquals(id, places, GraphPlace.OWN_NAMES)
            val keys =
                s
                    .getValue("android")
                    .jsonObject
                    .getValue("key")
                    .jsonPrimitive.content
                    .split(',')
            val ours = if (id == "graph.hours.place") GraphSettings.PlaceHours else GraphSettings.SensorTimePlace
            assertEquals(id, keys, GraphPlace.OWN.map { ours.getValue(it).key })
        }
    }

    /** #209: the sensor's time left is the app's, every place follows it; a place's Same / On / Off is its own. */
    @Test fun eachPlaceFollowsTheAppsSensorTimeUnlessItHasItsOwn() =
        runTest {
            settings.set(GraphSettings.SensorTime, false)
            settings.set(GraphSettings.SensorTimePlace.getValue(GraphPlace.NOTIFICATION), "on")
            var c = GraphSettings.config(settings).first()
            assertFalse(c.sensorTime(GraphPlace.APP))
            assertTrue(c.sensorTime(GraphPlace.NOTIFICATION))
            assertFalse(c.sensorTime(GraphPlace.WIDGETS))
            settings.set(GraphSettings.SensorTime, true)
            settings.set(GraphSettings.SensorTimePlace.getValue(GraphPlace.WIDGETS), "off")
            settings.set(GraphSettings.SensorTimePlace.getValue(GraphPlace.NOTIFICATION), "same")
            c = GraphSettings.config(settings).first()
            assertEquals(mapOf(GraphPlace.WIDGETS to false), c.sensorPlace)
            assertTrue(c.sensorTime(GraphPlace.APP) && c.sensorTime(GraphPlace.NOTIFICATION))
            // an unknown stored name reads as Same
            settings.set(GraphSettings.SensorTimePlace.getValue(GraphPlace.WIDGETS), "maybe")
            assertTrue(GraphSettings.config(settings).first().sensorTime(GraphPlace.WIDGETS))
            // the values are the schema's option list, in order
            assertEquals(
                app.mauri.sukrara.settings.SettingsSpec.OPTIONS
                    .getValue("placeOverride"),
                PlaceOverride.entries.map { it.key },
            )
        }

    @Test fun damagedTextReadsAsTheDefault() =
        runTest {
            settings.set(GraphSettings.Slots, "TIR:86400:1")
            settings.set(GraphSettings.RingOrder, "TIGHT,TIGHT,LOW")
            settings.set(GraphSettings.Lines, "abc,5,100,100,999")
            val c = GraphSettings.config(settings).first()
            assertEquals(GraphConfig.DEFAULT_SLOTS, c.slots)
            assertEquals(RingSpec.DEFAULT_RING_ORDER, c.ringOrder)
            assertEquals(listOf(100), c.lines)
        }

    @Test fun bandsStayInOrderWhateverIsStored() =
        runTest {
            // a high range below the low one can't be drawn: the config clamps it like the renderer does
            settings.set(GraphSettings.Bands[1], 90.0)
            settings.set(GraphSettings.Bands[3], 95.0)
            val b = GraphSettings.config(settings).first().bands
            assertTrue("$b", b.low < b.tight && b.tight <= b.high && b.high < b.veryHigh)
        }

    /** A changed range changes the card the widgets and home draw (not only the settings page). */
    @Test fun aChangedRangeChangesTheDrawnCard() =
        runTest {
            val model =
                CardModel(
                    readings = (0..60).map { GlucosePoint(now - (60 - it) * 60L, 170, 0.0) },
                    treatments = emptyList(),
                    nowS = now,
                    words = Scenes.words(),
                )

            fun draw(c: GraphConfig) =
                GlucoseCard.image(
                    200f,
                    100f,
                    2f,
                    model,
                    widgetCardStyle(CardSurface.WIDGET_VALUE, c),
                    GraphPalette.of(false),
                    CardSurface.WIDGET_VALUE,
                )

            val before = GraphSettings.config(settings).first()
            // "high above" 160: the reading of 170 is high now
            settings.set(GraphSettings.Bands[3], 160.0)
            val after = GraphSettings.config(settings).first()
            assertEquals(160, after.bands.high)
            assertEquals(after.bands, after.appPanelStyle().bands)
            assertEquals(after.bands, widgetCardStyle(CardSurface.WIDGET_GRAPH, after).graph?.bands)
            assertFalse("the value's colour follows the new range", draw(before).sameAs(draw(after)))
        }
}
