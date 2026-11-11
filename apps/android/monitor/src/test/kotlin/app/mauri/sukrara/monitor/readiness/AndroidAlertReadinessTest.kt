// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readiness

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.core.testing.TestDispatchers
import app.mauri.sukrara.monitor.alerts.AlertChannels
import app.mauri.sukrara.monitor.alerts.AlertMirror
import app.mauri.sukrara.monitor.notify.Channels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun readiness(
        failed: Set<String> = emptySet(),
        settings: Flow<AlertSettings> = flowOf(AlertSettings()),
    ) = AndroidAlertReadiness(
        app,
        scope,
        TestDispatchers(StandardTestDispatcher(scope.testScheduler)),
        settings,
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

    /**
     * Very low's alarm channel as the person edited it (no pop-up, no override): Android keeps it from taking the
     * override in place, as Robolectric does with any existing channel.
     */
    private fun editedVeryLow(sound: Uri = AlertChannels.soundUri(app, AlertKind.VERY_LOW)) =
        nm.createNotificationChannel(
            NotificationChannel(Channels.alarm("very_low"), "Very low", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(sound, AlertChannels.audioAttributes(alarm = true))
            },
        )

    private fun veryLowOverrides() = nm.getNotificationChannel(AlertChannels.id(app, AlertKind.VERY_LOW, alarm = true)).canBypassDnd()

    /** #15: the person grants access (in Android's list, from Fix) and comes back: the edited channel moves. */
    @Test fun aGrantSeenHereMovesTheEditedAlarmChannel() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        editedVeryLow()
        val r = readiness()
        assertTrue(AlertRequirement.AlarmsInDnd in settled(r))

        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        r.refresh()

        assertTrue(AlertRequirement.AlarmsInDnd !in settled(r))
        assertEquals("alert_alarm.very_low.2", AlertChannels.id(app, AlertKind.VERY_LOW, alarm = true))
        assertTrue(veryLowOverrides())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, nm.getNotificationChannel("alert_alarm.very_low.2").importance)
    }

    /** Access granted before (the override turned off on purpose since?): it stays off until the person taps Fix. */
    @Test fun withAccessFromBeforeAnEditedChannelWaitsForFix() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        editedVeryLow()
        val r = readiness()
        assertTrue(AlertRequirement.AlarmsInDnd in settled(r))
        assertFalse(veryLowOverrides())

        r.fix(AlertRequirement.AlarmsInDnd)
        scope.testScheduler.advanceUntilIdle()

        assertTrue(veryLowOverrides())
        assertEquals("no page: the item just goes", null, shadowOf(app).nextStartedActivity)
        assertTrue(AlertRequirement.AlarmsInDnd !in settled(r))
    }

    /** A sound Sukrara can't read would be lost on a new channel: Fix opens the channel's own page instead. */
    @Test fun fixOpensTheChannelPageWhenItsSoundCantMove() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        editedVeryLow("content://nobody.here/sound".toUri())
        val r = readiness()
        settled(r)

        r.fix(AlertRequirement.AlarmsInDnd)
        scope.testScheduler.advanceUntilIdle()

        val page = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, page.action)
        assertEquals(Channels.alarm("very_low"), page.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        assertFalse(veryLowOverrides())
    }

    /**
     * A mode that silences alarms mutes the alarm volume (AudioService), override or not: the alarm-level alert shows
     * and vibrates but makes no sound. The checklist says so while it is on, and Fix opens Android's modes.
     */
    @Test fun aModeWithoutAlarmsSilencesEvenAnAlarmThatOverrides() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        AlertChannels.ensure(app)
        assertTrue(veryLowOverrides())
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        shadowOf(nm).setConsolidatedNotificationPolicy(NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_CALLS, 0, 0))
        val r = readiness()

        assertTrue(AlertRequirement.AlertChannel in settled(r))
        assertTrue(AlertRequirement.AlarmsInDnd !in settled(r))
        r.fix(AlertRequirement.AlertChannel)
        assertEquals(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, shadowOf(app).nextStartedActivity.action)

        // the mode lets alarms through: nothing to fix
        shadowOf(
            nm,
        ).setConsolidatedNotificationPolicy(NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0))
        r.refresh()
        assertTrue(AlertRequirement.AlertChannel !in settled(r))
    }

    @Test fun withAccessAChannelNotCreatedYetWillOverride() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        assertTrue(AlertRequirement.AlarmsInDnd !in readiness().readiness.value.missing)
    }

    /**
     * #57: a follower mirroring the wearer runs the wearer's rules (`AlertMirror.effective`), so the checklist judges
     * those. This phone's own very low and extremely high are off; the wearer's are on and ring like an alarm here.
     */
    @Test fun aMirroringFollowerIsAskedAboutTheRulesItRuns() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        val own =
            listOf(AlertKind.VERY_LOW, AlertKind.EXTREMELY_HIGH).fold(AlertSettings()) { s, k ->
                s.withRule(k, s.rule(k).copy(on = false))
            }

        val ownRules = settled(readiness(settings = AlertMirror.inUse(flowOf(own), flowOf(null))))
        assertTrue(AlertRequirement.AlarmsInDnd !in ownRules)

        val wearersRules = settled(readiness(settings = AlertMirror.inUse(flowOf(own), flowOf(AlertSettings()))))
        assertTrue(AlertRequirement.AlarmsInDnd in wearersRules)
    }

    /** What the checklist says once a screen collects it and every input has answered. */
    private fun settled(r: AndroidAlertReadiness): Set<AlertRequirement> {
        val screen = scope.launch { r.readiness.collect {} }
        scope.testScheduler.advanceUntilIdle()
        return r.readiness.value.missing
            .also { screen.cancel() }
    }
}
