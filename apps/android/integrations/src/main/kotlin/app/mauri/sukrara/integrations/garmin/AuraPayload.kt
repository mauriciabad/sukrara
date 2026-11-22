// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SensorTiming
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.graph.LastTreatment
import app.mauri.sukrara.graph.TrendAngle
import app.mauri.sukrara.graph.cardInfo
import app.mauri.sukrara.graph.id
import app.mauri.sukrara.graph.muteRuleOf
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.integrations.CurrentSensor
import java.time.Instant
import kotlin.math.abs
import kotlin.math.floor

/**
 * What Sukrara Aura (the Garmin face) gets from the phone, contracts/watchface/payload (version [VERSION]): the same
 * decisions the card and the glucose notification draw (the hub's [GlucoseState]: THE trend, the alert runtime's
 * status, the sensor), with absolute times so the watch keeps every age and countdown right between messages. Pure:
 * [of] builds the Monkey C value tree (maps with string keys, lists, Int, Float, Boolean, String, null) that
 * [MonkeyC.encode] writes.
 */
object AuraPayload {
    /** The format's version (`v`); a face ignores a message of another version. */
    const val VERSION = 3

    /** History slots before the newest reading (3 hours at 5 min: the sparkline's longest reach, SPEC.md §6.5). */
    const val HISTORY = 36

    /** History step and the tolerance a reading may be off its slot (s). */
    const val STEP_S = 300L
    private const val SLACK_S = 150L

    /** `until` for a mute with no end. */
    const val NEVER = -1

    /**
     * The payload for [state] at [now]. [treatments] = the stored entries (newest or oldest first); [unit], [bands] and
     * [countdownHours] = the person's settings; [tir] = today's time in range (share 0…1, null = not enough data) and
     * [dayEnd] = when today ends on this phone (the watch drops [tir] then); [sensor] = the wearer's sensor with its life
     * (the sensor screen's: its start and end, null on a follower or before one is paired), for the face's sensor rail.
     */
    fun of(
        state: GlucoseState,
        treatments: List<Treatment>,
        now: Instant,
        unit: GlucoseUnit,
        bands: Bands,
        countdownHours: Int,
        tir: Double?,
        dayEnd: Instant,
        sensor: CurrentSensor? = null,
    ): Map<String, Any?> {
        val nowS = now.epochSecond
        val monitoring = state.notMonitoring == null
        val latest = state.latest.takeIf { monitoring }
        val b = bands.sane()
        return linkedMapOf(
            "v" to VERSION,
            "sent" to nowS.toInt(),
            "unit" to unit.key,
            "bands" to listOf(b.veryLow, b.low, b.tight, b.high, b.veryHigh, b.extremelyHigh),
            "g" to latest?.let(::glucose),
            "hist" to (latest?.let { history(it, state.recent) } ?: emptyList<Int?>()),
            "st" to status(state.alerts.status).takeIf { monitoring },
            "last" to last(state, treatments, nowS).takeIf { monitoring },
            "sensor" to sensor(state, countdownHours, sensor),
            "tir" to tir?.takeIf { monitoring }?.let { linkedMapOf("pct" to percent(it), "until" to dayEnd.epochSecond.toInt()) },
        )
    }

    private fun glucose(r: Reading): Map<String, Any?> {
        val rate = r.rate?.takeIf { !r.isLo && !r.isHi }
        return linkedMapOf(
            "t" to r.t.epochSecond.toInt(),
            "v" to r.mgdl,
            "rate" to rate?.let { round2(it) },
            "band" to rate?.let { r.trend?.band },
            "deg" to rate?.let { round2(TrendAngle.deg(it).toDouble()) },
        )
    }

    /**
     * Slot i (oldest first) holds the reading nearest to `latest − 300 s · (HISTORY − i)` within 150 s; a gap is null.
     * LO / HI travel as 39 / 401, as everywhere.
     */
    internal fun history(
        latest: Reading,
        recent: List<Reading>,
    ): List<Int?> {
        val t0 = latest.t.epochSecond
        return (HISTORY downTo 1).map { k ->
            val at = t0 - STEP_S * k
            recent
                .filter { abs(it.t.epochSecond - at) <= SLACK_S }
                .minWithOrNull(compareBy<Reading> { abs(it.t.epochSecond - at) }.thenBy { it.t })
                ?.mgdl
        }
    }

    /** The alert runtime's answer behind the card's pill ([CardStatus]); null = no pill. */
    internal fun status(s: CardStatus): Map<String, Any?>? =
        when (s) {
            CardStatus.None -> {
                null
            }

            is CardStatus.Needed -> {
                linkedMapOf("k" to "needed", "side" to side(s.lowSide))
            }

            is CardStatus.Taken -> {
                linkedMapOf("k" to "taken", "side" to side(s.treatment == TreatmentKind.Carbs), "until" to until(s.until))
            }

            is CardStatus.Paused -> {
                linkedMapOf("k" to "paused", "side" to side(s.lowSide), "until" to until(s.until))
            }

            is CardStatus.Muted -> {
                linkedMapOf("k" to "muted", "until" to until(s.until))
            }
        }

    private fun side(low: Boolean) = if (low) "low" else "high"

    private fun until(s: Long): Int = if (s >= CardStatus.NEVER) NEVER else s.toInt()

    /**
     * The face's treatment field: the card's slot beside the value (THE rule, [LastTreatment.of], as the widget and the
     * card draw it) at [nowS], `{t, items, until, next}`: `until` = when that answer ends with nothing new logged (−1
     * never), `next` = the answer from then on (the same shape; null = nothing), so the face moves on by itself with
     * the phone out of reach. The answer can only change where one of the rule's windows ends (an entry's keep, its
     * quiet, the alert's Taken); there [LastTreatment.of] is asked again over the same entries (one dated later than
     * [nowS] waits for the next message, as on the card), the Taken status counted as over from its end.
     */
    internal fun last(
        state: GlucoseState,
        treatments: List<Treatment>,
        nowS: Long,
    ): Map<String, Any?>? {
        val rule = muteRuleOf(state.alerts)
        val status = state.alerts.status
        val taken = status as? CardStatus.Taken
        val logged = treatments.filter { !it.deleted && it.at.epochSecond <= nowS }

        fun at(s: Long): Map<String, Any?>? {
            val st = if (taken != null && s >= taken.until) CardStatus.None else status
            val info = LastTreatment.of(logged, rule, st, s) ?: return null
            val items = listOfNotNull(info.tr, info.carbs?.tr)
            return linkedMapOf(
                "t" to items.maxOf { it.at.epochSecond }.toInt(),
                "items" to items.map { linkedMapOf("k" to it.kind.id, "a" to round2(it.amount)) },
            )
        }
        val ends =
            logged
                .flatMap { listOfNotNull(LastTreatment.keepS(it)?.let { k -> it.at.epochSecond + k }, rule?.muteUntil(it)) }
                .plus(listOfNotNull(taken?.until?.takeIf { it < CardStatus.NEVER }))
                .filter { it > nowS }
                .distinct()
                .sorted()
        val steps = ArrayList<MutableMap<String, Any?>>()
        var open = at(nowS)?.toMutableMap() ?: return null
        for (end in ends) {
            val then = at(end)
            if (then == open) continue
            steps += open.apply { put("until", end.toInt()) }
            open = then?.toMutableMap() ?: break
        }
        if (steps.lastOrNull() !== open) steps += open.apply { put("until", NEVER) }
        return steps.foldRight(null as Map<String, Any?>?) { step, next -> step.apply { put("next", next) } }
    }

    /**
     * The sensor as the card decides it ([cardInfo]), with absolute times: `warmup` from `start` (ready at `ready`, the
     * state held until a reading after `start` or until `hold`; its life's `end` when known), `active` (its life's
     * `start` when known, `end` and `warn`, the start of the countdown's last [countdownHours] hours, when known),
     * `ended`, or `none` (not monitoring: nothing paired). The life ([life], the sensor screen's dates) counts only for
     * the same sensor: one whose serial differs from the hub's is a sensor that is no longer there.
     */
    internal fun sensor(
        state: GlucoseState,
        countdownHours: Int,
        life: CurrentSensor? = null,
    ): Map<String, Any?> {
        if (state.notMonitoring != null) return linkedMapOf("s" to "none")
        val s = state.sensor
        val info = s.cardInfo()
        val id = (s as? SensorState.Active)?.id ?: (s as? SensorState.WarmingUp)?.id
        val dates = life?.takeIf { id == null || it.serial == id }
        return when {
            s == SensorState.Ended -> {
                linkedMapOf("s" to "ended")
            }

            s is SensorState.WarmingUp && info != null -> {
                linkedMapOf(
                    "s" to "warmup",
                    "start" to info.startS.toInt(),
                    "ready" to info.warmupEndS.toInt(),
                    "hold" to (info.warmupEndS + SensorTiming.WARMUP_GRACE_S).toInt(),
                ).apply { dates?.let { put("end", it.end.epochSecond.toInt()) } }
            }

            info != null -> {
                linkedMapOf<String, Any?>("s" to "active").apply {
                    dates?.let { put("start", it.start.epochSecond.toInt()) }
                    put("end", info.endS.toInt())
                    put("warn", (info.endS - countdownHours * 3600L).toInt())
                }
            }

            else -> {
                linkedMapOf("s" to "active")
            }
        }
    }

    /** Today's share in range as a whole percent, never 100 unless every reading was in range (the card's crown). */
    internal fun percent(share: Double): Int = if (share >= 1.0) 100 else floor(share * 100).toInt().coerceIn(0, 99)

    private fun round2(x: Double): Float = (Math.round(x * 100) / 100.0).toFloat()
}
