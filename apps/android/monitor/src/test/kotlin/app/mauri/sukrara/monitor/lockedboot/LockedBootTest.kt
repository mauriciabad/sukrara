// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.lockedboot

import app.mauri.sukrara.monitor.alerts.FakeAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The boot path before the first unlock: notice, alarm, ring, and the unlock that ends it. */
class LockedBootTest {
    private class FakeSurface : LockedBootSurface {
        val log = mutableListOf<String>()

        override fun showWaiting() {
            log += "waiting"
        }

        override fun showMonitoring() {
            log += "monitoring"
        }

        override fun ring(plan: LockedBootPlan) {
            log += "ring"
        }

        override fun clear() {
            log += "clear"
        }
    }

    private val last = 1_760_000_000L
    private var now = last + 120
    private var unlocked = false
    private var secure = true
    private var plan: LockedBootPlan? = LockedBootPlan(monitoring = true, lastReadingAt = last, noDataMin = 20, repeatMin = 60)
    private val surface = FakeSurface()
    private val alarm = FakeAlarm()
    private val boot = LockedBoot({ plan }, surface, alarm, { secure }, { unlocked }, { now })

    @Test
    fun `a locked boot says monitoring waits and arms no data`() {
        boot.onLockedBoot(monitoring = false)
        assertEquals(listOf("waiting"), surface.log)
        assertEquals(last + 20 * 60, alarm.at)
    }

    @Test
    fun `the alarm rings and comes back every re-alert interval until the unlock`() {
        boot.onLockedBoot(monitoring = false)
        now = requireNotNull(alarm.at)
        boot.onAlarm()
        assertEquals(listOf("waiting", "ring"), surface.log)
        assertEquals(now + 3600, alarm.at)
        unlocked = true
        now = requireNotNull(alarm.at)
        boot.onAlarm()
        assertEquals(listOf("waiting", "ring", "clear"), surface.log)
        assertNull(alarm.at)
    }

    @Test
    fun `a restart long after the last reading rings at once`() {
        now = last + 2 * 3600
        boot.onLockedBoot(monitoring = false)
        assertEquals(listOf("waiting", "ring"), surface.log)
        assertEquals(now + 3600, alarm.at)
    }

    @Test
    fun `the unlock clears the notice and the alarm`() {
        boot.onLockedBoot(monitoring = false)
        unlocked = true
        boot.onUnlocked()
        assertEquals(listOf("waiting", "clear"), surface.log)
        assertNull(alarm.at)
    }

    @Test
    fun `nothing without a screen lock, when already unlocked, or with nothing to monitor`() {
        secure = false
        boot.onLockedBoot(monitoring = false)
        secure = true
        unlocked = true
        boot.onLockedBoot(monitoring = false)
        unlocked = false
        plan = LockedBootPlan()
        boot.onLockedBoot(monitoring = false)
        plan = null
        boot.onLockedBoot(monitoring = false)
        assertEquals(emptyList<String>(), surface.log)
        assertNull(alarm.at)
    }

    @Test
    fun `a phone that monitors before the unlock says so, with no alarm of its own (the alerts run), and the unlock clears it`() {
        alarm.set(last)
        boot.onLockedBoot(monitoring = true)
        assertEquals(listOf("monitoring"), surface.log)
        assertNull("the alert runtime's own no data rings, not this", alarm.at)
        unlocked = true
        boot.onUnlocked()
        assertEquals(listOf("monitoring", "clear"), surface.log)
    }

    @Test
    fun `monitoring before the unlock shows nothing on a phone without a screen lock or already unlocked`() {
        secure = false
        boot.onLockedBoot(monitoring = true)
        secure = true
        unlocked = true
        boot.onLockedBoot(monitoring = true)
        assertEquals(emptyList<String>(), surface.log)
    }

    @Test
    fun `no data off still says monitoring waits, without an alarm`() {
        plan = plan?.copy(noDataMin = null)
        boot.onLockedBoot(monitoring = false)
        assertEquals(listOf("waiting"), surface.log)
        assertNull(alarm.at)
    }
}
