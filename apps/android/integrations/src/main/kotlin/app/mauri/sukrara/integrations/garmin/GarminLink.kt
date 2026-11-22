// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.KindFacts
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphSettings
import app.mauri.sukrara.graph.WatchDisplay
import app.mauri.sukrara.graph.stats.Bands
import app.mauri.sukrara.graph.stats.GlucoseMetrics
import app.mauri.sukrara.graph.stats.toPoint
import app.mauri.sukrara.integrations.CurrentSensor
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
import kotlinx.coroutines.flow.flowOf
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

/**
 * One Sukrara app on one watch as the push sees it (one Connect IQ app id: the public app or its beta). [installed]:
 * Garmin Connect says it is there, null = unknown.
 */
data class AppLink(
    val installed: Boolean? = null,
    /** The last message Garmin Connect said reached it. */
    val delivered: Instant? = null,
    /** The last message that didn't arrive (failed or no answer) after [delivered]. */
    val failed: Instant? = null,
    /** Garmin Connect took messages but never answered (its messaging bug): the app then fetches every 5 min. */
    val noAnswer: Boolean = false,
    /** The time of the reading the last delivered message carried (null = none yet, or it carried none). */
    val reading: Instant? = null,
    /** The first message that didn't arrive since the last one that did. */
    val failingSince: Instant? = null,
    /** The last few deliveries, oldest first: a report is judged against the one made before it. */
    val deliveries: List<Delivery> = emptyList(),
) {
    /** No message has arrived for [GarminLink.STUCK] (#242). */
    fun stuck(now: Instant) = failingSince != null && now >= failingSince + GarminLink.STUCK

    /** [deliveries], or the last delivery alone when only it is known. */
    val recent: List<Delivery> get() = deliveries.ifEmpty { listOfNotNull(delivered?.let { Delivery(it, reading) }) }

    companion object {
        /** Deliveries kept: more than a report's 5 min at one push a minute. */
        const val KEPT = 8
    }
}

/** A message Garmin Connect said reached the app [at], carrying the reading of [reading] (null = none). */
data class Delivery(
    val at: Instant,
    val reading: Instant?,
)

/**
 * One watch as the link sees it: its Sukrara apps ([links], by Connect IQ app id) and, for the page's watch line, what
 * they add up to: [installed] = a Sukrara app is there (true), none is (false: every one Garmin Connect was asked about),
 * or unknown; the last delivery and failure to any of them; [failingSince] while none of them gets messages.
 */
data class GarminWatch(
    val id: Long,
    val name: String,
    val connected: Boolean,
    val installed: Boolean?,
    /** The last message Garmin Connect said reached it. */
    val delivered: Instant? = null,
    /** The last message that didn't arrive (failed or no answer) after [delivered]. */
    val failed: Instant? = null,
    /** Garmin Connect took messages but never answered (its messaging bug): the apps then fetch every 5 min. */
    val noAnswer: Boolean = false,
    /** The time of the reading the last delivered message carried (null = none yet, or it carried none). */
    val reading: Instant? = null,
    /** The first message that didn't arrive since the last one that did, while no app gets any. */
    val failingSince: Instant? = null,
    val links: Map<String, AppLink> = emptyMap(),
) {
    /**
     * No message has arrived for [GarminLink.STUCK]: Garmin Connect's link to the watch is stuck (#242: it can stay so for
     * a day, until the watch reconnects). Longer than the stalls Garmin Connect ends itself (within 5 min).
     */
    fun stuck(now: Instant) = failingSince != null && now >= failingSince + GarminLink.STUCK

    /** [app]'s link here: the id Garmin Connect says is installed, else the public one; null = never asked. */
    fun link(app: GarminApp): AppLink? =
        app.ids.mapNotNull { links[it] }.let { l ->
            l.firstOrNull { it.installed == true }
                ?: l.firstOrNull()
        }

    /** Whether [app] is on this watch (true / false / unknown); a watch known only by [installed] has the faces. */
    fun has(app: GarminApp): Boolean? {
        if (links.isEmpty()) return if (app.isFace) installed else null
        val l = app.ids.map { links[it]?.installed }
        return when {
            true in l -> true
            l.all { it == false } -> false
            else -> null
        }
    }

    companion object {
        /** The watch line from its apps' links (only the ones that may be there count). */
        fun of(
            id: Long,
            name: String,
            connected: Boolean,
            links: Map<String, AppLink>,
        ): GarminWatch {
            val there = links.values.filter { it.installed != false }
            val installed =
                when {
                    there.any { it.installed == true } -> true
                    links.isNotEmpty() && there.isEmpty() -> false
                    else -> null
                }
            val newest = there.filter { it.delivered != null }.maxByOrNull { it.delivered!! }
            val lastFail = there.filter { it.failed != null }.maxByOrNull { it.failed!! }
            val failing = there.filter { it.delivered != null || it.failed != null }
            val since = if (failing.isNotEmpty() && failing.all { it.failingSince != null }) failing.minOf { it.failingSince!! } else null
            return GarminWatch(
                id,
                name,
                connected,
                installed,
                newest?.delivered,
                lastFail?.failed?.takeIf { f -> newest?.delivered?.let { it < f } ?: true },
                lastFail?.noAnswer ?: false,
                newest?.reading,
                since,
                links,
            )
        }
    }
}

/**
 * One background delivery as the face saw it: [kind] `p` (a push), `f` (its fetch), `e<code>` (a fetch that failed),
 * with `x` when it kept the older payload; [at] the watch's clock; [sent] when the phone built that payload; [readingAt]
 * its reading's time (epoch s, 0 = none). After the deliveries come the face's screen events in the same shape (docs/43
 * "The screen"): [kind] `S` / `X` / `E` / `C` (onShow, onExitSleep, onEnterSleep, onDisplayModeChanged) or `Da` / `Dn`
 * / `Dw` (it drew always-on / night / awake) or `P…` (the night decision's raw facts), [sent] the display mode then,
 * [readingAt] 0.
 */
data class LinkRun(
    val kind: String,
    val at: Long,
    val sent: Long,
    val readingAt: Long,
)

/**
 * The face's own account, sent with each fetch: its last deliveries ([runs], oldest first) and the reading it draws
 * ([drawn], its time on the watch clock) since [drawnSince]; [at] = when the phone got it.
 */
data class LinkReport(
    val at: Instant,
    val runs: List<LinkRun>,
    val drawn: Instant?,
    val drawnSince: Instant?,
) {
    /**
     * Every message the app got in [runs] it kept the older payload for (`px` / `fx`: it can't read this phone's, a
     * payload version it doesn't know), and none it took: the app needs updating, not a restart.
     */
    val rejected: Boolean
        get() {
            val got = runs.filter { it.kind == "p" || it.kind == "f" || it.kind == "px" || it.kind == "fx" }
            return got.isNotEmpty() && got.all { it.kind.endsWith("x") }
        }

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
        /** `r=p.<at>.<sent>.<readingAt>_f.…&d=<readingAt>.<since>` (shared/link Link.mc saveReport); null when there is none. */
        fun parse(
            query: Map<String, String>,
            at: Instant,
        ): LinkReport? {
            val r = query["r"] ?: return null
            val runs =
                r.split('_').mapNotNull { run ->
                    val f = run.split('.')
                    if (f.size != 4) return@mapNotNull null
                    val n = f.drop(1).map { it.toLongOrNull() ?: return@mapNotNull null }
                    LinkRun(f[0], n[0], n[1], n[2])
                }
            val d = query["d"]?.split('.')?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 2 }
            return LinkReport(at, runs, d?.let { Instant.ofEpochSecond(it[0]) }, d?.let { Instant.ofEpochSecond(it[1]) })
        }
    }
}

/**
 * Everything Settings › Share with apps › Garmin shows: the switch ([on], default on), whether Garmin Connect is
 * there and answers, each watch, and when Sukrara's apps last fetched from this phone ([fetched] the newest, [fetchedBy]
 * per app: proof an app runs and the link works).
 */
data class GarminLinkState(
    val on: Boolean = true,
    val garminConnect: Boolean = false,
    /** Garmin Connect's Connect IQ service answered the last time it was asked; null = not asked yet. */
    val answering: Boolean? = null,
    val watches: List<GarminWatch> = emptyList(),
    val fetched: Instant? = null,
    /** Another app holds the fetch port. */
    val portBusy: Boolean = false,
    /** The fetch port is open (only while a watch is known to have a Sukrara app). */
    val fetchOpen: Boolean = false,
    /** What the watch face in use said it got and draws, with its last fetch (the face profile's). */
    val report: LinkReport? = null,
    /** Each profile's own account with its last fetch (the faces' is also [report]). */
    val reports: Map<WatchProfile, LinkReport> = emptyMap(),
    /** Each profile's last fetch: a watch face's, the watch app's, the data field's. */
    val fetchedBy: Map<WatchProfile, Instant> = emptyMap(),
    /** When the watch app on each watch last fetched (by the `w` its fetch sends back). */
    val appFetchedOn: Map<Long, Instant> = emptyMap(),
)

/**
 * Every profile's payload from one snapshot of the phone ([profiles]), and the app's for each watch with its own display
 * settings ([watches], by Garmin Connect's id for it: SPEC §11).
 */
data class Payloads(
    val profiles: Map<WatchProfile, Map<String, Any?>>,
    val watches: Map<Long, Map<String, Any?>> = emptyMap(),
) {
    operator fun get(profile: WatchProfile) = profiles[profile]

    /** What [profile] gets on watch [watch] (null = not known which): the app's profile with that watch's own values. */
    fun of(
        profile: WatchProfile,
        watch: Long?,
    ): Map<String, Any?>? = watch?.takeIf { profile == WatchProfile.App }?.let { watches[it] } ?: profiles[profile]
}

/**
 * The phone → Sukrara's Connect IQ apps link (docs/43, docs/47): each app's payload ([WatchPayload], its own profile of
 * the state the card and the glucose notification draw) goes out
 *
 * 1. **pushed** through Garmin Connect to each Sukrara app ([GarminApp], public or beta id) on every connected watch,
 *    on every change (a new reading, a status, a treatment, a setting, a ring), retried with backoff per app until a
 *    newer one replaces it, and again when a watch reconnects; the app's background wakes on it within seconds. The
 *    app's push carries the key its writes are signed with ([WatchKey]);
 * 2. **fetched** by each app itself every 5 min from 127.0.0.1:[PORT] (Garmin Connect's web proxy), in case Garmin
 *    Connect drops messages (its known bug) or one was lost. The port is open only while a watch is known to have a
 *    Sukrara app ([fetchOpen]): any app on the phone could read it, so a phone without one serves nothing. The same
 *    port takes the app's writes ([WatchWrites]: entries, mutes), each signed with the key.
 *
 * On by default whenever Garmin Connect is installed: nothing to set up. The switch (`apps.garmin`) stops all of it.
 */
class GarminLink(
    private val settings: SettingsRepository,
    private val glucose: Flow<GlucoseState>,
    private val treatments: TreatmentsRepository,
    private val readings: ReadingsRepository,
    private val port: ConnectIqPort,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val log: (String) -> Unit,
    private val fetchPort: Int = PORT,
    /** The wearer's sensor with its life (the sensor screen's dates): the face's sensor rail. */
    private val sensor: Flow<CurrentSensor?> = flowOf(null),
    /** The phone's own treatment entry, for the app's entries. */
    private val entries: WatchEntries = WatchEntries.None,
    /** The signing key; null = no writes (nothing is signed, nothing taken). */
    private val key: WatchKey? = null,
    /** The alert runtime's actions, for the app's mutes; null = none taken. */
    alerts: app.mauri.sukrara.core.model.AlertControls? = null,
    /** Each watch's own display settings for the app ([WatchSettingsStore]); a watch not listed follows the app. */
    private val watchDisplays: Flow<Map<Long, WatchDisplay>> = flowOf(emptyMap()),
) {
    private val _state = MutableStateFlow(GarminLinkState())
    val state: StateFlow<GarminLinkState> = _state.asStateFlow()

    val on: Flow<Boolean> = settings.flow(SETTING)

    suspend fun setOn(on: Boolean) = settings.set(SETTING, on)

    @Volatile private var current: Payloads? = null

    /** Asks again now (the page's *Send now*), whatever the backoff. */
    private val kick = MutableStateFlow(0)

    fun sendNow() = kick.update { it + 1 }

    /** The last *Send now* / reconnect the push saw, and whether a try is owed for a newer one (even while stuck). */
    private var trigger: Pair<Int, Int>? = null

    @Volatile private var force = false

    val writes = WatchWrites({ key?.get() }, entries, alerts, clock, ::trace)

    private val server =
        LocalHttpServer(
            fetchPort,
            ::answer,
            onRequest = { req, a ->
                val profile = WatchProfile.ofPath(req.path)
                val at = clock.now()
                val report = LinkReport.parse(req.query, at)
                // another watch face may read the face profile too (payload README "Other watch faces"): only a fetch
                // with Sukrara's report is one of Sukrara's faces
                val ours = profile != WatchProfile.Face || report != null
                if (profile != null && ours && a.status.startsWith("200")) {
                    _state.update {
                        it.copy(
                            fetched = at,
                            fetchedBy = it.fetchedBy + (profile to at),
                            appFetchedOn =
                                req.query["w"]
                                    ?.toLongOrNull()
                                    ?.takeIf { profile == WatchProfile.App }
                                    ?.let { w -> it.appFetchedOn + (w to at) } ?: it.appFetchedOn,
                            report = if (profile == WatchProfile.Face) report ?: it.report else it.report,
                            reports = report?.let { r -> it.reports + (profile to r) } ?: it.reports,
                        )
                    }
                    // times only (never a value): what the app got and when, for adb logcat -s Sukrara.Garmin
                    report?.let { r ->
                        val runs = r.runs.joinToString(" ") { "${it.kind}@${it.at}/sent ${it.sent}/reading ${it.readingAt}" }
                        trace("${profile.key} report: drawn ${r.drawn?.epochSecond} since ${r.drawnSince?.epochSecond}; runs $runs")
                    }
                }
            },
            posts = setOf(WatchWrites.ENTRIES, WatchWrites.UNDO, WatchWrites.MUTE),
        )

    /** Runs the link for as long as [scope] lives. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch { on.collect { o -> _state.update { it.copy(on = o) } } }
        // the payloads, rebuilt on every change of what they carry
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
        // the port: open only while the link is on, Garmin Connect is there and a watch is known to have a Sukrara app
        // (Garmin Connect said so, or a push reached it): nobody else's glucose server on a phone without one
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
        // the push: every new payload (or a kick, or a watch coming back) to every Sukrara app on every watch
        scope.launch {
            combine(on, payloads.debounce(SETTLE_MS), kick, watchTick()) { o, p, k, t -> Triple(o, p, k to t) }
                .distinctUntilChanged { a, b -> a.first == b.first && same(a.second, b.second) && a.third == b.third }
                .collectLatest { (o, p, t) ->
                    if (t != trigger) {
                        trigger = t
                        force = true
                    }
                    if (o) withContext(io) { push(p) }
                }
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
    private fun payloads(): Flow<Payloads> {
        val unit = settings.flow(GraphSettings.GlucoseUnitSetting).map(GlucoseUnit::of)
        val bands =
            combine(
                GraphSettings.Bands.map(settings::flow),
            ) { v -> v.map(Double::toInt).let { Bands(it[0], it[1], it[2], it[3], it[4], it[5]) } }
        val hours = settings.flow(SurfaceSettings.CountdownHours).map { it.toInt() }
        val graph = GraphSettings.config(settings).distinctUntilChanged()
        val own = watchDisplays.distinctUntilChanged()
        // today's readings (for time in range) and the last month's treatments (the app's usual amounts), the windows
        // moving on every 10 min
        val since = ticks(TEN_MIN)
        val today = since.flatMapLatest { readings.window(startOfDay(it)) }
        val entries = since.flatMapLatest { treatments.since(it - Duration.ofDays(WatchPayload.LOG_DAYS + 1)) }
        // the app's report numbers: only while a watch has the app, every 10 min (they move slowly; a month of readings)
        val hasApp = _state.map { s -> s.watches.any { it.has(GarminApp.App) == true } }.distinctUntilChanged()
        val metrics =
            combine(hasApp, graph, own) { a, g, w -> Triple(a, g, w) }.flatMapLatest { (a, g, w) ->
                if (!a) flowOf(emptyMap()) else ticks(TEN_MIN).map { withContext(io) { metrics(g, w.values, it) } }
            }
        return combine(
            combine(glucose, entries, sensor) { g, t, life -> Triple(g, t, life) },
            combine(unit, bands, hours) { u, b, h -> Triple(u, b, h) },
            today,
            combine(graph, own, metrics, this.entries.open) { g, w, m, o -> Display(g, w, m, o) },
        ) { (g, t, life), (u, b, h), day, d ->
            // THE kinds' order from the phone's own facts at this moment (a reading, a ring, a pen read's entry rebuild it)
            val kinds = if (d.open) runCatching { this.entries.facts() }.getOrDefault(KindFacts.NONE) else KindFacts.NONE
            val quiet = if (d.open) runCatching { this.entries.quiet() }.getOrDefault(WatchQuiet.NONE) else WatchQuiet.NONE
            build(g, t, u, b, h, day.map { it.toPoint() }, life, d, kinds, quiet)
        }.distinctUntilChanged(::same)
    }

    private fun ticks(every: Duration): Flow<Instant> =
        flow {
            while (true) {
                emit(clock.now())
                delay(every.toMillis())
            }
        }

    /** The settings the app's payloads are built from: the app's [graph], each watch's [own], the [metrics], [open]. */
    private data class Display(
        val graph: GraphConfig,
        val own: Map<Long, WatchDisplay>,
        val metrics: Map<Long, GlucoseMetrics>,
        val open: Boolean,
    )

    private suspend fun metrics(
        g: GraphConfig,
        watches: Collection<WatchDisplay>,
        at: Instant,
    ): Map<Long, GlucoseMetrics> {
        val windows = WatchPayload.windows(g, watches)
        val nowS = at.epochSecond
        val points = readings.range(at - Duration.ofSeconds(windows.max()), at.plusSeconds(1)).map { it.toPoint() }
        return windows.associateWith { w -> GlucoseMetrics.of(points, nowS - w, nowS, g.bands.sane()) }
    }

    private fun build(
        g: GlucoseState,
        t: List<Treatment>,
        unit: GlucoseUnit,
        bands: Bands,
        hours: Int,
        day: List<app.mauri.sukrara.graph.stats.GlucosePoint>,
        life: CurrentSensor?,
        d: Display,
        kinds: KindFacts,
        quiet: WatchQuiet,
    ): Payloads {
        val now = clock.now()
        val from = startOfDay(now)
        val end = from.atZone(clock.zone).plusDays(1).toInstant()
        val tir = GlucoseMetrics.of(day, from.epochSecond, now.epochSecond, bands.sane()).tir
        val share = tir.inRange.takeIf { tir.coverage >= TIR_COVERAGE }
        val i = PayloadInputs(g, t, now, unit, bands, hours, share, end, life, d.graph, d.metrics, d.open, kinds, quiet = quiet)
        return Payloads(
            WatchProfile.entries.associateWith { WatchPayload.of(it, i) },
            d.own.mapValues { (_, own) -> WatchPayload.of(WatchProfile.App, i.copy(watch = own)) },
        )
    }

    private fun startOfDay(at: Instant): Instant = LocalDate.ofInstant(at, clock.zone).atStartOfDay(clock.zone).toInstant()

    /**
     * Sends each app its payload from [p] on every connected watch, retrying the ones that failed until a newer payload
     * comes (an app that got this one isn't sent it again). An app whose messages are [AppLink.stuck] gets one try every
     * [PROBE] (and at once on *Send now* or when the watch reconnects): retrying every reading into a stuck Garmin
     * Connect only wakes it, and the app fetches meanwhile.
     */
    private suspend fun push(p: Payloads) {
        var wait = RETRY_FIRST_MS
        val done = HashSet<Pair<Long, String>>()
        val secret = p[WatchProfile.App]?.let { key?.get()?.let(WatchKey::hex) }
        while (true) {
            val devices = port.devices()
            _state.update { it.copy(garminConnect = port.installed(), answering = devices != null) }
            if (devices == null) return
            val forced = force
            val sent = clock.now().epochSecond.toInt()
            var retry = false
            var untilProbe: Long? = null
            val watches =
                devices.map { d ->
                    val before = _state.value.watches.firstOrNull { it.id == d.id }
                    val links = LinkedHashMap(before?.links.orEmpty())
                    for (app in GarminApp.entries) {
                        // the app with this watch's own display settings (SPEC §11)
                        val payload = p.of(app.profile, d.id) ?: continue
                        for (id in app.ids) {
                            var l = links[id] ?: AppLink()
                            val installed = installed(d, id, before, l)
                            l = l.copy(installed = installed)
                            // an app Garmin Connect hasn't said is there gets its public id only
                            val target = installed == true || (installed == null && id == app.id)
                            val now = clock.now()
                            val probe = l.failed?.plus(PROBE)?.takeIf { l.stuck(now) && !forced && now < it }
                            if (!d.connected || !target || (d.id to id) in done) {
                                // nothing to send
                            } else if (probe != null) {
                                // stuck: the next try comes with the probe
                                val ms = Duration.between(now, probe).toMillis()
                                untilProbe = minOf(untilProbe ?: ms, ms)
                            } else {
                                var out = payload + ("sent" to sent)
                                if (app.logs && secret != null) out = out + ("key" to secret)
                                // which watch this is: its fetch sends it back (`&w=`) for its own values
                                if (app.profile == WatchProfile.App) out = out + ("w" to d.id.toString())
                                val result = port.send(d, id, MonkeyC.encode(out))
                                val readingAt = (payload["g"] as? Map<*, *>)?.get("t")
                                trace("push ${app.key}${if (id == app.beta) " beta" else ""} sent $sent reading $readingAt: $result")
                                val at = clock.now()
                                l =
                                    when (result) {
                                        IqSend.Delivered -> {
                                            done += d.id to id
                                            val reading = (readingAt as? Int)?.let { Instant.ofEpochSecond(it.toLong()) }
                                            l.copy(
                                                delivered = at,
                                                failed = null,
                                                noAnswer = false,
                                                installed = true,
                                                reading = reading,
                                                failingSince = null,
                                                deliveries = (l.deliveries + Delivery(at, reading)).takeLast(AppLink.KEPT),
                                            )
                                        }

                                        is IqSend.Failed -> {
                                            retry = true
                                            l.copy(failed = at, failingSince = l.failingSince ?: at)
                                        }

                                        IqSend.NoAnswer -> {
                                            retry = true
                                            l.copy(failed = at, noAnswer = true, failingSince = l.failingSince ?: at)
                                        }
                                    }
                            }
                            links[id] = l
                        }
                    }
                    GarminWatch.of(d.id, d.name, d.connected, links)
                }
            if (forced) force = false
            _state.update { it.copy(watches = watches) }
            // a failed send backs off; a stuck app waits for its probe
            delay(listOfNotNull(wait.takeIf { retry }, untilProbe).minOrNull() ?: return)
            if (retry) wait = (wait * 2).coerceAtMost(RETRY_MAX_MS)
        }
    }

    /** Whether app id [app] is on [d], asked again every [RECHECK_MS] or when the watch reconnects; null = unknown. */
    private suspend fun installed(
        d: IqDevice,
        app: String,
        before: GarminWatch?,
        link: AppLink,
    ): Boolean? {
        val last = checked[d.id to app]
        val fresh = last != null && before?.connected == true && clock.now() < last + Duration.ofMillis(RECHECK_MS)
        if (fresh || !d.connected) return link.installed
        val v = port.appVersion(d, app)
        checked[d.id to app] = clock.now()
        return when (v) {
            // no answer keeps what was known (unknown: the public id is still pushed to; the port opens once a push arrives)
            ConnectIqPort.NO_ANSWER -> link.installed

            null -> false

            else -> true
        }
    }

    private val checked = HashMap<Pair<Long, String>, Instant>()

    /**
     * The fetch: `GET /sukrara/<app>` → that app's payload as JSON, `sent` now (the watch reads it like a push), never the
     * key; the app's with the asking watch's own values (`w`, from its push; without it the one watch with the app); the
     * writes go to [writes].
     */
    internal fun answer(req: HttpRequest): HttpAnswer {
        if (req.method == "POST") return writes.answer(req)
        val profile = WatchProfile.ofPath(req.path) ?: return HttpAnswer.NOT_FOUND
        val watch =
            req.query["w"]?.toLongOrNull()
                ?: _state.value.watches
                    .filter { it.has(GarminApp.App) == true }
                    .singleOrNull()
                    ?.id
        val p = current?.of(profile, watch) ?: return HttpAnswer("503 Service Unavailable", "{}")
        return HttpAnswer("200 OK", json(p + ("sent" to clock.now().epochSecond.toInt())).toString())
    }

    /** A link event in logcat (`Sukrara.Garmin`) and the diagnostic log: times, ids and states only, never a value or the key. */
    private fun trace(line: String) {
        runCatching { android.util.Log.i(TAG, line) }
        log(line)
    }

    /** Stops the port (the switch stays as it is). */
    fun stop() = server.stop()

    companion object {
        /** The fetch: http://127.0.0.1:17582/sukrara/<app> (Garmin Connect allows http only to loopback). */
        const val PORT = 17582
        const val TAG = "Sukrara.Garmin"

        val SETTING = SchemaSettings.bool("apps.garmin", default = true)

        private const val CHECK_MS = 60_000L
        private val TEN_MIN: Duration = Duration.ofMinutes(10)

        /** A reading changes the payload in steps (the hub, then today's readings): one message once it settles. */
        const val SETTLE_MS = 3_000L
        private const val RETRY_FIRST_MS = 15_000L
        private const val RETRY_MAX_MS = 120_000L
        private const val RECHECK_MS = 30 * 60_000L

        /** No message through for this long: stuck ([AppLink.stuck]). */
        val STUCK: Duration = Duration.ofMinutes(15)

        /** While stuck, one try this often (the apps' fetch rhythm). */
        val PROBE: Duration = Duration.ofMinutes(5)

        /** Today's time in range only with data for a quarter of the day so far (as the card's rings ask). */
        const val TIR_COVERAGE = 0.25

        /** Whether the port may be open: on, Garmin Connect installed, a watch known to have a Sukrara app. */
        internal fun fetchOpen(
            on: Boolean,
            s: GarminLinkState,
        ) = on && s.garminConnect && s.watches.any { it.installed == true }

        /** Two payloads say the same apart from when they were built. */
        internal fun same(
            a: Payloads,
            b: Payloads,
        ) = same(a.profiles, b.profiles) && same(a.watches, b.watches)

        private fun <K> same(
            a: Map<K, Map<String, Any?>>,
            b: Map<K, Map<String, Any?>>,
        ) = a.keys == b.keys && a.all { (k, v) -> v - "sent" == b[k]?.minus("sent") }

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
