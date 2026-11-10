// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.feature.treatments.TreatmentTileService.TileWords
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.face
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** The Quick Settings tile's subtitle (surface `quick-tile`) takes the card's face in every state. */
class TileSubtitleTest {
    private val nowS = 1_800_000_000L

    private fun state(
        agoS: Long?,
        sensor: SensorState = SensorState.Active(null),
    ) = GlucoseState.Initial.copy(
        latest = agoS?.let { Reading(Instant.ofEpochSecond(nowS - it), 123, 1.2, SourceId.Libre3) },
        sensor = sensor,
    )

    private fun subtitle(state: GlucoseState) =
        TreatmentTileService.subtitle(
            GlucoseCard.face(state, nowS, CardSurface.QUICK_TILE, GlucoseUnit.MGDL),
            state.latest?.t?.epochSecond,
            nowS,
        ) {
            when (it) {
                TileWords.SensorEnded -> "ended"
                is TileWords.WarmingUp -> "warming ${it.left}"
                is TileWords.NoReadingSince -> "since ${it.time}"
            }
        }

    @Test fun `a fresh value with its arrow, struck with the ring once late`() {
        assertEquals(true, subtitle(state(30))!!.startsWith("123 "))
        val late = subtitle(state(6 * 60))!!
        assertEquals("1̶2̶3̶ ○", late)
    }

    @Test fun `the sensor's words and since when, nothing before a first reading`() {
        assertEquals(true, subtitle(state(25 * 60))!!.startsWith("since "))
        assertEquals("ended", subtitle(state(6 * 60, SensorState.Ended)))
        // past its end but still sending (#3): the value, as the card shows it
        assertEquals(true, subtitle(state(30, SensorState.Ended))!!.startsWith("123 "))
        assertEquals(true, subtitle(state(null, SensorState.WarmingUp(Instant.ofEpochSecond(nowS + 600))))!!.startsWith("warming "))
        assertNull(subtitle(state(null)))
    }
}
