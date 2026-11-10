// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * THE glucose unit of every number a person reads or types (setting `general.unit`; contracts/settings/units.json).
 * Values are stored, sent, alerted on and exported in mg/dL; mmol/L is only how they are shown and typed. Every
 * surface writes a glucose value or rate through [text] / [rateText], and every input turns a typed value back with
 * [stored]: one conversion and one format (the Mac's `GlucoseUnit` is the same).
 */
enum class GlucoseUnit(
    /** The stored setting value (schema option `unit`). */
    val key: String,
    /** "mg/dL" / "mmol/L". */
    val symbol: String,
    /** "mg/dL/min" / "mmol/L/min". */
    val rateSymbol: String,
    /** Decimals of a value. */
    val decimals: Int,
    /** Decimals of a rate. */
    val rateDecimals: Int,
) {
    MGDL("mgdl", "mg/dL", "mg/dL/min", 0, 1),
    MMOL("mmol", "mmol/L", "mmol/L/min", 1, 2),
    ;

    /** mg/dL (or mg/dL/min) as a number of this unit, rounded to [decimals] half away from zero. */
    fun shown(
        mgdl: Double,
        decimals: Int = this.decimals,
    ): Double = round(if (this == MMOL) mgdl / MGDL_PER_MMOL else mgdl, decimals)

    /** A number of this unit (typed or picked) → mg/dL on [step] (a threshold typed in mmol/L lands on whole mg/dL). */
    fun stored(
        value: Double,
        step: Double = 1.0,
    ): Double {
        val mgdl = if (this == MMOL) value * MGDL_PER_MMOL else value
        return (mgdl / step).roundToLong() * step
    }

    /** A value as written, without the unit: "112", "6.2" / "6,2" (the language's decimal separator). */
    fun text(
        mgdl: Int,
        locale: Locale,
    ): String = number(shown(mgdl.toDouble()), decimals, locale)

    /** A trend rate (mg/dL/min) as written in this unit per minute, without the unit: "2.0" / "0.11". */
    fun rateText(
        mgdlPerMin: Double,
        locale: Locale,
    ): String = number(shown(mgdlPerMin, rateDecimals), rateDecimals, locale)

    companion object {
        /** mg/dL per mmol/L: glucose's molar mass / 10 (units.json `factor`). */
        const val MGDL_PER_MMOL = 18.0182

        /** The unit of a stored setting value; anything else is mg/dL. */
        fun of(key: String?): GlucoseUnit = entries.firstOrNull { it.key == key } ?: MGDL

        private fun round(
            x: Double,
            decimals: Int,
        ): Double {
            val p = Math.pow(10.0, decimals.toDouble())
            val r = floor(abs(x) * p + 0.5) / p
            return if (x < 0 && r != 0.0) -r else r
        }

        private fun number(
            v: Double,
            decimals: Int,
            locale: Locale,
        ): String {
            val s = String.format(Locale.ROOT, "%.${decimals}f", v)
            return if (decimals == 0) s else s.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
        }
    }
}
