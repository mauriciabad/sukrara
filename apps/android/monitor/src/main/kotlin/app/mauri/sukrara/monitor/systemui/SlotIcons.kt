// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import app.mauri.sukrara.graph.GlucoseLook
import app.mauri.sukrara.graph.TrendAngle
import app.mauri.sukrara.graph.TrendMark
import java.util.Locale

/**
 * The root status bar's system icons: two slots System UI draws itself (tinted like Wi-Fi and the battery) from
 * drawables in this APK, one per value (System UI's Compose status bar ignores the icon level). What they show is the
 * card's decision ([app.mauri.sukrara.graph.GlucoseCard.face] for `CardSurface.STATUS_ICONS`).
 *
 * What TalkBack says on them is THE sentence ([app.mauri.sukrara.graph.A11y], in the person's language, built by the
 * app: [Words]) after a mark that names Sukrara and tells the System UI module which icon it is ([isOurs], [isTrend],
 * [isValue]): System UI can't load this app's translations, so the mark is the only part the module reads, the same in
 * every language. "Sukrara: " before the number's sentence, "Sukrara, " before the trend's words, "Sukrara. " before
 * a single icon's (no readings, sensor ended); another app's icons never start so (#159).
 */
object SlotIcons {
    /** Slot names, distinct from any other app's (System UI keys icons by slot name). */
    const val SLOT_NUMBER = "sukrara_glucose"
    const val SLOT_TREND = "sukrara_glucose_trend"

    /** Our slots in the order they show: the number, then the trend. */
    val ORDER = listOf(SLOT_NUMBER, SLOT_TREND)

    /** The marks before the words: the number (and the module's text with a value), the trend, a single icon. */
    const val VALUE = "Sukrara: "
    const val TREND = "Sukrara, "
    const val SINGLE = "Sukrara. "

    /** One of our icons other than the trend beside a number, by its description. */
    fun isOurs(description: String) = description.startsWith(VALUE) || description.startsWith(SINGLE)

    /** The trend icon beside a number. */
    fun isTrend(description: String) = description.startsWith(TREND)

    /** A number icon (the module colours these by range). */
    fun isValue(description: String) = description.startsWith(VALUE)

    /**
     * What TalkBack says, in the person's language: [sentence] for the value (or why there is none: no reading since,
     * sensor ended), [trend] for the trend beside the number ("falling", "trend unknown", "old reading" when struck).
     */
    data class Words(
        val sentence: String,
        val trend: String,
    )

    /**
     * What the two slots show: drawable names (null = remove the slot) and what TalkBack says ([desc] on the number,
     * or on the single icon in the trend slot; [trendDesc] on the trend beside a number).
     */
    data class Plan(
        val trend: String?,
        val number: String?,
        val desc: String,
        val trendDesc: String = desc,
    ) {
        companion object {
            val EMPTY = Plan(null, null, "")
        }
    }

    /**
     * The icons for [look]: fresh or late = the number and the trend arrow (5° steps, a double arrow from ±3); from
     * 5 min the number struck through at half strength beside the struck "no trend" ring; from 20 min only the "no
     * readings" icon in the trend slot; an ended sensor the flag. A warming-up sensor shows nothing: there is no number
     * to draw yet (the notification and the card show the countdown). TalkBack says [words] after each icon's mark,
     * the range in words too, as the icons' colour shows it (#26: never by colour alone).
     */
    fun plan(
        look: GlucoseLook,
        words: Words,
    ): Plan =
        when (look.state) {
            GlucoseLook.State.ENDED -> {
                Plan("monitor_ic_sensor_ended", null, SINGLE + words.sentence)
            }

            GlucoseLook.State.NO_READINGS -> {
                Plan("monitor_ic_no_readings", null, SINGLE + words.sentence)
            }

            GlucoseLook.State.NONE, GlucoseLook.State.WARMING_UP -> {
                Plan.EMPTY
            }

            GlucoseLook.State.VALUE -> {
                val text = checkNotNull(look.text)
                // card-ok: the look's text → its drawable name
                val v = text.lowercase(Locale.ROOT)
                val number =
                    when {
                        look.strike -> "sb_s$v"
                        v == "lo" || v == "hi" -> "sb_$v"
                        else -> "sb_n$v"
                    }
                val rate = look.rate
                val trend =
                    when {
                        look.trend == TrendMark.ARROW && rate != null -> trendDrawable(rate)
                        look.strike -> "sb_t_none_s"
                        else -> "sb_t_none"
                    }
                Plan(trend, number, VALUE + words.sentence, TREND + words.trend)
            }
        }

    /** The arrow for [rate] (mg/dL/min): one drawable per band ([TrendAngle.step]), as the card draws it. */
    fun trendDrawable(rate: Double): String = TREND_DRAWABLES[TrendAngle.step(rate) + 3]

    /** The band's drawable, ⇊ … ⇈ (tools/android/gen-slot-drawables.swift). */
    val TREND_DRAWABLES = listOf("sb_t_down2", "sb_t_down", "sb_t_down45", "sb_t_flat", "sb_t_up45", "sb_t_up", "sb_t_up2")

    /**
     * The slot daemon's commands for [plan] ([resId] resolves a drawable name), in the order to send them: System UI
     * puts the newest external icon first (leftmost), so the trend goes before the number. Descriptions are one line.
     */
    fun commands(
        plan: Plan,
        resId: (String) -> Int,
    ): List<String> {
        fun line(s: String) = s.replace('\n', ' ').replace('\r', ' ')
        val trendDesc = if (plan.number == null) plan.desc else plan.trendDesc
        val trend = plan.trend?.let { "set $SLOT_TREND ${resId(it)} 0 ${line(trendDesc)}" } ?: "remove $SLOT_TREND"
        val number = plan.number?.let { "set $SLOT_NUMBER ${resId(it)} 0 ${line(plan.desc)}" } ?: "remove $SLOT_NUMBER"
        return listOf(trend, number)
    }
}
