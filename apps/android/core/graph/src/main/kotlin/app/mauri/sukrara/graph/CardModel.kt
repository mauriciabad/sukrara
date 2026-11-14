// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.SensorTiming
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TrendRate
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
 * THE trend arrow (contracts/trend `bands.json`, #204): the drawn arrow turns with the trend's rate, continuously
 * ([deg]: flat at 0, vertical from ±3 mg/dL/min), so 0.4 and 0.9 look different; its text band (`ReadingTrend.band`:
 * Nightscout's ±1 / 2 / 3 with hysteresis, doubled only once held) decides the text arrow, the words, the Nightscout
 * direction and whether the drawn arrow is doubled. The drawn arrow stays within 30° of the text arrow beside it.
 * Places that can't rotate (system icons) draw the band ([bandDeg]). No place maps a rate to an arrow itself.
 */
object TrendAngle {
    /** mg/dL/min from which the arrow is vertical and can double (⇈ / ⇊). */
    const val FULL_SCALE = TrendRate.FULL_SCALE

    /** The plain band of a rate with nothing before it (no hysteresis, no hold): -3 (⇊) … 0 (→) … 3 (⇈). */
    fun step(rate: Double): Int = TrendRate.plainBand(rate)

    /** The drawn arrow's rotation in degrees, y-down (negative = up): linear in the rate, ±90 from ±3. */
    fun deg(rate: Double): Float = TrendRate.deg(rate).toFloat()

    /** The band's own angle (0, ±45, ±90), for places that can only draw pre-made arrows. */
    fun bandDeg(band: Int): Float = DEGREES[band.coerceIn(-3, 3) + 3]

    fun isDouble(band: Int) = abs(band) == 3

    /**
     * THE text arrow for a band, for places that can only write text (notification title, alert text), as the Mac menu
     * bar writes them.
     */
    fun symbol(band: Int): String = SYMBOLS[band.coerceIn(-3, 3) + 3]

    /** [symbol] for a band that may be missing: no trend = the "no trend" ring ([TrendMark.RING]), as the card draws it. */
    fun text(band: Int?): String = band?.let(::symbol) ?: TrendMark.RING

    /**
     * The band's Nightscout direction ("FortyFiveUp"; "NONE" without a trend), for the Nightscout upload and the local
     * Nightscout / xDrip web endpoints. The xDrip+ broadcast keeps xDrip+'s own rule (BroadcastPayloads.trendName).
     */
    fun nightscout(band: Int?): String = band?.let { NIGHTSCOUT[it.coerceIn(-3, 3) + 3] } ?: "NONE"

    private val DEGREES = listOf(90f, 90f, 45f, 0f, -45f, -90f, -90f)
    private val SYMBOLS = listOf("⇊", "↓", "↘", "→", "↗", "↑", "⇈")
    private val NIGHTSCOUT = listOf("DoubleDown", "SingleDown", "FortyFiveDown", "Flat", "FortyFiveUp", "SingleUp", "DoubleUp")
}
