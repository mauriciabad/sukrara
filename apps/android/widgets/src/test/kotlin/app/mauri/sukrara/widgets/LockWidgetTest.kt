// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.LockAppearance
import app.mauri.sukrara.core.model.LockBackground
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.model.LockWidgetLink
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.graph.CardLayout
import app.mauri.sukrara.graph.CardPart
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.LockHole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The lock-screen widget's app side (#66): the provider System UI's module binds, what it draws (only through the
 * renderer, around the lock icon), what it says to TalkBack and the ids it keeps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class LockWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-27T08:00:00Z")

    @After fun tearDown() {
        GlucoseCard.observer = null
        GlucoseCard.faceObserver = null
    }

    private fun frame(
        mgdl: Int? = 128,
        age: Duration = Duration.ofMinutes(1),
        look: LockLook = LockLook(),
        role: Role = Role.Wearer,
    ): WidgetFrame {
        val readings =
            if (mgdl == null) {
                emptyList()
            } else {
                (36 downTo 0).map { i -> Reading(now.minus(age).minusSeconds(i * 300L), mgdl - i, 0.4, SourceId("synthetic")) }
            }
        val g =
            GlucoseState.Initial.copy(
                role = role,
                latest = readings.lastOrNull(),
                recent = readings,
                sensor = SensorState.Active(now.plus(Duration.ofDays(6))),
            )
        return WidgetFrame(g, emptyList(), GraphConfig(), WidgetPlacements(), now, dark = true, lock = look)
    }

    @Test fun `the module binds this provider by name`() {
        assertEquals(LockWidgetLink.RECEIVER, LockWidgetReceiver::class.java.name)
    }

    @Test fun `no launcher or hub offers it, and it never redraws on a timer`() {
        val ns = "http://schemas.android.com/apk/res/android"

        fun parse(f: File): Element =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(f)
                .documentElement
        val receivers = parse(File("src/main/AndroidManifest.xml")).getElementsByTagName("receiver")
        val r =
            (0 until receivers.length).map { receivers.item(it) as Element }.single {
                it.getAttributeNS(ns, "name") == LockWidgetLink.RECEIVER
            }
        assertEquals("the system's widget broadcasts reach it", "true", r.getAttributeNS(ns, "exported"))
        val info = parse(File("src/main/res/xml/widget_lock_info.xml"))
        assertEquals("keyguard", info.getAttributeNS(ns, "widgetCategory"))
        assertTrue(info.getAttributeNS(ns, "widgetFeatures").split("|").contains("hide_from_picker"))
        assertEquals("0", info.getAttributeNS(ns, "updatePeriodMillis"))
        assertTrue(File("src/main/res/layout/widget_lock_initial.xml").isFile)
    }

    @Test fun `the lock icon's spot comes from the module, centred until it does`() {
        assertEquals(LockHole.centred(380f), lockHole(Bundle(), 380f))
        val b =
            Bundle().apply {
                putFloat(LockWidgetLink.HOLE_LEFT, 140f)
                putFloat(LockWidgetLink.HOLE_RIGHT, 230f)
                putFloat(LockWidgetLink.HOLE_BOTTOM, 75f)
            }
        assertEquals(LockHole(140f, 230f, 75f), lockHole(b, 380f))
    }

    @Test fun `the renderer draws the value, the slots and the chart around the icon`() {
        val drawn = mutableListOf<CardLayout>()
        GlucoseCard.observer = { drawn += it }
        val hole = LockHole(146f, 233f, 75f)
        val bmp = renderLockWidget(context, 380f, 190f, hole, frame())
        assertTrue(bmp.width > 0 && bmp.height > 0)
        assertEquals(listOf(CardPart.VALUE, CardPart.SLOTS, CardPart.GRAPH), drawn.map { it.part })
        assertTrue(drawn.all { it.surface == CardSurface.LOCK_WIDGET })
        val d = drawn.first().d
        // the value stays left of the icon, the slots right of it (frosted glass: 12 dp off the edges)
        assertTrue(drawn[0].w <= (hole.left - 12) * d + 1)
        assertTrue(drawn[1].w <= (380 - 12 - hole.right) * d + 1)
    }

    @Test fun `every background and appearance draws, no readings too`() {
        for (b in LockBackground.entries) {
            for (a in LockAppearance.entries) renderLockWidget(context, 380f, 190f, LockHole.centred(380f), frame(look = LockLook(b, a)))
        }
        renderLockWidget(context, 380f, 190f, LockHole.centred(380f), frame(mgdl = null))
        renderLockWidget(context, 380f, 90f, LockHole.centred(380f), frame())
    }

    @Test fun `TalkBack hears the renderer's sentence, then what a tap does`() {
        val seen = mutableListOf<CardSurface>()
        GlucoseCard.faceObserver = { surface, _ -> seen += surface }
        val said = describeLock(context, frame())
        assertTrue(CardSurface.LOCK_DESCRIPTION in seen)
        assertTrue(said, said.endsWith(". Add treatment"))
        // no value on screen: the tap opens the app, so it isn't promised
        val none = describeLock(context, frame(mgdl = null))
        assertFalse(none, none.endsWith("Add treatment"))
    }

    @Test fun `the hosted ids are kept until System UI lets go of them`() =
        runTest {
            val store = WidgetSettingsStore(InMemoryStateStore(WidgetPlacements(mapOf(3 to WidgetOverrides(hours = 6)))))
            store.rememberLock(listOf(41))
            store.rememberLock(listOf(41))
            assertEquals(setOf(41), store.placements.first().lock)
            store.forget(intArrayOf(41))
            assertEquals(emptySet<Int>(), store.placements.first().lock)
            assertEquals(
                "the home widgets' own settings stay",
                setOf(3),
                store.placements
                    .first()
                    .byId.keys,
            )
        }
}
