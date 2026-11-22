// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLockTest {
    @Test
    fun theLockRuleIsLockedUnlessTheScreenIsOnWithoutTheLockScreen() {
        assertTrue("screen off", PhoneLock.locked(interactive = false, keyguard = false))
        assertTrue("the lock screen up", PhoneLock.locked(interactive = true, keyguard = true))
        assertFalse("in the person's hand", PhoneLock.locked(interactive = true, keyguard = false))
    }
}
