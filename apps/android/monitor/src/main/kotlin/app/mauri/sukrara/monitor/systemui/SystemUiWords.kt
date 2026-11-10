// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import android.content.res.Resources
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.graph.A11y
import app.mauri.sukrara.graph.CardModel
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.Freshness
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.SensorBadge
import app.mauri.sukrara.graph.SensorInfo
import app.mauri.sukrara.graph.TrendMark
import app.mauri.sukrara.graph.cardModelOf
import app.mauri.sukrara.graph.stats.Bands
import java.time.Instant
import app.mauri.sukrara.core.graph.R as GraphR

/**
 * What TalkBack says for Sukrara in System UI (#159), in the person's language: THE sentence ([A11y.value]), built
 * here in the app because System UI can't load the app's translations. The root icons carry it in their description
 * ([SlotIcons.Words]), the module's text gets it in the state ([HookState.spoken]). No age and no alert: the status bar
 * shows neither, so the words change only when the picture does.
 */
object SystemUiWords {
    /** The root icons' words for [state] at [nowS] (in mg/dL: the icons are drawables of the mg/dL numbers). */
    fun icons(
        state: GlucoseState,
        bands: Bands,
        nowS: Long,
        res: Resources,
    ): SlotIcons.Words {
        val model = model(state, nowS, CardWords.of(res))
        val sentence = A11y.value(model, bands, CardSurface.STATUS_ICONS, GlucoseUnit.MGDL, badge = false, age = false)
        val look = RootStatusBar.look(state, nowS)
        val trend =
            if (look.strike) {
                res.getString(GraphR.string.a11y_value_old)
            } else {
                model.words.trend(look.rate.takeIf { look.trend == TrendMark.ARROW })
            }
        return SlotIcons.Words(sentence, trend)
    }

    /**
     * The module text's sentences by [HookText.spokenKey], each after its mark ([HookText.mark]): for the face of
     * [hook] now, in the next two minutes (a warm-up's time left), when the warm-up ends and at every step the reading
     * reaches without a new one (struck at 5 min, no reading since at 20 min, the day from 12 h), so the module never
     * says an earlier face's words while the app is silent. A face the sentence wouldn't describe gets none (the module
     * then says its short English line).
     */
    fun text(
        state: GlucoseState,
        hook: HookState,
        bands: Bands,
        nowS: Long,
        res: Resources,
    ): Map<String, String> {
        val model = model(state, nowS, CardWords.of(res))
        val at = state.latest?.t?.epochSecond
        val steps = generateSequence(Freshness.nextChangeAt(at, nowS)) { Freshness.nextChangeAt(at, it) }.take(STEPS)
        val instants =
            (listOf(nowS, nowS + 60, nowS + 120) + listOfNotNull(hook.readyAtS.takeIf { it > nowS }) + steps).distinct()
        val out = LinkedHashMap<String, String>()
        for (t in instants) {
            val look = HookText.face(hook, t)
            val key = HookText.spokenKey(hook, look, t)
            if (key in out) continue
            val m = model.copy(nowS = t)
            val said =
                GlucoseCard.face(
                    m.latest,
                    t,
                    SensorBadge.look(m.sensor, t, m.latest?.t, m.words),
                    CardSurface.STATUS_HOOK,
                    hook.unit,
                )
            if (said.state != look.state || said.strike != look.strike) continue
            out[key] = HookText.mark(look) + A11y.value(m, bands, CardSurface.STATUS_HOOK, hook.unit, badge = false, age = false)
        }
        return out
    }

    /**
     * The card's model with the sensor as the status bar decides it ([HookText.sensorLook]: the hub's ended and
     * warming-up states, even without the sensor's dates), so the words say what the icons and the text show.
     */
    private fun model(
        state: GlucoseState,
        nowS: Long,
        words: CardWords,
    ): CardModel {
        val model = cardModelOf(state, emptyList(), Instant.ofEpochSecond(nowS), words)
        val latest = state.latest?.t?.epochSecond ?: 0
        val sensor =
            when (val s = state.sensor) {
                SensorState.Ended -> {
                    SensorInfo(startS = nowS - LIFE_S, endS = nowS)
                }

                is SensorState.WarmingUp -> {
                    val ready = s.readyAt.epochSecond
                    val start = maxOf(ready - WARMUP_S, latest + 1).coerceAtMost(nowS)
                    SensorInfo(startS = start, endS = start + LIFE_S, warmupEndS = ready)
                }

                else -> {
                    model.sensor
                }
            }
        return model.copy(sensor = sensor)
    }

    /** Freshness steps looked ahead: late, struck, no readings, no chart, the day, the date. */
    private const val STEPS = 6
    private const val LIFE_S = 15 * 86_400L
    private const val WARMUP_S = 3_600L
}
