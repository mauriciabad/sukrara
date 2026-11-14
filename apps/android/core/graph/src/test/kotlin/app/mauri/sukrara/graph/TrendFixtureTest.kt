// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.graph.Fixtures.bool
import app.mauri.sukrara.graph.Fixtures.double
import app.mauri.sukrara.graph.Fixtures.str
import app.mauri.sukrara.graph.stats.GlucosePoint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * contracts/trend `bands.json`: THE trend arrow (Mac: GraphTests TrendFixtureTests). Per band the text arrow, the
 * doubled arrow, the face's [GlucoseLook.arrowText] every text place writes, the spoken word and the Nightscout
 * direction; per rate the drawn arrow's continuous angle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrendFixtureTest {
    private val doc = Fixtures.read("../trend/bands.json")
    private val arrows = (doc["arrows"] as JsonArray).map { it.jsonObject }
    private val angles = (doc["angles"] as JsonArray).map { it.jsonObject }

    private fun JsonObject.band(): Int? = this["band"].takeIf { it != JsonNull }?.jsonPrimitive?.int

    @Test fun `the fixture has every band and the ring`() = assertEquals(listOf(3, 2, 1, 0, -1, -2, -3, null), arrows.map { it.band() })

    @Test fun `text and double follow the band`() =
        arrows.forEach { a ->
            val band = a.band()
            assertEquals("text $band", a["text"].str(), TrendAngle.text(band))
            assertEquals("double $band", a["double"].bool(), band?.let(TrendAngle::isDouble))
        }

    @Test fun `the drawn angle is continuous in the rate`() =
        angles.forEach { a ->
            val rate = a["rate"].double()!!
            assertEquals("deg $rate", a["deg"].double()!!, TrendAngle.deg(rate).toDouble(), 1e-4)
        }

    /** The face of a fresh reading with each band writes the band's arrow; a struck (stale) one the ring, like the card. */
    @Test fun `the face's arrow text is the band's`() {
        val now = 1_800_000_000L
        arrows.forEach { a ->
            val band = a.band()
            // a rate inside the band, and the band itself (with hysteresis it can differ from the rate's plain band)
            val rate = band?.let { it * 0.9 }
            val want = a["text"].str()
            val fresh =
                GlucoseCard.face(
                    GlucosePoint(now - 30, 100, rate, band),
                    now,
                    null,
                    CardSurface.NOTIFICATION_TEXT,
                    GlucoseUnit.MGDL,
                )
            assertEquals("band $band", want, fresh.arrowText)
            val stale =
                GlucoseCard.face(
                    GlucosePoint(now - 6 * 60, 100, rate, band),
                    now,
                    null,
                    CardSurface.NOTIFICATION_TEXT,
                    GlucoseUnit.MGDL,
                )
            assertEquals("band $band", TrendMark.RING, stale.arrowText)
        }
        // the band decides the text, not the rate: 1.1 held at → by the hysteresis writes →
        val held = GlucoseCard.face(GlucosePoint(now - 30, 100, 1.1, 0), now, null, CardSurface.NOTIFICATION_TEXT, GlucoseUnit.MGDL)
        assertEquals("→", held.arrowText)
        assertEquals(null, GlucoseCard.face(null, now, null, CardSurface.NOTIFICATION_TEXT, GlucoseUnit.MGDL).arrowText)
    }

    /** One word per arrow: what TalkBack says for each band, in English and Spanish. */
    @Test fun `the words follow the band`() {
        for ((key, locale) in mapOf("en" to Locale.ENGLISH, "es" to Locale.forLanguageTag("es"))) {
            val w = Scenes.words(locale)
            arrows.forEach { a ->
                assertEquals("${a.band()} ($key)", (a["words"] as JsonObject)[key].str(), w.trend(a.band()))
            }
        }
    }

    /** The Nightscout upload and the local web endpoints send the band's direction name. */
    @Test fun `the Nightscout direction follows the band`() =
        arrows.forEach { a -> assertEquals("${a.band()}", a["nightscout"].str(), TrendAngle.nightscout(a.band())) }
}
