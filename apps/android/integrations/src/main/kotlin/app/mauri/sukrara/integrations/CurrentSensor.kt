// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations

import app.mauri.sukrara.core.model.SourceId
import java.time.Instant

/**
 * The wearer's current sensor as the integrations need it: its serial, life ([start], [end]), family ([source], the
 * tag its readings carry) and the wear length the sensor itself reports ([wearMin], minutes: a Libre 3's patch info,
 * kept when the end moves earlier; 0 = it reports none, e.g. read through Juggluco).
 */
data class CurrentSensor(
    val serial: String,
    val start: Instant,
    val end: Instant,
    val source: SourceId = SourceId.Libre3,
    val wearMin: Int = 0,
)
