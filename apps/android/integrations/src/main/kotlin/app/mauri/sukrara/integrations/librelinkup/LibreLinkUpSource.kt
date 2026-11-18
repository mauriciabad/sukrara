// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.librelinkup

import app.mauri.sukrara.core.data.libreview.LibreLinkUp
import app.mauri.sukrara.core.data.libreview.LluCallOutcome
import app.mauri.sukrara.core.data.libreview.LluPerson
import app.mauri.sukrara.core.data.libreview.LluPick
import app.mauri.sukrara.core.model.CallOutcome
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.ReadingSource
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.integrations.nightscout.site.NetworkState
import app.mauri.sukrara.integrations.nightscout.site.NsSensor
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
import java.time.Duration
import java.time.Instant

/** Whose glucose the LibreLinkUp account brings (schema `source.libreLinkUpPatient`; "" = pick it). */
data class LluSourceSettings(
    val patient: String = "",
)

/** Where the source's settings live (the app's `source.json`), and whose glucose the phone shows. */
interface LluSourceConfig {
    val data: Flow<LluSourceSettings>

    /** Yours or someone else's (picks the account itself, or another person, when there is one choice). */
    val person: Flow<Person?>

    suspend fun set(settings: LluSourceSettings)
}

/** What the source remembers (typed state file `librelinkup-source`): the person's name and the sensor it said. */
@Serializable
data class LluSourceState(
    /** The person read, as LibreLinkUp names them (*Ana Example*); "" = not known yet. */
    val name: String = "",
    /** The people the account follows, from the last connections answer (a pick that's gone lists them). */
    val people: List<LluPersonState> = emptyList(),
    /** The sensor LibreLinkUp last said (serial = its id: a new serial is a new sensor); empty = none known. */
    val sensorSerial: String = "",
    val sensorStartS: Long = 0,
) {
    val sensorLife: NsSensor? get() =
        if (sensorSerial.isNotEmpty() && sensorStartS > 0) NsSensor(sensorSerial, Instant.ofEpochSecond(sensorStartS)) else null
}

@Serializable
data class LluPersonState(
    val id: String,
    val name: String,
)

/** What Settings › Source, setup's Connect step and home show of the source. */
data class LluSourceView(
    /** An account is saved with its password (it can sign in). */
    val signedIn: Boolean = false,
    /** The saved account's email ("" = none). */
    val email: String = "",
    /** The saved account can't be read, or its password was refused: sign in again. */
    val signInAgain: Boolean = false,
    /** Whose glucose is read (its patient id, "" = not picked yet). */
    val patient: String = "",
    /** Their name, as LibreLinkUp says it. */
    val name: String = "",
    /** The people the account follows (to pick one when several, or again when the one picked is gone). */
    val people: List<LluPerson> = emptyList(),
    val health: OnlineHealth = OnlineHealth.Connecting,
    /** The newest reading LibreLinkUp had (null = none yet). */
    val newest: Instant? = null,
    /** The sensor LibreLinkUp says is running. */
    val sensor: NsSensor? = null,
) {
    /** Signed in and someone picked: the source can read. */
    val configured: Boolean get() = signedIn && patient.isNotEmpty()
}

/** The answer of the Connect step's *Connect* (or of picking a person). */
sealed interface LluConnectResult {
    /** Signed in: [people] the account follows; [picked] the one read now (null = several: pick one). */
    data class Connected(
        val people: List<LluPerson>,
        val picked: String?,
    ) : LluConnectResult

    data object BadCredentials : LluConnectResult

    /** New terms to accept in LibreLinkUp first. */
    data object Terms : LluConnectResult

    data object RateLimited : LluConnectResult

    /** Signed in, but no one shares with this account yet. */
    data object NoOne : LluConnectResult

    data object Unreachable : LluConnectResult

    data object Offline : LluConnectResult

    /** LibreLinkUp refuses this client: Abbott changed it. */
    data object Stopped : LluConnectResult
}

/** When the source asks again (contracts/sources/librelinkup/poll.json). */
object LluPoll {
    const val EVERY_S = 60L
    val BACKOFF_S = listOf(60L, 120L, 300L, 600L)
    const val RATE_LIMITED_MIN_S = 600L
    const val CONNECTIONS_EVERY_S = 1800L

    /** New terms: asked again now and then (the person accepts in LibreLinkUp; no lockout risk). */
    const val TERMS_S = 600L

    /** Abbott refused this client: asked again rarely. */
    const val RETIRED_S = 3600L

    /** Seconds to the next call, null = only when the person acts (a refused password: retries lock the account). */
    fun waitS(
        failures: Int,
        rateLimitedS: Long? = null,
        refused: Boolean = false,
    ): Long? {
        if (refused) return null
        if (rateLimitedS != null) return maxOf(RATE_LIMITED_MIN_S, rateLimitedS)
        if (failures == 0) return EVERY_S
        return BACKOFF_S[(failures - 1).coerceAtMost(BACKOFF_S.size - 1)]
    }
}

/**
 * Glucose from LibreLinkUp (docs/44 step 2, contracts/sources/librelinkup): the person the account follows, their
 * live reading each minute (with its arrow as the sensor's rate) and the graph's 5-minute points of the last 12 h,
 * which only fill gaps ([app.mauri.sukrara.core.model.SourceId.LibreLinkUpGraph]); the sensor's serial and start give
 * its life. Its health ([OnlineHealth], with LibreLinkUp's own: new terms, not shared, stopped) is what the person
 * sees; the hub gets it as [SourceStatus]. Runs only while the hub collects [readings]. The account lives only in the
 * secure store ([LluAccount]); https only, redirects never followed.
 */
class LibreLinkUpSource(
    private val config: LluSourceConfig,
    val account: LluAccount,
    private val store: StateStore<LluSourceState>,
    private val network: NetworkState,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val log: (String) -> Unit = {},
) : ReadingSource {
    private val statusFlow = MutableStateFlow<SourceStatus>(SourceStatus.NotStarted)
    override val status: StateFlow<SourceStatus> = statusFlow.asStateFlow()

    private val sensorFlow = MutableStateFlow<SensorState>(SensorState.Active(endsAt = null))
    override val sensor: StateFlow<SensorState> = sensorFlow.asStateFlow()

    /** The sensor LibreLinkUp says is running (Sensor end in calendar, the takeover's sensor life), null = none known. */
    val sensorLife: Flow<NsSensor?> = store.data.map { it.sensorLife }.distinctUntilChanged()

    private val live = MutableStateFlow(Live())

    private data class Live(
        val outcome: CallOutcome? = null,
        val online: Boolean = true,
        val newest: Instant? = null,
    )

    private val kicks = Channel<Unit>(Channel.CONFLATED)

    /** The person the last call read (another one starts from nothing). */
    @Volatile private var lastPatient: String? = null

    @Volatile private var peopleCheckedS = 0L

    /** What the pages show. */
    val view: Flow<LluSourceView> =
        combine(config.data, store.data, live, account.saved, ticker()) { s, st, l, saved, now ->
            val readable = saved as? LluAccount.Saved.Readable
            LluSourceView(
                signedIn = readable != null && !readable.signedOut,
                email = readable?.email.orEmpty(),
                signInAgain = saved == LluAccount.Saved.Unreadable || readable?.signedOut == true,
                patient = s.patient,
                name = st.name,
                people = st.people.map { LluPerson(it.id, it.name) },
                health = health(l, now),
                newest = l.newest,
                sensor = st.sensorLife,
            )
        }.distinctUntilChanged()

    /** Ask now (a new sign-in, a person picked, *Try again*). */
    fun kick() {
        kicks.trySend(Unit)
    }

    /**
     * *Connect* (setup's Connect step, Settings › Source's *Sign in again*): signs in, reads whom the account follows
     * and picks the person when the choice is clear (connections.json); several = [LluConnectResult.Connected] with no
     * pick (the step lists them, [choose]).
     */
    suspend fun connect(
        email: String,
        password: String,
    ): LluConnectResult =
        withContext(io) {
            when (val s = account.connect(email, password)) {
                is LluSession.Ready -> pickAfterSignIn(s, saved = null)
                LluSession.SignedOut -> LluConnectResult.BadCredentials
                is LluSession.Terms -> LluConnectResult.Terms
                is LluSession.RateLimited -> LluConnectResult.RateLimited
                LluSession.Retired -> LluConnectResult.Stopped
                LluSession.Failed -> if (network.online()) LluConnectResult.Unreachable else LluConnectResult.Offline
            }
        }

    /** *Try again* (no one shared, new terms): whom the saved account follows, read again; the pick kept while shared. */
    suspend fun recheck(): LluConnectResult =
        withContext(io) {
            when (val s = account.session()) {
                is LluSession.Ready -> {
                    pickAfterSignIn(
                        s,
                        saved =
                            config.data
                                .first()
                                .patient
                                .ifEmpty { null },
                    )
                }

                LluSession.SignedOut -> {
                    LluConnectResult.BadCredentials
                }

                is LluSession.Terms -> {
                    LluConnectResult.Terms
                }

                is LluSession.RateLimited -> {
                    LluConnectResult.RateLimited
                }

                LluSession.Retired -> {
                    LluConnectResult.Stopped
                }

                LluSession.Failed -> {
                    if (network.online()) LluConnectResult.Unreachable else LluConnectResult.Offline
                }
            }
        }

    private suspend fun pickAfterSignIn(
        s: LluSession.Ready,
        saved: String?,
    ): LluConnectResult {
        val (people, r) = account.people(s)
        people ?: return when (r.outcome) {
            LluCallOutcome.Terms -> LluConnectResult.Terms
            LluCallOutcome.RateLimited -> LluConnectResult.RateLimited
            LluCallOutcome.Retired, LluCallOutcome.Version -> LluConnectResult.Stopped
            LluCallOutcome.Network -> if (network.online()) LluConnectResult.Unreachable else LluConnectResult.Offline
            else -> LluConnectResult.Unreachable
        }
        val yours = config.person.first() != Person.Theirs
        // a new sign-in picks again (another account follows other people); *Try again* keeps a pick still shared
        val p =
            LibreLinkUp.pick(people, s.accountId, yours, saved).let {
                if (it.state ==
                    LluPick.State.Gone
                ) {
                    LibreLinkUp.pick(people, s.accountId, yours, null)
                } else {
                    it
                }
            }
        store.update { st ->
            st.copy(
                people = people.map { LluPersonState(it.id, it.name) },
                name = people.firstOrNull { it.id == p.pick }?.name ?: st.name.takeIf { p.pick == null }.orEmpty(),
            )
        }
        peopleCheckedS = clock.now().epochSecond
        if (p.state == LluPick.State.None) {
            config.set(LluSourceSettings(patient = ""))
            return LluConnectResult.NoOne
        }
        config.set(LluSourceSettings(patient = p.pick.orEmpty()))
        kick()
        return LluConnectResult.Connected(people, p.pick)
    }

    /** The person picked among the account's (several, or the one picked before stopped sharing). */
    suspend fun choose(patient: String) {
        val st = store.data.first()
        store.update {
            it.copy(
                name =
                    st.people
                        .firstOrNull { p -> p.id == patient }
                        ?.name
                        .orEmpty(),
            )
        }
        config.set(LluSourceSettings(patient = patient))
        kick()
    }

    /** Signs out of the source's account (Settings › Source): the readings stay, the source waits for a new sign-in. */
    suspend fun signOut() {
        account.forget()
        config.set(LluSourceSettings())
        store.update { LluSourceState() }
        live.value = Live(online = network.online())
        kick()
    }

    override fun onBluetoothOn() = Unit

    override fun onBluetoothOff() = Unit

    override val readings: Flow<List<Reading>> =
        channelFlow {
            live.value = Live(online = network.online())
            statusFlow.value = SourceStatus.Connecting
            launch { network.back.collect { kick() } }
            launch { config.data.drop(1).collect { kick() } }
            try {
                run(this)
            } finally {
                statusFlow.value = SourceStatus.NotStarted
            }
        }

    private suspend fun run(out: ProducerScope<List<Reading>>) {
        var failures = 0
        while (true) {
            val c =
                try {
                    withContext(io) { cycle(out) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("librelinkup source: ${e.javaClass.simpleName}")
                    Cycle(CallOutcome.Server)
                }
            if (c.outcome == CallOutcome.Ok) failures = 0 else failures++
            val l = live.value
            live.value = l.copy(outcome = c.outcome ?: l.outcome, online = network.online(), newest = c.newest ?: l.newest)
            publish()
            val wait =
                when (c.outcome) {
                    null -> {
                        null
                    }

                    CallOutcome.Terms -> {
                        LluPoll.TERMS_S
                    }

                    CallOutcome.Retired -> {
                        LluPoll.RETIRED_S
                    }

                    CallOutcome.NotShared -> {
                        LluPoll.CONNECTIONS_EVERY_S
                    }

                    else -> {
                        LluPoll.waitS(
                            failures,
                            c.retryAfterS.takeIf { c.outcome == CallOutcome.RateLimited },
                            c.outcome == CallOutcome.Refused,
                        )
                    }
                }
            waitFor(wait)
        }
    }

    /** Waits [seconds] (null = until a kick), publishing the health as it ages on the way. */
    private suspend fun waitFor(seconds: Long?) {
        var left = (seconds ?: Long.MAX_VALUE / 1000) * 1000
        while (left > 0) {
            val step = minOf(left, TICK_MS)
            if (withTimeoutOrNull(step) { kicks.receive() } != null) return
            left -= step
            publish()
        }
    }

    private suspend fun publish() {
        val l = live.value.copy(online = network.online())
        live.value = l
        val now = clock.now()
        sensorFlow.value = store.data
            .first()
            .sensorLife
            ?.state(l.newest, now) ?: SensorState.Active(endsAt = null)
        val h = health(l, now)
        statusFlow.value =
            when {
                h == OnlineHealth.Connecting -> SourceStatus.Connecting
                h.fine && l.newest != null -> SourceStatus.Live(l.newest)
                h.fine -> SourceStatus.Connecting
                else -> SourceStatus.Interrupted(l.newest ?: now, h.problem)
            }
    }

    private fun health(
        l: Live,
        now: Instant,
    ): OnlineHealth = OnlineHealth.of(l.outcome, l.online, l.newest?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) })

    private class Cycle(
        /** null = not set up (no account, or no one picked: the Connect step). */
        val outcome: CallOutcome?,
        val newest: Instant? = null,
        val retryAfterS: Long? = null,
    )

    private fun outcomeOf(o: LluCallOutcome): CallOutcome =
        when (o) {
            LluCallOutcome.Ok -> CallOutcome.Ok

            LluCallOutcome.Expired -> CallOutcome.Refused

            // a second refusal right after a new sign-in
            LluCallOutcome.RateLimited -> CallOutcome.RateLimited

            LluCallOutcome.Terms -> CallOutcome.Terms

            LluCallOutcome.Version, LluCallOutcome.Retired -> CallOutcome.Retired

            LluCallOutcome.Server -> CallOutcome.Server

            LluCallOutcome.Network -> CallOutcome.Network
        }

    /** One call: the session, whom to read (now and then), then the graph. */
    private suspend fun cycle(out: ProducerScope<List<Reading>>): Cycle {
        val patient = config.data.first().patient
        var session =
            when (val s = account.session()) {
                is LluSession.Ready -> s
                else -> return failed(s)
            }
        if (patient.isEmpty()) return Cycle(null)
        if (patient != lastPatient) {
            // another person: what the last one had says nothing about this one
            lastPatient = patient
            live.value = live.value.copy(newest = null)
        }
        val now = clock.now()
        if (now.epochSecond - peopleCheckedS >= LluPoll.CONNECTIONS_EVERY_S) {
            var (people, r) = account.people(session)
            if (r.outcome == LluCallOutcome.Expired) {
                // the token ended before its time: one new sign-in with the saved password, then the same call
                account.expire()
                session = account.session().let { it as? LluSession.Ready ?: return failed(it) }
                val again = account.people(session)
                people = again.first
                r = again.second
            }
            if (people == null) return Cycle(outcomeOf(r.outcome), retryAfterS = r.retryAfterS)
            peopleCheckedS = now.epochSecond
            store.update { it.copy(people = people.map { p -> LluPersonState(p.id, p.name) }) }
            if (people.none { it.id == patient }) return Cycle(CallOutcome.NotShared)
        }
        var r = account.api.get(LibreLinkUp.graph(patient), session.region, session.token, session.accountId)
        if (r.outcome == LluCallOutcome.Expired) {
            account.expire()
            session = account.session().let { it as? LluSession.Ready ?: return failed(it) }
            r = account.api.get(LibreLinkUp.graph(patient), session.region, session.token, session.accountId)
        }
        if (r.outcome != LluCallOutcome.Ok) {
            // the person may have stopped sharing: the connections say so at the next call
            if (r.outcome == LluCallOutcome.Retired) peopleCheckedS = 0
            return Cycle(outcomeOf(r.outcome), retryAfterS = r.retryAfterS)
        }
        val g = LibreLinkUp.graph(r.body ?: return Cycle(CallOutcome.Server))
        val known = live.value.newest
        // the whole 12 h at the first call and after a gap, else only what's new (the graph's old points don't change)
        val from = if (known == null || Duration.between(known, now) > GAP) Instant.EPOCH else known.minus(OVERLAP)
        val batch = g.readings.filter { it.t.isAfter(from) && !it.t.isAfter(now.plus(AHEAD)) }
        if (batch.isNotEmpty()) out.send(batch)
        val newestLive = g.readings.filter { it.source == SourceId.LibreLinkUp }.maxOfOrNull { it.t }
        val newest = listOfNotNull(newestLive ?: g.readings.maxOfOrNull { it.t }, known).maxOrNull()
        store.update { st ->
            st.copy(
                name = g.name.ifEmpty { st.name },
                sensorSerial = g.sensor?.serial ?: st.sensorSerial,
                sensorStartS = g.sensor?.start?.epochSecond ?: st.sensorStartS,
            )
        }
        return Cycle(CallOutcome.Ok, newest)
    }

    /** A session that isn't ready, as the health reads it (no account at all = not set up). */
    private fun failed(s: LluSession): Cycle =
        when (s) {
            is LluSession.Ready -> Cycle(CallOutcome.Server)
            LluSession.SignedOut -> Cycle(if (account.saved.value == LluAccount.Saved.None) null else CallOutcome.Refused)
            is LluSession.Terms -> Cycle(CallOutcome.Terms)
            is LluSession.RateLimited -> Cycle(CallOutcome.RateLimited, retryAfterS = s.retryAfterS)
            LluSession.Retired -> Cycle(CallOutcome.Retired)
            LluSession.Failed -> Cycle(CallOutcome.Network)
        }

    private fun ticker(): Flow<Instant> =
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(clock.now())
                kotlinx.coroutines.delay(TICK_MS)
            }
        }

    companion object {
        /** A gap after which the whole graph is read again. */
        private val GAP: Duration = Duration.ofMinutes(5)

        /** Each call keeps a little before the newest reading known (a late graph point). */
        private val OVERLAP: Duration = Duration.ofMinutes(20)

        /** A reading stamped a little ahead (the phone's clock behind) still counts; the hub drops what is too far. */
        private val AHEAD: Duration = Duration.ofMinutes(5)
        private const val TICK_MS = 30_000L
    }
}
