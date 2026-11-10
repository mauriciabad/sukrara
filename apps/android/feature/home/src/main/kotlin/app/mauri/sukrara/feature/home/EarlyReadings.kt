// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.graph.cardInfo
import java.time.Instant

/**
 * A new sensor's first hours (#46): its readings can be less accurate (more noise, a lag while it settles in), so home
 * says so under the card until [HOURS] after the sensor's start, with what to do (a meter when a value doesn't match
 * how the person feels). The start is the one every surface derives from the sensor's end (`cardInfo`).
 */
object EarlyReadings {
    const val HOURS = 12L

    /** When the early hours end, while [now] is in them and the sensor sends readings (not warming up); else null. */
    fun until(
        sensor: SensorState,
        now: Instant,
    ): Instant? {
        if (sensor !is SensorState.Active) return null
        val start = sensor.cardInfo()?.startS ?: return null
        val end = start + HOURS * 3_600
        return Instant.ofEpochSecond(end).takeIf { now.epochSecond in start until end }
    }
}
