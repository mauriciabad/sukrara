// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.monitor.notify.GlucoseAlert

/**
 * Where the glucose notification takes its alert from ([app.mauri.sukrara.monitor.LiveNotification]): the alert to
 * show (null: none) and how to post it now.
 */
interface GlucoseAlertSink {
    /** Show [alert] ([how]: ring it on its channel, update it, or stop a ring at once by posting on the silent channel). */
    fun alert(
        alert: GlucoseAlert?,
        how: Post,
    )

    /** Whether the glucose notification is on screen now (false once the person swiped it away). */
    fun showing(): Boolean

    enum class Post {
        /** A ring: the alert's channel without only-alert-once; full screen when an alarm comes from silent. */
        RING,

        /** An update: through the post gate, on the ring's channel while it still sounds, else silent. */
        UPDATE,

        /** At once on the silent channel: a sound still playing stops (a mute, a clear, a swipe). */
        QUIET,
    }
}

/**
 * The alerts on Android (docs/36, SPEC §1 "Showing an alert"): the glucose notification IS the alert while one is up,
 * so there is one Sukrara notification on the phone. Each ring re-posts it on the kind's own channel (its sound,
 * volume, vibration, Do Not Disturb and full-screen choices), it goes back to silent after the ring window still
 * showing the alert, and its swipe answers the alert and hides it until the next ring. Two groups at once: the newest
 * ring is shown, the other is a line under it ("Also: Falling fast") and rings through the same notification on its
 * own turn.
 *
 * With *Alerts on my watch* ([watch]) every alert is a notification of its own instead ([separate]: Wear OS bridges
 * those, never an ongoing notification) and the glucose notification carries no alert. The plain alert and the
 * *Monitoring problems* notice stay the separate stand-ins when the alert can't be shown.
 */
class GlucoseAlertPresenter(
    private val separate: AlertNotifier,
    private val glucose: GlucoseAlertSink,
    private val watch: () -> Boolean,
) : AlertPresenter {
    /** Each group's alert or muted note on the glucose notification, the newest ring last. */
    private val parts = LinkedHashMap<AlertGroup, GlucoseAlert>()

    /** The group whose ring the notification shows (the newest ring), or null. */
    private var front: AlertGroup? = null

    /** Whether the last decision was the watch's: a switch moves what is up to the other side. */
    private var watchMode: Boolean? = null

    @Synchronized
    override fun show(
        firing: Firing,
        settings: AlertSettings,
        silent: Boolean,
        who: String?,
        relaxedMin: Int,
        now: Long,
    ) {
        if (onWatch()) return separate.show(firing, settings, silent, who, relaxedMin, now)
        val part = separate.glucoseAlert(firing, settings, who, relaxedMin, now)
        // a stand-in of this group (the plain alert, the problem notice) goes once the alert itself shows
        separate.clearSlot(part.group)
        parts[part.group] = part
        if (!silent) {
            // the ring: in front, posted at once (a failure throws, so the runtime shows the plain stand-in)
            front = part.group
            glucose.alert(current(), GlucoseAlertSink.Post.RING)
            separate.rang(part.group, part.channel, part.alarm)
        } else {
            glucose.alert(current(), GlucoseAlertSink.Post.UPDATE)
        }
    }

    override fun isShowing(group: AlertGroup): Boolean {
        if (onWatch()) return separate.isShowing(group)
        return synchronized(this) { group in parts } && glucose.showing()
    }

    override fun showPlain(firing: Firing, settings: AlertSettings, silent: Boolean, who: String?) =
        separate.showPlain(firing, settings, silent, who)

    override fun showProblem(group: AlertGroup, silent: Boolean) = separate.showProblem(group, silent)

    override suspend fun beforeRing() = separate.beforeRing()

    @Synchronized
    override fun showMuted(group: AlertGroup, side: Side, kind: AlertKind, until: Long, settings: AlertSettings, now: Long) {
        if (onWatch()) return separate.showMuted(group, side, kind, until, settings, now)
        val wasRinging = front == group
        parts[group] = separate.mutedAlert(side, kind, until, settings)
        if (wasRinging) front = null
        separate.stopSound(group)
        // a mute stops the sound at once: the silent channel
        glucose.alert(current(), if (wasRinging) GlucoseAlertSink.Post.QUIET else GlucoseAlertSink.Post.UPDATE)
    }

    @Synchronized
    override fun clear(group: AlertGroup) {
        separate.clear(group)
        val had = parts.remove(group) != null
        val wasRinging = front == group
        if (wasRinging) front = null
        if (had) glucose.alert(current(), if (wasRinging) GlucoseAlertSink.Post.QUIET else GlucoseAlertSink.Post.UPDATE)
    }

    /**
     * The glucose notification carrying [kind]'s alert was swiped away (its delete intent): the alert part goes until the
     * next ring ([firingId]: the swiped one's; a newer ring isn't hidden by the swipe of an older one) and the glucose
     * notification comes back at once on the silent channel (a re-post on the alert's channel would ring again).
     */
    @Synchronized
    fun swiped(
        kind: AlertKind,
        firingId: String?,
    ) {
        val group = kind.group
        val part = parts[group]
        if (part != null && part.firingId == firingId) {
            parts.remove(group)
            if (front == group) front = null
            separate.stopSound(group)
        }
        glucose.alert(current(), GlucoseAlertSink.Post.QUIET)
    }

    /** The alert the glucose notification shows now: the ringing group's, else the newest; the others as a line. */
    private fun current(): GlucoseAlert? {
        val main = front?.let(parts::get) ?: parts.values.lastOrNull() ?: return null
        val others = parts.values.filter { it.group != main.group }
        return main.copy(also = others.joinToString(" · ") { it.title }.ifEmpty { null })
    }

    /**
     * *Alerts on my watch* now; when it changed, what was up moves to the other side (the glucose notification drops
     * its alert; the separate notifications go): the runtime shows them again from its own state on its next pass.
     */
    @Synchronized
    private fun onWatch(): Boolean {
        val now = runCatching(watch).getOrDefault(false)
        val before = watchMode
        watchMode = now
        if (before != null && before != now) {
            if (now) {
                parts.clear()
                front = null
                glucose.alert(null, GlucoseAlertSink.Post.QUIET)
            } else {
                AlertGroup.entries.forEach(separate::clear)
            }
        }
        return now
    }
}
