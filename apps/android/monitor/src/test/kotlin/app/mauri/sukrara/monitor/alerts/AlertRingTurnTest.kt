// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * #130: an alert waits its turn before it rings (an info sound less than a second before would mute it: SoundGap); a
 * silent re-post never waits.
 */
class AlertRingTurnTest {
    @Test fun everyLoudAlertWaitsItsTurnAndSilentOnesDont() =
        runBlocking {
            val rig = AlertRig()
            val runtime = rig.runtime()
            runtime.onState(rig.wearerState(rig.reading(60)))
            assertEquals(listOf("show low"), rig.presenter.log)
            assertEquals(1, rig.presenter.waits)

            // a process death: the alert comes back silently, without waiting
            val again = rig.runtime()
            again.start()
            assertEquals("resume low", rig.presenter.log.last())
            assertEquals(1, rig.presenter.waits)

            // its repeat rings: it waits again
            rig.clock.instant = rig.clock.instant + Duration.ofMinutes(5)
            again.onState(rig.wearerState(rig.reading(60)))
            assertEquals("show low", rig.presenter.log.last())
            assertEquals(2, rig.presenter.waits)
        }
}
