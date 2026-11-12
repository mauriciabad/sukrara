// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.alerts

import app.mauri.sukrara.core.model.SensorTiming
import kotlinx.serialization.Serializable

/**
 * A glucose reading as the rules see it: [t] epoch seconds, [mgdl], [rate] mg/dL/min (null = unknown: no rate alert).
 * Double on purpose: the falling-high prediction `mgdl + rate × min` must match the Mac and the scenarios exactly.
 */
@Serializable
data class AlertReading(val t: Long, val mgdl: Int, val rate: Double? = null) {
    /** This reading, dated no later than [now]. */
    fun notAfter(now: Long): AlertReading = if (t > now) copy(t = now) else this

    /** The sensor's LO (below what it measures): below every low threshold (SPEC §1). */
    val isLo: Boolean get() = mgdl < FLOOR

    /** The sensor's HI (above what it measures): above every high threshold. */
    val isHi: Boolean get() = mgdl > CEILING

    companion object {
        /** The shown range (`:core:model` GlucoseRange, the Mac's GlucoseRange): outside it a value is LO / HI. */
        const val FLOOR = 40
        const val CEILING = 400
    }
}

/** Times (epoch s) of the logged, not deleted, carbs and rapid insulin: the treatment quiet rules (SPEC §2). */
data class Doses(val carbs: List<Long> = emptyList(), val rapid: List<Long> = emptyList()) {
    fun of(kind: TreatmentKind): List<Long> = if (kind == TreatmentKind.CARBS) carbs else rapid
}

/**
 * The current sensor's life (epoch s), so no `no_data` alerts while no reading is expected: the sensor has ended or
 * is warming up (SPEC §1 `no_data`). A warm-up counts until [WARMUP_GRACE_S] past its end, or until a reading of this
 * sensor arrives: the one rule the card's badge uses too ([SensorTiming], contracts/sensor/life.json; parity with the
 * Mac: contracts/alerts scenarios `sensor ended|started`).
 */
data class SensorWindow(val startEpoch: Long, val endEpoch: Long, val warmupEndEpoch: Long) {
    fun ended(now: Long): Boolean = now >= endEpoch

    fun warming(latestT: Long?, now: Long): Boolean = SensorTiming.warming(startEpoch, warmupEndEpoch, latestT, now)

    /** No reading is expected at [now]. */
    fun silent(latestT: Long?, now: Long): Boolean = ended(now) || warming(latestT, now)

    companion object {
        const val WARMUP_GRACE_S = SensorTiming.WARMUP_GRACE_S
        const val DEFAULT_WARMUP_MIN = SensorTiming.DEFAULT_WARMUP_MIN

        /** A sensor started at [startEpoch] lasting [wearMin] minutes with a [warmupMin] warm-up. */
        fun started(startEpoch: Long, wearMin: Int, warmupMin: Int = DEFAULT_WARMUP_MIN) =
            SensorWindow(startEpoch, startEpoch + wearMin * 60L, startEpoch + warmupMin * 60L)
    }
}

/** This time moved back by [delta] (a clock set back); 0 = none and [Rules.NEVER] stay. */
internal fun Long.shiftedBack(delta: Long): Long = if (this <= 0 || this == Long.MAX_VALUE) this else this - delta

/** The pure predicates shared by [AlertEngine], [WearerMirror] and [AlertStatus]. */
object Rules {
    /** Readings older than this never alert (backfill, a late chunk). */
    const val FRESH_S = 15 * 60L

    /** An episode lasts until nothing of its side matched for its repeat interval, but at least this long. */
    const val MIN_HOLD_MIN = 5

    /** A *Mute all* without an end: until the user turns alerts back on. */
    const val NEVER = Long.MAX_VALUE

    /** A backward clock jump smaller than this (NTP corrections) is ignored. */
    const val CLOCK_BACK_TOLERANCE_S = 60L

    fun fresh(r: AlertReading?, now: Long): Boolean = r != null && now - r.t <= FRESH_S

    /**
     * The level kind [r] is in at [now] (the most severe that matches), or null: stale, in range, alerts off. A LO
     * matches every low kind and a HI every high kind, whatever the thresholds: its real value is beyond them all.
     */
    fun levelOf(r: AlertReading?, s: AlertSettings, now: Long): AlertKind? {
        if (r == null || !s.on || !fresh(r, now)) return null
        return AlertKind.LEVELS_BY_SEVERITY.firstOrNull { k ->
            val rule = s.rule(k)
            rule.on && ((if (k.isLowSide) r.isLo else r.isHi) || k.reaches(r.mgdl.toDouble(), rule.threshold))
        }
    }

    /** The rate kind [r] matches at [now], or null (stale, no rate, rate alerts off). */
    fun rateOf(r: AlertReading?, s: AlertSettings, now: Long): AlertKind? {
        val rate = r?.rate
        if (!fresh(r, now) || rate == null) return null
        val falling = s.rule(AlertKind.FALLING_FAST)
        val rising = s.rule(AlertKind.RISING_FAST)
        return when {
            falling.on && rate <= -falling.threshold -> AlertKind.FALLING_FAST
            rising.on && rate >= rising.threshold -> AlertKind.RISING_FAST
            else -> null
        }
    }

    /**
     * [kind] is a high that the current fall brings below the high threshold within the falling-high window
     * (`rate < 0` and `mgdl + rate × min < high`, SPEC §2 `muteFallingHigh`).
     */
    fun fallingBackIntoRange(kind: AlertKind, r: AlertReading?, s: AlertSettings): Boolean {
        val rate = r?.rate ?: return false
        // a HI's real value is unknown: no prediction from it
        if (r.isHi || !s.fallingHigh.on || kind !in AlertKind.FALLING_MUTABLE || rate >= 0) return false
        return r.mgdl + rate * s.fallingHigh.min < s.rule(AlertKind.HIGH).threshold
    }

    /**
     * [r] is back in range by the margin for [side] (SPEC §2 "Scope and end"): [r] moved `backMgdl` further out still
     * matches no enabled kind of the side, so lows at least the margin above the low threshold (70 + 10: back from 80)
     * and more than it above very low's when low is off (55 + 10: back from 66), highs below the high threshold minus
     * it (180 − 10: back from 169); a rate side when [r]'s rate doesn't match it. No data is judged by the caller
     * (readings coming). Null = [r] can't tell: a rate side and a reading without a rate (a LO / HI is sent without
     * one) is no sign the fast change stopped (SPEC §1). [margin]: the notification actions' by default; a level
     * episode's own end uses `persistence.marginMgdl` ([outOfRange]).
     */
    fun backBy(side: Side, r: AlertReading, s: AlertSettings, margin: Int = s.actions.backMgdl): Boolean? =
        when (side) {
            // a LO / HI is beyond every threshold: never back in range, whatever the margin's edge
            Side.LOW -> {
                !r.isLo &&
                    AlertKind.entries
                        .filter { it.side == Side.LOW && s.rule(it).on }
                        .none { it.reaches((r.mgdl - margin).toDouble(), s.rule(it).threshold) }
            }

            Side.HIGH -> {
                !r.isHi &&
                    AlertKind.entries
                        .filter { it.side == Side.HIGH && s.rule(it).on }
                        .none { it.reaches((r.mgdl + margin).toDouble(), s.rule(it).threshold) }
            }

            Side.FALLING_FAST -> {
                s.rule(AlertKind.FALLING_FAST).let { if (!it.on) true else r.rate?.let { v -> v > -it.threshold } }
            }

            Side.RISING_FAST -> {
                s.rule(AlertKind.RISING_FAST).let { if (!it.on) true else r.rate?.let { v -> v < it.threshold } }
            }

            Side.NO_DATA -> {
                true
            }
        }

    /**
     * [r] keeps a level episode of [side] out of range (SPEC §1 "Back by the margin", #37): fresh, and not yet
     * `persistence.marginMgdl` inside the side's threshold. No margin (0, the default): never, today's behaviour.
     */
    fun outOfRange(side: Side, r: AlertReading?, s: AlertSettings, now: Long): Boolean =
        s.persistence.marginMgdl > 0 && r != null && s.on && fresh(r, now) && backBy(side, r, s, s.persistence.marginMgdl) == false

    /** The doses of [kind] that quiet it at [now] (`now − min < t ≤ now`), logged after [after]: the newest, or null. */
    fun coveringDose(kind: AlertKind, s: AlertSettings, d: Doses, now: Long, after: Long = 0): Long? {
        val by = s.mutedBy(kind) ?: return null
        return d.of(by).filter { t -> t <= now && now - t < s.quietSeconds(by) && t > after }.maxOrNull()
    }
}
