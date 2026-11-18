// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.librelinkup

import app.mauri.sukrara.core.data.libreview.LibreLinkUp
import app.mauri.sukrara.core.data.libreview.LibreViewStatus
import app.mauri.sukrara.core.data.libreview.LluCallOutcome
import app.mauri.sukrara.core.data.libreview.LluPerson
import app.mauri.sukrara.core.data.libreview.LluPick
import app.mauri.sukrara.core.model.CallOutcome
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.LifeSettings
import app.mauri.sukrara.core.model.MaintenanceWindow
import app.mauri.sukrara.core.model.MoveRules
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.ReadingSource
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.integrations.nightscout.site.NetworkState
import app.mauri.sukrara.integrations.nightscout.site.NsSensor
import app.mauri.sukrara.integrations.nightscout.site.NsSensorLife
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
import kotlinx.coroutines.flow.flowOf
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
    /** The sensor's product type LibreLinkUp said (`pt`: 4 = Libre 3 / 3 Plus), null = not said. */
    val sensorPt: Int? = null,
    /**
     * The person whose LibreLinkUp readings this phone holds (patient id, "" = none): reading anyone else deletes them
     * first, with the treatments logged here (two people's glucose never mix). A sign-out keeps it; a change of whose
     * glucose (which deletes the history) clears it.
     */
    val held: String = "",
    /**
     * The device type of the app that uploads the person's glucose, as the last graph answer said it (`dtid`; 0 = not
     * said): which LibreView identity Sukrara may send treatments under (docs/45).
     */
    val deviceType: Int = 0,
    /** The start (epoch s) of the LibreView maintenance window last announced ahead (one heads-up a window). */
    val warnedS: Long = 0,
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
    /** The person whose readings this phone holds (patient id, "" = none): another one is asked first. */
    val held: String = "",
    /**
     * The person a sign-in or *Try again* would read now, instead of the one read: not read until the person says so
     * ([LibreLinkUpSource.choose]) or keeps the one read ([LibreLinkUpSource.keepPerson]); null = none.
     */
    val pending: LluPerson? = null,
    /** LibreView's maintenance end while [health] is [OnlineHealth.Maintenance], when Abbott's status page says it. */
    val until: Instant? = null,
    /** The next LibreView maintenance window of the account's region (the warn-ahead), null = none listed. */
    val ahead: MaintenanceWindow? = null,
) {
    /** Signed in and someone picked: the source can read. */
    val configured: Boolean get() = signedIn && patient.isNotEmpty()
}

/** What LibreLinkUp shows of the app that uploads the person's glucose ([LibreLinkUpSource.uploader]). */
data class LluUploader(
    /** Its device type (`dtid`, the graph's patient device); null = not said yet. */
    val deviceType: Int?,
    /** The newest reading LibreLinkUp had (null = none yet). */
    val newest: Instant?,
    /** The last time LibreLinkUp answered (null = not since the source started): whether [newest] is news. */
    val answeredAt: Instant?,
)

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

    /** LibreView is down for maintenance: sign in once it ends (nothing is wrong with the account). */
    data object Maintenance : LluConnectResult
}

/** When the source asks again (contracts/sources/librelinkup/poll.json). */
object LluPoll {
    /** The Libre app uploads each reading as the sensor reads it, each minute. */
    const val CADENCE_S = 60L

    /** LibreLinkUp has a reading within seconds: the call goes this long after it is due (it grows when late). */
    const val MARGIN_S = 8L
    const val MARGIN_UP_S = 5L
    const val MARGIN_HOLD = 10
    const val MARGIN_MAX_S = 40L

    /** A timed call never sooner than this after an answer: one call a reading at most. */
    const val MIN_S = 45L
    val BACKOFF_S = listOf(60L, 120L, 300L, 600L)
    const val RATE_LIMITED_MIN_S = 600L
    const val CONNECTIONS_EVERY_S = 1800L

    /** New terms: asked again now and then (the person accepts in LibreLinkUp; no lockout risk). */
    const val TERMS_S = 600L

    /** Abbott refused this client: asked again rarely. */
    const val RETIRED_S = 3600L

    /** LibreView's maintenance: the backoff, at most this far apart (the first answer after it ends it soon). */
    const val MAINTENANCE_MAX_S = 300L

    /** ... and this long after the window's end when the status page gives it and that is sooner. */
    const val MAINTENANCE_AFTER_END_S = 30L

    /** How late after a reading is due the call goes ([marginS]), and the new readings found in time in a row. */
    data class Timing(
        val marginS: Long = MARGIN_S,
        val streak: Int = 0,
    )

    /**
     * The timing after a call answered at [nowS] that found the live reading [foundS] (null = none), [beforeS] the
     * newest live reading known before it (null = none: a first call says nothing about timing). A new reading more
     * than a cadence old when found missed its call: the margin grows; [MARGIN_HOLD] in a row found in time take a
     * second off. Nothing new (the sensor missed one, or it is on its way): unchanged. (poll.json `margins`)
     */
    fun timing(
        t: Timing,
        nowS: Long,
        beforeS: Long?,
        foundS: Long?,
    ): Timing {
        if (foundS == null || beforeS == null || foundS <= beforeS) return t
        if (nowS - foundS > CADENCE_S) return Timing(minOf(t.marginS + MARGIN_UP_S, MARGIN_MAX_S), 0)
        val streak = t.streak + 1
        return if (streak >= MARGIN_HOLD) Timing(maxOf(t.marginS - 1, MARGIN_S), 0) else t.copy(streak = streak)
    }

    /**
     * Seconds to the next call after an answer: [marginS] after the next reading is due (the newest live reading
     * [newestS] + a cadence), the first such time at least [MIN_S] from [nowS]; a reading stamped ahead counts as
     * now; none yet: a cadence. (poll.json `timed`)
     */
    fun timedS(
        nowS: Long,
        newestS: Long?,
        marginS: Long,
    ): Long {
        newestS ?: return CADENCE_S
        val n = minOf(newestS, nowS)
        val k = maxOf(1L, -Math.floorDiv(n + marginS - nowS - MIN_S, CADENCE_S))
        return n + k * CADENCE_S + marginS - nowS
    }

    /**
     * Seconds to the next call after a cycle that ended in [outcome] (null = not set up), the [failures] in a row and a
     * 429's [retryAfterS]; null = only when the person acts. An answer: timed to the next reading ([timedS]). Asked to
     * wait without a time: [RATE_LIMITED_MIN_S]. LibreView's maintenance: [maintenanceS], to its end [untilS] when known.
     */
    fun next(
        outcome: CallOutcome?,
        failures: Int,
        retryAfterS: Long?,
        nowS: Long = 0,
        newestS: Long? = null,
        marginS: Long = MARGIN_S,
        untilS: Long? = null,
    ): Long? =
        when (outcome) {
            null -> null
            CallOutcome.Maintenance -> maintenanceS(failures, nowS, untilS)
            CallOutcome.Ok -> timedS(nowS, newestS, marginS)
            CallOutcome.Terms -> TERMS_S
            CallOutcome.Retired -> RETIRED_S
            CallOutcome.NotShared -> CONNECTIONS_EVERY_S
            CallOutcome.RateLimited -> waitS(failures, rateLimitedS = retryAfterS ?: 0L)
            else -> waitS(failures, refused = outcome == CallOutcome.Refused)
        }

    /**
     * Seconds to the next call while LibreView is down for maintenance (poll.json `after`): the failures' backoff, at most
     * [MAINTENANCE_MAX_S], and [MAINTENANCE_AFTER_END_S] after the window's end [untilS] when that is sooner (never sooner
     * than the first backoff step).
     */
    fun maintenanceS(
        failures: Int,
        nowS: Long,
        untilS: Long?,
    ): Long {
        val w = minOf(waitS(failures) ?: MAINTENANCE_MAX_S, MAINTENANCE_MAX_S)
        if (untilS == null || untilS <= nowS) return w
        return minOf(w, maxOf(untilS - nowS + MAINTENANCE_AFTER_END_S, BACKOFF_S.first()))
    }

    /** Seconds to the next call after a failure, null = only when the person acts (a refused password: retries lock the account). */
    fun waitS(
        failures: Int,
        rateLimitedS: Long? = null,
        refused: Boolean = false,
    ): Long? {
        if (refused) return null
        if (rateLimitedS != null) return maxOf(RATE_LIMITED_MIN_S, rateLimitedS)
        return BACKOFF_S[(failures.coerceAtLeast(1) - 1).coerceAtMost(BACKOFF_S.size - 1)]
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
    /** The person's side of the sensor's life: its wear length (LibreLinkUp says the start, not the length). */
    private val life: Flow<LifeSettings> = flowOf(LifeSettings()),
    private val log: (String) -> Unit = {},
    /**
     * Another person is about to be read than the one whose readings this phone holds: deletes those readings and the
     * treatments logged here, and starts monitoring over (the app's; Appendix G finding 6).
     */
    private val anotherPerson: suspend () -> Unit = {},
    /** Abbott's status page (LibreView's maintenance windows); null = never asked (tests). */
    private val statusPage: LibreViewStatus? = null,
    /** A maintenance window of the account's region begins in an hour: the one heads-up (the app posts it). */
    private val warn: (MaintenanceWindow) -> Unit = {},
) : ReadingSource {
    private val statusFlow = MutableStateFlow<SourceStatus>(SourceStatus.NotStarted)
    override val status: StateFlow<SourceStatus> = statusFlow.asStateFlow()

    private val sensorFlow = MutableStateFlow<SensorState>(SensorState.Active(endsAt = null))
    override val sensor: StateFlow<SensorState> = sensorFlow.asStateFlow()

    /**
     * The sensor LibreLinkUp says is running, with the wear length (Sensor end in calendar, the takeover's start,
     * Settings › Sensor), null = none known.
     */
    val sensorLife: Flow<NsSensor?> =
        combine(store.data, life) { st, lf -> effective(st.sensorLife, lf, clock.now()) }.distinctUntilChanged()

    private fun effective(
        said: NsSensor?,
        lf: LifeSettings,
        now: Instant,
    ) = NsSensorLife.effective(SourceKind.LibreLinkUp, said, lf, now)

    private val live = MutableStateFlow(Live())

    private data class Live(
        val outcome: CallOutcome? = null,
        val online: Boolean = true,
        val newest: Instant? = null,
        /** The last time LibreLinkUp answered a graph call (null = not since the source started). */
        val answeredAt: Instant? = null,
    )

    private val kicks = Channel<Unit>(Channel.CONFLATED)

    /** The person the last call read (another one starts from nothing). */
    @Volatile private var lastPatient: String? = null

    @Volatile private var peopleCheckedS = 0L

    /** Another person a sign-in or *Try again* found to read, waiting for the person's OK ([LluSourceView.pending]). */
    private val pending = MutableStateFlow<LluPerson?>(null)

    /** The newest live reading of the person read (epoch s, null = none yet) and the calls' timing to the next one. */
    @Volatile private var liveNewestS: Long? = null

    @Volatile private var timing = LluPoll.Timing()

    /** LibreLinkUp asked to wait (429): no call before this (epoch s), whatever kicks the loop; a new sign-in ends it. */
    @Volatile private var holdUntilS = 0L

    /** The account region's maintenance windows, from the status page's last good answer (status.json). */
    private val windows = MutableStateFlow<List<MaintenanceWindow>>(emptyList())

    /** When the status page was last asked (epoch s; 0 = never this run). */
    @Volatile private var statusAskedS = 0L

    /** What the pages show. */
    val view: Flow<LluSourceView> =
        combine(
            config.data,
            combine(store.data, life, pending, ::Triple),
            live,
            combine(account.saved, windows, ::Pair),
            ticker(),
        ) { s, (st, lf, waiting), l, (saved, ws), now ->
            val readable = saved as? LluAccount.Saved.Readable
            val sensor = effective(st.sensorLife, lf, now)
            val health = health(l, now, sensor)
            LluSourceView(
                signedIn = readable != null && !readable.signedOut,
                email = readable?.email.orEmpty(),
                signInAgain = saved == LluAccount.Saved.Unreadable || readable?.signedOut == true,
                patient = s.patient,
                name = st.name,
                people = st.people.map { LluPerson(it.id, it.name) },
                health = health,
                newest = l.newest,
                sensor = sensor,
                held = st.held,
                pending = waiting,
                until = LibreViewStatus.until(ws, now).takeIf { health == OnlineHealth.Maintenance },
                ahead = LibreViewStatus.upcoming(ws, now).takeIf { readable != null && !readable.signedOut },
            )
        }.distinctUntilChanged()

    /**
     * The name of the person read, as LibreLinkUp says it (null = no one picked, or no name): the hub's name while
     * LibreLinkUp is the source (alerts, the notification, widgets, Settings › Source).
     */
    val personName: Flow<String?> =
        combine(config.data, store.data) { s, st -> st.name.takeIf { s.patient.isNotEmpty() && it.isNotEmpty() } }
            .distinctUntilChanged()

    /**
     * The app that uploads the person's glucose, as LibreLinkUp shows it (the LibreView upload while LibreLinkUp is the
     * source: which identity it may send treatments under, and its watchdog): null while no one is picked.
     */
    val uploader: Flow<LluUploader?> =
        combine(config.data, store.data, live) { s, st, l ->
            if (s.patient.isEmpty()) null else LluUploader(st.deviceType.takeIf { it != 0 }, l.newest, l.answeredAt)
        }.distinctUntilChanged()

    /**
     * The LibreLinkUp sensor's family is one Sukrara has tested directly ([MoveRules.testedFamily]): with your glucose
     * and no loop app, LibreView maintenance's quiet *Read the sensor in Sukrara* (the app's [MoveRules.mayOffer]).
     */
    val testedFamily: Flow<Boolean> = store.data.map { MoveRules.testedFamily(it.sensorPt) }.distinctUntilChanged()

    /** The next LibreView maintenance window of the account's region (home's line from 12 h before). */
    val maintenanceAhead: Flow<MaintenanceWindow?> = view.map { it.ahead }.distinctUntilChanged()

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
                LluSession.Maintenance -> LluConnectResult.Maintenance
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

                LluSession.Maintenance -> {
                    LluConnectResult.Maintenance
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
            LluCallOutcome.Maintenance -> LluConnectResult.Maintenance
            LluCallOutcome.Network -> if (network.online()) LluConnectResult.Unreachable else LluConnectResult.Offline
            else -> LluConnectResult.Unreachable
        }
        val yours = config.person.first() != Person.Theirs
        val current = config.data.first().patient
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
        // another person than the one read: never read without the person's OK (Appendix G finding 6)
        val another = p.pick?.takeIf { current.isNotEmpty() && it != current }?.let { id -> people.first { it.id == id } }
        store.update { st ->
            st.copy(
                people = people.map { LluPersonState(it.id, it.name) },
                name =
                    if (another != null) {
                        st.name
                    } else {
                        people.firstOrNull { it.id == p.pick }?.name ?: st.name.takeIf { p.pick == null }.orEmpty()
                    },
            )
        }
        peopleCheckedS = clock.now().epochSecond
        // a sign-in LibreLinkUp accepted: no longer asked to wait
        holdUntilS = 0L
        pending.value = another
        if (another != null) return LluConnectResult.Connected(people, p.pick)
        if (p.state == LluPick.State.None) {
            config.set(LluSourceSettings(patient = ""))
            return LluConnectResult.NoOne
        }
        read(p.pick.orEmpty())
        return LluConnectResult.Connected(people, p.pick)
    }

    /** Reads [patient] from now on, asked at once: a change kicks through the config, the same person here (once). */
    private suspend fun read(patient: String) {
        val same = config.data.first().patient == patient
        config.set(LluSourceSettings(patient = patient))
        if (same) kick()
    }

    /**
     * The person picked among the account's (several, or the one picked before stopped sharing), or the one a sign-in
     * found, once the person said so: another person than the one whose readings this phone holds deletes them first
     * ([LluSourceState.held], at the next call).
     */
    suspend fun choose(patient: String) {
        pending.value = null
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
        read(patient)
    }

    /**
     * A change of whose glucose cleared the pick (`source.libreLinkUpPatient`) and deleted the history: the other
     * person's name goes with it, and no LibreLinkUp readings are held any more. [recheck] then picks again by the rule.
     */
    suspend fun forgetPick() {
        pending.value = null
        store.update { it.copy(name = "", held = "", deviceType = 0) }
    }

    /** *Cancel* on the person a sign-in or *Try again* found ([LluSourceView.pending]): the one read stays. */
    fun keepPerson() {
        pending.value = null
    }

    /** Signs out of the source's account (Settings › Source): the readings stay, the source waits for a new sign-in. */
    suspend fun signOut() {
        account.forget()
        config.set(LluSourceSettings())
        pending.value = null
        // the readings stay: whose they are is kept, so the next account's person can't mix with them
        store.update { LluSourceState(held = it.held) }
        live.value = Live(online = network.online())
        kick()
    }

    override fun onBluetoothOn() = Unit

    override fun onBluetoothOff() = Unit

    override val readings: Flow<List<Reading>> =
        channelFlow {
            live.value = Live(online = network.online())
            liveNewestS = null
            timing = LluPoll.Timing()
            statusFlow.value = SourceStatus.Connecting
            launch { network.back.collect { kick() } }
            launch { config.data.drop(1).collect { kick() } }
            // a wear length chosen on the Sensor page applies at once (also one chosen before this subscribed: no drop)
            launch { life.distinctUntilChanged().collect { publish() } }
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
            val entered = c.outcome == CallOutcome.Maintenance && l.outcome != CallOutcome.Maintenance
            live.value =
                l.copy(
                    outcome = c.outcome ?: l.outcome,
                    online = network.online(),
                    newest = c.newest ?: l.newest,
                    answeredAt = if (c.outcome == CallOutcome.Ok) clock.now() else l.answeredAt,
                )
            // LibreView's windows: at once on entering maintenance and every 10 min in it (its end), else every 6 h
            if (c.outcome != null) askStatus(maintenance = c.outcome == CallOutcome.Maintenance, entered = entered)
            publish()
            val nowS = clock.now().epochSecond
            if (c.outcome == CallOutcome.Ok) {
                // timed to the next reading: the margin follows how soon LibreLinkUp has them
                val found = c.live?.epochSecond
                timing = LluPoll.timing(timing, nowS, liveNewestS, found)
                if (found != null && (liveNewestS ?: Long.MIN_VALUE) < found) liveNewestS = found
            }
            val untilS = LibreViewStatus.until(windows.value, clock.now())?.epochSecond
            val wait = LluPoll.next(c.outcome, failures, c.retryAfterS, nowS, liveNewestS, timing.marginS, untilS)
            // asked to wait: nothing calls LibreLinkUp before then (each call may lengthen the lockout); a sign-in does
            holdUntilS = if (c.outcome == CallOutcome.RateLimited && wait != null) clock.now().epochSecond + wait else 0L
            waitFor(wait)
        }
    }

    /**
     * Abbott's status page, asked only while LibreLinkUp is a signed-in source (status.json): at once when a call
     * [entered] maintenance and every [LibreViewStatus.MAINTENANCE_EVERY_S] while it lasts, else every
     * [LibreViewStatus.EVERY_S]. A failure keeps the last good answer and says nothing.
     */
    private suspend fun askStatus(
        maintenance: Boolean,
        entered: Boolean,
    ) {
        val page = statusPage ?: return
        val nowS = clock.now().epochSecond
        val every = if (maintenance) LibreViewStatus.MAINTENANCE_EVERY_S else LibreViewStatus.EVERY_S
        if (!entered && statusAskedS > 0 && nowS - statusAskedS < every) return
        statusAskedS = nowS
        val region = account.region()
        withContext(io) { page.fetch(region) }?.let { windows.value = it }
    }

    /**
     * The one heads-up a window ([LluSourceState.warnedS]): [LibreViewStatus.NOTIFY_S] before the next window of the
     * account's region begins, while LibreLinkUp is read (never for a window already begun or past).
     */
    private suspend fun warnAhead(now: Instant) {
        val next = LibreViewStatus.upcoming(windows.value, now) ?: return
        if (Duration.between(now, next.start).seconds > LibreViewStatus.NOTIFY_S) return
        if (store.data.first().warnedS == next.start.epochSecond) return
        store.update { it.copy(warnedS = next.start.epochSecond) }
        warn(next)
    }

    /** Waits [seconds] (null = until a kick), publishing the health as it ages on the way. */
    private suspend fun waitFor(seconds: Long?) {
        var left = (seconds ?: Long.MAX_VALUE / 1000) * 1000
        while (left > 0) {
            val step = minOf(left, TICK_MS)
            val kicked = withTimeoutOrNull(step) { kicks.receive() } != null
            // a kick (the network back, *Try again*, a pick) asks now, unless LibreLinkUp asked to wait longer
            if (kicked && clock.now().epochSecond >= holdUntilS) return
            if (!kicked) left -= step
            publish()
        }
    }

    private suspend fun publish() {
        val l = live.value.copy(online = network.online())
        live.value = l
        val now = clock.now()
        val sensor = effective(store.data.first().sensorLife, life.first(), now)
        sensorFlow.value = sensor?.state(l.newest, now) ?: SensorState.Active(endsAt = null)
        val h = health(l, now, sensor)
        statusFlow.value =
            when {
                // warming up: the newest reading is the old sensor's, so not live (and no problem: the card says it)
                h == OnlineHealth.Connecting || h == OnlineHealth.WarmingUp -> {
                    SourceStatus.Connecting
                }

                h.fine && l.newest != null -> {
                    SourceStatus.Live(l.newest)
                }

                h.fine -> {
                    SourceStatus.Connecting
                }

                // LibreView's maintenance: every surface says it, with its end when the status page gives one
                h == OnlineHealth.Maintenance -> {
                    SourceStatus.Interrupted(
                        l.newest ?: now,
                        h.problem,
                        LibreViewStatus.until(windows.value, now),
                    )
                }

                else -> {
                    SourceStatus.Interrupted(l.newest ?: now, h.problem)
                }
            }
        if (l.outcome != null) warnAhead(now)
    }

    /** The health at [now], the sensor LibreLinkUp says ([life]) warming up or not (contracts/sources/health.json). */
    private fun health(
        l: Live,
        now: Instant,
        life: NsSensor?,
    ): OnlineHealth =
        OnlineHealth.of(
            l.outcome,
            l.online,
            l.newest?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) },
            warmingUp = life?.state(l.newest, now) is SensorState.WarmingUp,
        )

    private class Cycle(
        /** null = not set up (no account, or no one picked: the Connect step). */
        val outcome: CallOutcome?,
        val newest: Instant? = null,
        val retryAfterS: Long? = null,
        /** The live reading in the answer (null = none): the calls are timed to the next one. */
        val live: Instant? = null,
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

            LluCallOutcome.Maintenance -> CallOutcome.Maintenance
        }

    /** One call: the session, whom to read (now and then), then the graph. */
    private suspend fun cycle(out: ProducerScope<List<Reading>>): Cycle {
        val patient = config.data.first().patient
        val session =
            when (val s = account.session()) {
                is LluSession.Ready -> s
                else -> return failed(s)
            }
        if (patient.isEmpty()) return Cycle(null)
        if (patient != lastPatient) {
            // another person: what the last one had says nothing about this one
            lastPatient = patient
            live.value = live.value.copy(newest = null)
            liveNewestS = null
            timing = LluPoll.Timing()
        }
        val held = store.data.first().held
        if (held != patient) {
            // the readings this phone holds are another person's: deleted before this one's arrive (never mixed)
            if (held.isNotEmpty()) anotherPerson()
            store.update { it.copy(held = patient) }
        }
        val now = clock.now()
        val checked = now.epochSecond - peopleCheckedS < LluPoll.CONNECTIONS_EVERY_S
        if (!checked) shared(session, patient, now)?.let { return it }
        val r = account.call(session) { account.api.get(LibreLinkUp.graph(patient), it.region, it.token, it.accountId) }
        if (r.outcome != LluCallOutcome.Ok) {
            // LibreLinkUp refuses this person's graph: they may have stopped sharing (*Pick again*), said now, not in an
            // hour as *stopped*
            if (r.outcome == LluCallOutcome.Retired && checked) shared(session, patient, now)?.let { return it }
            return Cycle(outcomeOf(r.outcome), retryAfterS = r.retryAfterS)
        }
        val g = LibreLinkUp.graph(r.body ?: return Cycle(CallOutcome.Server))
        val known = live.value.newest
        // the whole 12 h at the first call and after a gap, else only what's new (the graph's old points don't change)
        val from = if (known == null || Duration.between(known, now) > GAP) Instant.EPOCH else known.minus(OVERLAP)
        val batch = g.readings.filter { it.t.isAfter(from) && !it.t.isAfter(now.plus(AHEAD)) }
        // another person picked while this call ran: the earlier person's answer is dropped, never stored as theirs
        if (config.data.first().patient != patient) return Cycle(null)
        if (batch.isNotEmpty()) out.send(batch)
        val newestLive = g.readings.filter { it.source == SourceId.LibreLinkUp }.maxOfOrNull { it.t }
        val newest = listOfNotNull(newestLive ?: g.readings.maxOfOrNull { it.t }, known).maxOrNull()
        store.update { st ->
            st.copy(
                name = g.name.ifEmpty { st.name },
                sensorSerial = g.sensor?.serial ?: st.sensorSerial,
                sensorStartS = g.sensor?.start?.epochSecond ?: st.sensorStartS,
                deviceType = g.deviceType ?: st.deviceType,
                sensorPt = g.sensor?.pt ?: st.sensorPt,
            )
        }
        return Cycle(CallOutcome.Ok, newest, live = newestLive)
    }

    /**
     * Whom the account follows, read again (a token that ended before its time signs in again once, a version LibreLinkUp
     * asks for is sent once): null while [patient] is still shared, else the cycle's end (not shared, or the call's
     * failure).
     */
    private suspend fun shared(
        session: LluSession.Ready,
        patient: String,
        now: Instant,
    ): Cycle? {
        val (people, r) = account.people(session)
        if (people == null) return Cycle(outcomeOf(r.outcome), retryAfterS = r.retryAfterS)
        peopleCheckedS = now.epochSecond
        store.update { it.copy(people = people.map { p -> LluPersonState(p.id, p.name) }) }
        return if (people.none { it.id == patient }) Cycle(CallOutcome.NotShared) else null
    }

    /** A session that isn't ready, as the health reads it (no account at all = not set up). */
    private fun failed(s: LluSession): Cycle =
        when (s) {
            is LluSession.Ready -> Cycle(CallOutcome.Server)
            LluSession.SignedOut -> Cycle(if (account.saved.value == LluAccount.Saved.None) null else CallOutcome.Refused)
            is LluSession.Terms -> Cycle(CallOutcome.Terms)
            is LluSession.RateLimited -> Cycle(CallOutcome.RateLimited, retryAfterS = s.retryAfterS)
            LluSession.Retired -> Cycle(CallOutcome.Retired)
            LluSession.Maintenance -> Cycle(CallOutcome.Maintenance)
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
