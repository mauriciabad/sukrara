// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.Freshness
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.stats.GlucosePoint
import kotlin.math.abs

/**
 * When an alert's notification shows the newest reading again, without sound (#195, SPEC §1 "Showing an alert"): a
 * value left up for minutes is wrong (glucose always moves), and one that differs from the glucose notification beside
 * it reads as wrong (the owner's choice: it always matches). So it is updated whenever what it says changes: the value
 * moved [MIN_MOVE_MGDL] (any change), the arrow changed, it became or left LO / HI, or the card's freshness step changed
 * (the reading's time shown once late, struck through from 5 min: [GlucoseCard.face], surface `alert-notification`).
 * Never in the [AFTER_RING_S] after the alert rang: Android stops a sound or vibration still playing when its
 * notification is updated without alerting. The Mac has the same rule (`AlertLiveValue.swift`).
 */
object AlertLiveValue {
    /** The smallest move of the value (mg/dL) that updates the notification: any change, so it matches the card. */
    const val MIN_MOVE_MGDL = 1

    /** No update this soon (s) after the alert rang. */
    const val AFTER_RING_S = 10L

    /**
     * What the notification says about a reading: its value, its arrow (or the ring), its time shown, struck, and
     * whether it is the sensor's LO (-1) or HI (1), said in words whatever the number.
     */
    data class Look(
        val mgdl: Int,
        val arrow: String?,
        val late: Boolean,
        val struck: Boolean,
        val bound: Int = 0,
    )

    /** [r]'s look at [now], the card's ([GlucoseCard.face]). */
    fun look(
        r: AlertReading,
        now: Long,
    ): Look {
        val face = GlucoseCard.face(GlucosePoint(r.t, r.mgdl, r.rate), now, null, CardSurface.ALERT_NOTIFICATION, GlucoseUnit.MGDL)
        return Look(
            r.mgdl,
            face.arrowText,
            face.fresh.showTime,
            face.strike,
            if (r.isLo) {
                -1
            } else if (r.isHi) {
                1
            } else {
                0
            },
        )
    }

    /** Whether the notification showing [shown] is updated to [next]. */
    fun changed(
        shown: Look,
        next: Look,
    ): Boolean =
        abs(next.mgdl - shown.mgdl) >= MIN_MOVE_MGDL ||
            next.arrow != shown.arrow ||
            next.late != shown.late ||
            next.struck != shown.struck ||
            next.bound != shown.bound

    /**
     * Whether to update now: something [changed] and the alert didn't ring in the last [AFTER_RING_S] ([rangAt], epoch
     * s; null = it didn't ring since this notification was shown; one after [now], the clock set back, is long past).
     */
    fun due(
        shown: Look,
        next: Look,
        rangAt: Long?,
        now: Long,
    ): Boolean = (rangAt == null || now - rangAt !in 0 until AFTER_RING_S) && changed(shown, next)

    /** When [r]'s look changes by itself next (the card's next freshness step after [now]), or null. */
    fun nextStepAt(
        r: AlertReading,
        now: Long,
    ): Long? = Freshness.nextChangeAt(r.t, now)
}
