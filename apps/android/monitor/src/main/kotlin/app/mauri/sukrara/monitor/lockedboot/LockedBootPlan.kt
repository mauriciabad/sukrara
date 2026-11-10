// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.lockedboot

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Rules
import app.mauri.sukrara.alerts.SensorWindow
import app.mauri.sukrara.monitor.alerts.SavedSensor
import kotlinx.serialization.Serializable

/**
 * What the phone needs to know before the first unlock after a restart (docs/30-direct-boot.md), the only state kept in
 * device-protected storage. Everything else (readings, treatments, the sensor pairing, the identity, the alert record)
 * stays in credential-encrypted storage, so before the first unlock Sukrara can only say that it isn't monitoring and
 * ring when the person's *No data* alert would have. Never a glucose value: only times and the *No data* rule.
 *
 * Written by the alert runtime after every change ([of]); read by [LockedBootReceiver].
 */
@Serializable
data class LockedBootPlan(
    /** A role is set and monitoring had started: only then does the phone say anything before the first unlock. */
    val monitoring: Boolean = false,
    /**
     * Since when nothing was read (epoch s; never a value): the last reading, or monitoring's start when newer (a source
     * that never delivered a reading). *No data* counts from it.
     */
    val lastReadingAt: Long? = null,
    /** *No data*'s minutes while alerts and *No data* are on (and monitoring started); null = no alarm before unlock. */
    val noDataMin: Int? = null,
    /** *No data*'s re-alert interval (0 = once). */
    val repeatMin: Int = 0,
    /** Ring at alarm volume: *No data* or any other alert that is on rings as an alarm (they all wait for the unlock). */
    val alarm: Boolean = false,
    /** *Mute all*'s end (epoch s; 0 = off, [Rules.NEVER] = until turned back on). */
    val muteAllUntil: Long = 0,
    /** The sensor's life: no alarm while it warms up or once it has ended, as the *No data* rule. */
    val sensor: SavedSensor? = null,
    /** *No data*'s sound (a pack, `system` or `none`) and the shuffle, for the sound provider before the unlock. */
    val sound: String = "quest",
    val shuffle: Boolean = true,
) {
    /**
     * When the alarm should ring next, at or after [now] (epoch s): [lastReadingAt] + *No data*'s minutes, later while
     * the sensor warms up or *Mute all* is on; null when nothing should ring (no reading yet, *No data* off, the sensor
     * ends first, *Mute all* until turned back on). The same conditions as the engine's `no_data` (SPEC §1).
     */
    fun firstAlarm(now: Long): Long? {
        val last = lastReadingAt ?: return null
        val min = noDataMin ?: return null
        if (!monitoring || muteAllUntil >= Rules.NEVER) return null
        var at = maxOf(now, last + min * 60L)
        val window = sensor?.window
        if (window != null && window.warming(last, at)) at = window.warmupEndEpoch + SensorWindow.WARMUP_GRACE_S
        if (muteAllUntil > at) at = muteAllUntil
        if (window != null && window.ended(at)) return null
        return at
    }

    /** After a ring at [rangAt]: the next one, [repeatMin] later (null = it rings once), unless the sensor has ended by then. */
    fun nextAlarm(rangAt: Long): Long? {
        if (repeatMin <= 0 || noDataMin == null) return null
        val at = rangAt + repeatMin * 60L
        return at.takeUnless { sensor?.window?.ended(it) == true }
    }

    companion object {
        /** A file that exists but can't be read: say that monitoring waits, ring nothing (no time to ring at). */
        val UNREADABLE = LockedBootPlan(monitoring = true)

        /**
         * The plan from the alert runtime's state: [settings] are the machine's (already with *No data* off before
         * monitoring started), null without a role.
         */
        fun of(
            settings: AlertSettings?,
            monitoring: Boolean,
            lastReadingAt: Long?,
            muteAllUntil: Long,
            sensor: SensorWindow?,
        ): LockedBootPlan {
            if (settings == null || !monitoring) return LockedBootPlan()
            val noData = settings.rule(AlertKind.NO_DATA)
            val ringing = settings.on && noData.on
            return LockedBootPlan(
                monitoring = true,
                lastReadingAt = lastReadingAt,
                noDataMin = noData.threshold.toInt().takeIf { ringing },
                repeatMin = noData.repeatMin,
                alarm = AlertKind.entries.any { settings.rule(it).let { r -> r.on && r.alarm } },
                muteAllUntil = muteAllUntil,
                sensor = sensor?.let(::SavedSensor),
                sound = settings.sounds.soundOf(AlertKind.NO_DATA),
                shuffle = settings.sounds.shuffle,
            )
        }
    }
}
