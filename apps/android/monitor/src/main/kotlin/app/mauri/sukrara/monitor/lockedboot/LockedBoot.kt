// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.lockedboot

import app.mauri.sukrara.monitor.alerts.AlertAlarm

/** The one notification of the time before the first unlock (Android in the app, a fake in tests). */
interface LockedBootSurface {
    /** "Unlock to resume monitoring": silent, on the lock screen. */
    fun showWaiting()

    /** "Monitoring before unlock": readings and alerts run; followers, uploads and the app wait. Silent, on the lock screen. */
    fun showMonitoring()

    /** The same slot as an alert: "No glucose data · unlock your phone", on *No data*'s channel. */
    fun ring(plan: LockedBootPlan)

    fun clear()
}

/**
 * Before the first unlock after a restart (docs/30-direct-boot.md), on a phone with a screen lock:
 * - **the wearer reading their own sensor monitors** (#160: the pairing, the alert settings and state are
 *   device-protected): at the locked boot the receiver starts monitoring as after any restart and this says so on the
 *   lock screen, with what waits for the unlock (followers, uploads, the app);
 * - **otherwise** (a follower, Juggluco as the source, no sensor paired), with something set up to monitor
 *   ([LockedBootPlan.monitoring]): the notice that monitoring waits for the unlock, and the alarm armed for when *No
 *   data* would have rung ([LockedBootPlan.firstAlarm]); at the alarm the notice rings as *No data* (its channel, at
 *   alarm volume when any alert is an alarm), again every *No data* re-alert interval;
 * - at the unlock: the notice and the alarm go; monitoring goes on (or the normal boot path starts it).
 * Pure: the receiver gives it the platform.
 */
class LockedBoot(
    private val plan: () -> LockedBootPlan?,
    private val surface: LockedBootSurface,
    private val alarm: AlertAlarm,
    /** The phone has a screen lock (without one, Android unlocks on its own right after the boot). */
    private val secure: () -> Boolean,
    private val unlocked: () -> Boolean,
    /** Epoch seconds. */
    private val now: () -> Long,
) {
    /** [monitoring]: this phone monitors before the unlock (the receiver started it). */
    fun onLockedBoot(monitoring: Boolean) {
        if (unlocked() || !secure()) return
        if (monitoring) {
            surface.showMonitoring()
            alarm.set(null)
            return
        }
        val p = plan()?.takeIf { it.monitoring } ?: return
        surface.showWaiting()
        val at = p.firstAlarm(now())
        if (at != null && at <= now()) ring(p) else alarm.set(at)
    }

    fun onAlarm() {
        if (unlocked()) return onUnlocked()
        val p = plan()?.takeIf { it.monitoring }
        if (p == null) return onUnlocked()
        ring(p)
    }

    fun onUnlocked() {
        surface.clear()
        alarm.set(null)
    }

    private fun ring(p: LockedBootPlan) {
        surface.ring(p)
        alarm.set(p.nextAlarm(now()))
    }
}
