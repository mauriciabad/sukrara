// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import android.graphics.Bitmap
import android.graphics.Canvas
import app.mauri.sukrara.core.model.CardStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * #26: an out-of-range value says its range in words on the card, never by colour alone (Mac: RangeWordTests). A low
 * without a pill (alerts off for it, a follower without the wearer's alerts) is exactly the case colour alone missed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RangeWordTest {
    @Before
    fun setUp() = RoundedFont.init(RuntimeEnvironment.getApplication())

    private fun draw(
        model: CardModel,
        w: Float = 250f,
        h: Float = 80f,
    ): CardLayout {
        val st = widgetCardStyle(CardSurface.WIDGET_VALUE, GraphConfig())
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        return GlucoseCard.draw(Canvas(bmp), w, h, model, st, GraphPalette.of(false), 1f, CardSurface.TEST, CardPart.ALL)
    }

    @Test fun `a low without a pill says Low under the value`() {
        val lay = draw(Scenes.model(Scenes.day(64, swing = 6), Scenes.words()))
        assertEquals("low [row]", lay.first("value.range")?.info)
        assertNull(lay.first("value.status"))
    }

    @Test fun `in Spanish too, and very high is said apart from low`() {
        val es = Scenes.words(Locale.forLanguageTag("es"))
        assertEquals("Baja", es.range(RangeState.LOW))
        assertEquals("Muy alta", es.range(RangeState.VERY_HIGH))
        val lay = draw(Scenes.model(Scenes.day(262, swing = 6), es))
        assertEquals("very_high [row]", lay.first("value.range")?.info)
    }

    @Test fun `Eat carbs says the side already, a mute pill doesn't`() {
        val needed = draw(Scenes.model(Scenes.day(64, swing = 6), Scenes.words(), status = CardStatus.Needed(lowSide = true)))
        assertNull(needed.first("value.range"))
        val paused = draw(Scenes.model(Scenes.day(64, swing = 6), Scenes.words(), status = CardStatus.Paused(true, Scenes.NOW + 600)))
        assertEquals("low [row]", paused.first("value.range")?.info)
    }

    @Test fun `in range and struck values say nothing`() {
        assertNull(draw(Scenes.model(Scenes.day(125, swing = 6), Scenes.words())).first("value.range"))
        assertNull(draw(Scenes.model(Scenes.day(64, ageS = 6 * 60, swing = 6), Scenes.words())).first("value.range"))
    }

    @Test fun `a short value widget puts the word beside the arrow`() {
        val lay = draw(Scenes.model(Scenes.day(64, swing = 6), Scenes.words()), 160f, 40f)
        assertEquals("low [beside]", lay.first("value.range")?.info)
    }
}
