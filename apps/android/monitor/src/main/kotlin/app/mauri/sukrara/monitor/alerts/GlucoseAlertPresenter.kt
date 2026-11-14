// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.monitor.LiveNotification
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

    /**
     * Whether it can carry an alert: false when the person turned its channel off (Android then drops every post on
     * it, so the alert would stay as it rang, never updated, muted or cleared): the alerts are notifications of their
     * own then, as with *Alerts on my watch*.
     */
    fun canCarry(): Boolean = true

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
    /** Elapsed ms (a clock that never jumps): how long ago the front's ring went out. */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    /** Whether Android lets an alarm-level ring open full screen now. */
    private val canFullScreen: () -> Boolean = separate::canFullScreen,
    /** Runs a block after a delay (ms): the end of a full-screen ring's own notification. */
    private val later: (Long, () -> Unit) -> Unit = { ms, run -> Handler(Looper.getMainLooper()).postDelayed(run, ms) },
) : AlertPresenter {
    /** Each group's alert or muted note on the glucose notification, the newest ring last. */
    private val parts = LinkedHashMap<AlertGroup, GlucoseAlert>()

    /** The group whose ring the notification shows (the newest ring), or null. */
    private var front: AlertGroup? = null

    /** When [front] rang ([clock]). */
    private var frontRangAt = 0L

    /** The firing whose full-screen ring is a notification of its own now, per group (until its ring window ends). */
    private val fullScreen = HashMap<AlertGroup, String>()

    /** How to show each group's alert or muted note as a notification of its own, silently (a switch to [separate]). */
    private val redo = HashMap<AlertGroup, () -> Unit>()

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
        // a stand-in of this group (the plain alert, the problem notice, a full-screen ring's own) goes once the alert
        // itself shows
        separate.clearSlot(part.group)
        fullScreen.remove(part.group)
        parts[part.group] = part
        redo[part.group] = { separate.show(firing, settings, silent = true, who = who, relaxedMin = relaxedMin, now = now) }
        if (!silent && outranked(part)) {
            // a second ring of the same reading (the runtime posts the one that must be heard first): Android mutes it
            // anyway (one sound per app per second), and taking the front would put "Falling fast" over a very low
            // and move the notification off the alarm's channel (its volume, its Do Not Disturb pass): a line instead
            glucose.alert(current(), GlucoseAlertSink.Post.UPDATE)
        } else if (!silent && part.alarm && runCatching(canFullScreen).getOrDefault(false)) {
            // an alarm-level ring that opens full screen: Android starts a full-screen intent only for a notification
            // it adds (Android 16 never for an update of the glucose notification, which is always there), so this
            // ring is the alert of its own as before #36, the same channel, sound and buttons, for the ring window; the
            // glucose notification shows the alert silently and carries it on once that one goes
            front = part.group
            frontRangAt = clock()
            separate.show(firing, settings, silent = false, who = who, relaxedMin = relaxedMin, now = now)
            fullScreen[part.group] = firing.id
            later(LiveNotification.RING_WINDOW_MS) { fullScreenEnded(part.group, firing.id) }
            glucose.alert(current(), GlucoseAlertSink.Post.UPDATE)
        } else if (!silent) {
            // the ring: in front, posted at once (a failure throws, so the runtime shows the plain stand-in)
            front = part.group
            frontRangAt = clock()
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
        redo[group] = { separate.showMuted(group, side, kind, until, settings, now) }
        if (wasRinging) front = null
        separate.stopSound(group)
        dropFullScreen(group)
        // a mute stops the sound at once: the silent channel
        glucose.alert(current(), if (wasRinging) GlucoseAlertSink.Post.QUIET else GlucoseAlertSink.Post.UPDATE)
    }

    @Synchronized
    override fun clear(group: AlertGroup) {
        separate.clear(group)
        fullScreen.remove(group)
        redo.remove(group)
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
            redo.remove(group)
            if (front == group) front = null
            separate.stopSound(group)
            dropFullScreen(group)
        }
        glucose.alert(current(), GlucoseAlertSink.Post.QUIET)
    }

    /** A full-screen ring's ring window ended: its own notification goes, the glucose notification carries the alert. */
    @Synchronized
    private fun fullScreenEnded(
        group: AlertGroup,
        firingId: String,
    ) {
        if (fullScreen[group] != firingId || runCatching(watch).getOrDefault(false)) return
        dropFullScreen(group)
    }

    /** The full-screen ring's own notification of [group] goes (no swipe: Android sends no delete intent). */
    private fun dropFullScreen(group: AlertGroup) {
        if (fullScreen.remove(group) != null) runCatching { separate.clearSlot(group) }
    }

    /**
     * Whether [part]'s ring comes while another group's ring is still sounding (its ring window) and that one goes
     * first in the runtime's ring order ([ringOrder]: alarm-level, then the most severe, then level → rate → no data).
     */
    private fun outranked(part: GlucoseAlert): Boolean {
        val ringing = front?.takeIf { it != part.group }?.let(parts::get) ?: return false
        if (clock() - frontRangAt >= LiveNotification.RING_WINDOW_MS) return false
        return RANK.compare(ringing, part) <= 0
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
                // what is up shows at once as notifications of their own, silently (it already rang)
                val up = redo.values.toList()
                parts.clear()
                redo.clear()
                fullScreen.clear()
                front = null
                glucose.alert(null, GlucoseAlertSink.Post.QUIET)
                up.forEach { runCatching(it) }
            } else {
                AlertGroup.entries.forEach(separate::clear)
            }
        }
        return now
    }

    private companion object {
        /** The ring order's ([ringOrder]): alarm-level first, then the most severe, then level → rate → no data. */
        val RANK: Comparator<GlucoseAlert> =
            compareBy<GlucoseAlert>({ !it.alarm }, { -it.kind.severity }, { it.group.ordinal })
    }
}
