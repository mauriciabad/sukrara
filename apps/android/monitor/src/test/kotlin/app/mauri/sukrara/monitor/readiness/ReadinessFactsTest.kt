// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readiness

import app.mauri.sukrara.core.model.AlertRequirement
import org.junit.Assert.assertEquals
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
            dndSilencesAlarms = false,
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
                allGood.copy(dndSilencesAlarms = true) to AlertRequirement.AlertChannel,
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

    @Test fun alarmsThatCantOverrideDoNotDisturbAreAnItemOnlyWhileAKindRingsLikeAnAlarm() {
        assertEquals(setOf(AlertRequirement.AlarmsInDnd), allGood.copy(alarmsBypassDnd = false).readiness().missing)
        assertTrue(allGood.copy(anyAlarmKind = false, alarmsBypassDnd = false).readiness().ready)
    }

    @Test fun theChannelsAreCreatedAgainOnceDoNotDisturbAccessIsGranted() {
        // access granted, an alarm channel created before it: create again (Android applies the bypass now)
        assertTrue(AndroidAlertReadiness.needsDndSync(accessGranted = true, alarmChannelsBypass = listOf(true, false)))
        // no access: creating again changes nothing; all overriding already: nothing to do
        assertEquals(false, AndroidAlertReadiness.needsDndSync(accessGranted = false, alarmChannelsBypass = listOf(false)))
        assertEquals(false, AndroidAlertReadiness.needsDndSync(accessGranted = true, alarmChannelsBypass = listOf(true, true)))
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
