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
        boot.onLockedBoot()
        assertEquals(listOf("waiting"), surface.log)
        assertEquals(last + 20 * 60, alarm.at)
    }

    @Test
    fun `the alarm rings and comes back every re-alert interval until the unlock`() {
        boot.onLockedBoot()
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
        boot.onLockedBoot()
        assertEquals(listOf("waiting", "ring"), surface.log)
        assertEquals(now + 3600, alarm.at)
    }

    @Test
    fun `the unlock clears the notice and the alarm`() {
        boot.onLockedBoot()
        unlocked = true
        boot.onUnlocked()
        assertEquals(listOf("waiting", "clear"), surface.log)
        assertNull(alarm.at)
    }

    @Test
    fun `nothing without a screen lock, when already unlocked, or with nothing to monitor`() {
        secure = false
        boot.onLockedBoot()
        secure = true
        unlocked = true
        boot.onLockedBoot()
        unlocked = false
        plan = LockedBootPlan()
        boot.onLockedBoot()
        plan = null
        boot.onLockedBoot()
        assertEquals(emptyList<String>(), surface.log)
        assertNull(alarm.at)
    }

    @Test
    fun `no data off still says monitoring waits, without an alarm`() {
        plan = plan?.copy(noDataMin = null)
        boot.onLockedBoot()
        assertEquals(listOf("waiting"), surface.log)
        assertNull(alarm.at)
    }
}
