// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.alerts

/**
 * Where glucose is heading, read from the readings themselves (SPEC §2 "Trend from the readings", #193), never from the
 * sensor's own rate: that lags by many minutes (the owner's evening: the values climbed 1–3 mg/dL a minute while the
 * rate still said falling). The one judgement behind the falling-high and the rising-low quiet ([Rules]); the Mac's
 * `Trend` is the same, number for number (its tests and these run the same cases).
 */
object Trend {
    /** The window: the readings taken in the 5 minutes up to the newest one. */
    const val WINDOW_S = 5 * 60L

    /** The newest reading may be this old: a held alert fires at most this long after the readings stop. */
    const val MAX_AGE_S = 150L

    /** At least this many readings in the window ... */
    const val MIN_READINGS = 4

    /** ... spanning at least this long ... */
    const val MIN_SPAN_S = 3 * 60L

    /** ... and none further than this after the one before (one missed reading of a 1-minute sensor, not two). */
    const val MAX_GAP_S = 150L

    /** A step against the direction this big is still a Libre's reading noise; a bigger one is no trend. */
    const val NOISE_MGDL = 1

    /** The newest two readings each beyond every reading taken more than this before them: still moving. */
    const val MOVING_S = 90L

    /** The least-squares slope must be at least this steep (mg/dL/min). */
    const val MIN_SLOPE = 0.5

    /**
     * [recent] (ascending by time) with [r] in it, keeping only what a window can use: the readings taken within
     * [WINDOW_S] of the newest one; a reading of the same time replaces the one there.
     */
    fun keep(
        recent: List<AlertReading>,
        r: AlertReading,
    ): List<AlertReading> {
        val all = (recent.filter { it.t != r.t } + r).sortedBy { it.t }
        val newest = all.last().t
        return all.filter { newest - it.t < WINDOW_S }
    }

    /**
     * The least-squares slope (mg/dL/min) of [readings] (ascending by time, as [keep] leaves them) when they show a
     * clear move up ([up]) or down at [now], else null: the window of the newest reading (at most [MAX_AGE_S] old at
     * [now]) has [MIN_READINGS] spanning [MIN_SPAN_S] without a gap over [MAX_GAP_S] or a LO / HI, no step against the
     * direction over [NOISE_MGDL], its newest two readings each beyond every reading [MOVING_S] older, and a slope of
     * at least [MIN_SLOPE] that way.
     */
    fun clear(
        readings: List<AlertReading>,
        now: Long,
        up: Boolean,
    ): Double? {
        val newest = readings.lastOrNull() ?: return null
        if (now - newest.t > MAX_AGE_S) return null
        val w = readings.filter { newest.t - it.t < WINDOW_S && it.t <= newest.t }
        if (w.size < MIN_READINGS || newest.t - w.first().t < MIN_SPAN_S) return null
        if (w.any { it.isLo || it.isHi }) return null
        val dir = if (up) 1 else -1
        for (i in 1 until w.size) {
            if (w[i].t - w[i - 1].t > MAX_GAP_S) return null
            if (dir * (w[i].mgdl - w[i - 1].mgdl) < -NOISE_MGDL) return null
        }
        for (r in w.takeLast(2)) {
            if (w.any { it.t < r.t - MOVING_S && dir * (r.mgdl - it.mgdl) <= 0 }) return null
        }
        val slope = slope(w)
        return slope.takeIf { dir * it >= MIN_SLOPE }
    }

    /**
     * The least-squares slope of [w] in mg/dL/min: time in seconds from the newest reading, summed oldest first, then
     * `Σ(dt·dv) × 60 / Σdt²` (the Mac sums in the same order, so both get the same Double).
     */
    fun slope(w: List<AlertReading>): Double {
        val t0 = w.last().t
        val n = w.size.toDouble()
        var st = 0.0
        var sv = 0.0
        for (r in w) {
            st += (r.t - t0).toDouble()
            sv += r.mgdl.toDouble()
        }
        val mt = st / n
        val mv = sv / n
        var num = 0.0
        var den = 0.0
        for (r in w) {
            val dt = (r.t - t0).toDouble() - mt
            num += dt * (r.mgdl.toDouble() - mv)
            den += dt * dt
        }
        return if (den == 0.0) 0.0 else num * 60.0 / den
    }
}
