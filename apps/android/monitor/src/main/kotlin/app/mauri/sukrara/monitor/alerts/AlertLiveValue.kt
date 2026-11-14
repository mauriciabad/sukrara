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
 * value left up for minutes is wrong (glucose always moves), but every wiggle posted again would reach a watch that
 * buzzes on each update (third-party bridges ignore Android's only-alert-once). So it is updated only when what it
 * says changes enough: the value moved [MIN_MOVE_MGDL] or more, the arrow changed, or the card's freshness step did
 * (the reading's time shown once late, struck through from 5 min: [GlucoseCard.face], surface `alert-notification`).
 * Never in the [AFTER_RING_S] after the alert rang: Android stops a sound or vibration still playing when its
 * notification is updated without alerting. The Mac has the same rule (`AlertLiveValue.swift`).
 */
object AlertLiveValue {
    /** The smallest move of the value (mg/dL, about 0.3 mmol/L) that updates the notification. */
    const val MIN_MOVE_MGDL = 5

    /** No update this soon (s) after the alert rang. */
    const val AFTER_RING_S = 10L

    /** What the notification says about a reading: its value, its arrow (or the ring), its time shown, struck. */
    data class Look(
        val mgdl: Int,
        val arrow: String?,
        val late: Boolean,
        val struck: Boolean,
    )

    /** [r]'s look at [now], the card's ([GlucoseCard.face]). */
    fun look(
        r: AlertReading,
        now: Long,
    ): Look {
        val face = GlucoseCard.face(GlucosePoint(r.t, r.mgdl, r.rate), now, null, CardSurface.ALERT_NOTIFICATION, GlucoseUnit.MGDL)
        return Look(r.mgdl, face.arrowText, face.fresh.showTime, face.strike)
    }

    /** Whether the notification showing [shown] is updated to [next]. */
    fun changed(
        shown: Look,
        next: Look,
    ): Boolean =
        abs(next.mgdl - shown.mgdl) >= MIN_MOVE_MGDL ||
            next.arrow != shown.arrow ||
            next.late != shown.late ||
            next.struck != shown.struck

    /**
     * Whether to update now: something [changed] and the alert didn't ring in the last [AFTER_RING_S] ([rangAt], epoch
     * s; null = it didn't ring since this notification was shown).
     */
    fun due(
        shown: Look,
        next: Look,
        rangAt: Long?,
        now: Long,
    ): Boolean = (rangAt == null || now - rangAt >= AFTER_RING_S) && changed(shown, next)

    /** When [r]'s look changes by itself next (the card's next freshness step after [now]), or null. */
    fun nextStepAt(
        r: AlertReading,
        now: Long,
    ): Long? = Freshness.nextChangeAt(r.t, now)
}
