// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.lockedboot

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Rules
import app.mauri.sukrara.alerts.SensorWindow
import app.mauri.sukrara.alerts.SoundSettings
import app.mauri.sukrara.monitor.alerts.SavedSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the alarm before the first unlock rings: the *No data* rule's conditions, from the saved plan only. */
class LockedBootPlanTest {
    private val last = 1_760_000_000L
    private val plan = LockedBootPlan(monitoring = true, lastReadingAt = last, noDataMin = 20, repeatMin = 60)

    @Test
    fun `rings when no data would have, counted from the last reading`() {
        assertEquals(last + 20 * 60, plan.firstAlarm(now = last + 60))
    }

    @Test
    fun `a restart after the no data time rings at once`() {
        assertEquals(last + 3600, plan.firstAlarm(now = last + 3600))
    }

    @Test
    fun `nothing rings without a reading, with no data off or before monitoring started`() {
        assertNull(plan.copy(lastReadingAt = null).firstAlarm(last))
        assertNull(plan.copy(noDataMin = null).firstAlarm(last))
        assertNull(plan.copy(monitoring = false).firstAlarm(last))
        assertNull(LockedBootPlan.UNREADABLE.firstAlarm(last))
    }

    @Test
    fun `mute all moves the alarm to its end, or keeps it off until turned back on`() {
        assertEquals(last + 7200, plan.copy(muteAllUntil = last + 7200).firstAlarm(last + 60))
        assertEquals(last + 1200, plan.copy(muteAllUntil = last + 600).firstAlarm(last + 60))
        assertNull(plan.copy(muteAllUntil = Rules.NEVER).firstAlarm(last + 60))
    }

    @Test
    fun `a warming sensor rings after its warm-up and grace, an ended one never`() {
        val warming = SensorWindow.started(last + 300, wearMin = 15 * 24 * 60)
        assertEquals(
            warming.warmupEndEpoch + SensorWindow.WARMUP_GRACE_S,
            plan.copy(sensor = SavedSensor(warming)).firstAlarm(last + 600),
        )
        val ending = SensorWindow(startEpoch = 0, endEpoch = last + 600, warmupEndEpoch = 0)
        assertNull("the sensor ends before no data", plan.copy(sensor = SavedSensor(ending)).firstAlarm(last + 60))
    }

    @Test
    fun `rings again every re-alert interval, once with 0`() {
        assertEquals(last + 3600, plan.nextAlarm(last))
        assertNull(plan.copy(repeatMin = 0).nextAlarm(last))
        val ending = SensorWindow(startEpoch = 0, endEpoch = last + 1800, warmupEndEpoch = 0)
        assertNull(plan.copy(sensor = SavedSensor(ending)).nextAlarm(last))
    }

    @Test
    fun `the plan from the runtime keeps times and the no data rule, never a value`() {
        val settings = AlertSettings(sounds = SoundSettings(pack = "kart", perKind = mapOf(AlertKind.NO_DATA to "soft"), shuffle = false))
        val p = LockedBootPlan.of(settings, monitoring = true, lastReadingAt = last, muteAllUntil = 0, sensor = null)
        assertEquals(LockedBootPlan(true, last, 20, 60, alarm = true, sound = "soft", shuffle = false), p)
    }

    @Test
    fun `alerts or no data off means no alarm, and the alarm volume follows the alerts that are on`() {
        val off = AlertSettings(on = false)
        assertNull(LockedBootPlan.of(off, true, last, 0, null).noDataMin)
        val noDataOff = AlertSettings().let { it.withRule(AlertKind.NO_DATA, it.rule(AlertKind.NO_DATA).copy(on = false)) }
        assertNull(LockedBootPlan.of(noDataOff, true, last, 0, null).noDataMin)
        val quiet =
            AlertSettings().let { s ->
                AlertKind.entries.fold(s) { acc, k -> acc.withRule(k, acc.rule(k).copy(alarm = false)) }
            }
        assertFalse(LockedBootPlan.of(quiet, true, last, 0, null).alarm)
        assertTrue("very low rings as an alarm by default", LockedBootPlan.of(AlertSettings(), true, last, 0, null).alarm)
    }

    @Test
    fun `without a role or before monitoring nothing is said before the unlock`() {
        assertEquals(LockedBootPlan(), LockedBootPlan.of(null, true, last, 0, null))
        assertEquals(LockedBootPlan(), LockedBootPlan.of(AlertSettings(), false, last, 0, null))
    }
}
