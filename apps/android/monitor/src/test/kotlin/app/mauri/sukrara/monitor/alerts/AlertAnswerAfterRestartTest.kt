// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #39 after Android stopped the process while a very low showed: swiping it away (its notification's tidy-up) or
 * opening the app is still an answer on the wearer's phone (the followers' escalation doesn't come). The new process's
 * runtime reads the saved alerts first; it used to see no alert machine yet and drop the answer.
 */
class AlertAnswerAfterRestartTest {
    private val rig = AlertRig()

    private suspend fun answered() =
        checkNotNull(
            rig.record.data
                .first()
                .machine,
        ).escalation.answered

    /** A very low on the wearer's phone, then the process dies: a new runtime over the same saved state. */
    private suspend fun veryLowThenRestart(): AlertRuntime {
        val before = rig.runtime()
        before.onState(rig.wearerState(latest = rig.reading(50)))
        assertTrue("very_low" in rig.published.firingKinds)
        assertFalse(answered())
        return rig.runtime()
    }

    @Test fun aSwipeInANewProcessIsAnAnswer() =
        runTest {
            veryLowThenRestart().acknowledge("very_low")
            assertTrue(answered())
        }

    @Test fun openingTheAppInANewProcessIsAnAnswer() =
        runTest {
            veryLowThenRestart().acknowledge()
            assertTrue(answered())
        }
}
