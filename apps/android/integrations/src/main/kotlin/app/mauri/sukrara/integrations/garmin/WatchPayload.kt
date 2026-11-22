// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.model.AlertQuietSide
import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.KindFacts
import app.mauri.sukrara.core.model.KindOrder
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SensorTiming
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.LastPlace
import app.mauri.sukrara.graph.LastTreatment
import app.mauri.sukrara.graph.MarkShow
import app.mauri.sukrara.graph.RingSpec
import app.mauri.sukrara.graph.SlotKind
import app.mauri.sukrara.graph.TirMark
import app.mauri.sukrara.graph.TrendAngle
import app.mauri.sukrara.graph.WatchDisplay
import app.mauri.sukrara.graph.cardInfo
import app.mauri.sukrara.graph.id
import app.mauri.sukrara.graph.muteRuleOf
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.GlucoseMetrics
import app.mauri.sukrara.graph.stats.Goal
import app.mauri.sukrara.graph.stats.TimeInRange
import app.mauri.sukrara.graph.stats.TirRange
import app.mauri.sukrara.integrations.CurrentSensor
import app.mauri.sukrara.settings.SettingsSpec
import java.time.Instant
import kotlin.math.abs
import kotlin.math.floor

/**
 * Everything a payload is built from, one snapshot for every profile. [treatments] = the stored entries of the last
 * [WatchPayload.LOG_DAYS] days (any order); [unit], [bands] and [countdownHours] = the person's settings; [tir] = today's
 * time in range (share 0…1, null = not enough data) and [dayEnd] = when today ends on this phone (the face drops [tir]
 * then); [sensor] = the wearer's sensor with its life (the sensor screen's: its start and end, null on a follower or
 * before one is paired). The app's: [graph] = the person's metric settings, [metrics] = the report numbers per window
 * (s) as `GlucoseMetrics.of` gives them, [logOpen] = this phone takes entries now (its Add treatment is offered),
 * [kinds] = what THE kinds' order is decided from now (the phone's Add treatment's facts: `log.order`).
 */
data class PayloadInputs(
    val state: GlucoseState,
    val treatments: List<Treatment>,
    val now: Instant,
    val unit: GlucoseUnit,
    val bands: Bands,
    val countdownHours: Int,
    val tir: Double?,
    val dayEnd: Instant,
    val sensor: CurrentSensor? = null,
    val graph: GraphConfig = GraphConfig(),
    val metrics: Map<Long, GlucoseMetrics> = emptyMap(),
    val logOpen: Boolean = false,
    val kinds: KindFacts = KindFacts.NONE,
    /** The watch's own values for the app (the app profile only; nothing own = the app's). */
    val watch: WatchDisplay = WatchDisplay(),
    /** How long an entry logged now quiets the alerts (the phone's Add treatment's quiet line). */
    val quiet: WatchQuiet = WatchQuiet.NONE,
)

/**
 * What Sukrara's Connect IQ apps get from the phone, contracts/watchface/payload (version [VERSION]), one profile per
 * kind of app ([WatchProfile]: every watch face, the watch app, the data field): the same decisions the card and the glucose notification draw (the hub's [GlucoseState]: THE
 * trend, the alert runtime's status and what it rings, the sensor, the card's last treatment and metrics), with
 * absolute times so the watch keeps every age and countdown right between messages, and only what that app draws (the
 * face is nearly full; the app's background has 64 KB). Pure: [of] builds the Monkey C value tree (maps with string
 * keys, lists, Int, Float, Boolean, String, null) that [MonkeyC.encode] writes.
 */
object WatchPayload {
    /** The format's version (`v`); an app ignores a message of another version or another profile (`p`). */
    const val VERSION = 5

    /** History slots before the newest reading, per profile: the face and the field 3 h (their sparklines), the app 24 h. */
    fun history(profile: WatchProfile) =
        when (profile) {
            WatchProfile.Face -> 36
            WatchProfile.App -> 288
            WatchProfile.Field -> 36
        }

    /** History step and the tolerance a reading may be off its slot (s). */
    const val STEP_S = 300L
    private const val SLACK_S = 150L

    /** `until` for a mute with no end. */
    const val NEVER = -1

    /** The app's `tx`: the treatments of this many seconds before `sent`, at most [TX_MAX]. */
    const val TX_S = 86_400L
    const val TX_MAX = 64

    /** `log`'s last and usual amounts look back this many days. */
    const val LOG_DAYS = 30L

    /** `ranges`: time in range over the last day and the last week (rolling, as the card's rings). */
    val RANGES = listOf(86_400L, 7 * 86_400L)

    /** A metric shows a number with this much of its window covered (the card's `GlucoseCard.MIN_COVERAGE`). */
    const val METRIC_COVERAGE = 0.25

    /** The windows the app's metrics need, for [settings]' slots and every watch's own ([watches]). */
    fun windows(
        settings: GraphConfig,
        watches: Collection<WatchDisplay> = emptyList(),
    ): Set<Long> =
        (listOf(settings) + watches.map { it.applyTo(settings) })
            .flatMap { g -> g.rings().map { it.windowS } + listOfNotNull(g.lastElseRing()?.windowS) }
            .plus(RANGES + REPORT_DAYS.map { it * 86_400L })
            .toSet()

    /** The payload of [profile] from [i] at [PayloadInputs.now] (the app's push adds `key`: [GarminLink]). */
    fun of(
        profile: WatchProfile,
        i: PayloadInputs,
    ): Map<String, Any?> {
        val state = i.state
        val nowS = i.now.epochSecond
        val monitoring = state.notMonitoring == null
        val latest = state.latest.takeIf { monitoring }
        val b = i.bands.sane()
        val p =
            linkedMapOf(
                "v" to VERSION,
                "p" to profile.key,
                "sent" to nowS.toInt(),
                "unit" to i.unit.key,
                "bands" to listOf(b.veryLow, b.low, b.tight, b.high, b.veryHigh, b.extremelyHigh),
                "g" to latest?.let(::glucose),
                "hist" to (latest?.let { history(it, state.recent, history(profile)) } ?: emptyList<Int?>()),
                "st" to status(state.alerts.status).takeIf { monitoring },
            )
        if (profile !=
            WatchProfile.Face
        ) {
            // what the wrist says: what rang with the phone locked and is still up, unseen (#248, the runtime's wristKinds)
            p["ring"] = if (monitoring) ring(state.alerts.wristKinds, state.alerts.quiet) else emptyList<Any?>()
        }
        if (profile == WatchProfile.Field) return p
        if (profile == WatchProfile.Face) {
            // the face's treatment field is its own: always the card's rule, whatever the card's place says
            p["last"] = last(state, i.treatments, nowS).takeIf { monitoring }
            p["sensor"] = sensor(state, i.countdownHours, i.sensor)
            p["tir"] = i.tir?.takeIf { monitoring }?.let { linkedMapOf("pct" to percent(it), "until" to i.dayEnd.epochSecond.toInt()) }
            return p
        }
        val metrics = if (monitoring) i.metrics else emptyMap()
        // the display settings as this watch shows them, resolved in one place: the last treatment follows them too
        val d = GarminDisplay.resolve(i.graph, i.watch)
        p["last"] = last(state, i.treatments, nowS).takeIf { monitoring && d.graph.lastPlace != LastPlace.OFF }
        p["sensor"] = sensor(state, i.countdownHours, i.sensor)
        p["tx"] = tx(if (monitoring) i.treatments else emptyList(), nowS)
        p["ranges"] = RANGES.map { w -> metrics[w]?.takeIf { it.tir.coverage >= METRIC_COVERAGE }?.let { range(w, it) } }
        p["m"] = d.graph.rings().map { metric(it, metrics[it.windowS]) }
        p["else"] =
            d.graph
                .lastElseRing()
                ?.takeIf { d.graph.lastPlace != LastPlace.OFF }
                ?.let { metric(it, metrics[it.windowS]) }
        p["ro"] = d.graph.ringOrder.map { it.ordinal }
        p["log"] = if (monitoring && i.logOpen) log(i.treatments, i.kinds, nowS, i.quiet) else null
        p["ds"] = display(d)
        p["rep"] = REPORT_DAYS.map { d -> metrics[d * 86_400L]?.takeIf { !it.empty }?.let { report(d, it) } }
        p["repD"] = d.reportDays
        return p
    }

    /** The Numbers page's periods (days) and the one it opens on (Android's report card: 1 / 7 / 14 / 30, from 14). */
    val REPORT_DAYS: List<Long> = SettingsSpec.OPTIONS.getValue("reportPeriod").map(String::toLong)

    private fun display(d: GarminDisplay): Map<String, Any?> =
        linkedMapOf(
            "hours" to d.hours,
            "marks" to MarkShow.KINDS.associateWith { k -> MarkShow.parts(k).filter { d.marks.has(k, it) } },
            "side" to d.side,
            "crown" to d.crown,
        )

    private fun report(
        days: Long,
        m: GlucoseMetrics,
    ): Map<String, Any?> =
        linkedMapOf(
            "d" to days.toInt(),
            "cov" to Math.round(m.tir.coverage.coerceIn(0.0, 1.0) * 1000).toInt(),
            "b" to perMille(m.tir),
            "avg" to Math.round(m.mean).toInt(),
            "gmi" to (Math.round(m.gmi * 10) / 10.0).toFloat(),
            "cv" to (Math.round(m.cv * 10) / 10.0).toFloat(),
            // the report card's goals as it decides them, one bit each in Goal's order: the watch holds no thresholds
            "ok" to Goal.entries.foldIndexed(0) { i, bits, g -> if (g.met(m)) bits or (1 shl i) else bits },
        )

    /** What the alert runtime rings, in the kinds' order, each with its side. */
    internal fun ring(
        firing: Set<String>,
        sides: List<AlertQuietSide>,
    ): List<Map<String, Any?>> =
        RING_KINDS.filter { it.first in firing }.map { (k, side) ->
            val q = sides.firstOrNull { it.kind == k }
            // relaxed: it reminds less often now (Remind as usual undoes it); less: Remind less would slow it
            linkedMapOf("k" to k, "side" to side, "relaxed" to ((q?.relaxMin ?: 0) > 0), "less" to (q?.canRemindLess == true))
        }

    private val RING_KINDS =
        listOf(
            "very_low" to "low",
            "low" to "low",
            "high" to "high",
            "very_high" to "high",
            "extremely_high" to "high",
            "falling_fast" to "low",
            "rising_fast" to "high",
            "no_data" to "none",
        )

    private val LOGGED = setOf(TreatmentKind.RapidInsulin, TreatmentKind.LongInsulin, TreatmentKind.Carbs)

    private fun live(
        treatments: List<Treatment>,
        nowS: Long,
    ) = treatments.filter { !it.deleted && it.kind in LOGGED && it.at.epochSecond <= nowS }

    /**
     * The treatments of the [TX_S] before [nowS], oldest first, the newest [TX_MAX], as four lists side by side (`t`,
     * `k`, `a`, and `i` = the first [ID_CHARS] characters of the id, which the watch finds its own entries by): a
     * dictionary per entry would be twice the bytes, and the app's background decodes it in 64 KB.
     */
    internal fun tx(
        treatments: List<Treatment>,
        nowS: Long,
    ): Map<String, Any?> {
        val day =
            live(treatments, nowS)
                .filter { nowS - it.at.epochSecond < TX_S }
                .sortedWith(compareBy<Treatment> { it.at }.thenBy { it.id })
                .takeLast(TX_MAX)
        return linkedMapOf(
            "t" to day.map { it.at.epochSecond.toInt() },
            "k" to day.map { it.kind.id },
            "a" to day.map { round2(it.amount) },
            "i" to day.map { it.id.take(ID_CHARS) },
        )
    }

    /** How much of an entry's id `tx` carries. */
    const val ID_CHARS = 8

    private fun range(
        w: Long,
        m: GlucoseMetrics,
    ): Map<String, Any?> = linkedMapOf("w" to w.toInt(), "pct" to percent(m.tir.inRange), "b" to perMille(m.tir))

    private fun perMille(t: TimeInRange) =
        listOf(
            t.veryLow,
            t.low,
            t.tight,
            t.target,
            t.high,
            t.veryHigh,
        ).map { Math.round(it * 1000).toInt() }

    /** One of the card's metrics: its number (null under [METRIC_COVERAGE]), and a ring's range, shares and reward. */
    internal fun metric(
        spec: RingSpec,
        m: GlucoseMetrics?,
    ): Map<String, Any?> {
        val ok = m?.takeIf { it.tir.coverage >= METRIC_COVERAGE }
        val out =
            linkedMapOf<String, Any?>(
                "k" to
                    when (spec.kind) {
                        SlotKind.TIR -> "tir"
                        SlotKind.GMI -> "gmi"
                        SlotKind.AVERAGE -> "avg"
                        SlotKind.CV -> "cv"
                    },
                "w" to spec.windowS.toInt(),
                "v" to
                    ok?.let {
                        when (spec.kind) {
                            SlotKind.TIR -> percent(it.tir.of(spec.range))
                            SlotKind.GMI -> (Math.round(it.gmi * 10) / 10.0).toFloat()
                            SlotKind.AVERAGE -> Math.round(it.mean).toInt()
                            SlotKind.CV -> Math.round(it.cv).toInt()
                        }
                    },
                "dim" to spec.faded,
            )
        if (spec.kind == SlotKind.TIR) {
            out["r"] = if (spec.range == TirRange.TIGHT) "tight" else "in"
            out["b"] = ok?.let { perMille(it.tir) }
            out["mark"] = (ok?.takeIf { spec.marks }?.let { TirMark.of(it.tir, spec.range) } ?: TirMark.NONE).name.lowercase()
        }
        return out
    }

    /**
     * What logging offers: `order` = THE kinds' order at [nowS] ([KindOrder] over [kinds], as the phone's Add treatment
     * focuses), and per kind the newest amount of the last [LOG_DAYS] days and up to three amounts logged most often
     * then (ties: the most recently logged), smallest first; `q` = the side an entry of it logged now quiets and for how
     * long ([quiet], the Add treatment's quiet line), null for long-acting or a rule that's off.
     */
    internal fun log(
        treatments: List<Treatment>,
        kinds: KindFacts,
        nowS: Long,
        quiet: WatchQuiet = WatchQuiet.NONE,
    ): Map<String, Any?> {
        val recent = live(treatments, nowS).filter { nowS - it.at.epochSecond <= LOG_DAYS * 86_400L }
        val out = linkedMapOf<String, Any?>("order" to KindOrder.of(kinds, nowS).map { it.id })
        return LOG_ORDER.associateTo(out) { kind ->
            val mine = recent.filter { it.kind == kind }
            val usual =
                mine
                    .groupBy { round2(it.amount) }
                    .entries
                    .sortedWith(
                        compareByDescending<Map.Entry<Float, List<Treatment>>> { it.value.size }.thenByDescending { e ->
                            e.value.maxOf { it.at }
                        },
                    ).take(3)
                    .map { it.key }
                    .sorted()
            val q =
                when (kind) {
                    TreatmentKind.RapidInsulin -> quiet.afterInsulinMin?.let { side(low = false) to it }
                    TreatmentKind.Carbs -> quiet.afterCarbsMin?.let { side(low = true) to it }
                    else -> null
                }?.takeIf { it.second > 0 }
            kind.id to
                linkedMapOf(
                    "last" to mine.maxByOrNull { it.at }?.let { round2(it.amount) },
                    "usual" to usual,
                    "q" to q?.let { (s, min) -> linkedMapOf("side" to s, "min" to min) },
                )
        }
    }

    private val LOG_ORDER = listOf(TreatmentKind.RapidInsulin, TreatmentKind.LongInsulin, TreatmentKind.Carbs)

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
     * Slot i of [slots] (oldest first) holds the reading nearest to `latest − 300 s · (slots − i)` within 150 s; a gap is null.
     * LO / HI travel as 39 / 401, as everywhere.
     */
    internal fun history(
        latest: Reading,
        recent: List<Reading>,
        slots: Int,
    ): List<Int?> {
        val t0 = latest.t.epochSecond
        return (slots downTo 1).map { k ->
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
            val items = listOfNotNull(info, info.carbs)
            return linkedMapOf(
                "t" to items.maxOf { it.tr.at.epochSecond }.toInt(),
                // each with its own time and its mute's end: the card's double ring (Info.left) at every draw
                "items" to
                    items.map {
                        linkedMapOf(
                            "k" to it.tr.kind.id,
                            "a" to round2(it.tr.amount),
                            "t" to
                                it.tr.at.epochSecond
                                    .toInt(),
                            "m" to (it.until?.toInt() ?: NEVER),
                        )
                    },
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
