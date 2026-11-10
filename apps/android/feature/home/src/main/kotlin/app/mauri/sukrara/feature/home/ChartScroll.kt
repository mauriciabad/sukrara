// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.mauri.sukrara.graph.ChartView
import kotlin.math.abs

/**
 * Where home's chart looks: the end of its window ([endS], epoch s; null = following now) and its length while zoomed
 * ([spanS], s; null = the place's *Time shown*). Exact values are kept as doubles so sub-second drag steps add up.
 * Hoisted state: the gestures, the TalkBack actions and the tests drive the same object; [rememberChartScroll] keeps
 * the window across a rotation (and any other configuration change) and opens at now otherwise.
 */
@Stable
class ChartScroll(
    endS: Long? = null,
    spanS: Long? = null,
) {
    var endS by mutableStateOf(endS)
        private set
    var spanS by mutableStateOf(spanS)
        private set
    private var exactEnd: Double? = endS?.toDouble()
    private var exactSpan: Double? = spanS?.toDouble()

    /** Scrolled or zoomed: the chart doesn't show the usual window up to now (the *Now* pill shows). */
    val away: Boolean get() = endS != null || spanS != null

    /** The window's length now; [default] = the place's *Time shown*. */
    fun span(default: Long): Long = spanS ?: default

    /**
     * Moves the end by [dt] s, kept within [minEnd]..[now] (at [now] the chart follows now again); returns the seconds
     * actually moved (less at an edge: the rest is overscroll).
     */
    fun by(
        dt: Double,
        minEnd: Long,
        now: Long,
    ): Double {
        val cur = exactEnd ?: now.toDouble()
        val next = (cur + dt).coerceIn(minOf(minEnd, now).toDouble(), now.toDouble())
        exactEnd = if (next >= now) null else next
        endS = exactEnd?.toLong()
        return next - cur
    }

    /**
     * Zooms the time axis by [factor] (> 1 = in) keeping the moment at [anchor] (0 = the plot's left edge, 1 = its right
     * edge) in place. The length stays within [ChartView.MIN_SPAN_S]..[ChartView.MAX_SPAN_S] and the history ([first] =
     * the oldest reading), never narrower or wider than [default] needs; a window that reaches now keeps following now.
     */
    fun zoom(
        factor: Double,
        anchor: Double,
        default: Long,
        first: Long,
        now: Long,
    ) {
        if (factor <= 0.0 || factor.isNaN() || factor.isInfinite()) return
        val (lo, hi) = spanLimits(default, first, now)
        val span0 = exactSpan ?: default.toDouble()
        val span1 = (span0 / factor).coerceIn(lo, hi)
        val end0 = exactEnd ?: now.toDouble()
        val a = anchor.coerceIn(0.0, 1.0)
        val at = end0 - span0 * (1 - a)
        val end1 = (at + span1 * (1 - a)).coerceIn(minOf(first + span1, now.toDouble()), now.toDouble())
        set(end1, span1, default, now)
    }

    /**
     * Whether one more step [out] (or in) changes the window: false at the shortest / longest length for [default]
     * with history from [first] at [now] (the Zoom in / Zoom out actions are hidden there).
     */
    fun canZoom(
        out: Boolean,
        default: Long,
        first: Long,
        now: Long,
    ): Boolean {
        val (lo, hi) = spanLimits(default, first, now)
        // through the state (spanS and exactSpan change together), so a derivedStateOf of this follows the zoom
        val span = spanS?.let { exactSpan } ?: default.toDouble()
        return if (out) span < hi - LIMIT_S else span > lo + LIMIT_S
    }

    /** One zoom step for TalkBack and keyboard: twice as long ([out]) or half as long, anchored at the window's end. */
    fun step(
        out: Boolean,
        default: Long,
        first: Long,
        now: Long,
    ) = zoom(if (out) 0.5 else 2.0, 1.0, default, first, now)

    /**
     * Where a double tap takes the window (#95; the Mac's two-finger double tap, `ChartEase.smartZoom`): from the place's
     * own length (or any other) to [CLOSE_S] for the close look before a bolus, and from 3 h back to the place's own
     * (always, on a place that shows 3 h or less), keeping the moment at [anchor] (0 = the plot's left edge, 1 = its
     * right edge) in place as [zoom] does. The new end and length; null when the window is already there.
     */
    fun closeLook(
        anchor: Double,
        default: Long,
        first: Long,
        now: Long,
    ): Pair<Double, Double>? {
        val current = exactSpan ?: default.toDouble()
        val target = if (abs(current - CLOSE_S) < SAME_SPAN_S || default <= CLOSE_S) default.toDouble() else CLOSE_S.toDouble()
        if (abs(target - current) < SAME_SPAN_S) return null
        val (lo, hi) = spanLimits(default, first, now)
        val span1 = target.coerceIn(lo, hi)
        val end0 = exactEnd ?: now.toDouble()
        val a = anchor.coerceIn(0.0, 1.0)
        val at = end0 - current * (1 - a)
        val end1 = (at + span1 * (1 - a)).coerceIn(minOf(first + span1, now.toDouble()), now.toDouble())
        return end1 to span1
    }

    /** Whether a double tap would show [CLOSE_S] (else it goes back to the place's own length, or does nothing). */
    fun closeLookIn(default: Long): Boolean = default > CLOSE_S && abs((spanS?.let { exactSpan } ?: default.toDouble()) - CLOSE_S) >= SAME_SPAN_S

    /** Puts the window at [end] with length [span] (the way back to now animates through this). */
    fun set(
        end: Double,
        span: Double,
        default: Long,
        now: Long,
    ) {
        exactSpan = if (abs(span - default) < SAME_SPAN_S) null else span
        spanS = exactSpan?.toLong()
        exactEnd = if (end >= now) null else end
        endS = exactEnd?.toLong()
    }

    companion object {
        /** A length within this of the place's own counts as not zoomed (the pill goes away). */
        private const val SAME_SPAN_S = 30.0

        /** The double tap's close look (#95): 3 h, as on the Mac. */
        const val CLOSE_S = 3 * 3600L

        /** A length within this of a limit is at it (no further zoom step). */
        private const val LIMIT_S = 1.0

        /**
         * Saves the exact end and length (NaN = following now / the place's own) while [keep] says so (a configuration
         * change: the same window to the second after a rotation), else the usual window up to now (leaving home, the
         * process being recreated later: home opens at now, glucose first).
         */
        fun saver(keep: () -> Boolean): Saver<ChartScroll, DoubleArray> =
            Saver(
                save = {
                    if (keep()) {
                        doubleArrayOf(
                            it.exactEnd ?: Double.NaN,
                            it.exactSpan ?: Double.NaN,
                        )
                    } else {
                        doubleArrayOf(Double.NaN, Double.NaN)
                    }
                },
                restore = { saved ->
                    val end = saved.getOrNull(0)?.takeUnless(Double::isNaN)
                    val span = saved.getOrNull(1)?.takeUnless(Double::isNaN)
                    ChartScroll(end?.toLong(), span?.toLong()).apply {
                        exactEnd = end
                        exactSpan = span
                    }
                },
            )

        /** The shortest and longest window for [default] with history from [first] at [now] (s). */
        fun spanLimits(
            default: Long,
            first: Long,
            now: Long,
        ): Pair<Double, Double> {
            val lo = minOf(ChartView.MIN_SPAN_S, default).toDouble()
            val hi = maxOf(default, minOf(ChartView.MAX_SPAN_S, now - first)).toDouble()
            return lo to hi
        }
    }
}

/**
 * Home's [ChartScroll], kept across a rotation (or another configuration change: theme, font size, window size) and
 * back at now when home is left or the app comes back after the process was recreated. [keep] is asked at save time.
 */
@Composable
fun rememberChartScroll(keep: () -> Boolean = changingConfigurations()): ChartScroll =
    rememberSaveable(saver = ChartScroll.saver(keep)) { ChartScroll() }

/** Whether this screen's activity is being recreated for a configuration change (asked when its state is saved). */
@Composable
private fun changingConfigurations(): () -> Boolean {
    val context = LocalContext.current
    return { context.activity()?.isChangingConfigurations == true }
}

private tailrec fun Context.activity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
