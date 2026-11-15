// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import android.graphics.Bitmap
import android.graphics.Canvas
import app.mauri.sukrara.core.model.CardStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * contracts/graph README "The value with a pill" (round 3 of #36; the owner: "when there are pills under the glucose
 * value, the value's text size is decreased … it's fine to decrease it a little"): with a pill or the range word the
 * number keeps at least 85 % of its height without one, on the notification's value box and on the widgets. Mac:
 * PillValueSizeTests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PillValueSizeTest {
    @Before
    fun setUp() = RoundedFont.init(RuntimeEnvironment.getApplication())

    private fun capH(
        w: Float,
        h: Float,
        st: CardStyle,
        part: CardPart,
        status: CardStatus,
        mgdl: Int = 64,
    ): Float {
        val model = Scenes.model(Scenes.day(mgdl, swing = 6), Scenes.words(), status = status)
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val lay = GlucoseCard.draw(Canvas(bmp), w, h, model, st, GraphPalette.of(false), 1f, CardSurface.TEST, part)
        val box = lay.first("value.number")?.box
        assertNotNull(box)
        return box!!.height()
    }

    /** [st] with a pill vs the same card saying nothing beside its value (its title says the range: no word, no pill). */
    private fun keeps(
        w: Float,
        h: Float,
        st: CardStyle,
        part: CardPart,
        mgdl: Int = 64,
    ) {
        val bare = capH(w, h, st.copy(rangeSaid = true), part, CardStatus.None, mgdl)
        for (status in listOf(CardStatus.Needed(lowSide = mgdl < 100), CardStatus.Paused(mgdl < 100, Scenes.NOW + 1800))) {
            val with = capH(w, h, st, part, status, mgdl)
            assertTrue("$w×$h $status: ${with / bare} of the bare number", with >= bare * ValueFace.PILL_KEEP - 0.5f)
        }
        // the range word alone (no pill) is a pill too
        val word = capH(w, h, st, part, CardStatus.None, mgdl)
        assertTrue("$w×$h range word: ${word / bare}", word >= bare * ValueFace.PILL_KEEP - 0.5f)
    }

    @Test fun `the notification's value box keeps the number large`() {
        // the expanded header's value box (48 dp) beside two slots, and the collapsed one (40 dp)
        keeps(151f, 48f, notificationCardStyle(GraphConfig()), CardPart.VALUE)
        keeps(171f, 40f, notificationCardStyle(GraphConfig()), CardPart.VALUE)
        keeps(151f, 48f, notificationCardStyle(GraphConfig()), CardPart.VALUE, mgdl = 262)
    }

    /** The pill is drawn, never dropped for room, on the notification's value boxes (in range and out, every kind). */
    @Test fun `the notification's value box always shows the pill`() {
        val st = notificationCardStyle(GraphConfig())
        for ((w, h) in listOf(151f to 48f, 171f to 40f, 157f to 48f)) {
            for (mgdl in listOf(64, 104, 262)) {
                for (status in listOf(
                    CardStatus.Needed(lowSide = mgdl < 100),
                    CardStatus.Paused(mgdl < 100, Scenes.NOW + 1800),
                    CardStatus.Muted(Scenes.NOW + 1800),
                )) {
                    val model = Scenes.model(Scenes.day(mgdl, swing = 6), Scenes.words(), status = status)
                    val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
                    val lay = GlucoseCard.draw(Canvas(bmp), w, h, model, st, GraphPalette.of(true), 1f, CardSurface.TEST, CardPart.VALUE)
                    val pill = lay.first("value.status")
                    assertTrue("$w×$h $mgdl $status: ${pill?.info}", pill != null && pill.box != null)
                }
            }
        }
    }

    @Test fun `the widgets keep the number large`() {
        val value = widgetCardStyle(CardSurface.WIDGET_VALUE, GraphConfig())
        keeps(170f, 80f, value, CardPart.ALL)
        keeps(250f, 80f, value, CardPart.ALL)
        // (a 110 × 48 value widget has room neither under nor beside: there the row under a smaller number, the last resort)
        keeps(250f, 80f, value, CardPart.ALL, mgdl = 262)
    }
}
