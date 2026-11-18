// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import java.time.Instant

/** A sensor a source said started: its [id] (a new id = a new sensor) and its start ([startS], epoch s). */
data class SaidSensor(
    val id: String,
    val startS: Long,
)

/** Where a sensor's start came from: the source, or the person on the Sensor page. */
enum class LifeFrom { Source, Manual }

/**
 * A sensor's life as every surface uses it (epoch s): the card's warm-up and time left, *Sensor ended* (no data stays
 * quiet), the reminders, the countdown and the calendar. [from] says whose start it is.
 */
data class SensorSpan(
    val id: String,
    val startS: Long,
    val warmupEndS: Long,
    val endS: Long,
    val from: LifeFrom,
) {
    val start: Instant get() = Instant.ofEpochSecond(startS)
    val warmupEnd: Instant get() = Instant.ofEpochSecond(warmupEndS)
    val end: Instant get() = Instant.ofEpochSecond(endS)

    /** The length it was given (minutes). */
    val wearMin: Int get() = ((endS - startS) / 60).toInt()

    /** The hub's state at [nowS], the newest reading taken at [latestS]: ended, warming (THE warm-up rule) or active. */
    fun state(
        latestS: Long?,
        nowS: Long,
    ): SensorState =
        when {
            nowS >= endS -> SensorState.Ended
            SensorTiming.warming(startS, warmupEndS, latestS, nowS) -> SensorState.WarmingUp(warmupEnd, id)
            else -> SensorState.Active(end, id)
        }
}

/**
 * The person's side of a source's sensor life (the source file: schema `source.*Wear`, `sensor.manualStart`): whose
 * glucose it is, the source's wear length ([wearMin], minutes; 0 = the default) and the manual start ([manualStartS],
 * epoch s; 0 = none).
 */
data class LifeSettings(
    val person: Person? = null,
    val wearMin: Int = 0,
    val manualStartS: Long = 0,
)

/**
 * THE sensor life of a source that doesn't say all of it (contracts/sources/sensor-life.json, docs/44 decision 10):
 * the source's start unless it's ahead of now; a manual start only where the source may not say one
 * ([SourceLife.manualStart]), for your glucose, never ahead of now; the newer wins, the source's on a tie. The length is
 * the source's wear setting (or the default). The Mac's `SensorLives` is the same rule.
 */
object SensorLives {
    /** The id of a sensor the person started by hand: a new start is a new sensor (its reminders start again). */
    fun manualId(startS: Long) = "manual-$startS"

    /** The wear length to use (minutes): the setting, or the default when none is valid. */
    fun wearMin(setting: Int): Int = if (setting > 0) setting else SensorTiming.DEFAULT_WEAR_MIN

    fun of(
        life: SourceLife,
        person: Person?,
        said: SaidSensor?,
        wearMin: Int,
        manualStartS: Long?,
        nowS: Long,
    ): SensorSpan? {
        val source = said?.takeIf { it.startS <= nowS }
        val manual =
            manualStartS?.takeIf { life.manualStart && person == Person.Yours && it > 0 && it <= nowS }
        val (id, start, from) =
            when {
                source != null && (manual == null || source.startS >= manual) -> Triple(source.id, source.startS, LifeFrom.Source)
                manual != null -> Triple(manualId(manual), manual, LifeFrom.Manual)
                else -> return null
            }
        return SensorSpan(
            id,
            start,
            start + SensorTiming.DEFAULT_WARMUP_MIN * 60L,
            start + wearMin(wearMin) * 60L,
            from,
        )
    }

    /** [of] for [kind] with the person's [settings]. */
    fun of(
        kind: SourceKind,
        settings: LifeSettings,
        said: SaidSensor?,
        nowS: Long,
    ): SensorSpan? = of(kind.life, settings.person, said, settings.wearMin, settings.manualStartS.takeIf { it > 0 }, nowS)
}
