// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

/**
 * THE sensor-life numbers on Android: the card's badge and warm-up (`:core:graph` SensorLife), the alert engine's quiet
 * `no_data` (`:core:alerts` SensorWindow) and the sensor source (`:sensor` SensorLife) all decide with these, so the badge
 * and the alerts never disagree about a warm-up. The Mac's `SukraraCore` SensorTiming has the same numbers; both apps'
 * tests check them against contracts/sensor/life.json.
 */
object SensorTiming {
    /** The warm-up state lasts until the first reading, at most this long past the warm-up's end (s). */
    const val WARMUP_GRACE_S = 15 * 60L

    /** The warm-up when the sensor doesn't say (minutes). */
    const val DEFAULT_WARMUP_MIN = 60

    /** The wear length when the sensor doesn't say (minutes): 15 days. */
    const val DEFAULT_WEAR_MIN = 15 * 24 * 60

    /**
     * Warming up at [now] (epoch s): the sensor started ([startS]), no reading since its start ([latestT] = the newest
     * reading's time, null = none) and not more than [WARMUP_GRACE_S] past the warm-up's end ([warmupEndS]).
     */
    fun warming(
        startS: Long,
        warmupEndS: Long,
        latestT: Long?,
        now: Long,
    ): Boolean = now >= startS && now < warmupEndS + WARMUP_GRACE_S && (latestT == null || latestT < startS)
}
