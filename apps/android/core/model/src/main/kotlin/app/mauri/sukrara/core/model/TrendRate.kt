// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import kotlin.math.abs
import kotlin.math.exp

/**
 * The *Trend arrow* setting (`glucose.trend`, mirrored from the wearer): from the readings with the weighting [tauS]
 * (Quick / Balanced / Smooth), or the sensor's own rate ([Sensor]). contracts/trend, docs/34-trend.md.
 */
enum class TrendSetting(
    val id: String,
    val tauS: Long?,
) {
    Quick("quick", 120),
    Balanced("balanced", 180),
    Smooth("smooth", 300),
    Sensor("sensor", null),
    ;

    companion object {
        val DEFAULT = Balanced

        /** The stored / wire value; anything else reads as the default. */
        fun of(id: String?): TrendSetting = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Where a reading's trend came from: the readings around it, or the sensor's own rate (the setting or the fallback). */
enum class TrendFrom { Readings, Sensor }

/**
 * THE trend at a reading ([TrendRate.annotate]): [rate] mg/dL/min (what the drawn arrow turns by), [band] the text band
 * -3 … 3 with hysteresis and the doubled arrow's hold (what every text arrow, word and Nightscout direction says),
 * [from] where it came from, [held] what the falling / rising fast alerts judge (null = not held for 5 min yet).
 */
data class ReadingTrend(
    val rate: Double,
    val band: Int,
    val from: TrendFrom,
    val held: Double?,
)

/** A reading as the quiets' clear-move rule reads it: epoch s and mg/dL (LO / HI as stored, [GlucoseRange]). */
interface TrendPoint {
    val t: Long
    val mgdl: Int
}

/**
 * THE trend (contracts/trend, docs/34-trend.md, #204), one module: every arrow, spoken word, alert text, output and
 * the fast alerts read [Reading.trend], which only [annotate] writes, at the one place readings are read
 * (`:core:data` TrendReadings). The Mac's `TrendRate` is the same, number for number (both run contracts/trend).
 *
 * From the readings, at each reading: walk back while readings are less than 5τ older, stopping at a LO / HI or a step
 * over [MAX_STEP_S]; at least [MIN_READINGS] readings spanning [MIN_SPAN_S]; a least-squares slope weighted
 * `exp(−age / τ)`. Else the reading's own sensor rate, else no trend. A LO / HI has none.
 */
object TrendRate {
    /** The window: the readings of the last 5 τ. */
    const val WINDOW_TAUS = 5

    /** At least this many readings ... */
    const val MIN_READINGS = 2

    /** ... spanning at least this long (s). */
    const val MIN_SPAN_S = 180L

    /** A longer step between two readings starts over (2.5 readings of a 5-min sensor). */
    const val MAX_STEP_S = 750L

    /** A text band is left only once the rate is this far past its edge (mg/dL/min). */
    const val HYSTERESIS = 0.2

    /** From here the drawn arrow is vertical and doubles (band ±3). */
    const val FULL_SCALE = 3.0

    /** The doubled arrow: the rate at least [FULL_SCALE] at every reading of the last 2 min. */
    const val DOUBLE_HOLD_S = 120L

    /** The fast alerts: the rate past their threshold at every reading of the last 5 min. */
    const val ALERT_HOLD_S = 300L

    /**
     * [readings] with every reading's [Reading.trend] at its own time, from the readings at or before it, under
     * [setting]; sorted by time (the last one given for a time wins the computation; readings of the same time get the
     * same trend). The one function that writes [Reading.trend].
     */
    fun annotate(
        readings: List<Reading>,
        setting: TrendSetting,
    ): List<Reading> {
        if (readings.isEmpty()) return readings
        val sorted = readings.sortedBy { it.t }
        val byT = LinkedHashMap<Long, Reading>()
        for (r in readings) byT[r.t.epochSecond] = r
        val ts = byT.keys.sorted()
        val values = ts.map { t -> byT.getValue(t).let { if (it.isLo || it.isHi) null else it.mgdl } }
        val rates = arrayOfNulls<Double>(ts.size)
        val from = arrayOfNulls<TrendFrom>(ts.size)
        for (i in ts.indices) {
            if (values[i] == null) continue
            val tau = setting.tauS
            val own = if (tau != null) fromReadings(ts, values, i, tau) else null
            val sensor = byT.getValue(ts[i]).sensorRate
            when {
                own != null -> {
                    rates[i] = own
                    from[i] = TrendFrom.Readings
                }

                sensor != null -> {
                    rates[i] = sensor
                    from[i] = TrendFrom.Sensor
                }
            }
        }
        val rateList = rates.toList()
        val bands = bands(ts, rateList)
        val trends =
            ts.indices.associate { i ->
                val r = rates[i]
                ts[i] to
                    r?.let {
                        ReadingTrend(it, bands[i]!!, from[i]!!, if (setting == TrendSetting.Sensor) it else held(ts, rateList, i))
                    }
            }
        return sorted.map { it.copy(trend = trends[it.t.epochSecond]) }
    }

    /** Nightscout's direction bands ±1 / 2 / 3 (a boundary belongs to the steeper band): -3 … 3, nothing before it. */
    fun plainBand(rate: Double): Int {
        val a = abs(rate)
        val s =
            when {
                a >= FULL_SCALE -> 3
                a >= 2.0 -> 2
                a >= 1.0 -> 1
                else -> 0
            }
        return if (rate >= 0) s else -s
    }

    /**
     * The text band at each of [ts] (epoch s, ascending, one per time) for [rates] (null = no trend): the band before
     * is remembered while the next reading follows within [MAX_STEP_S] with a rate, and left only [HYSTERESIS] past its
     * edge; band ±3 (the doubled arrow) is entered when the rate was at least [FULL_SCALE] that way at every reading of
     * the last [DOUBLE_HOLD_S] (that hold is its way in), and left with the hysteresis.
     */
    fun bands(
        ts: List<Long>,
        rates: List<Double?>,
    ): List<Int?> {
        val out = ArrayList<Int?>(ts.size)
        for (i in ts.indices) {
            val r = rates[i]
            if (r == null) {
                out += null
                continue
            }
            val prev = if (i > 0 && ts[i] - ts[i - 1] <= MAX_STEP_S) out[i - 1] else null
            var b = withHysteresis(r, prev)
            if (prev == null || abs(prev) != 3 || (prev > 0) != (r > 0)) {
                val sign = if (r > 0) 1 else -1
                val idx = back(ts, i, DOUBLE_HOLD_S)
                val doubled = idx != null && idx.all { k -> rates[k]?.let { sign * it >= FULL_SCALE } == true }
                b = if (doubled) 3 * sign else b.coerceIn(-2, 2)
            }
            out += b
        }
        return out
    }

    /** The drawn arrow's rotation, degrees y-down (negative = up): linear, 0° flat, vertical from ±[FULL_SCALE]. */
    fun deg(rate: Double): Double = -(rate / FULL_SCALE).coerceIn(-1.0, 1.0) * 90.0 + 0.0

    private fun withHysteresis(
        r: Double,
        prev: Int?,
    ): Int {
        val b = plainBand(r)
        if (prev == null || b == prev) return b
        return if (b > prev) maxOf(prev, plainBand(r - HYSTERESIS)) else minOf(prev, plainBand(r + HYSTERESIS))
    }

    /**
     * The rate at [i] from the readings at or before it ([values] null = LO / HI): back while less than 5 τ older, a
     * step over [MAX_STEP_S] or a LO / HI stops; at least [MIN_READINGS] spanning [MIN_SPAN_S]; else null.
     */
    private fun fromReadings(
        ts: List<Long>,
        values: List<Int?>,
        i: Int,
        tau: Long,
    ): Double? {
        val t0 = ts[i]
        var j = i - 1
        while (j >= 0) {
            if (t0 - ts[j] >= WINDOW_TAUS * tau || values[j] == null || ts[j + 1] - ts[j] > MAX_STEP_S) break
            j--
        }
        val first = j + 1
        if (i - first + 1 < MIN_READINGS || t0 - ts[first] < MIN_SPAN_S) return null
        return weightedSlope(ts, values, first, i, tau)
    }

    /**
     * Least squares weighted `exp(−age / τ)` over [first]..[last], mg/dL/min: time in s from the newest reading, sums
     * oldest first, `Σw·(dt − t̄)·(v − v̄) × 60 / Σw·(dt − t̄)²` (the Mac sums in the same order).
     */
    private fun weightedSlope(
        ts: List<Long>,
        values: List<Int?>,
        first: Int,
        last: Int,
        tau: Long,
    ): Double {
        val t0 = ts[last]
        val w = DoubleArray(last - first + 1) { k -> exp(-(t0 - ts[first + k]).toDouble() / tau.toDouble()) }
        var sw = 0.0
        var st = 0.0
        var sv = 0.0
        for (k in w.indices) {
            sw += w[k]
            st += w[k] * (ts[first + k] - t0).toDouble()
            sv += w[k] * values[first + k]!!.toDouble()
        }
        val mt = st / sw
        val mv = sv / sw
        var num = 0.0
        var den = 0.0
        for (k in w.indices) {
            val dt = (ts[first + k] - t0).toDouble() - mt
            num += w[k] * dt * (values[first + k]!!.toDouble() - mv)
            den += w[k] * dt * dt
        }
        return if (den == 0.0) 0.0 else num * 60.0 / den
    }

    /**
     * The fast alerts' rate at [i]: over the readings back to the first at or before [ALERT_HOLD_S] earlier, all with a
     * rate, the one nearest zero when they agree in sign, 0 when they don't; null when they don't reach back that far.
     */
    private fun held(
        ts: List<Long>,
        rates: List<Double?>,
        i: Int,
    ): Double? {
        val idx = back(ts, i, ALERT_HOLD_S) ?: return null
        val rs = idx.map { rates[it] ?: return null }
        return when {
            rs.all { it >= 0 } -> rs.min()
            rs.all { it <= 0 } -> rs.max()
            else -> 0.0
        }
    }

    /**
     * The indices from [i] back to the first reading at or before `ts[i] − hold`, none more than [MAX_STEP_S] after the
     * one before; null when the readings don't reach back that far.
     */
    private fun back(
        ts: List<Long>,
        i: Int,
        hold: Long,
    ): List<Int>? {
        val out = mutableListOf(i)
        var j = i
        while (ts[i] - ts[j] < hold) {
            if (j == 0 || ts[j] - ts[j - 1] > MAX_STEP_S) return null
            j--
            out += j
        }
        return out
    }

    // ---- the #193 quiets' clear move (contracts/alerts/SPEC.md §2 "Clear move in the readings"), unchanged

    /** The window: the readings taken in the 5 minutes up to the newest one. */
    const val CLEAR_WINDOW_S = 5 * 60L

    /** The newest reading may be this old: a held alert fires at most this long after the readings stop. */
    const val CLEAR_MAX_AGE_S = 150L

    /** At least this many readings in the window ... */
    const val CLEAR_MIN_READINGS = 4

    /** ... spanning at least this long ... */
    const val CLEAR_MIN_SPAN_S = 3 * 60L

    /** ... and none further than this after the one before (one missed reading of a 1-minute sensor, not two). */
    const val CLEAR_MAX_GAP_S = 150L

    /** A step against the direction this big is still a Libre's reading noise; a bigger one is no trend. */
    const val CLEAR_NOISE_MGDL = 1

    /** The newest two readings each beyond every reading taken more than this before them: still moving. */
    const val CLEAR_MOVING_S = 90L

    /** The least-squares slope must be at least this steep (mg/dL/min). */
    const val CLEAR_MIN_SLOPE = 0.5

    /**
     * The least-squares slope (mg/dL/min) of [readings] (ascending by time) when they show a clear move up ([up]) or
     * down at [now], else null: the window of the newest reading (at most [CLEAR_MAX_AGE_S] old at [now]) has
     * [CLEAR_MIN_READINGS] spanning [CLEAR_MIN_SPAN_S] without a gap over [CLEAR_MAX_GAP_S] or a LO / HI, no step against
     * the direction over [CLEAR_NOISE_MGDL], its newest two readings each beyond every reading [CLEAR_MOVING_S] older,
     * and a slope of at least [CLEAR_MIN_SLOPE] that way. The quiets' own rule (#193): it reads the readings, never the
     * trend setting.
     */
    fun clear(
        readings: List<TrendPoint>,
        now: Long,
        up: Boolean,
    ): Double? {
        val newest = readings.lastOrNull() ?: return null
        if (now - newest.t > CLEAR_MAX_AGE_S) return null
        val w = readings.filter { newest.t - it.t < CLEAR_WINDOW_S && it.t <= newest.t }
        if (w.size < CLEAR_MIN_READINGS || newest.t - w.first().t < CLEAR_MIN_SPAN_S) return null
        if (w.any { GlucoseRange.isLo(it.mgdl) || GlucoseRange.isHi(it.mgdl) }) return null
        val dir = if (up) 1 else -1
        for (i in 1 until w.size) {
            if (w[i].t - w[i - 1].t > CLEAR_MAX_GAP_S) return null
            if (dir * (w[i].mgdl - w[i - 1].mgdl) < -CLEAR_NOISE_MGDL) return null
        }
        for (r in w.takeLast(2)) {
            if (w.any { it.t < r.t - CLEAR_MOVING_S && dir * (r.mgdl - it.mgdl) <= 0 }) return null
        }
        val slope = slope(w)
        return slope.takeIf { dir * it >= CLEAR_MIN_SLOPE }
    }

    /**
     * The least-squares slope of [w] in mg/dL/min: time in seconds from the newest reading, summed oldest first, then
     * `Σ(dt·dv) × 60 / Σdt²` (the Mac sums in the same order, so both get the same Double).
     */
    fun slope(w: List<TrendPoint>): Double {
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
