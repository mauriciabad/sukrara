// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.designsystem.alerts.AlertWords
import app.mauri.sukrara.designsystem.alerts.MuteText
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.Freshness
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.TrendMark
import app.mauri.sukrara.graph.stats.GlucosePoint
import app.mauri.sukrara.monitor.R
import java.util.Locale

/**
 * An alert notification's words in the app's language (the mute lines are [MuteText]'s): every case of
 * contracts/alerts/copy.json, the same words the Mac writes (AlertCopyTest).
 */
object AlertCopy {
    /**
     * The words this module asks [AlertWords] for by name, with their ids written here so resource shrinking keeps them
     * (a release build without them posted no alert: AlertWords.of, #181); `AlertCopyTest` checks every name asked is
     * here.
     */
    val Ids: Map<String, Int> =
        mapOf(
            "alert_headline_very_low" to R.string.alert_headline_very_low,
            "alert_headline_low" to R.string.alert_headline_low,
            "alert_headline_high" to R.string.alert_headline_high,
            "alert_headline_very_high" to R.string.alert_headline_very_high,
            "alert_headline_extremely_high" to R.string.alert_headline_extremely_high,
            "alert_headline_falling_fast" to R.string.alert_headline_falling_fast,
            "alert_headline_rising_fast" to R.string.alert_headline_rising_fast,
            "alert_headline_no_data" to R.string.alert_headline_no_data,
            "alert_headline_with_context" to R.string.alert_headline_with_context,
            "alert_context_eat_more" to R.string.alert_context_eat_more,
            "alert_context_eat_more_again" to R.string.alert_context_eat_more_again,
            "alert_context_after_insulin" to R.string.alert_context_after_insulin,
            "alert_context_after_more_insulin" to R.string.alert_context_after_more_insulin,
            "alert_context_no_response" to R.string.alert_context_no_response,
            "alert_context_maintenance" to R.string.alert_context_maintenance,
            "alert_text_no_data_maintenance" to R.string.alert_text_no_data_maintenance,
            "alert_text_value" to R.string.alert_text_value,
            "alert_text_value_at" to R.string.alert_text_value_at,
            "alert_text_glucose" to R.string.alert_text_glucose,
            "alert_text_lo" to R.string.alert_text_lo,
            "alert_text_hi" to R.string.alert_text_hi,
            "alert_text_falling" to R.string.alert_text_falling,
            "alert_text_rising" to R.string.alert_text_rising,
            "alert_text_no_reading" to R.string.alert_text_no_reading,
            "alert_text_no_data" to R.string.alert_text_no_data,
            "alert_text_no_data_last" to R.string.alert_text_no_data_last,
            "alert_kind_very_low" to R.string.alert_kind_very_low,
            "alert_kind_low" to R.string.alert_kind_low,
            "alert_kind_high" to R.string.alert_kind_high,
            "alert_kind_very_high" to R.string.alert_kind_very_high,
            "alert_kind_extremely_high" to R.string.alert_kind_extremely_high,
            "alert_kind_falling_fast" to R.string.alert_kind_falling_fast,
            "alert_kind_rising_fast" to R.string.alert_kind_rising_fast,
            "alert_kind_no_data" to R.string.alert_kind_no_data,
        )

    /**
     * The title (SPEC §1 "Headline"): the kind, and for a level alert the answering treatments logged in the episode
     * (`· eat more`, `· still high after insulin`; highs never suggest more insulin).
     */
    fun headline(
        kind: AlertKind,
        treated: Int,
        w: AlertWords,
        /** #39: a follower's very low nobody answered on the wearer's phone ("Very low glucose · no response"). */
        escalated: Boolean = false,
        /** LibreView is down for maintenance (docs/45): no data says why ("No data · LibreView maintenance"). */
        maintenance: Boolean = false,
    ): String {
        val base = w.get("alert_headline_${kind.wire}")
        if (escalated) return w.get("alert_headline_with_context").format(base, w.get("alert_context_no_response"))
        if (maintenance && kind == AlertKind.NO_DATA) {
            return w.get("alert_headline_with_context").format(base, w.get("alert_context_maintenance"))
        }
        val context =
            when {
                treated <= 0 || !kind.isLevel -> return base
                kind.side == Side.LOW -> if (treated == 1) "alert_context_eat_more" else "alert_context_eat_more_again"
                else -> if (treated == 1) "alert_context_after_insulin" else "alert_context_after_more_insulin"
            }
        return w.get("alert_headline_with_context").format(base, w.get(context))
    }

    /**
     * An alert notification's body (registered glyph surface `alert-notification`, contracts/graph/surfaces): the value
     * and arrow (`69 mg/dL ↘`), with the rate for falling / rising fast, or for no data when the last reading came and
     * its value. The value's look is the card's ([GlucoseCard.face]) for the reading at [now]: once it is late the
     * reading's time follows (`69 mg/dL ↘ · 14:05`), from 5 min it is struck through ([AlertBody.strikeTo]) with the
     * ring for its trend. Never a made-up number: without a reading it says so (no "0 mg/dL"), and without a rate the
     * fast alerts show only the value. Numbers in the person's [unit] (`3,8 mmol/L`, `0,13 mmol/L/min`) with
     * [locale]'s decimal separator.
     */
    fun body(
        f: Firing,
        w: AlertWords,
        unit: GlucoseUnit,
        locale: Locale = Locale.getDefault(),
        now: Long = f.reading?.t ?: f.at,
        maintenanceEnds: String? = null,
    ): String = bodyOf(f, w, unit, locale, now, maintenanceEnds).text

    /** [body], with where its strike-through ends. */
    fun bodyOf(
        f: Firing,
        w: AlertWords,
        unit: GlucoseUnit,
        locale: Locale = Locale.getDefault(),
        now: Long = f.reading?.t ?: f.at,
        /** LibreView's maintenance end as the card writes a time (copy.json `why: maintenance`), null = none known. */
        maintenanceEnds: String? = null,
    ): AlertBody {
        val r = f.reading
        if (f.kind == AlertKind.NO_DATA) {
            // a source that never delivered a reading: nothing to count from but its start, nothing to show
            if (r == null) return AlertBody(w.get("alert_text_no_data"))
            // when it came, not "for 20 min": a time never goes out of date on a notification left up
            val at = Freshness.timeText(r.t, now, locale)
            val last = value(r, null, w, unit, locale)
            if (maintenanceEnds != null) return AlertBody(w.get("alert_text_no_data_maintenance").format(maintenanceEnds, at, last))
            return AlertBody(w.get("alert_text_no_data_last").format(at, last))
        }
        if (r == null) return AlertBody(w.get("alert_text_no_reading"))
        val look = GlucoseCard.face(GlucosePoint(r.t, r.mgdl, r.rate, r.arrowBand), now, null, CardSurface.ALERT_NOTIFICATION, unit, locale)
        val shown = value(r, look.text, w, unit, locale)
        val value = w.get("alert_text_value").format(shown, look.arrowText ?: TrendMark.RING)
        // a struck value's rate is as old as it is: the ring says so, no "falling 2.4" beside it
        val rate = r.rate?.takeIf { !look.strike }?.let { unit.rateText(kotlin.math.abs(it), locale) }
        val text =
            when {
                rate == null -> value
                f.kind == AlertKind.FALLING_FAST -> w.get("alert_text_falling").format(value, rate, unit.rateSymbol)
                f.kind == AlertKind.RISING_FAST -> w.get("alert_text_rising").format(value, rate, unit.rateSymbol)
                else -> value
            }
        val timed = if (look.fresh.showTime) w.get("alert_text_value_at").format(text, Freshness.timeText(r.t, now, locale)) else text
        return AlertBody(timed, strikeTo = if (look.strike) shown.length else 0)
    }

    /**
     * `69 mg/dL` ([number] = the look's, else the reading's own: no data's last value), or the sensor's LO / HI with what
     * it means, the bound in [unit] (never its stored 39 / 401).
     */
    private fun value(
        r: AlertReading,
        number: String?,
        w: AlertWords,
        unit: GlucoseUnit,
        locale: Locale,
    ): String {
        fun glucose(n: String) = w.get("alert_text_glucose").format(n, unit.symbol)
        return when {
            r.isLo -> w.get("alert_text_lo").format(glucose(unit.text(GlucoseRange.FLOOR, locale)))
            r.isHi -> w.get("alert_text_hi").format(glucose(unit.text(GlucoseRange.CEILING, locale)))
            else -> glucose(number ?: unit.text(r.mgdl, locale))
        }
    }
}

/** An alert notification's body: its [text] and how much of it, from the start, is struck through (the value). */
data class AlertBody(
    val text: String,
    val strikeTo: Int = 0,
)
