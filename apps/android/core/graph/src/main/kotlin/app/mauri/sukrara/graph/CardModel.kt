// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.SensorTiming
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.GlucoseMetrics
import app.mauri.sukrara.graph.stats.GlucosePoint
import kotlin.math.abs

/**
 * Everything a card shows, decided by the caller from the one glucose state: the renderer never reads app singletons
 * (alerts, sensor, settings). [readings] oldest or newest first (the renderer sorts nothing it doesn't need);
 * [status] = the alert answer behind the badge (precomputed from `:core:alerts`); [muteRule] = how long each treatment
 * mutes alerts (the chart's mute lines and the last-treatment ring; null = none); [sensor] = the wearer's sensor
 * (null = nothing known); [countdownHours] = the sensor countdown window; [words] = the phone's language.
 */
data class CardModel(
    val readings: List<GlucosePoint>,
    val treatments: List<Treatment>,
    val nowS: Long,
    val words: CardWords,
    val status: CardStatus = CardStatus.None,
    val muteRule: MuteRule? = null,
    val sensor: SensorInfo? = null,
    val countdownHours: Int = SensorLife.DEFAULT_COUNTDOWN_HOURS,
    /** The report numbers of a slot's window; the default computes them from [readings]. */
    val metricsOf: ((RingSpec, Bands) -> GlucoseMetrics)? = null,
) {
    val latest: GlucosePoint? by lazy { readings.maxByOrNull { it.t } }

    fun metrics(
        spec: RingSpec,
        bands: Bands,
    ): GlucoseMetrics = metricsOf?.invoke(spec, bands) ?: GlucoseMetrics.of(readings, nowS - spec.windowS, nowS, bands)
}

/** When a treatment's alert mute ends (epoch s), or null when its kind mutes nothing (the user's alert settings). */
fun interface MuteRule {
    fun muteUntil(t: Treatment): Long?
}

/** The wearer's sensor as the card needs it: start, end and warm-up end (epoch s). */
data class SensorInfo(
    val startS: Long,
    val endS: Long,
    val warmupEndS: Long = startS + 60L * SensorTiming.DEFAULT_WARMUP_MIN,
) {
    val lengthS: Long get() = (endS - startS).coerceAtLeast(1)
}

/** Treatment fields in the card's units: epoch seconds and the kind's contract id. */
internal val Treatment.t: Long get() = at.epochSecond

internal val Treatment.k: String get() = kind.id

/** The contract id of a treatment kind (marks, badges, settings `graph.marks`). */
val TreatmentKind.id: String
    get() =
        when (this) {
            TreatmentKind.RapidInsulin -> "rapid"
            TreatmentKind.LongInsulin -> "long"
            TreatmentKind.Carbs -> "carbs"
            TreatmentKind.Note -> "note"
        }

/**
 * THE trend arrow for a rate (mg/dL/min), one picture and one word per arrow (contracts/graph/trend, #4): the rate
 * falls in one of Nightscout's direction bands (±1 / 2 / 3), and every place draws, writes and says that band. The
 * drawn arrow snaps to the band's angle (→ flat, ↗ 45°, ↑ and ⇈ straight up, ⇈ doubled), so a glance never sees a
 * tilted icon beside a flat text arrow; surfaces that animate it turn between the snapped angles.
 */
object TrendAngle {
    /** mg/dL/min from which the arrow doubles (⇈ / ⇊). */
    const val FULL_SCALE = 3.0

    /** The band: -3 (⇊) … 0 (→) … 3 (⇈), from Nightscout's ±1 / 2 / 3 (a boundary belongs to the steeper band). */
    fun step(rate: Double): Int =
        when {
            rate >= FULL_SCALE -> 3
            rate >= 2.0 -> 2
            rate >= 1.0 -> 1
            rate > -1.0 -> 0
            rate > -2.0 -> -1
            rate > -FULL_SCALE -> -2
            else -> -3
        }

    /** The drawn arrow's rotation in degrees, y-down (negative = up): the band's 0, ±45 or ±90. */
    fun deg(rate: Double): Float = DEGREES[step(rate) + 3]

    fun isDouble(rate: Double) = abs(step(rate)) == 3

    /**
     * THE text arrow for a rate (mg/dL/min), for places that can only write text (notification title, alert text),
     * as the Mac menu bar writes them. A place that also draws the arrow (the Live Update chip's icon) draws the same
     * band, so the two can never disagree.
     */
    fun symbol(rate: Double): String = SYMBOLS[step(rate) + 3]

    /** [symbol] for a rate that may be missing: no rate = the "no trend" ring ([TrendMark.RING]), as the card draws it. */
    fun text(rate: Double?): String = rate?.let(::symbol) ?: TrendMark.RING

    /**
     * The band's Nightscout direction ("FortyFiveUp"; "NONE" without a rate), for the Nightscout upload and the local
     * Nightscout / xDrip web endpoints. The xDrip+ broadcast keeps xDrip+'s own rule (BroadcastPayloads.trendName).
     */
    fun nightscout(rate: Double?): String = rate?.let { NIGHTSCOUT[step(it) + 3] } ?: "NONE"

    private val DEGREES = listOf(90f, 90f, 45f, 0f, -45f, -90f, -90f)
    private val SYMBOLS = listOf("⇊", "↓", "↘", "→", "↗", "↑", "⇈")
    private val NIGHTSCOUT = listOf("DoubleDown", "SingleDown", "FortyFiveDown", "Flat", "FortyFiveUp", "SingleUp", "DoubleUp")
}
