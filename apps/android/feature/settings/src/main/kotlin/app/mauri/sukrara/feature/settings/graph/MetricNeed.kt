// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.graph

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.GlucoseMetrics
import app.mauri.sukrara.graph.stats.toPoint
import java.time.Instant
import kotlin.math.roundToLong
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * Why a metric isn't on the card (docs/44 Appendix G §4, the owner's Q3): its readings cover [haveS] of the last
 * [windowS], and the renderer draws a metric from [needS] on (`GlucoseMetrics.SLOT_MIN_COVERAGE`, 25 %). Settings › Metrics
 * says it in the metric's summary; the card stays the renderer's and says nothing.
 */
data class MetricNeed(
    val haveS: Long,
    val windowS: Long,
    val needS: Long,
) {
    companion object {
        /** How much of the last [windowS] before [now] [readings] cover, as the card counts it. */
        fun coverage(
            readings: List<Reading>,
            now: Instant,
            windowS: Long,
        ): Double = GlucoseMetrics.of(readings.map { it.toPoint() }, now.epochSecond - windowS, now.epochSecond, Bands()).tir.coverage

        /** The need for a window of [windowS] covered [coverage]; null = enough (or not known): the card shows it. */
        fun of(
            coverage: Double?,
            windowS: Long,
        ): MetricNeed? =
            coverage?.takeIf { it < GlucoseMetrics.SLOT_MIN_COVERAGE }?.let {
                MetricNeed((it * windowS).roundToLong(), windowS, (GlucoseMetrics.SLOT_MIN_COVERAGE * windowS).roundToLong())
            }
    }
}

/** *Needs more readings: 12 h of the last 7 days, 42 h needed*. */
@Composable
fun metricNeedText(need: MetricNeed): String =
    stringResource(R.string.st_metric_needs, needDuration(need.haveS), periodLabeler()(need.windowS.toDouble()), needDuration(need.needS))

/** A coverage as the person reads it: hours up to three days (*12 h*, *42 h*), then days (*8 days*). */
@Composable
private fun needDuration(seconds: Long): String {
    val hours = (seconds / 3_600.0).roundToLong()
    if (hours < 72) return "$hours ${stringResource(DsR.string.ds_unit_h)}"
    val days = (seconds / 86_400.0).roundToLong().toInt()
    return LocalResources.current.getQuantityString(DsR.plurals.ds_days, days, days)
}
