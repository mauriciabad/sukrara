// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertEvent
import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.Side

/*
 * What the alert runtime drives. The Android implementations are AlertNotifier, AndroidAlarms.alertCheck and the module's
 * wiring; tests use fakes, so no test ever posts a real notification or sets a real alarm.
 */

/** Shows and removes alert notifications: one slot per [AlertGroup] (an alert, or its side's muted card). */
interface AlertPresenter {
    /**
     * Posts [firing] ([silent]: no sound: shown again after a restart, or its value updated, #195). [who] = the followed
     * person, or null; [relaxedMin] > 0: the side reminds less (offer *Remind as usual*). [now]: the value's look is
     * the reading's at this time (its time once late, struck from 5 min); by default the reading's own time (fresh).
     */
    fun show(
        firing: Firing,
        settings: AlertSettings,
        silent: Boolean,
        who: String?,
        relaxedMin: Int,
        now: Long = firing.reading?.t ?: firing.at,
    )

    /**
     * Whether [group]'s alert is still on screen: false once the person swiped or tapped it away (#195: an update
     * never brings it back).
     */
    fun isShowing(group: AlertGroup): Boolean = true

    /**
     * The alert as a plain notification, when [show] failed (0.14.1: strings [show] looks up by name were missing from
     * the release build, and no alert was posted): [firing]'s kind, its value with the unit and the arrow, [who], and
     * *Open Sukrara*, built only from resources the code names directly and plain values. On the alert's own channel
     * (its sound, vibration and Do Not Disturb behaviour), in the group's slot; no actions that need the alert's words.
     */
    fun showPlain(firing: Firing, settings: AlertSettings, silent: Boolean, who: String?)

    /**
     * The last resort when [showPlain] failed too: "Sukrara couldn't show an alert · Open Sukrara" on the `status`
     * channel (*Monitoring problems*), in [group]'s slot (a later alert replaces it, [clear] removes it).
     */
    fun showProblem(group: AlertGroup, silent: Boolean)

    /** Right before an alert rings: waits (a second at most) until nothing of the app's can mute its sound. */
    suspend fun beforeRing() = Unit

    /** Replaces the group's slot with "[side] alerts muted · until [until]" (count-down, Unmute, Change time). */
    fun showMuted(group: AlertGroup, side: Side, kind: AlertKind, until: Long, settings: AlertSettings, now: Long)

    fun clear(group: AlertGroup)
}

/** The one exact wake-up of the alert runtime: [AlertRuntime.tick] at [at] (epoch s); null = none needed. */
fun interface AlertAlarm {
    fun set(at: Long?)
}

/** Sends the wearer's `alert` events to the followers (publisher; a no-op until it is wired). */
fun interface AlertEventSink {
    fun publish(event: AlertEvent)

    /**
     * Whether anyone follows now (sharing runs with an active follower): the escalation's history line says the
     * followers were told only then (PROTOCOL-v2 "Leave").
     */
    fun listening(): Boolean = true
}

/**
 * Restarts the monitoring service after a check found it dead (`MonitorStarter`; it surfaces a denial).
 * Suspends until the start was tried, so the alarm's broadcast (and its foreground-service start exemption) is still
 * alive when it happens.
 */
fun interface MonitorRestarter {
    suspend fun restart()
}
