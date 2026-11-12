// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readiness

import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.DndStatus
import app.mauri.sukrara.core.model.SoundTest
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.monitor.alerts.AlertRuntime

/**
 * What the phone (and the alert runtime) answered for each item of the alert checklist, read by
 * [AndroidAlertReadiness]. Kept apart from the reads so THE rule ([readiness]: may an alert not reach the person?) is a
 * plain function with JVM tests.
 */
data class ReadinessFacts(
    /** The alert runtime's steps that failed on their last attempt, with the exception class (`AlertUiState.failedSteps`). */
    val failedSteps: Set<String> = emptySet(),
    val notificationsEnabled: Boolean,
    /** At least one alert kind rings "like an alarm" (only then the full-screen intent matters). */
    val anyAlarmKind: Boolean,
    val fullScreenAllowed: Boolean,
    val exactAlarmAllowed: Boolean,
    val batteryExempt: Boolean,
    /** Settings → Apps → Battery "Restricted": Android stops the service in the background. */
    val backgroundRestricted: Boolean,
    /** An alert channel the settings use (or its group) is blocked or lowered below sound. */
    val alertChannelSilenced: Boolean,
    /**
     * Where Sukrara stands with Do Not Disturb: the person's choice (an exception or not) and what a mode on now holds
     * back. Shown, never a fault (#184).
     */
    val dnd: DndStatus = DndStatus(),
    /** Android's *Vibrate only when unlocked*, when it says ([NotificationCooldown]); null = it won't (#129). */
    val vibrateOnlyUnlocked: Boolean? = false,
    /** What the person heard from the unlocked test (#183). */
    val unlockedSound: UnlockedSound = UnlockedSound.Unchecked,
    /** Whether that test can show Android's behaviour now. */
    val soundTest: SoundTest = SoundTest.Ready,
    /** The role's source talks to the sensor over Bluetooth (the wearer's own sensor, not Juggluco or a follower). */
    val needsBluetooth: Boolean,
    val bluetoothOn: Boolean,
    val bluetoothPermitted: Boolean,
) {
    fun readiness(): AlertReadiness =
        AlertReadiness(
            buildSet {
                // an alert's own notification (and its stand-ins) apart from the other steps: "An alert couldn't be shown"
                val (shown, other) = failedSteps.partition(::isShowStep)
                if (shown.isNotEmpty()) add(AlertRequirement.AlertShown)
                if (other.isNotEmpty()) add(AlertRequirement.AlertDelivery)
                if (!notificationsEnabled) add(AlertRequirement.Notifications)
                if (anyAlarmKind && !fullScreenAllowed) add(AlertRequirement.FullScreenIntent)
                if (!exactAlarmAllowed) add(AlertRequirement.ExactAlarm)
                if (!batteryExempt || backgroundRestricted) add(AlertRequirement.BatteryExemption)
                if (alertChannelSilenced) add(AlertRequirement.AlertChannel)
                // AlertRequirement.DoNotDisturb is never missing: it's the person's choice, shown as a status
                if (NotificationCooldown.unanswered(vibrateOnlyUnlocked, unlockedSound)) add(AlertRequirement.SoundWhenUnlocked)
                if (needsBluetooth && !(bluetoothOn && bluetoothPermitted)) add(AlertRequirement.Bluetooth)
            },
            failedSteps,
            unlockedSound,
            dnd,
            soundTest,
        )

    private companion object {
        private val SHOWN = listOf(AlertRuntime.Step.Show, AlertRuntime.Step.Plain, AlertRuntime.Step.Problem).map { "${it.name}:" }

        /** A step of an alert's own notification or its stand-ins (`Show:level …`, `Plain:…`, `Problem:…`). */
        fun isShowStep(step: String) = SHOWN.any(step::startsWith)
    }
}
