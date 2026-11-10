// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor

import android.app.AlarmManager
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorReceiverTest {
    /** #44: the exact-alarm permission granted again re-arms the alert check, not only the health check. */
    @Test fun theAlertCheckRunsAfterEverySystemEventThatCanCostItsAlarm() {
        for (action in listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )) {
            assertTrue(action, MonitorReceiver.checksAlerts(action))
        }
        assertFalse(MonitorReceiver.checksAlerts(Intent.ACTION_LOCALE_CHANGED))
        assertFalse(MonitorReceiver.checksAlerts(MonitorReceiver.ACTION_HEALTH_CHECK))
    }
}
