// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readiness

import android.app.Application
import android.app.NotificationManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.testing.TestDispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The checklist's Do Not Disturb access item (#15) as the phone answers it, and where its Fix goes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidAlertReadinessTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val nm = app.getSystemService(NotificationManager::class.java)

    private fun readiness(failed: Set<String> = emptySet()) =
        AndroidAlertReadiness(app, TestScope(), TestDispatchers(), flowOf(AlertSettings()), flowOf(false), flowOf(failed))

    @Test fun withoutDoNotDisturbAccessTheAlarmLevelAlertsAskForIt() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        val r = readiness()
        // very low and extremely high ring like an alarm by default
        assertTrue(AlertRequirement.AlarmsInDnd in r.readiness.value.missing)
        r.fix(AlertRequirement.AlarmsInDnd)
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    @Test fun withAccessAChannelNotCreatedYetWillOverride() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        assertTrue(AlertRequirement.AlarmsInDnd !in readiness().readiness.value.missing)
    }
}
