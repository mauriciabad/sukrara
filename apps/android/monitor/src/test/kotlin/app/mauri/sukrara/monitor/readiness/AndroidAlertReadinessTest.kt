// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readiness

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.DndStatus
import app.mauri.sukrara.core.model.SoundTest
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The checklist's *Do Not Disturb* status (#15, #184) and *Sound while the phone is unlocked* (#129, #183) as the phone
 * answers them, and where their buttons go.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidAlertReadinessTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val nm = app.getSystemService(NotificationManager::class.java)

    private val answers = mutableListOf<UnlockedSound>()
    private val tests = mutableListOf<AlertKind>()
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
        { answers += it },
        { kind, _ -> tests += kind },
    )

    @Before fun soundUp() {
        val audio = app.getSystemService(AudioManager::class.java)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 5, 0)
        audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 5, 0)
    }

    @Test fun whatTheTestDidIsStored() {
        readiness().answerUnlockedSound(UnlockedSound.OnlyVibrated)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(listOf(UnlockedSound.OnlyVibrated), answers)
    }

    /** #183: the test goes out on the alert that matters most (very low rings like an alarm by default). */
    @Test fun theUnlockedTestSendsTheFirstAlarmLevelAlert() {
        val r = readiness()
        settled(r)
        assertTrue(r.testUnlockedSound())
        assertEquals(listOf(AlertKind.VERY_LOW), tests)
    }

    /** A silence from a mode isn't the unlocked setting's: the test waits until the mode is off. */
    @Test fun theUnlockedTestWaitsWhileAModeIsOn() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        shadowOf(
            nm,
        ).setConsolidatedNotificationPolicy(NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0))
        val r = readiness()
        settled(r)
        assertEquals(SoundTest.ModeOn, r.readiness.value.soundTest)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        r.refresh()
        settled(r)
        assertEquals(SoundTest.Ready, r.readiness.value.soundTest)
    }

    @Test fun theUnlockedTestWaitsWhileThePhoneIsOnVibrate() {
        app.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_VIBRATE
        val r = readiness()
        settled(r)
        assertEquals(SoundTest.Silent, r.readiness.value.soundTest)
    }

    @Test fun withNoAlertOnThereIsNothingToTest() {
        val r = readiness(settings = flowOf(AlertSettings().copy(on = false)))
        settled(r)
        assertEquals(SoundTest.NoAlert, r.readiness.value.soundTest)
        assertFalse(r.testUnlockedSound())
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

    /**
     * Very low's alarm channel as the person edited it (no pop-up, no override): Android keeps it from changing its
     * override in place, as Robolectric does with any existing channel.
     */
    private fun editedVeryLow(sound: Uri = AlertChannels.soundUri(app, AlertKind.VERY_LOW)) =
        nm.createNotificationChannel(
            NotificationChannel(Channels.alarm("very_low"), "Very low", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(sound, AlertChannels.audioAttributes(alarm = true))
            },
        )

    private fun veryLowOverrides() = nm.getNotificationChannel(AlertChannels.id(app, AlertKind.VERY_LOW, alarm = true)).canBypassDnd()

    /** #184: a fresh install respects Do Not Disturb, and says so without a fault. */
    @Test fun byDefaultSukraraDoesntAlertInDoNotDisturbAndThatIsNoFault() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        AlertChannels.ensure(app)
        val r = readiness()
        assertTrue(AlertRequirement.DoNotDisturb !in settled(r))
        assertFalse(r.readiness.value.dnd.through)
        assertTrue(AlertChannels.alarmIds(app).values.none { nm.getNotificationChannel(it).canBypassDnd() })
    }

    /** Let Sukrara through without access: Android's access list; the grant, seen on the way back, applies it. */
    @Test fun lettingThroughAsksForAccessAndTheGrantAppliesIt() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        editedVeryLow()
        val r = readiness()
        settled(r)

        r.letThroughDnd(true)
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, shadowOf(app).nextStartedActivity.action)
        assertFalse(r.readiness.value.dnd.through)

        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        r.refresh()
        settled(r)

        assertTrue(r.readiness.value.dnd.through)
        assertEquals("alert_alarm.very_low.2", AlertChannels.id(app, AlertKind.VERY_LOW, alarm = true))
        assertTrue(veryLowOverrides())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, nm.getNotificationChannel("alert_alarm.very_low.2").importance)
    }

    /** A grant Sukrara didn't ask for (made in Android's list on its own) changes nothing: the button decides. */
    @Test fun aGrantNobodyAskedForChangesNothing() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        AlertChannels.ensure(app)
        val r = readiness()
        settled(r)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        r.refresh()
        settled(r)
        assertFalse(r.readiness.value.dnd.through)
    }

    /** With access, the button works both ways without leaving the app, and the statement follows. */
    @Test fun withAccessTheButtonFlipsItBothWays() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        editedVeryLow()
        val r = readiness()
        settled(r)
        assertFalse(r.readiness.value.dnd.through)

        r.fix(AlertRequirement.DoNotDisturb)
        scope.testScheduler.advanceUntilIdle()
        settled(r)
        assertTrue(veryLowOverrides())
        assertTrue(r.readiness.value.dnd.through)
        assertEquals("no page: it just changes", null, shadowOf(app).nextStartedActivity)

        r.letThroughDnd(false)
        scope.testScheduler.advanceUntilIdle()
        settled(r)
        assertFalse(veryLowOverrides())
        assertFalse(r.readiness.value.dnd.through)
    }

    /** A sound Sukrara can't read would be lost on a new channel: the button opens the channel's own page instead. */
    @Test fun aChannelWhoseSoundCantMoveOpensItsPage() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        editedVeryLow("content://nobody.here/sound".toUri())
        val r = readiness()
        settled(r)

        r.letThroughDnd(true)
        scope.testScheduler.advanceUntilIdle()

        val page = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, page.action)
        assertEquals(Channels.alarm("very_low"), page.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        assertFalse(veryLowOverrides())
    }

    /**
     * A mode on now that keeps the alerts quiet is shown (home, Settings), never a fault: a mode without alarms mutes
     * the alarm volume even for an alarm that overrides (#15), and a mode that lets alarms through only holds back the
     * others.
     */
    @Test fun aModeOnNowIsShownNotFlagged() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        AlertChannels.setDndBypass(app, bypass = true)
        assertTrue(veryLowOverrides())
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        shadowOf(nm).setConsolidatedNotificationPolicy(NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_CALLS, 0, 0))
        val r = readiness()

        val missing = settled(r)
        assertTrue(AlertRequirement.AlertChannel !in missing && AlertRequirement.DoNotDisturb !in missing)
        assertEquals(DndStatus.Held.All, r.readiness.value.dnd.heldNow)

        shadowOf(
            nm,
        ).setConsolidatedNotificationPolicy(NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0))
        r.refresh()
        settled(r)
        assertEquals(DndStatus.Held.Some, r.readiness.value.dnd.heldNow)

        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        r.refresh()
        settled(r)
        assertEquals(DndStatus.Held.None, r.readiness.value.dnd.heldNow)
    }

    /**
     * #57: a follower mirroring the wearer runs the wearer's rules (`AlertMirror.effective`), so the checklist judges
     * those. Every alert of this phone's own is off; the wearer's are on: the unlocked test has something to send.
     */
    @Test fun aMirroringFollowerIsAskedAboutTheRulesItRuns() {
        val own = AlertKind.entries.fold(AlertSettings()) { s, k -> s.withRule(k, s.rule(k).copy(on = false)) }

        val ownRules = readiness(settings = AlertMirror.inUse(flowOf(own), flowOf(null)))
        settled(ownRules)
        assertEquals(SoundTest.NoAlert, ownRules.readiness.value.soundTest)

        val wearersRules = readiness(settings = AlertMirror.inUse(flowOf(own), flowOf(AlertSettings())))
        settled(wearersRules)
        assertEquals(SoundTest.Ready, wearersRules.readiness.value.soundTest)
    }

    /** What the checklist says once a screen collects it and every input has answered. */
    private fun settled(r: AndroidAlertReadiness): Set<AlertRequirement> {
        val screen = scope.launch { r.readiness.collect {} }
        scope.testScheduler.advanceUntilIdle()
        return r.readiness.value.missing
            .also { screen.cancel() }
    }
}
