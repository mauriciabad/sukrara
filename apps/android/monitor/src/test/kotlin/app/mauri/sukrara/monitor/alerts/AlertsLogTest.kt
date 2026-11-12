// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * A failed alert step reaches logcat under its tag (a warning: release builds strip only verbose and debug lines) as
 * well as the diagnostic log behind *Copy report*, so an alert that wasn't shown is visible with `adb logcat` too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertsLogTest {
    @Test fun aFailedStepGoesToLogcatAndTheDiagnosticLog() {
        val diag = mutableListOf<String>()
        alertsLog { diag += it }("show failed: NotFoundException")
        assertEquals(listOf("show failed: NotFoundException"), diag)
        val logcat = ShadowLog.getLogsForTag(AlertsModule.TAG).map { it.type to it.msg }
        assertEquals(listOf(Log.WARN to "show failed: NotFoundException"), logcat)
    }
}
