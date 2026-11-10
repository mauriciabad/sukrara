// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.feature.home.Samples.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/** A new sensor's first 12 hours (#46): from its start (derived from its end, 15 days) to start + 12 h. */
class EarlyReadingsTest {
    private fun activeSince(ago: Duration) = SensorState.Active(NOW.plus(Duration.ofDays(15)).minus(ago))

    @Test fun inTheFirstTwelveHoursItSaysUntilWhen() {
        assertEquals(NOW.plus(Duration.ofHours(9)), EarlyReadings.until(activeSince(Duration.ofHours(3)), NOW))
        assertEquals(NOW.plusSeconds(1), EarlyReadings.until(activeSince(Duration.ofHours(12).minusSeconds(1)), NOW))
    }

    @Test fun goneAtTwelveHours() {
        assertNull(EarlyReadings.until(activeSince(Duration.ofHours(12)), NOW))
        assertNull(EarlyReadings.until(activeSince(Duration.ofDays(5)), NOW))
    }

    @Test fun notWhileWarmingUpEndedOrUnknown() {
        assertNull(EarlyReadings.until(SensorState.WarmingUp(NOW.plus(Duration.ofMinutes(20))), NOW))
        assertNull(EarlyReadings.until(SensorState.Ended, NOW))
        assertNull(EarlyReadings.until(SensorState.Active(null), NOW))
    }
}
