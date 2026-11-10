// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertInput
import app.mauri.sukrara.alerts.Rules
import app.mauri.sukrara.monitor.lockedboot.LockedBootPlan
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The alert runtime keeps the plan for the time before the first unlock in step with every save. */
class LockedBootPlanSaveTest {
    private val rig = AlertRig()

    @Test fun everyReadingKeepsItsTimeButNotItsValue() =
        runTest {
            val r = rig.reading(140)
            rig.runtime().onState(rig.wearerState(latest = r))
            val plan = requireNotNull(rig.lockedBoot)
            assertTrue(plan.monitoring)
            assertEquals(r.t.epochSecond, plan.lastReadingAt)
            assertEquals(20, plan.noDataMin)
            assertEquals(60, plan.repeatMin)
            assertFalse("no glucose value in it", rig.lockedBoot.toString().contains("140"))
        }

    @Test fun beforeMonitoringStartsNothingIsSaidBeforeTheUnlock() =
        runTest {
            rig.runtime().onState(rig.wearerState(latest = null, monitoring = false))
            assertEquals(LockedBootPlan(), rig.lockedBoot)
        }

    @Test fun muteAllAndStartFreshReachThePlan() =
        runTest {
            val runtime = rig.runtime()
            runtime.onState(rig.wearerState(latest = rig.reading(120)))
            runtime.process(AlertInput.MuteAll(Rules.NEVER))
            assertEquals(Rules.NEVER, rig.lockedBoot?.muteAllUntil)
            runtime.reset()
            assertEquals(LockedBootPlan(), rig.lockedBoot)
        }
}
