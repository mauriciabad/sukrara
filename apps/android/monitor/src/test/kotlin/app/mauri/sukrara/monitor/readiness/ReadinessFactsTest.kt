// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readiness

import android.app.NotificationManager
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.DndStatus
import app.mauri.sukrara.core.model.SoundTest
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.monitor.alerts.AlertCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The alert checklist's rule: which phone answers make an item missing (and which don't apply). */
class ReadinessFactsTest {
    private val allGood =
        ReadinessFacts(
            notificationsEnabled = true,
            anyAlarmKind = true,
            fullScreenAllowed = true,
            exactAlarmAllowed = true,
            batteryExempt = true,
            backgroundRestricted = false,
            alertChannelSilenced = false,
            needsBluetooth = true,
            bluetoothOn = true,
            bluetoothPermitted = true,
        )

    @Test fun everythingGrantedIsReady() = assertTrue(allGood.readiness().ready)

    @Test fun eachAnswerMapsToItsItem() {
        val cases =
            mapOf(
                allGood.copy(notificationsEnabled = false) to AlertRequirement.Notifications,
                allGood.copy(fullScreenAllowed = false) to AlertRequirement.FullScreenIntent,
                allGood.copy(exactAlarmAllowed = false) to AlertRequirement.ExactAlarm,
                allGood.copy(batteryExempt = false) to AlertRequirement.BatteryExemption,
                allGood.copy(backgroundRestricted = true) to AlertRequirement.BatteryExemption,
                allGood.copy(alertChannelSilenced = true) to AlertRequirement.AlertChannel,
                allGood.copy(bluetoothOn = false) to AlertRequirement.Bluetooth,
                allGood.copy(bluetoothPermitted = false) to AlertRequirement.Bluetooth,
            )
        for ((facts, item) in cases) assertEquals(facts.toString(), setOf(item), facts.readiness().missing)
    }

    @Test fun aFailedAlertStepIsTheChecklistsFirstProblemWithItsReport() {
        val r = allGood.copy(failedSteps = setOf("Notify:level", "Alarm"), notificationsEnabled = false).readiness()
        assertEquals(AlertRequirement.AlertDelivery, r.firstProblem)
        assertEquals(setOf(AlertRequirement.AlertDelivery, AlertRequirement.Notifications), r.missing)
        val report = r.report("1.2.3", 37)
        assertTrue(report, report.contains("Alarm, Notify:level") && report.contains("AlertDelivery, Notifications"))
        assertTrue(report, report.contains("Sukrara 1.2.3 · Android API 37"))
    }

    @Test fun theStepThatWorksAgainLeavesTheChecklist() = assertTrue(allGood.copy(failedSteps = emptySet()).readiness().ready)

    /** #184: Do Not Disturb is the person's choice: never missing, whatever it is and whatever a mode holds back. */
    @Test fun doNotDisturbIsAStatusNeverAFault() {
        for (through in listOf(false, true)) {
            for (held in DndStatus.Held.entries) {
                val r = allGood.copy(dnd = DndStatus(through, held)).readiness()
                assertTrue(r.ready)
                assertEquals(DndStatus(through, held), r.dnd)
            }
        }
    }

    @Test fun sukraraIsAnExceptionOnlyWhileEveryAlarmChannelOverrides() {
        assertTrue(DndHold.through(listOf(true, true)))
        assertFalse(DndHold.through(listOf(true, false)))
        assertFalse("no channel yet: no override", DndHold.through(emptyList()))
    }

    private val alarm = DndHold.Channel(alarm = true, bypass = false)
    private val alarmThrough = alarm.copy(bypass = true)
    private val notification = DndHold.Channel(alarm = false, bypass = false)

    private fun priority(
        alarms: Boolean,
        reminders: Boolean,
    ) = AlertCategory.Dnd(NotificationManager.INTERRUPTION_FILTER_PRIORITY, alarms, reminders)

    @Test fun whatAModeOnNowHoldsBack() {
        val off = AlertCategory.Dnd(NotificationManager.INTERRUPTION_FILTER_ALL, false, false)
        assertEquals(DndStatus.Held.None, DndHold.held(listOf(alarm, notification), off))
        assertEquals("unknown: no claim", DndStatus.Held.None, DndHold.held(listOf(alarm, notification), null))
        val total = AlertCategory.Dnd(NotificationManager.INTERRUPTION_FILTER_NONE, false, false)
        assertEquals(DndStatus.Held.All, DndHold.held(listOf(alarmThrough, notification), total))
        val alarmsOnly = AlertCategory.Dnd(NotificationManager.INTERRUPTION_FILTER_ALARMS, false, false)
        assertEquals(DndStatus.Held.Some, DndHold.held(listOf(alarm, notification), alarmsOnly))
        // Android's default mode lets alarms through, not reminders: the alarm-level alert rings, the other is held
        assertEquals(DndStatus.Held.Some, DndHold.held(listOf(alarm, notification), priority(alarms = true, reminders = false)))
        // a Bedtime mode without alarms: the override shows and vibrates, but the alarm volume is muted (#15)
        assertEquals(DndStatus.Held.All, DndHold.held(listOf(alarmThrough), priority(alarms = false, reminders = false)))
        assertEquals(DndStatus.Held.None, DndHold.held(listOf(alarm, notification), priority(alarms = true, reminders = true)))
    }

    @Test fun aNotificationVolumeAlertThatOverridesPassesPriority() =
        assertTrue(DndHold.sounds(notification.copy(bypass = true), priority(alarms = false, reminders = false)))

    @Test fun vibrateOnlyWhenUnlockedIsAskedOnceWhenAndroidWontSay() {
        val hidden = allGood.copy(vibrateOnlyUnlocked = null)
        assertEquals(setOf(AlertRequirement.SoundWhenUnlocked), hidden.readiness().missing)
        // either answer settles it (It only vibrated: Sukrara plays the sound itself), and it travels with the checklist
        for (answer in listOf(UnlockedSound.Heard, UnlockedSound.OnlyVibrated)) {
            val r = hidden.copy(unlockedSound = answer).readiness()
            assertTrue(r.ready)
            assertEquals(answer, r.unlockedSound)
        }
        // Android said: on (Sukrara plays it) or off, nothing to ask
        assertTrue(allGood.copy(vibrateOnlyUnlocked = true).readiness().ready)
        assertTrue(allGood.copy(vibrateOnlyUnlocked = false).readiness().ready)
    }

    @Test fun theUnlockedTestStateTravelsWithTheChecklist() {
        for (t in SoundTest.entries) assertEquals(t, allGood.copy(soundTest = t).readiness().soundTest)
    }

    /** #183: the test goes out on a channel that sounds and vibrates (only there Android drops the sound), alarm first. */
    @Test fun theUnlockedTestPicksAChannelThatSoundsAndVibrates() {
        val alarmKinds = setOf("very_low")
        val vibrates = setOf("low", "high")

        fun pick(on: List<String>) = NotificationCooldown.testKind(on, { it in alarmKinds }) { it in vibrates }
        assertEquals("low", pick(listOf("very_low", "low", "high")))
        assertEquals("high", pick(listOf("high", "very_low")))
        assertEquals("none vibrates: the first, alarm first", "very_low", pick(listOf("rising_fast", "very_low")))
        assertEquals(null, pick(emptyList()))
    }

    @Test fun fullScreenOnlyMattersWhenAKindRingsLikeAnAlarm() =
        assertTrue(allGood.copy(anyAlarmKind = false, fullScreenAllowed = false).readiness().ready)

    @Test fun bluetoothOnlyMattersForTheWearersOwnSensor() =
        assertTrue(allGood.copy(needsBluetooth = false, bluetoothOn = false, bluetoothPermitted = false).readiness().ready)

    @Test fun theRowNamesTheFirstProblemInChecklistOrder() =
        assertEquals(
            AlertRequirement.Notifications,
            allGood.copy(bluetoothOn = false, notificationsEnabled = false).readiness().firstProblem,
        )
}
