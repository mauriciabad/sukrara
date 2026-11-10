// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.sensor.libre3

import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.sensor.libre3.Libre3Protocol.VALID_MGDL

/**
 * What one Libre 3 glucose packet's current value is: a number, the sensor's LO / HI, or no value.
 *
 * The capped reading word (packet bytes 2–3) carries more than the number (DiaBLE `Libre3.swift`
 * `parseOneMinuteReading` / `parseHistoricalPackets`, Juggluco `libre3/bluetooth.cpp`):
 * - bits 0–12: the capped value in mg/dL,
 * - bits 13–14: the result range (0 in range, 1 below, 2 above, 3 no range); Juggluco's `8231` (0x2027) is a
 *   below-range 39,
 * - bit 15: the data-quality error (0x8000; 0xA000 too hot, 0xC000 too cold): the Libre app shows no value.
 *
 * Rules, safest first: a data-quality error is never a value (not even a LO / HI); a range flag counts only when the
 * numbers don't say otherwise (below: capped value at most 40 or 0, uncapped none or at most 40; above: capped at least
 * 400 or 0, uncapped none or at least 400), a contradiction is no value; without a flag the value rule stands
 * (uncapped and capped both below 40 → LO, above 400 → HI). Once a real LO / HI packet confirms the flags, LO / HI can
 * require them (#132).
 */
internal sealed interface Libre3Value {
    /** A reading in the sensor's range, [mgdl] from the uncapped value when it is one. */
    data class Number(val mgdl: Int) : Libre3Value

    /** Below the sensor's range. */
    data object Lo : Libre3Value

    /** Above the sensor's range; [mgdl] is the sensor's own number, kept for LibreView. */
    data class Hi(val mgdl: Int) : Libre3Value

    /** Not a measurement: [why] is logged. */
    data class None(val why: String) : Libre3Value

    companion object {
        const val VALUE_MASK = 0x1FFF
        const val DQ_ERROR = 0x8000
        private const val RANGE_SHIFT = 13
        const val RANGE_IN = 0
        const val RANGE_BELOW = 1
        const val RANGE_ABOVE = 2

        /** [capped] and [uncapped] are the packet's raw little-endian words (bytes 2–3 and 15–16). */
        fun decode(capped: Int, uncapped: Int): Libre3Value {
            if (capped and DQ_ERROR != 0) return None("data quality error 0x%04x".format(capped))
            val value = capped and VALUE_MASK
            val unc = uncapped.takeIf { it in VALID_MGDL }
            return when ((capped shr RANGE_SHIFT) and 0x3) {
                RANGE_IN -> {
                    byValue(value, uncapped)
                }

                RANGE_BELOW -> {
                    if (value <= GlucoseRange.FLOOR &&
                        (unc == null || unc <= GlucoseRange.FLOOR)
                    ) {
                        Lo
                    } else {
                        None("below-range flag with $value / $uncapped")
                    }
                }

                RANGE_ABOVE -> {
                    val agrees = (value == 0 || value >= GlucoseRange.CEILING) && (unc == null || unc >= GlucoseRange.CEILING)
                    if (agrees) {
                        Hi(
                            unc ?: value.takeIf { it > GlucoseRange.CEILING } ?: GlucoseRange.HI,
                        )
                    } else {
                        None("above-range flag with $value / $uncapped")
                    }
                }

                else -> {
                    None("no-range flag 0x%04x".format(capped))
                }
            }
        }

        private fun byValue(capped: Int, uncapped: Int): Libre3Value {
            val mgdl = if (uncapped in VALID_MGDL) uncapped else capped
            // below the range: only when the uncapped AND the capped value both say so (one odd word is no LO)
            val belowRange = mgdl !in VALID_MGDL && uncapped in 1 until GlucoseRange.FLOOR && capped in 1..GlucoseRange.FLOOR
            return when {
                belowRange -> Lo
                mgdl !in VALID_MGDL -> None("out-of-range glucose")
                GlucoseRange.isLo(mgdl) -> Lo
                GlucoseRange.isHi(mgdl) -> Hi(mgdl)
                else -> Number(mgdl)
            }
        }
    }
}
