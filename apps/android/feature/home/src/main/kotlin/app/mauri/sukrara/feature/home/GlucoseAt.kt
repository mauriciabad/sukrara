// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import app.mauri.sukrara.core.model.Reading
import java.time.Duration
import java.time.Instant

/**
 * The glucose an entry was logged at (#207, docs/37): the reading nearest to its time within [WINDOW] either side, the
 * earlier one on a tie; null when none is that close (a gap, the sensor warming up, an entry ahead of now). Its trend
 * is the reading's own ([Reading.trend], THE trend from TrendReadings): nothing here works one out.
 */
object GlucoseAt {
    /**
     * 5 minutes: one reading interval of a 5-min sensor (Libre 2, Dexcom), so every entry gets a value while readings
     * arrive, and the age from which the card strikes a value: an older one no longer says what glucose was then.
     */
    val WINDOW: Duration = Duration.ofMinutes(5)

    /** [readings] oldest first (home's history). */
    fun of(
        at: Instant,
        readings: List<Reading>,
    ): Reading? {
        if (readings.isEmpty()) return null
        // the first reading at or after [at]; the one before it is the other candidate
        var lo = 0
        var hi = readings.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (readings[mid].t < at) lo = mid + 1 else hi = mid
        }
        val before = readings.getOrNull(lo - 1)
        val after = readings.getOrNull(lo)
        val best =
            listOfNotNull(before, after).minByOrNull { Duration.between(it.t, at).abs() } ?: return null
        return best.takeIf { Duration.between(it.t, at).abs() <= WINDOW }
    }
}
