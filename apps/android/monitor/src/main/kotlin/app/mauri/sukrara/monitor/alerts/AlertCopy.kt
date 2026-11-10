// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.designsystem.alerts.AlertWords
import app.mauri.sukrara.designsystem.alerts.MuteText
import app.mauri.sukrara.graph.TrendAngle
import java.util.Locale

/**
 * An alert notification's words in the app's language (the mute lines are [MuteText]'s): every case of
 * contracts/alerts/copy.json, the same words the Mac writes (AlertCopyTest).
 */
object AlertCopy {
    /**
     * The title (SPEC §1 "Headline"): the kind, and for a level alert the answering treatments logged in the episode
     * (`· eat more`, `· still high after insulin`; highs never suggest more insulin).
     */
    fun headline(
        kind: AlertKind,
        treated: Int,
        w: AlertWords,
    ): String {
        val base = w.get("alert_headline_${kind.wire}")
        val context =
            when {
                treated <= 0 || !kind.isLevel -> return base
                kind.side == Side.LOW -> if (treated == 1) "alert_context_eat_more" else "alert_context_eat_more_again"
                else -> if (treated == 1) "alert_context_after_insulin" else "alert_context_after_more_insulin"
            }
        return w.get("alert_headline_with_context").format(base, w.get(context))
    }

    /**
     * An alert notification's body: the value and arrow (`69 mg/dL ↘`), with the rate for falling / rising fast, or for
     * no data how long and the last value. Never a made-up number: without a reading it says so (no "0 mg/dL"), and
     * without a rate the fast alerts show only the value. Numbers in the person's [unit] (`3,8 mmol/L`, `0,13
     * mmol/L/min`) with [locale]'s decimal separator.
     */
    fun body(
        f: Firing,
        w: AlertWords,
        unit: GlucoseUnit,
        locale: Locale = Locale.getDefault(),
    ): String {
        val r = f.reading
        if (f.kind == AlertKind.NO_DATA) {
            // a source that never delivered a reading: nothing to count from but its start, nothing to show
            if (r == null) return w.get("alert_text_no_data")
            val since = Durations.text(((f.at - r.t) / 60).coerceAtLeast(f.threshold.toLong()), w.durations)
            return w.get("alert_text_no_data_last").format(since, value(r, w, unit, locale))
        }
        if (r == null) return w.get("alert_text_no_reading")
        val value = w.get("alert_text_value").format(value(r, w, unit, locale), TrendAngle.text(r.rate))
        val rate = r.rate?.let { unit.rateText(kotlin.math.abs(it), locale) } ?: return value
        return when (f.kind) {
            AlertKind.FALLING_FAST -> w.get("alert_text_falling").format(value, rate, unit.rateSymbol)
            AlertKind.RISING_FAST -> w.get("alert_text_rising").format(value, rate, unit.rateSymbol)
            else -> value
        }
    }

    /** `69 mg/dL`, or the sensor's LO / HI with what it means, the bound in [unit] (never its stored 39 / 401). */
    private fun value(
        r: AlertReading,
        w: AlertWords,
        unit: GlucoseUnit,
        locale: Locale,
    ): String {
        fun glucose(mgdl: Int) = w.get("alert_text_glucose").format(unit.text(mgdl, locale), unit.symbol)
        return when {
            r.isLo -> w.get("alert_text_lo").format(glucose(GlucoseRange.FLOOR))
            r.isHi -> w.get("alert_text_hi").format(glucose(GlucoseRange.CEILING))
            else -> glucose(r.mgdl)
        }
    }
}
