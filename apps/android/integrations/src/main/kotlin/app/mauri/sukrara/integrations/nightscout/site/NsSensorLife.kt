// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SensorTiming
import app.mauri.sukrara.integrations.nightscout.site.NsTreatmentSync.time
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

/** A sensor a Nightscout site says started: its [id] (a new id = a new sensor) and [start]. */
data class NsSensor(
    val id: String,
    val start: Instant,
) {
    /** The warm-up's end: the site doesn't say, so the default (contracts/sensor/life.json). */
    val warmupEnd: Instant get() = start.plusSeconds(SensorTiming.DEFAULT_WARMUP_MIN * 60L)

    /** The end: the site doesn't say how long the sensor lasts, so the default wear. */
    val end: Instant get() = start.plusSeconds(SensorTiming.DEFAULT_WEAR_MIN * 60L)

    /** The hub's state of this sensor at [now], the newest reading taken at [latest]: warming, active or ended. */
    fun state(
        latest: Instant?,
        now: Instant,
    ): SensorState =
        when {
            !now.isBefore(end) -> {
                SensorState.Ended
            }

            SensorTiming.warming(start.epochSecond, warmupEnd.epochSecond, latest?.epochSecond, now.epochSecond) -> {
                SensorState.WarmingUp(warmupEnd, id)
            }

            else -> {
                SensorState.Active(end, id)
            }
        }
}

/**
 * The sensor's life from a Nightscout source (contracts/sources/nightscout/sensor-life.json, docs/44: Nightscout's
 * sensor life = its *Sensor Start*): the newest *Sensor Start* or *Sensor Change* that isn't deleted or ahead of now.
 */
object NsSensorLife {
    private val EVENTS = setOf("Sensor Start", "Sensor Change")

    fun of(
        docs: List<JsonObject>,
        now: Instant,
    ): NsSensor? =
        docs
            .asSequence()
            .filter { it.text("eventType") in EVENTS && it.bool("isValid") != false }
            .mapNotNull { d -> d.time()?.takeIf { !it.isAfter(now) }?.let { t -> Triple(d, t, d.text("eventType") == "Sensor Start") } }
            .maxWithOrNull(compareBy({ it.second }, { it.third }))
            ?.let { (d, t, _) -> (d.text("identifier") ?: d.text("_id"))?.let { NsSensor(it, t) } }

    private fun JsonObject.text(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it != JsonNull }?.contentOrNull

    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it != JsonNull }?.booleanOrNull
}
