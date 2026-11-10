// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * What alerts need from the phone to ring, as one checklist: THE one answer to "may an alert not reach the person?".
 * The home screen's banner, Settings › Alert reliability and the first-run setup's permission step render the same
 * items in the same order. Implemented by the alert runtime / monitor (it reads the permissions and channel settings
 * and the runtime's failed steps); screens only render it and ask to fix an item.
 */
enum class AlertRequirement(
    /** A permission or setting the person grants (setup's permission step lists it), not a state of the moment. */
    val inSetup: Boolean = true,
) {
    /**
     * The alert runtime's last attempt to post an alert, arm the alert check, tell the followers or save the alert state
     * failed ([AlertReadiness.failedSteps]); goes when that step next works. Its way out is *Copy report*.
     */
    AlertDelivery(inSetup = false),
    Notifications,
    FullScreenIntent,
    ExactAlarm,
    BatteryExemption,

    /** The alert channels may break through Do Not Disturb and still have their importance. */
    AlertChannel,

    /** Bluetooth on and the nearby-devices permission (wearer only: the sensor talks over Bluetooth). */
    Bluetooth,
}

/**
 * The checklist's current answers: every requirement in [AlertRequirement] order with whether it is met, and the alert
 * runtime's steps that failed on their last attempt (step names like `Notify:level`, never values: *Copy report*).
 */
data class AlertReadiness(
    val missing: Set<AlertRequirement> = emptySet(),
    val failedSteps: Set<String> = emptySet(),
) {
    val ready: Boolean get() = missing.isEmpty()

    /** Every item setup asks for is granted (a failed alert step is not a setup item: home shows it). */
    val setupReady: Boolean get() = missing.none { it.inSetup }

    /** The first problem in checklist order (the one the row names), or null when alerts can ring. */
    val firstProblem: AlertRequirement? get() = AlertRequirement.entries.firstOrNull { it in missing }

    /**
     * The plain-text report *Copy report* puts on the clipboard for a bug report: what failed and what is missing, the
     * app's [version] and the Android [sdk]; no reading, name or key.
     */
    fun report(
        version: String,
        sdk: Int,
    ): String =
        listOf(
            "Sukrara $version · Android API $sdk",
            "Alert steps failed: ${failedSteps.sorted().joinToString().ifEmpty { "none" }}",
            "Alert checklist missing: ${AlertRequirement.entries.filter { it in missing }.joinToString().ifEmpty { "none" }}",
        ).joinToString("\n")

    companion object {
        val Ready = AlertReadiness()
    }
}

/** The live checklist and the way to fix one item (opens the system screen for it). */
interface AlertReadinessSource {
    val readiness: StateFlow<AlertReadiness>

    /** Re-reads every item (the app calls it on resume: permissions change in the system's settings). */
    fun refresh()

    /** Opens the system screen that fixes [requirement] (notification settings, battery exemption, …). */
    fun fix(requirement: AlertRequirement)
}
