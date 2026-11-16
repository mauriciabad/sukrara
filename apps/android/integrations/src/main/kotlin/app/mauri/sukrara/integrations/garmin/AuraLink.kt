// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import app.mauri.sukrara.graph.GraphSettings
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.GlucoseMetrics
import app.mauri.sukrara.graph.stats.toPoint
import app.mauri.sukrara.integrations.localserver.HttpAnswer
import app.mauri.sukrara.integrations.localserver.HttpRequest
import app.mauri.sukrara.integrations.localserver.LocalHttpServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One watch as the link sees it. [installed]: Sukrara Aura's version there, null = not installed or unknown. */
data class AuraWatch(
    val id: Long,
    val name: String,
    val connected: Boolean,
    val installed: Boolean?,
    /** The last message Garmin Connect said reached it, and the reading it carried. */
    val delivered: Instant? = null,
    /** The last message that didn't arrive (failed or no answer) after [delivered]. */
    val failed: Instant? = null,
    /** Garmin Connect took messages but never answered (its messaging bug): the face then fetches every 5 min. */
    val noAnswer: Boolean = false,
)

/**
 * One background delivery as the face saw it: [kind] `p` (a push), `f` (its fetch), `e<code>` (a fetch that failed),
 * with `x` when it kept the older payload; [at] the watch's clock; [sent] when the phone built that payload; [readingAt]
 * its reading's time (epoch s, 0 = none). After the deliveries come the face's screen events in the same shape (docs/43
 * "The screen"): [kind] `S` / `X` / `E` / `C` (onShow, onExitSleep, onEnterSleep, onDisplayModeChanged) or `Da` / `Dn`
 * / `Dw` (it drew always-on / night / awake), [sent] the display mode then, [readingAt] 0.
 */
data class AuraRun(
    val kind: String,
    val at: Long,
    val sent: Long,
    val readingAt: Long,
)

/**
 * The face's own account, sent with each fetch: its last deliveries ([runs], oldest first) and the reading it draws
 * ([drawn], its time on the watch clock) since [drawnSince]; [at] = when the phone got it.
 */
data class AuraReport(
    val at: Instant,
    val runs: List<AuraRun>,
    val drawn: Instant?,
    val drawnSince: Instant?,
) {
    /**
     * The newest reading the face kept (its time): what it shows at the next wrist raise. [drawn] only moves while the
     * screen draws, so a dark screen leaves it behind.
     */
    val has: Instant?
        get() =
            runs
                .filter { !it.kind.endsWith("x") && it.readingAt > 0 }
                .maxByOrNull { it.sent }
                ?.let { Instant.ofEpochSecond(it.readingAt) }
                ?: drawn

    companion object {
        /** `r=p.<at>.<sent>.<readingAt>_f.…&d=<readingAt>.<since>` (AuraLink.mc saveReport); null when there is none. */
        fun parse(
            query: Map<String, String>,
            at: Instant,
        ): AuraReport? {
            val r = query["r"] ?: return null
            val runs =
                r.split('_').mapNotNull { run ->
                    val f = run.split('.')
                    if (f.size != 4) return@mapNotNull null
                    val n = f.drop(1).map { it.toLongOrNull() ?: return@mapNotNull null }
                    AuraRun(f[0], n[0], n[1], n[2])
                }
            val d = query["d"]?.split('.')?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 2 }
            return AuraReport(at, runs, d?.let { Instant.ofEpochSecond(it[0]) }, d?.let { Instant.ofEpochSecond(it[1]) })
        }
    }
}

/**
 * Everything Settings › Share with apps › Sukrara Aura shows: the switch ([on], default on), whether Garmin Connect is
 * there and answers, each watch, and when the face last fetched from this phone ([fetched]: proof it is the watch face
 * and the link works).
 */
data class AuraLinkState(
    val on: Boolean = true,
    val garminConnect: Boolean = false,
    /** Garmin Connect's Connect IQ service answered the last time it was asked; null = not asked yet. */
    val answering: Boolean? = null,
    val watches: List<AuraWatch> = emptyList(),
    val fetched: Instant? = null,
    /** Another app holds the face's fetch port. */
    val portBusy: Boolean = false,
    /** The face's fetch port is open (only while a watch is known to have the face). */
    val fetchOpen: Boolean = false,
    /** What the face said it got and draws, with its last fetch. */
    val report: AuraReport? = null,
)

/**
 * The phone → Sukrara Aura link (docs/43): the payload ([AuraPayload], the same state the card and the glucose
 * notification draw) goes out
 *
 * 1. **pushed** through Garmin Connect to every connected watch with the face, on every change (a new reading, a
 *    status, a treatment, a setting), retried with backoff until a newer one replaces it, and again when a watch
 *    reconnects; the face's background wakes on it within seconds;
 * 2. **fetched** by the face itself every 5 min from 127.0.0.1:[PORT] (Garmin Connect's web proxy), in case Garmin
 *    Connect drops messages (its known bug) or one was lost. The port is open only while a watch is known to have the
 *    face ([fetchOpen]): any app on the phone could read it, so a phone without the face serves nothing.
 *
 * On by default whenever Garmin Connect is installed: nothing to set up. The switch (`apps.garminFace`) stops both.
 */
class AuraLink(
    private val settings: SettingsRepository,
    private val glucose: Flow<GlucoseState>,
    private val treatments: TreatmentsRepository,
    private val readings: ReadingsRepository,
    private val port: ConnectIqPort,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val log: (String) -> Unit,
    private val fetchPort: Int = PORT,
) {
    private val _state = MutableStateFlow(AuraLinkState())
    val state: StateFlow<AuraLinkState> = _state.asStateFlow()

    val on: Flow<Boolean> = settings.flow(SETTING)

    suspend fun setOn(on: Boolean) = settings.set(SETTING, on)

    @Volatile private var current: Map<String, Any?>? = null

    /** Asks again now (the page's *Send now*), whatever the backoff. */
    private val kick = MutableStateFlow(0)

    fun sendNow() = kick.update { it + 1 }

    private val server =
        LocalHttpServer(fetchPort, ::answer, onRequest = { req, a ->
            if (req.path == PATH && a.status.startsWith("200")) {
                val report = AuraReport.parse(req.query, clock.now())
                _state.update { it.copy(fetched = clock.now(), report = report ?: it.report) }
                // times only (never a value): what the face got and when, for adb logcat -s Sukrara.Aura
                report?.let { r ->
                    trace(
                        "face report: drawn ${r.drawn?.epochSecond} since ${r.drawnSince?.epochSecond}; runs ${r.runs.joinToString(
                            " ",
                        ) { "${it.kind}@${it.at}/sent ${it.sent}/reading ${it.readingAt}" }}",
                    )
                }
            }
        })

    /** Runs the link for as long as [scope] lives. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch { on.collect { o -> _state.update { it.copy(on = o) } } }
        // the payload, rebuilt on every change of what it carries
        val payloads = payloads().shareIn(scope, SharingStarted.Eagerly, 1)
        scope.launch { payloads.collect { current = it } }
        // Garmin Connect, checked every minute
        scope.launch {
            while (true) {
                val gcm = port.installed()
                _state.update { it.copy(garminConnect = gcm) }
                delay(CHECK_MS)
            }
        }
        // the fetch port: open only while the link is on, Garmin Connect is there and a watch is known to have the face
        // (Garmin Connect said so, or a push reached it): nobody else's glucose server on a phone without the face
        scope.launch {
            combine(on, _state) { o, s -> fetchOpen(o, s) }
                .distinctUntilChanged()
                .collect { open ->
                    withContext(io) {
                        if (open) {
                            val bound = server.running || server.bind()
                            _state.update { it.copy(fetchOpen = bound, portBusy = !bound) }
                        } else {
                            server.stop()
                            _state.update { it.copy(fetchOpen = false, portBusy = false) }
                        }
                    }
                }
        }
        // the push: every new payload (or a kick, or a watch coming back) to every watch with the face
        scope.launch {
            combine(on, payloads.debounce(SETTLE_MS), kick, watchTick()) { o, p, k, t -> Triple(o, p, k to t) }
                .distinctUntilChanged { a, b -> a.first == b.first && same(a.second, b.second) && a.third == b.third }
                .collectLatest { (o, p, _) -> if (o) withContext(io) { push(p) } }
        }
    }

    /** Ticks when a watch reconnects (checked every minute), so the current payload goes to it at once. */
    private fun watchTick(): Flow<Int> =
        flow {
            var n = 0
            var connected = emptySet<Long>()
            emit(n)
            while (true) {
                delay(CHECK_MS)
                val now =
                    port
                        .devices()
                        ?.filter { it.connected }
                        ?.map { it.id }
                        ?.toSet() ?: continue
                if ((now - connected).isNotEmpty()) emit(++n)
                connected = now
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun payloads(): Flow<Map<String, Any?>> {
        val unit = settings.flow(GraphSettings.GlucoseUnitSetting).map(GlucoseUnit::of)
        val bands =
            combine(
                GraphSettings.Bands.map(settings::flow),
            ) { v -> v.map(Double::toInt).let { Bands(it[0], it[1], it[2], it[3], it[4], it[5]) } }
        val hours = settings.flow(SurfaceSettings.CountdownHours).map { it.toInt() }
        // today's readings (for time in range) and the last day's treatments, the windows moving on every 10 min
        val since =
            flow {
                while (true) {
                    emit(clock.now())
                    delay(Duration.ofMinutes(10).toMillis())
                }
            }
        val today = since.flatMapLatest { readings.window(startOfDay(it)) }
        val entries = since.flatMapLatest { treatments.since(it - Duration.ofHours(25)) }
        return combine(glucose, entries, combine(unit, bands, hours) { u, b, h -> Triple(u, b, h) }, today) { g, t, (u, b, h), day ->
            build(g, t, u, b, h, day.map { it.toPoint() })
        }.distinctUntilChanged(::same)
    }

    private fun build(
        g: GlucoseState,
        t: List<Treatment>,
        unit: GlucoseUnit,
        bands: Bands,
        hours: Int,
        day: List<app.mauri.sukrara.graph.stats.GlucosePoint>,
    ): Map<String, Any?> {
        val now = clock.now()
        val from = startOfDay(now)
        val end = from.atZone(clock.zone).plusDays(1).toInstant()
        val tir = GlucoseMetrics.of(day, from.epochSecond, now.epochSecond, bands.sane()).tir
        val share = tir.inRange.takeIf { tir.coverage >= TIR_COVERAGE }
        return AuraPayload.of(g, t, now, unit, bands, hours, share, end)
    }

    private fun startOfDay(at: Instant): Instant = LocalDate.ofInstant(at, clock.zone).atStartOfDay(clock.zone).toInstant()

    /** Sends [p] to every connected watch with the face, retrying the ones that failed until a newer payload comes. */
    private suspend fun push(p: Map<String, Any?>) {
        var wait = RETRY_FIRST_MS
        while (true) {
            val devices = port.devices()
            _state.update { it.copy(garminConnect = port.installed(), answering = devices != null) }
            if (devices == null) return
            val sent = clock.now().epochSecond.toInt()
            val bytes = MonkeyC.encode(p + ("sent" to sent))
            val readingAt = (p["g"] as? Map<*, *>)?.get("t")
            var retry = false
            val watches =
                devices.map { d ->
                    val before = _state.value.watches.firstOrNull { it.id == d.id }
                    val installed = installed(d, before)
                    var w = AuraWatch(d.id, d.name, d.connected, installed, before?.delivered, before?.failed, before?.noAnswer ?: false)
                    if (d.connected && installed != false) {
                        val result = port.send(d, APP_ID, bytes)
                        trace("push sent $sent reading $readingAt: $result")
                        w =
                            when (result) {
                                IqSend.Delivered -> {
                                    w.copy(delivered = clock.now(), failed = null, noAnswer = false, installed = true)
                                }

                                IqSend.Failed -> {
                                    retry = true
                                    w.copy(failed = clock.now())
                                }

                                IqSend.NoAnswer -> {
                                    retry = true
                                    w.copy(failed = clock.now(), noAnswer = true)
                                }
                            }
                    }
                    w
                }
            _state.update { it.copy(watches = watches) }
            if (!retry) return
            delay(wait)
            wait = (wait * 2).coerceAtMost(RETRY_MAX_MS)
        }
    }

    /** Whether the face is on [d], asked again every [RECHECK_MS] or when the watch reconnects; null = unknown. */
    private suspend fun installed(
        d: IqDevice,
        before: AuraWatch?,
    ): Boolean? {
        val last = checked[d.id]
        val fresh = last != null && before?.connected == true && clock.now() < last + Duration.ofMillis(RECHECK_MS)
        if (fresh || !d.connected) return before?.installed
        val v = port.appVersion(d, APP_ID)
        checked[d.id] = clock.now()
        return when (v) {
            // no answer keeps what was known (unknown: still pushed to; the fetch opens once a push arrives)
            ConnectIqPort.NO_ANSWER -> before?.installed

            null -> false

            else -> true
        }
    }

    private val checked = HashMap<Long, Instant>()

    /** The fetch: `GET /sukrara/aura` → the current payload as JSON, `sent` now (the watch reads it like a push). */
    private fun answer(req: HttpRequest): HttpAnswer {
        if (req.path != PATH) return HttpAnswer.NOT_FOUND
        val p = current ?: return HttpAnswer("503 Service Unavailable", "{}")
        return HttpAnswer("200 OK", json(p + ("sent" to clock.now().epochSecond.toInt())).toString())
    }

    /** A link event in logcat (`Sukrara.Aura`) and the diagnostic log: times and states only, never a value. */
    private fun trace(line: String) {
        runCatching { android.util.Log.i(TAG, line) }
        log(line)
    }

    /** Stops the fetch port (the switch stays as it is). */
    fun stop() = server.stop()

    companion object {
        /** Sukrara Aura's Connect IQ app id (apps/garmin manifest). */
        const val APP_ID = "9ace188f-9a3a-42d6-9f7d-1afd4ce93293"

        /** The face's fetch: http://127.0.0.1:17582/sukrara/aura (Garmin Connect allows http only to loopback). */
        const val PORT = 17582
        const val PATH = "sukrara/aura"
        const val TAG = "Sukrara.Aura"

        val SETTING = SchemaSettings.bool("apps.garminFace", default = true)

        private const val CHECK_MS = 60_000L

        /** A reading changes the payload in steps (the hub, then today's readings): one message once it settles. */
        const val SETTLE_MS = 3_000L
        private const val RETRY_FIRST_MS = 15_000L
        private const val RETRY_MAX_MS = 120_000L
        private const val RECHECK_MS = 30 * 60_000L

        /** Today's time in range only with data for a quarter of the day so far (as the card's rings ask). */
        const val TIR_COVERAGE = 0.25

        /** Whether the fetch port may be open: on, Garmin Connect installed, a watch known to have the face. */
        internal fun fetchOpen(
            on: Boolean,
            s: AuraLinkState,
        ) = on && s.garminConnect && s.watches.any { it.installed == true }

        /** Two payloads say the same apart from when they were built. */
        internal fun same(
            a: Map<String, Any?>,
            b: Map<String, Any?>,
        ) = a - "sent" == b - "sent"

        /** The payload as JSON for the fetch: Garmin Connect parses it into the same dictionary a push gives. */
        internal fun json(v: Any?): JsonElement =
            when (v) {
                null -> JsonNull
                is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k as String to json(x) })
                is List<*> -> JsonArray(v.map(::json))
                is Boolean -> JsonPrimitive(v)
                is Int -> JsonPrimitive(v)
                is Float -> JsonPrimitive(v)
                is String -> JsonPrimitive(v)
                else -> error("not a payload value: $v")
            }
    }
}
