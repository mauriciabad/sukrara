// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.graph.R
import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.GlucoseStats
import app.mauri.sukrara.graph.stats.TirRange

/**
 * THE sentence TalkBack says for the glucose card (#25), on every surface that shows the card or its value: the home
 * panel, the widgets, the notification card and the Quick Settings tile all take it from here. Most important first:
 * the value with its unit (once), "old reading" when struck, the trend, the range in words (never by colour alone;
 * [RangeState]'s words), the age; the alert or mute the pill shows; the slots; the sensor's time left where the card
 * shows it; the chart summary. No symbols that would be read as punctuation (arrows, "·", "<"). Built from the same
 * decisions the card draws ([GlucoseCard.face], [StatusBadge.look], [SensorBadge.look], [GlucoseCard.slotItems]);
 * contracts/graph/spoken holds the exact English and Spanish sentences, the Mac's A11y.swift says the same.
 */
object A11y {
    /**
     * The glucose sentence: "112 milligrams per decilitre, falling, in range, 2 min ago", or the reason there is no
     * value; then the alert or mute the status pill says, when a value is shown and the place has [badge] on. The value
     * and the LO / HI bounds in the person's [unit] ("6,2 milimoles por litro").
     */
    fun value(
        model: CardModel,
        bands: Bands,
        surface: CardSurface,
        unit: GlucoseUnit,
        badge: Boolean = true,
        age: Boolean = true,
    ): String {
        val w = model.words
        val now = model.nowS
        val r = model.latest
        val sensor = SensorBadge.look(model.sensor, now, r?.t, w)
        val face = GlucoseCard.face(r, now, sensor, surface, unit, w.locale)
        val glucose =
            when (face.state) {
                GlucoseLook.State.WARMING_UP, GlucoseLook.State.ENDED -> {
                    sensorState(checkNotNull(sensor), now, w)
                }

                GlucoseLook.State.NONE -> {
                    w.string(R.string.a11y_no_readings)
                }

                GlucoseLook.State.NO_READINGS -> {
                    w.string(R.string.a11y_no_reading_since, Freshness.timeText(checkNotNull(r).t, now))
                }

                GlucoseLook.State.VALUE -> {
                    val p = checkNotNull(r)
                    val said = w.string(if (unit == GlucoseUnit.MMOL) R.string.a11y_unit_mmol else R.string.a11y_unit_mgdl)
                    val bound = GlucoseRange.isLo(p.sgv) || GlucoseRange.isHi(p.sgv)

                    fun number(mgdl: Int) = w.string(R.string.a11y_value, unit.text(mgdl, w.locale), said)
                    listOfNotNull(
                        when {
                            GlucoseRange.isLo(p.sgv) -> w.string(R.string.a11y_below, number(GlucoseRange.FLOOR))
                            GlucoseRange.isHi(p.sgv) -> w.string(R.string.a11y_above, number(GlucoseRange.CEILING))
                            else -> number(p.sgv)
                        },
                        w.string(R.string.a11y_value_old).takeIf { face.strike },
                        w.trend(p.rate.takeIf { face.trend == TrendMark.ARROW }).takeUnless { face.strike },
                        range(p.sgv, bands, w).takeUnless { bound },
                        ago(now - p.t, w).takeIf { age },
                    ).joinToString(", ")
                }
            }
        val alert = status(model.status, now, w).takeIf { badge && face.state == GlucoseLook.State.VALUE }
        return sentences(w, listOfNotNull(glucose, alert))
    }

    /**
     * A new reading as a screen reader announces it (a polite live region beside the card): the glucose sentence of
     * [model]'s latest reading as it came in, "112 milligrams per decilitre, falling, in range": no age and no alert, so
     * the words stay the same until the next reading. Null without a reading.
     */
    fun reading(
        model: CardModel,
        style: CardStyle,
        surface: CardSurface,
    ): String? {
        val r = model.latest ?: return null
        return value(model.copy(nowS = r.t), style.bands.sane(), surface, style.unit, badge = false, age = false)
    }

    /** The chart summary: "Last 12 hours: 82% in range, 3% below, 15% above" (or "no data"). */
    fun chart(
        model: CardModel,
        style: GraphStyle,
    ): String {
        val hours = (style.windowS / 3600).toInt()
        val from = model.nowS - style.windowS
        val tir = GlucoseStats.timeInRange(model.readings, from, model.nowS, style.bands.sane())
        if (tir.empty) return model.words.string(R.string.a11y_chart_empty, hours)
        val (lo, inr, hi) = GlucoseGraph.pct3(tir.below, tir.inRange, tir.above)
        return model.words.string(R.string.a11y_chart, hours, inr, lo, hi)
    }

    /**
     * The whole card [style] on [surface]: the glucose sentence (a card without a value says how old its chart is, as
     * its pill does), the slots, the sensor's time left where the card shows it (in the chart's hour row or as the
     * countdown ring over the main slot), then the chart summary.
     */
    fun card(
        model: CardModel,
        style: CardStyle,
        surface: CardSurface,
    ): String {
        val w = model.words
        val now = model.nowS
        val bands = style.bands.sane()
        val sensor = SensorBadge.look(model.sensor, now, model.latest?.t, w)
        val first = if (style.value) value(model, bands, surface, style.unit, style.badge) else age(model, surface)
        val slots = if (style.value) GlucoseCard.slotItems(model, style, MAX_SLOTS) else emptyList()
        val spoken = slots.mapNotNull { slot(it, now, w) }
        val life =
            sensor?.takeIf { s ->
                val fr = Freshness.of(model.latest?.let { now - it.t })
                GlucoseCard.sensorSpot(style, s, fr) == SensorSpot.GRAPH ||
                    (
                        s.warmup == null && !s.ended && slots.isNotEmpty() &&
                            SensorLife.countdownFraction(s.end, now, model.countdownHours) != null
                    )
            }
        val parts = listOfNotNull(first) + spoken + listOfNotNull(life?.let { sensorLeft(it.end - now, w) })
        return sentences(w, parts + listOfNotNull(style.graph?.let { chart(model, it) }))
    }

    /**
     * A card without a value says how old its chart is, as the picture does ([Freshness.AgeMark]): "last reading 9:50"
     * at every age, "old reading" too from 5 min, "no reading since 9:50" from 20 min (and once the chart is gone), "no
     * readings yet" without any; a sensor that ended or is warming up says so, as the value's place does.
     */
    private fun age(
        model: CardModel,
        surface: CardSurface,
    ): String? {
        val w = model.words
        val now = model.nowS
        val r = model.latest
        val sensor = SensorBadge.look(model.sensor, now, r?.t, w)
        // the age only: no number is said, so the unit doesn't matter
        val face = GlucoseCard.face(r, now, sensor, surface, GlucoseUnit.MGDL)
        if (face.state == GlucoseLook.State.WARMING_UP ||
            face.state == GlucoseLook.State.ENDED
        ) {
            return sensorState(checkNotNull(sensor), now, w)
        }
        r ?: return w.string(R.string.a11y_no_readings)
        val time = Freshness.timeText(r.t, now)
        return when (Freshness.of(now - r.t).ageMark) {
            Freshness.AgeMark.NONE -> null
            Freshness.AgeMark.TIME -> w.string(R.string.a11y_last_reading, time)
            Freshness.AgeMark.DIMMED -> w.string(R.string.a11y_last_reading, time) + ", " + w.string(R.string.a11y_value_old)
            Freshness.AgeMark.NO_READINGS, Freshness.AgeMark.FACE -> w.string(R.string.a11y_no_reading_since, time)
        }
    }

    /**
     * What the status pill says, in full words: the action an alert asks for, or the mute and its time left. Nothing
     * when the card draws no pill ([StatusBadge.look]: none, or a mute that has ended).
     */
    fun status(
        s: CardStatus,
        now: Long,
        w: CardWords,
    ): String? {
        StatusBadge.look(s, now, w) ?: return null
        return when (s) {
            CardStatus.None -> {
                null
            }

            is CardStatus.Needed -> {
                if (s.lowSide) w.eatCarbs else w.takeInsulin
            }

            is CardStatus.Taken -> {
                if (s.treatment == TreatmentKind.Carbs) {
                    left(R.plurals.a11y_low_muted_after_carbs, s.until, now, w)
                } else {
                    left(R.plurals.a11y_high_muted_after_insulin, s.until, now, w)
                }
            }

            is CardStatus.Paused -> {
                when {
                    s.until >= CardStatus.NEVER && s.lowSide -> w.string(R.string.a11y_low_muted_forever)
                    s.until >= CardStatus.NEVER -> w.string(R.string.a11y_high_muted_forever)
                    s.lowSide -> left(R.plurals.a11y_low_muted_left, s.until, now, w)
                    else -> left(R.plurals.a11y_high_muted_left, s.until, now, w)
                }
            }

            is CardStatus.Muted -> {
                if (s.until >= CardStatus.NEVER) {
                    w.string(R.string.a11y_muted_forever)
                } else {
                    left(R.plurals.a11y_muted_left, s.until, now, w)
                }
            }
        }
    }

    /** A drawn slot in words ([metric], [last]); null for a ring without data (the card draws none). */
    private fun slot(
        item: SlotItem,
        now: Long,
        w: CardWords,
    ): String? =
        when (item) {
            is SlotItem.Metric -> {
                if (item.m.empty) {
                    null
                } else {
                    val text =
                        if (item.spec.kind ==
                            SlotKind.TIR
                        ) {
                            TirRing.text(item.m.tir, item.spec)
                        } else {
                            MetricGauge.text(item.spec.kind, item.m, item.unit, w.locale).first
                        }
                    metric(item.spec.kind, item.spec.range, item.spec.windowS, text, w)
                }
            }

            is SlotItem.Last -> {
                last(item.info, now, w)
            }
        }

    /**
     * A report slot by the number it draws ([text]: "86", "6.7", "<0.1"): "86% in range over 1 day", "GMI 6.7% over 14
     * days", "Average 141 over 1 day" (the unit is said once, by the value), "Variability 37% over 1 day".
     */
    internal fun metric(
        kind: SlotKind,
        range: TirRange,
        windowS: Long,
        text: String,
        w: CardWords,
    ): String {
        val number =
            when {
                text.startsWith("<") -> w.string(R.string.a11y_less_than, w.decimal(text.drop(1)))
                text.startsWith(">") -> w.string(R.string.a11y_more_than, w.decimal(text.drop(1)))
                else -> w.decimal(text)
            }
        val period = period(windowS, w)
        return sentence(
            w,
            when (kind) {
                SlotKind.TIR -> {
                    w.string(
                        if (range ==
                            TirRange.TIGHT
                        ) {
                            R.string.a11y_slot_very_good
                        } else {
                            R.string.a11y_slot_in_range
                        },
                        number,
                        period,
                    )
                }

                SlotKind.GMI -> {
                    w.string(R.string.a11y_slot_gmi, number, period)
                }

                SlotKind.AVERAGE -> {
                    w.string(R.string.a11y_slot_average, number, period)
                }

                SlotKind.CV -> {
                    w.string(R.string.a11y_slot_cv, number, period)
                }
            },
        )
    }

    /** The last-treatment slot: "Rapid insulin 3 units, 10 min ago", then the carbs drawn inside it. */
    internal fun last(
        info: LastTreatment.Info,
        now: Long,
        w: CardWords,
    ): String =
        listOfNotNull(info, info.carbs).joinToString(". ") { treatment(it.tr.k, it.tr.amount, now - it.tr.t, w) }

    /** One treatment: its kind, amount and age ("Carbs 45 grams, just now", "Note, 10 min ago"). */
    internal fun treatment(
        k: String,
        amount: Double,
        ageS: Long,
        w: CardWords,
    ): String {
        val number = w.decimal(TreatmentMarks.number(amount))
        val quantity = if (amount == 1.0) 1 else 2
        val age = ago(ageS, w)
        return when (k) {
            "rapid" -> w.string(R.string.a11y_last_rapid, w.plural(R.plurals.a11y_units, quantity, number), age)
            "long" -> w.string(R.string.a11y_last_long, w.plural(R.plurals.a11y_units, quantity, number), age)
            "carbs" -> w.string(R.string.a11y_last_carbs, w.plural(R.plurals.a11y_grams, quantity, number), age)
            else -> w.string(R.string.a11y_last_note, age)
        }
    }

    /** "Sensor ends in 13 days" (whole days from 24 h, as the badge), "… in 5 h", "… in 45 min". */
    private fun sensorLeft(
        s: Long,
        w: CardWords,
    ): String {
        val min = s.coerceAtLeast(0) / 60
        val left = if (min >= 1440) w.plural(R.plurals.a11y_days, (min / 1440).toInt(), min / 1440) else Durations.text(min, w.durations)
        return w.string(R.string.a11y_sensor_ends, left)
    }

    /** The sensor's own states where the value goes: warming up (its time left) or ended. */
    private fun sensorState(
        look: SensorBadge.Look,
        now: Long,
        w: CardWords,
    ): String =
        when {
            look.ended -> w.string(R.string.a11y_sensor_ended)
            look.waiting(now) -> w.string(R.string.a11y_almost_ready)
            else -> left(R.plurals.a11y_warming_up, look.readyAt, now, w)
        }

    private fun range(
        sgv: Int,
        b: Bands,
        w: CardWords,
    ): String = RangeState.of(sgv, b)?.let { w.range(it).lowercase(w.locale) } ?: w.string(R.string.a11y_in_range)

    private fun ago(
        s: Long,
        w: CardWords,
    ): String = if (s < 60) w.string(R.string.a11y_just_now) else w.string(R.string.a11y_ago, Durations.text(s / 60, w.durations))

    /**
     * The sentence [id] with the time left until [until], partial minutes rounded up ("1 min" until it is over); its
     * plural agrees with that time ("queda 1 h" / "quedan 1 h 30 min").
     */
    private fun left(
        id: Int,
        until: Long,
        now: Long,
        w: CardWords,
    ): String {
        val min = ((until - now).coerceAtLeast(0) + 59) / 60
        return w.plural(id, Durations.textQuantity(min), Durations.text(min, w.durations))
    }

    /** A slot's window: "1 day", "7 days", "12 hours". */
    private fun period(
        windowS: Long,
        w: CardWords,
    ): String =
        if (windowS % 86_400 == 0L) {
            w.plural(R.plurals.a11y_days, (windowS / 86_400).toInt(), windowS / 86_400)
        } else {
            w.plural(R.plurals.a11y_hours, (windowS / 3_600).toInt(), windowS / 3_600)
        }

    private fun sentence(
        w: CardWords,
        s: String,
    ) = s.replaceFirstChar { it.titlecase(w.locale) }

    private fun sentences(
        w: CardWords,
        parts: List<String>,
    ) = parts.joinToString(". ") { sentence(w, it) }

    /** The most slots a card draws. */
    private const val MAX_SLOTS = 4
}
