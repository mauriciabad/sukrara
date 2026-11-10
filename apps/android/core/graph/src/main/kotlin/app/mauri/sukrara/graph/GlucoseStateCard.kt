// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentQuiet
import app.mauri.sukrara.graph.stats.MetricsCache
import app.mauri.sukrara.graph.stats.toPoint
import java.time.Duration
import java.time.Instant

/**
 * The card's model from the one glucose state ([GlucoseState]) at [now]: what a card surface outside the app panel
 * (the widgets, the notification, the lock screen) draws. The badge is the alert runtime's answer
 * ([AlertUiState.status]), the mute lines and last-treatment ring its quiet windows ([muteRuleOf]). The chart draws the live
 * window; the slots take their numbers from [slots] (a surface that has the stored days hands in a [MetricsCache] over
 * them, built once per frame: the 7–30-day rings need more than the live window); null = the live window's.
 */
fun cardModelOf(
    state: GlucoseState,
    treatments: List<Treatment>,
    now: Instant,
    words: CardWords,
    slots: MetricsCache? = null,
): CardModel =
    CardModel(
        readings = state.recent.map { it.toPoint() },
        treatments = treatments,
        nowS = now.epochSecond,
        words = words,
        status = state.alerts.status,
        muteRule = muteRuleOf(state.alerts),
        sensor = state.sensor.cardSensor(now),
        metricsOf = slots,
    )

/**
 * THE card's [MuteRule] on Android, for every surface (this file's [cardModelOf] and the app panel): the alert runtime's
 * quiet windows ([AlertUiState.treatmentQuiet], from the rules its engine runs), so a mute line or ring never says
 * alerts are quiet when they aren't, or the reverse; null when no treatment quiets anything (no engine, both rules off).
 */
fun muteRuleOf(alerts: AlertUiState): MuteRule? = alerts.treatmentQuiet.takeUnless { it.none }?.let(::QuietRule)

/** A [TreatmentQuiet] as the renderer's [MuteRule]; a data class, so equal windows make equal models. */
private data class QuietRule(
    val quiet: TreatmentQuiet,
) : MuteRule {
    override fun muteUntil(t: Treatment): Long? = quiet.until(t)
}

/** The sensor's dates as the card needs them; null when nothing is known (the card then shows no sensor badge). */
fun SensorState.cardInfo(): SensorInfo? =
    when (this) {
        is SensorState.Active -> {
            endsAt?.let { SensorInfo(startS = it.epochSecond - SENSOR_LIFE.seconds, endS = it.epochSecond) }
        }

        is SensorState.WarmingUp -> {
            val start = readyAt.epochSecond - WARMUP.seconds
            SensorInfo(startS = start, endS = start + SENSOR_LIFE.seconds, warmupEndS = readyAt.epochSecond)
        }

        else -> {
            null
        }
    }

/**
 * The sensor as a card draws it at [now]: [cardInfo], and an ended sensor whose dates the source didn't send
 * ([SensorState.Ended]) as ending at [now], so every card says *Sensor ended* with *Replace sensor* in the value's
 * place (as the app panel and the text surfaces do) instead of the last reading's age.
 */
fun SensorState.cardSensor(now: Instant): SensorInfo? =
    cardInfo()
        ?: if (this == SensorState.Ended) SensorInfo(startS = now.epochSecond - SENSOR_LIFE.seconds, endS = now.epochSecond) else null

private val SENSOR_LIFE = Duration.ofDays(15)
private val WARMUP = Duration.ofHours(1)
