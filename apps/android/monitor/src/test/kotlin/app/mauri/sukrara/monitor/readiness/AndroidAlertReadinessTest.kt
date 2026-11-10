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
import app.mauri.sukrara.core.model.UnlockedSound
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

/**
 * The checklist's Do Not Disturb access item (#15) and *Sound while the phone is unlocked* (#129) as the phone answers
 * them, and where their Fix goes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidAlertReadinessTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val nm = app.getSystemService(NotificationManager::class.java)

    private val answers = mutableListOf<UnlockedSound>()
    private val scope = TestScope()

    private fun readiness(failed: Set<String> = emptySet()) =
        AndroidAlertReadiness(
            app,
            scope,
            TestDispatchers(),
            flowOf(AlertSettings()),
            flowOf(false),
            flowOf(failed),
            flowOf(UnlockedSound.Unchecked),
        ) { answers += it }

    @Test fun theAnswerAboutVibrateOnlyWhenUnlockedIsStored() {
        readiness().answerUnlockedSound(UnlockedSound.AppPlays)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(listOf(UnlockedSound.AppPlays), answers)
    }

    @Test fun vibrateOnlyWhenUnlockedReadsAndroidsValueWhenItMay() {
        // Robolectric lets the app read it (a phone throws: NotificationCooldown answers null and the person is asked)
        Settings.System.putInt(app.contentResolver, "notification_cooldown_vibrate_unlocked", 1)
        assertEquals(true, NotificationCooldown.vibrateOnlyWhenUnlocked(app))
        Settings.System.putInt(app.contentResolver, "notification_cooldown_vibrate_unlocked", 0)
        assertEquals(false, NotificationCooldown.vibrateOnlyWhenUnlocked(app))
    }

    @Test fun theCooldownPageOpensForTheUnlockedItem() {
        readiness().fix(AlertRequirement.SoundWhenUnlocked)
        assertEquals("android.settings.MANAGE_ADAPTIVE_NOTIFICATIONS", shadowOf(app).nextStartedActivity.action)
    }

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
