// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.TrendSetting
import app.mauri.sukrara.core.testing.trended
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import kotlin.math.roundToInt

/** The Trend arrow page's preview says what the card says for the choice in use (contracts/trend). */
class TrendArrowPreviewTest {
    /**
     * A slow steady rise: 0.9 mg/dL a minute for 40 min, then 1.1 for an hour. The text band's hysteresis keeps → from
     * the 0.9 through the 1.1 (it leaves → only at 1.2), so the card shows →; the preview of the choice in use must show
     * the same, not the ↗ a read of only the last minutes starts with.
     */
    @Test fun `the choice in use previews the card's own arrow through a long slow drift`() {
        val t0 = Instant.ofEpochSecond(1_800_000_000)
        var v = 120.0
        val readings =
            (0 until 100).map { i ->
                v += if (i < 40) 0.9 else 1.1
                Reading(t0.plusSeconds(i * 60L), v.roundToInt(), null, SourceId.Libre3)
            }
        // the hub's readings: THE trend from the one annotate point over the whole window
        val hub = readings.trended(TrendSetting.Balanced)
        val card = hub.last().trend!!
        assertEquals("the card keeps → through the drift", 0, card.band)
        val preview = TrendArrowPreview.of(hub, hub.last().t.plusSeconds(20), GlucoseUnit.MGDL).getValue(TrendSetting.Balanced)
        assertEquals(card.band, preview.look!!.band)
        assertEquals(card.rate, preview.look!!.rate!!, 1e-9)
    }
}
