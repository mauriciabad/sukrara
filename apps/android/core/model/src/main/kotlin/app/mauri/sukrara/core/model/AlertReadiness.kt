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
     * An alert's own notification couldn't be built or posted (a `Show` step in [AlertReadiness.failedSteps]): the
     * alert runtime showed it plain on its channel instead, or as a *Monitoring problems* notice. Goes when an alert of
     * that group shows normally again (the runtime tries with every reading). Its way out is *Copy report*.
     */
    AlertShown(inSetup = false),

    /**
     * The alert runtime's last attempt to update or remove an alert notification, arm the alert check, tell the
     * followers or save the alert state failed ([AlertReadiness.failedSteps]); goes when that step next works. Its way
     * out is *Copy report*.
     */
    AlertDelivery(inSetup = false),
    Notifications,
    FullScreenIntent,
    ExactAlarm,
    BatteryExemption,

    /** The alert channels in use are not blocked or lowered below sound in Android's settings. */
    AlertChannel,

    /**
     * Not a fault, a choice: whether Sukrara is an exception to Do Not Disturb ([AlertReadiness.dnd]). Never missing;
     * the checklist shows where it stands (*Sukrara doesn't alert in Do Not Disturb* / *Sukrara alerts in Do Not
     * Disturb*) with one button to change it ([AlertReadinessSource.letThroughDnd]). #184.
     */
    DoNotDisturb,

    /**
     * Whether alerts make a sound while the phone is unlocked: Android's *Notification cooldown › Vibrate only when
     * unlocked* drops every notification sound then, alarms included, and hides the setting from apps. The person sends
     * a test alert with the phone unlocked and says what it did ([UnlockedSound]); missing until they do (or when
     * Android says it is on and Sukrara doesn't play the sound itself). #129, #183.
     */
    SoundWhenUnlocked,

    /** Bluetooth on and the nearby-devices permission (wearer only: the sensor talks over Bluetooth). */
    Bluetooth,
}

/**
 * What the person heard from a test alert sent with the phone unlocked (schema `alerts.unlockedSound`; Sukrara's own
 * unlocked sound stays off during that test, so it shows Android's behaviour): [Unchecked] = not tested yet; [Heard] =
 * Android plays the sound (Sukrara doesn't); [OnlyVibrated] = Android only vibrates while the phone is unlocked, so
 * Sukrara plays the alert sound itself then.
 */
enum class UnlockedSound(
    val wire: String,
) {
    Unchecked("unchecked"),
    Heard("heard"),
    OnlyVibrated("vibrated"),
    ;

    companion object {
        fun of(wire: String): UnlockedSound = entries.firstOrNull { it.wire == wire } ?: Unchecked
    }
}

/**
 * Whether the unlocked-sound test can show Android's behaviour now: [Ready]; [NoAlert] = no alert is on (nothing to
 * send); [ModeOn] = a Do Not Disturb mode is on (it could hold the test back: a silence that isn't the unlocked
 * setting); [Silent] = the phone is on vibrate or silent, or the test alert's volume is 0.
 */
enum class SoundTest { Ready, NoAlert, ModeOn, Silent }

/**
 * Where Sukrara stands with Do Not Disturb (#184): [through] = it is an exception (the alerts that ring like an alarm
 * override every mode: Android's channel override, after Do Not Disturb access); [heldNow] = a mode on now keeps some
 * ([Held.Some]) or all ([Held.All]) of the alerts in use from making a sound. Information, never a fault.
 */
data class DndStatus(
    val through: Boolean = false,
    val heldNow: Held = Held.None,
) {
    enum class Held { None, Some, All }
}

/**
 * The checklist's current answers: every requirement in [AlertRequirement] order with whether it is met, the alert
 * runtime's steps that failed on their last attempt (the step and the exception's class, like `Show:level
 * NotFoundException`, never values: *Copy report*) and
 * the person's answer about the sound while unlocked ([unlockedSound], and whether its test can run now: [soundTest]) and
 * where Sukrara stands with Do Not Disturb ([dnd]).
 */
data class AlertReadiness(
    val missing: Set<AlertRequirement> = emptySet(),
    val failedSteps: Set<String> = emptySet(),
    val unlockedSound: UnlockedSound = UnlockedSound.Unchecked,
    val dnd: DndStatus = DndStatus(),
    val soundTest: SoundTest = SoundTest.Ready,
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

    /**
     * Makes Sukrara an exception to Do Not Disturb ([through]) or not ([AlertRequirement.DoNotDisturb]), through
     * Android's own grant: Do Not Disturb access first when it isn't granted, else the alarm channels' override.
     */
    fun letThroughDnd(through: Boolean)

    /**
     * Sends the unlocked-sound test ([AlertRequirement.SoundWhenUnlocked]): the real alert, marked as a test, with
     * Sukrara's own unlocked sound off, so it does what Android does. False when nothing could be sent.
     */
    fun testUnlockedSound(): Boolean

    /** Stores what the person heard from that test ([AlertRequirement.SoundWhenUnlocked]). */
    fun answerUnlockedSound(answer: UnlockedSound)
}
