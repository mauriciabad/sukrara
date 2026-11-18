// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import app.mauri.sukrara.core.model.CallOutcome
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.ReadingSource
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.SecretUnreadableException
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import app.mauri.sukrara.integrations.nightscout.NightscoutUploader
import app.mauri.sukrara.integrations.nightscout.NsApi
import app.mauri.sukrara.integrations.nightscout.NsAuth
import app.mauri.sukrara.integrations.nightscout.NsClient
import app.mauri.sukrara.integrations.nightscout.NsConfig
import app.mauri.sukrara.integrations.nightscout.NsDocs
import app.mauri.sukrara.integrations.nightscout.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant

/** The source's site and its treatments switch (schema `source.nightscoutUrl`, `source.nightscoutTreatments`). */
data class NsSourceSettings(
    val url: String = "",
    val treatments: Boolean = true,
)

/** Where the source's settings live (the app's `source.json`, next to whose glucose and where from). */
interface NsSourceConfig {
    val data: Flow<NsSourceSettings>

    suspend fun set(settings: NsSourceSettings)
}

/** Whether this phone has a network now, and the moments one comes back (the source asks again then). */
interface NetworkState {
    fun online(): Boolean

    val back: Flow<Unit>

    companion object {
        /** Always online, no news (tests, previews). */
        val Always =
            object : NetworkState {
                override fun online() = true

                override val back: Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()
            }
    }
}

/** What the source remembers (typed state file `nightscout-source`): this install's name there and the treatment ledger. */
@Serializable
data class NsSourceState(
    /** The site the ledger is about (its root); another site starts a new one. */
    val site: String = "",
    /** This install's name on the site (`Sukrara-<6 hex>`): its treatments go up with it. */
    val device: String = "",
    val ledger: NsSyncLedger = NsSyncLedger(),
    /** The site refused a treatment write: the token can only read, Sukrara's treatments stay on the phone. */
    val readOnly: Boolean = false,
    /** A history pull the person asked for (*Bring back 90 days*), epoch s; 0 = none. */
    val pullFromS: Long = 0,
)

/** How treatments go with the site now. */
enum class NsTreatments { Off, BothWays, ReadOnly }

/** What Settings › Source, setup's Connect step and home show of the source. */
data class NsSourceView(
    val root: String = "",
    val host: String = "",
    /** A site and a saved token or secret. */
    val configured: Boolean = false,
    /** The saved token or secret exists but can't be read: enter it again. */
    val credentialUnreadable: Boolean = false,
    val health: OnlineHealth = OnlineHealth.Connecting,
    /** The newest reading the site had (null = none yet). */
    val newest: Instant? = null,
    val treatments: NsTreatments = NsTreatments.BothWays,
    /** A history pull is running (*Bring back 90 days*): from when. */
    val pulling: Instant? = null,
)

/** The answer of *Connect*. */
sealed interface NsConnectResult {
    /** Saved; [newest] = the site's newest reading (null: it has none yet). */
    data class Connected(val newest: Instant?) : NsConnectResult

    data object BadAddress : NsConnectResult

    data object NeedsHttps : NsConnectResult

    data object Refused : NsConnectResult

    data object NotNightscout : NsConnectResult

    data object Unreachable : NsConnectResult

    data object Offline : NsConnectResult
}

/**
 * Glucose and treatments from a Nightscout site (docs/44 step 1, contracts/sources/nightscout): the site's entries as
 * readings (`nightscout`), backfilled 24 h at the first call and after a gap, then asked again right after the next
 * reading should be there ([NsPoll]); treatments both ways ([NsTreatmentSync]) while the switch is on. Its health
 * ([OnlineHealth]) is what the person sees; the hub gets it as [SourceStatus]. Runs only while the hub collects
 * [readings]. The token or API secret lives only in the secure store (`source.nightscoutSecret`); https only, redirects
 * never followed ([NsClient]).
 */
class NightscoutSource(
    private val config: NsSourceConfig,
    private val account: NightscoutAccount,
    private val store: StateStore<NsSourceState>,
    private val treatments: TreatmentsRepository,
    /** This install's upload device on a site (the Nightscout upload's), never read back as glucose. */
    private val uploadDevice: suspend () -> String?,
    private val network: NetworkState,
    private val http: OkHttpClient,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val log: (String) -> Unit = {},
) : ReadingSource {
    private val statusFlow = MutableStateFlow<SourceStatus>(SourceStatus.NotStarted)
    override val status: StateFlow<SourceStatus> = statusFlow.asStateFlow()

    /** The site doesn't say when the sensor started or ends (step 2's manual start): an active sensor, end unknown. */
    override val sensor: StateFlow<SensorState> = MutableStateFlow(SensorState.Active(endsAt = null)).asStateFlow()

    private val live = MutableStateFlow(Live())

    private data class Live(
        val outcome: CallOutcome? = null,
        val online: Boolean = true,
        val newest: Instant? = null,
        val running: Boolean = false,
    )

    private val kicks = Channel<Unit>(Channel.CONFLATED)

    /** The site the last call read (another one starts from nothing). */
    @Volatile private var lastRoot: String? = null

    /** What the pages show: the settings, the saved credential, the last call and the treatments' state. */
    val view: Flow<NsSourceView> =
        combine(config.data, store.data, live, account.saved, ticker()) { s, st, l, saved, now ->
            val site = NightscoutSite.parse(s.url)
            NsSourceView(
                root = site.root,
                host = site.host,
                configured = s.url.isNotEmpty() && saved != NightscoutAccount.Saved.None,
                credentialUnreadable = saved == NightscoutAccount.Saved.Unreadable,
                health = health(l, now),
                newest = l.newest,
                treatments =
                    when {
                        !s.treatments -> NsTreatments.Off
                        st.readOnly -> NsTreatments.ReadOnly
                        else -> NsTreatments.BothWays
                    },
                pulling = st.pullFromS.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
            )
        }.distinctUntilChanged()

    /** Ask the site now (a treatment logged, a token saved, *Try again*). */
    fun kick() {
        kicks.trySend(Unit)
    }

    /** *Bring back 90 days from Nightscout*: the next call reads back to [from]. */
    suspend fun pullHistory(from: Instant) {
        store.update { it.copy(pullFromS = from.epochSecond) }
        kick()
    }

    override fun onBluetoothOn() = Unit

    override fun onBluetoothOff() = Unit

    override val readings: Flow<List<Reading>> =
        channelFlow {
            live.value = Live(running = true, online = network.online())
            statusFlow.value = SourceStatus.Connecting
            launch { network.back.collect { kick() } }
            // a treatment logged here goes up within seconds, not at the next reading
            launch { treatments.since(clock.now().minus(SYNC_WINDOW)).drop(1).collect { kick() } }
            launch { config.data.drop(1).collect { kick() } }
            try {
                run(this)
            } finally {
                live.value = live.value.copy(running = false)
                statusFlow.value = SourceStatus.NotStarted
            }
        }

    private suspend fun run(out: ProducerScope<List<Reading>>) {
        var failures = 0
        var previous: Instant? = null
        var retryAfter: Long? = null
        var refused = false
        while (true) {
            val outcome =
                try {
                    withContext(io) { cycle(out) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("nightscout source: ${e.javaClass.simpleName}")
                    Cycle(CallOutcome.Server)
                }
            val l = live.value
            if (outcome.outcome == CallOutcome.Ok) {
                failures = 0
                previous = outcome.previous ?: previous
            } else {
                failures++
            }
            retryAfter = outcome.retryAfterS
            refused = outcome.outcome == CallOutcome.Refused
            live.value = l.copy(outcome = outcome.outcome ?: l.outcome, online = network.online(), newest = outcome.newest ?: l.newest)
            publish()
            val now = clock.now().epochSecond
            val wait =
                if (outcome.outcome == null) {
                    NsPoll.REFUSED_S // not set up: until the settings or the token change (a kick)
                } else {
                    NsPoll.waitS(now, live.value.newest?.epochSecond, previous?.epochSecond, failures, retryAfter, refused)
                }
            // the health moves on by itself between calls (live → late → no new readings): published on the way
            waitFor(wait)
        }
    }

    private suspend fun waitFor(seconds: Long) {
        var left = seconds * 1000
        while (left > 0) {
            val step = minOf(left, TICK_MS)
            if (withTimeoutOrNull(step) { kicks.receive() } != null) return
            left -= step
            publish()
        }
    }

    private fun publish() {
        val l = live.value.copy(online = network.online())
        live.value = l
        val h = health(l, clock.now())
        val since = l.newest ?: clock.now()
        statusFlow.value =
            when {
                h == OnlineHealth.Connecting -> SourceStatus.Connecting
                h.fine && l.newest != null -> SourceStatus.Live(l.newest)
                h.fine -> SourceStatus.Connecting
                else -> SourceStatus.Interrupted(since, h.problem)
            }
    }

    private fun health(
        l: Live,
        now: Instant,
    ): OnlineHealth = OnlineHealth.of(l.outcome, l.online, l.newest?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) })

    private class Cycle(
        /** null = not set up (no site or no credential). */
        val outcome: CallOutcome?,
        val newest: Instant? = null,
        val previous: Instant? = null,
        val retryAfterS: Long? = null,
    )

    /** One call: the new entries (a backfill when due), then the treatments. */
    private suspend fun cycle(out: ProducerScope<List<Reading>>): Cycle {
        val s = config.data.first()
        val site = NightscoutSite.parse(s.url)
        if (s.url.isEmpty() || site.problem != null) return Cycle(null)
        val auth =
            try {
                account.auth()
            } catch (e: SecretUnreadableException) {
                null
            } ?: return Cycle(CallOutcome.Refused)
        val st = state(site.root)
        if (site.root != lastRoot) {
            // another site: what the last one had says nothing about this one
            lastRoot = site.root
            live.value = live.value.copy(newest = null)
        }
        val client = NsClient(NsConfig(site.site, auth, NsApi.V1, st.device), http) { clock.now().toEpochMilli() }
        val now = clock.now()
        val known = live.value.newest
        val pullFrom = st.pullFromS.takeIf { it > 0 }?.let(Instant::ofEpochSecond)
        val from =
            listOfNotNull(
                pullFrom,
                (known?.minus(OVERLAP) ?: now.minus(BACKFILL)).coerceAtLeast(now.minus(BACKFILL)),
            ).min()
        val own = uploadDevice()
        var newest: Instant? = null
        var previous: Instant? = null
        var upper: Long? = null
        // newest first, a page at a time, back to [from]
        while (true) {
            val path =
                buildString {
                    append("/api/v1/entries/sgv.json?count=$PAGE&find[date][\$gte]=${from.toEpochMilli()}")
                    upper?.let { append("&find[date][\$lt]=$it") }
                }
            val r = client.read(path)
            val o = outcomeOf(r.code)
            if (o != CallOutcome.Ok) return Cycle(o, retryAfterS = r.retryAfterS)
            val page =
                try {
                    NsEntries.parse(r.body, own)
                } catch (e: Exception) {
                    return Cycle(CallOutcome.Server)
                }
            val rawCount = runCatching { (Json.parseToJsonElement(r.body) as JsonArray).size }.getOrDefault(0)
            if (page.isNotEmpty()) {
                out.send(page)
                if (newest == null) {
                    newest = page.last().t
                    previous = page.getOrNull(page.size - 2)?.t
                }
            }
            if (rawCount < PAGE || page.isEmpty()) break
            upper = page.first().t.toEpochMilli()
        }
        if (pullFrom != null) store.update { it.copy(pullFromS = 0) }
        if (s.treatments) syncTreatments(client, st, now)
        return Cycle(CallOutcome.Ok, newest ?: known, previous)
    }

    /** The source's state for [root]: a new site (or a first call) gets a fresh ledger and this install's name. */
    private suspend fun state(root: String): NsSourceState {
        val st = store.data.first()
        if (st.site == root && st.device.isNotEmpty()) return st
        val fresh = NsSourceState(site = root, device = uploadDevice() ?: NightscoutUploader.newDevice(), pullFromS = st.pullFromS)
        store.update { fresh }
        return fresh
    }

    private suspend fun syncTreatments(
        client: NsClient,
        st: NsSourceState,
        now: Instant,
    ) {
        val from = now.minus(SYNC_WINDOW)
        val to = now.plus(AHEAD)
        val r = client.read("/api/v1/treatments.json?count=$TREATMENTS_MAX&find[created_at][\$gte]=${enc(NsDocs.isoUtc(from))}")
        if (outcomeOf(r.code) != CallOutcome.Ok) return
        val site = runCatching { (Json.parseToJsonElement(r.body) as JsonArray).mapNotNull { it as? JsonObject } }.getOrNull() ?: return
        // a full read only: a capped answer could make an entry look deleted there
        if (site.size >= TREATMENTS_MAX) return
        val local =
            treatments.changedSince(from.minus(LOCAL_SLACK)).map { it.treatment }.filter {
                !it.at.isBefore(from) &&
                    !it.at.isAfter(to)
            }
        val plan = NsTreatmentSync.plan(from, to, site, local, st.ledger, st.device)
        if (plan.empty) return
        var ledger = st.ledger
        var readOnly = st.readOnly
        val stamp = clock.now()
        val own = local.filter { it.id !in st.ledger.pulled }.mapTo(HashSet()) { it.id }
        for (e in plan.upsert) {
            val pulled = e.id !in own
            val stored =
                Treatment(e.id, e.kind, e.amount, e.at, e.note, modifiedAt = stamp, deleted = false, origin = TreatmentOrigin.Phone)
            treatments.upsertIfNewer(TreatmentRecord(stored, via = if (pulled) TreatmentRecord.FROM_NIGHTSCOUT else null))
            ledger =
                if (pulled) {
                    ledger.copy(pulled = ledger.pulled + e.id)
                } else {
                    ledger.copy(pushed = ledger.pushed + (e.id to stamp.toEpochMilli()))
                }
        }
        for (id in plan.delete) {
            val rec = treatments.get(id) ?: continue
            treatments.upsertIfNewer(rec.copy(treatment = rec.treatment.copy(deleted = true, modifiedAt = stamp)))
            ledger = ledger.copy(pushed = ledger.pushed - id)
        }
        if (!readOnly) {
            val zone = clock.zone
            for (id in plan.push) {
                val t = local.firstOrNull { it.id == id } ?: continue
                when (val o = client.upsertTreatment(NsDocs.treatment(t, st.device, zone))) {
                    is Outcome.Ok -> ledger = ledger.copy(pushed = ledger.pushed + (id to t.modifiedAt.toEpochMilli()))
                    is Outcome.AuthFailed -> readOnly = true
                    else -> log("nightscout source: treatment not sent (${o.javaClass.simpleName})")
                }
                if (readOnly) break
            }
            for (id in plan.remove) {
                if (readOnly) break
                when (client.deleteTreatment(id)) {
                    is Outcome.Ok -> ledger = ledger.copy(pushed = ledger.pushed - id)
                    is Outcome.AuthFailed -> readOnly = true
                    else -> Unit
                }
            }
        }
        // the ledger keeps what can still be in a window read
        val keep = treatments.changedSince(now.minus(LEDGER_KEEP)).mapTo(HashSet()) { it.treatment.id }
        ledger = NsSyncLedger(ledger.pulled.filterTo(HashSet()) { it in keep }, ledger.pushed.filterKeys { it in keep })
        store.update { it.copy(ledger = ledger, readOnly = readOnly) }
    }

    /** Settings › Source's *Treatments both ways* turned back on, or a new token: writes are tried again. */
    suspend fun retryWrites() {
        store.update { it.copy(readOnly = false) }
        kick()
    }

    private fun ticker(): Flow<Instant> =
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(clock.now())
                kotlinx.coroutines.delay(TICK_MS)
            }
        }

    companion object {
        /** Read back at the first call and after a gap. */
        val BACKFILL: Duration = Duration.ofHours(24)

        /** Each call reads a little before the newest reading known (an uploader that fills its own gaps late). */
        private val OVERLAP: Duration = Duration.ofMinutes(30)

        /** The treatments window both ways. */
        val SYNC_WINDOW: Duration = Duration.ofHours(24)
        private val AHEAD: Duration = Duration.ofMinutes(10)
        private val LOCAL_SLACK: Duration = Duration.ofDays(7)
        private val LEDGER_KEEP: Duration = Duration.ofDays(31)
        private const val PAGE = 1000
        private const val TREATMENTS_MAX = 2000
        private const val TICK_MS = 30_000L

        /** A call's answer as the source's health reads it (contracts/sources/health.json `outcome`). */
        fun outcomeOf(code: Int): CallOutcome =
            when {
                code == 0 -> CallOutcome.Network
                code in 200..299 -> CallOutcome.Ok
                code == 401 || code == 403 -> CallOutcome.Refused
                code == 404 -> CallOutcome.NotFound
                code == 429 -> CallOutcome.RateLimited
                else -> CallOutcome.Server
            }

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
