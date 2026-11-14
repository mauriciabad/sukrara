// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind

/**
 * The alert the glucose notification carries while one is up (docs/36, SPEC §1 "Showing an alert"): on Android the
 * glucose notification IS the alert, so its title, buttons, channel and swipe become the alert's. Built by the alert
 * presenter ([app.mauri.sukrara.monitor.alerts.GlucoseAlertPresenter]) in the app's words; [LiveNotification] draws it
 * into whichever layout the glucose notification has (the Live Update or the card).
 */
data class GlucoseAlert(
    val group: AlertGroup,
    val kind: AlertKind,
    /** The firing it shows (the swipe's answer names it); null for a side's muted note. */
    val firingId: String?,
    /** "Low · eat more", or the muted note's "Lows muted". */
    val title: String,
    /** The alert's value line ("62 mg/dL ↘", the card's face, surface `alert-notification`): where no value shows. */
    val body: String,
    /** The kind's channel (`alert_n_<kind>` / `alert_a_<kind>`): a ring is posted on it. */
    val channel: String,
    /** Alarm-level (the kind's *alarm*, or an escalated very low): the full-screen intent on a ring. */
    val alarm: Boolean,
    /** The notification category of a ring ([app.mauri.sukrara.monitor.alerts.AlertCategory]). */
    val category: String,
    /** Its buttons, in order (Treatment, Remind less / as usual, Mute; muted: Treatment, Unmute). */
    val buttons: List<AlertButton>,
    /** The muted note's end (epoch s): its countdown. */
    val mutedUntil: Long? = null,
    /** The followed person and "Reminders every 15 min", for the sub text. */
    val sub: String? = null,
    /** Another group's alert up at the same time, as a line ("Also: Falling fast"). */
    val also: String? = null,
    /** A level alert's headline names the value's range: the card draws no range word beside it (contracts/graph). */
    val rangeSaid: Boolean = false,
)

/** A button of [GlucoseAlert] (the Android action is built where it is posted: a Live Update can't take a direct reply). */
sealed interface AlertButton {
    /** Opens Add treatment: Carbs focused on the low side, Rapid on the high side; an answer to the alert (#39). */
    data class Treatment(val carbs: Boolean) : AlertButton

    data object RemindLess : AlertButton

    data object RemindAsUsual : AlertButton

    /** The lengths ([minutes]) as one-tap choices; in a Live Update a plain button that opens the lengths sheet. */
    data class Mute(val minutes: List<Int>) : AlertButton

    data object Unmute : AlertButton
}
