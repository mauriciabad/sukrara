// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.libreview

import app.mauri.sukrara.core.data.libreview.LibreViewAccountState
import app.mauri.sukrara.core.data.libreview.LibreViewLogin
import app.mauri.sukrara.core.data.libreview.email
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.storage.SecretUnreadableException
import app.mauri.sukrara.core.model.storage.SecureStore
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import app.mauri.sukrara.core.model.storage.UploadHold
import app.mauri.sukrara.designsystem.errors.Trouble
import app.mauri.sukrara.designsystem.errors.UserErrors
import app.mauri.sukrara.integrations.CurrentSensor
import app.mauri.sukrara.integrations.librelinkup.LluUploader
import app.mauri.sukrara.integrations.upload.UploadBackoff
import app.mauri.sukrara.integrations.upload.UploadLoop
import app.mauri.sukrara.integrations.upload.UploadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.util.TreeMap
import java.util.UUID
import kotlin.random.Random

/** The page's switches (schema `apps.libreview`, JSON in store `integrations`); never the token. */
@Serializable
data class LvSettings(
    val on: Boolean = false,
    val glucose: Boolean = true,
    val treatments: Boolean = true,
)

/**
 * A batch of readings by life count, tagged with the sensor they came from: a Libre 3's from the sensor link (5-minute
 * history), or a sensor another app reads ([everyMinute]: Juggluco's minute readings; only a Libre 1 / 2 goes up).
 */
data class LvReadings(
    val serial: String,
    val current: LvGlucose?,
    val history: List<LvGlucose>,
    val everyMinute: Boolean = false,
)

/** What the LibreView page shows. */
data class LvPageState(
    val settings: LvSettings,
    /** The app's LibreView account is signed in (once, on the LibreView account screen: `LibreViewLogin`). */
    val signedIn: Boolean,
    /** The account's email ("" without one). */
    val email: String,
    val status: UploadStatus,
    /** Sensor glucose goes up from here (null: it isn't uploading). */
    val glucoseFrom: Instant?,
    /** *Upload every* (`apps.libreviewInterval`), minutes. */
    val intervalMin: Double = LibreViewUploader.INTERVAL.default,
    /** LibreView no longer accepts the account's saved password: sign in again on the account screen. */
    val signInAgain: Boolean = false,
    /**
     * The glucose comes from a source that can't go up (another family, or Juggluco reading anything but a Libre 1 / 2):
     * only a Libre 3 read here or a Libre 1 / 2 read through Juggluco can.
     */
    val otherSource: SourceId? = null,
    /** The phone holds the current sensor's glucose from this moment, before [glucoseFrom]: *Send earlier glucose*. */
    val earlierFrom: Instant? = null,
    /**
     * The official app uploads the glucose (LibreLinkUp is the source) and Sukrara sends the treatments beside it, under
     * this identity (docs/45; the clinic sees them under its [LvIdentity.device]); null = Sukrara is the uploader, or
     * the official app's identity isn't known (nothing goes: [UploadStatus.OfficialAppUploads]).
     */
    val besideAs: LvIdentity? = null,
)

/**
 * What LibreView has of the entries while the upload runs (the wearer, signed in, switch on), for home's list: [from] =
 * new entries go up when changed after it (epoch s; 0 = the entries switch is off: no marks); [stopped] = nothing goes
 * up now: [held] until the person acts (sign in again, take over from another device, or the watchdog stopped it), or
 * [refused] (LibreView refused the last upload) until the next change; [waitsForLibreApp] = the official app uploads and its identity isn't known: every entry waits,
 * nothing is wrong, and they go up once Sukrara reads the sensor and the person takes over; [official] = the identity
 * the official app uploads under: an entry LibreView has under it (sent before LibreLinkUp became the source) waits the
 * same way for its edits and deletes (signing in there would stop the official app's upload).
 */
data class LvEntryMarks(
    val ledger: Map<String, LvSent>,
    val from: Long,
    /** Held until the person acts: sign in again, *Take over*, *Send again* after the Libre app stopped. */
    val held: Boolean,
    val waitsForLibreApp: Boolean = false,
    val official: LvIdentity? = null,
    /** LibreView refused the last upload: nothing goes until an entry or a setting changes (a delete is such a change). */
    val refused: Boolean = false,
) {
    /** Nothing goes up now ([held], or [refused]): the rows' *stopped* mark. */
    val stopped: Boolean get() = held || refused

    fun mark(
        t: Treatment,
        now: Instant,
    ): LvMark? = if (from == 0L) null else LibreViewQueue.mark(t, ledger, from, now.epochSecond)

    /** [t]'s change waits for the official app to stop uploading ([waitsForLibreApp], or [t] lives under [official]). */
    fun waits(t: Treatment): Boolean =
        waitsForLibreApp || (official != null && ledger[t.id]?.let { it.v != 0L && LvIdentity.of(it.via) == official } == true)

    /**
     * Deleting [t] takes it out of LibreView on the next upload: it has a live version, and the upload isn't held by
     * something only the person lifts or waiting for the Libre app. A refused upload doesn't hold it: the delete is the
     * change that sends again (the owner's phone, d342b0818: the delete went up, the snackbar didn't say so).
     */
    fun removesOnDelete(t: Treatment): Boolean = !held && !waits(t) && (ledger[t.id]?.v ?: 0L) != 0L
}

/** Where the upload stands: Sukrara is the uploader, or it sends beside the official app ([beside]). */
private data class LvMode(
    val beside: Boolean,
    /** The identity the official app uploads under (null: Sukrara is the uploader, or it isn't known). */
    val official: LvIdentity?,
) {
    /** The identity new entries go up under; null = none (beside an app whose identity isn't known: nothing goes). */
    val send: LvIdentity? get() = if (!beside) LvIdentity.Libre3 else official?.other

    companion object {
        fun of(
            r: SourceSpec?,
            app: LluUploader?,
        ) = if (r?.officialAppUploads == true) LvMode(true, LvIdentity.ofDeviceType(app?.deviceType)) else LvMode(false, null)
    }
}

/** The result of *Take over*. */
sealed interface LvTakeOver {
    data object Done : LvTakeOver

    /** No account to take over with (signed out, or LibreView refused the saved password): the account screen. */
    data object SignIn : LvTakeOver

    data class Failed(val why: Trouble) : LvTakeOver
}

/**
 * Settings › Share with apps › LibreView: the wearer's entries and Libre glucose (a Libre 3 this phone reads, a Libre 1 /
 * 2 read through Juggluco) to their LibreView account, the way the FreeStyle Libre 3 app sends them: one device, one
 * identity for every family, so no two uploaders fight over the account (docs/41). Off by default; wearer only.
 *
 * While the official app uploads the glucose (LibreLinkUp is the source), Sukrara sends only the entries, under the app
 * identity the official app doesn't use ([LvIdentity], from LibreLinkUp's device type; none while it's unknown), with
 * its own device id there: no glucose, no sensor registration, no `sensorstart`. It never signs in on the official
 * app's identity ([signInOn], the one way in), and a watchdog ([LvWatchdog]) stops it, saying why, if LibreLinkUp stops
 * getting the official app's readings right after one of its sign-ins (docs/45). Each entry stays under the identity it
 * first went up under ([LibreViewQueue.home]): back as the uploader, an entry sent as LibreLink is corrected there,
 * never sent again as the Libre 3 app.
 *
 * The account is the app's one LibreView account ([login], issue #177): signed in once on the account screen, never
 * asked again here. With it the upload signs in by itself (the LibreView app login) and keeps only that login's token
 * (SecureStore `libreview`); when the token stops working it signs in again by itself. This phone becomes the account's
 * uploading device only when the person chooses *Take over* (another device uploads: nothing goes up until then).
 * LibreView refusing the saved password asks to sign in again (the account screen); signing out drops the token.
 * Glucose and entries go up from the moment they were switched on; what LibreView got is remembered so an edit or a
 * delete corrects it. Libre 3 history not sent yet is kept in [unsent] too, so a process death doesn't lose it.
 */
class LibreViewUploader(
    private val settings: SettingsRepository,
    private val secure: SecureStore,
    private val store: StateStore<LvState>,
    private val unsent: StateStore<LvUnsent>,
    private val treatments: TreatmentsRepository,
    private val readings: Flow<LvReadings>,
    private val sensor: Flow<CurrentSensor?>,
    /** THE LibreView account (one per app; the sensor and Import history use it too). */
    private val login: LibreViewLogin,
    private val role: Flow<SourceSpec?>,
    private val client: LibreViewClient,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val log: (String) -> Unit = {},
    /** Holds back an entry whose pen session is still open (it goes up once the session closes). */
    private val hold: UploadHold = UploadHold.NONE,
    /** The newest stored reading: what the phone has that LibreView should get (the stall warning). */
    latest: Flow<Reading?> = flowOf(null),
    /** What LibreLinkUp shows of the official app (its device type, its newest reading), while it is the source. */
    private val officialApp: Flow<LluUploader?> = flowOf(null),
) {
    /** *Upload every*, ms: the loop's wait, and how often new glucose wakes it. */
    @Volatile private var intervalMs = (INTERVAL.default * 60_000).toLong()
    private val loop = UploadLoop({ intervalMs }, io, { e -> log("libreview cycle: ${e.javaClass.simpleName}") }) { cycle() }
    private val mutex = Mutex()

    /** Readings of the current sensor, kept in memory (its whole life: [LibreViewQueue.HISTORY_MIN]). */
    private var bufferSerial: String? = null
    private var current: LvGlucose? = null
    private val history = TreeMap<Int, LvGlucose>()

    /** The buffer's sensor and its oldest reading held (epoch s): what *Send earlier glucose* can reach back to. */
    private val held = MutableStateFlow<Pair<String, Long>?>(null)

    val settingsFlow: Flow<LvSettings> = settings.flow(SETTING).map(::decode)

    val intervalFlow: Flow<Double> = settings.flow(INTERVAL)

    /** The wearer's newest reading from this phone's own sensor (any source: what LibreView should get). */
    private val newest: Flow<Reading?> =
        latest
            .map { r ->
                r?.takeIf { it.source in SourceId.FirstHand && it.source !in SourceId.Online }
            }.distinctUntilChanged()

    /**
     * Since when LibreView lacks the glucose the phone has (epoch s) once uploading it has stalled
     * ([LibreViewQueue.behind]), null while it hasn't or glucose isn't uploading.
     */
    private val behindS: Flow<Long?> =
        combine(settingsFlow, store.data, newest, intervalFlow) { s, st, r, every ->
            if (s.on && s.glucose) LibreViewQueue.behind(r?.t?.epochSecond, st.glucoseUpToS, st.glucoseFrom, every) else null
        }.distinctUntilChanged()

    /**
     * Where the newest reading came from when it can't go up, else null: a Libre 3 this phone reads itself and a Libre 1
     * / 2 read through Juggluco can (by the current sensor's serial).
     */
    private val otherSource: Flow<SourceId?> =
        combine(newest, sensor) { r, s ->
            r?.source?.takeIf { src ->
                src != SourceId.Libre3 && !(src == SourceId.Juggluco && s != null && LibreSerial.libre12(s.serial))
            }
        }.distinctUntilChanged()

    /** The oldest reading held of the current sensor (epoch s), null when none. */
    private val heldFrom: Flow<Long?> =
        combine(held, sensor) { h, s -> h?.takeIf { s != null && it.first == s.serial }?.second }.distinctUntilChanged()

    /** Sukrara the uploader, or beside the official app (with its identity when LibreLinkUp says it). */
    private val modeFlow: Flow<LvMode> = combine(role, officialApp) { r, app -> LvMode.of(r, app) }.distinctUntilChanged()

    val page: Flow<LvPageState> =
        combine(
            combine(settingsFlow, login.account, store.data, role, intervalFlow) { s, a, st, r, every -> Page(s, a, st, r, every) },
            behindS,
            otherSource,
            heldFrom,
            modeFlow,
        ) { (s, a, st, r, every), behind, other, oldest, mode ->
            val from = st.glucoseFrom.takeIf { it > 0 }?.let(Instant::ofEpochSecond)
            LvPageState(
                s,
                a is LibreViewAccountState.SignedIn,
                a.email.orEmpty(),
                status(s, a, st, r, behind, other, mode),
                from,
                every,
                a is LibreViewAccountState.SignInAgain,
                other,
                oldest?.takeIf { s.on && s.glucose && it < st.glucoseFrom }?.let(Instant::ofEpochSecond),
                if (mode.beside) mode.send else null,
            )
        }

    private data class Page(
        val s: LvSettings,
        val a: LibreViewAccountState,
        val st: LvState,
        val r: SourceSpec?,
        val every: Double,
    )

    /**
     * Home's warning: since when LibreView lacks the wearer's glucose while uploading it has stalled (the oldest
     * 5-minute value it lacks), null otherwise: off, signed out, a follower, or only the internet missing (it goes up
     * by itself once the phone is back online; the page still says so).
     */
    val glucoseBehind: Flow<Instant?> =
        combine(behindS, login.account, store.data, role) { b, a, st, r ->
            b
                ?.takeIf {
                    r?.owner == true && a != LibreViewAccountState.SignedOut &&
                        (st.glucoseError ?: st.lastError) != Trouble.NoInternet
                }?.let(Instant::ofEpochSecond)
        }.distinctUntilChanged()

    /**
     * Home's warning: the sign-in after which LibreLinkUp stopped getting the official app's readings, so this upload
     * stopped ([LvWatchdog]); null while it didn't, or the upload is off.
     */
    val libreAppStopped: Flow<Instant?> =
        combine(settingsFlow, store.data, role, modeFlow) { s, st, r, mode ->
            st.libreAppStopped.takeIf { s.on && r?.owner == true && mode.beside && it > 0 }?.let(Instant::ofEpochSecond)
        }.distinctUntilChanged()

    /** What LibreView has of the entries while the upload runs (home's cloud marks); null while it's off. */
    val entryMarks: Flow<LvEntryMarks?> =
        combine(settingsFlow, login.account, store.data, role, modeFlow) { s, a, st, r, mode ->
            if (s.on && a != LibreViewAccountState.SignedOut && r?.owner == true) {
                val send = mode.send
                val held =
                    (send != null && st.otherOn(send)) || a is LibreViewAccountState.SignInAgain ||
                        (mode.beside && st.libreAppStopped > 0)
                LvEntryMarks(
                    st.ledger,
                    if (s.treatments) st.entriesFrom else 0L,
                    held,
                    waitsForLibreApp = mode.beside && send == null,
                    official = mode.official,
                    refused = st.refused,
                )
            } else {
                null
            }
        }.distinctUntilChanged()

    /**
     * Whether the app has a LibreView account (signed in, or one LibreView asks to sign in to again); emits once the
     * account was read, so nothing claims "signed out" before that (no clinic banner flash).
     */
    val signedInKnown: Flow<Boolean?> = login.account.map { it != LibreViewAccountState.SignedOut }

    /**
     * Reads the history not sent yet; call once from the app's load step. A token without an account (signed out while
     * the app wasn't running) goes.
     */
    suspend fun load() {
        if (login.current() == LibreViewAccountState.SignedOut) dropTokens()
        val kept = unsent.data.first()
        if (kept.serial.isNotEmpty()) {
            synchronized(history) {
                // the sensor link's first batch may already be here: it wins for its own life counts
                if (bufferSerial == null || bufferSerial == kept.serial) {
                    bufferSerial = kept.serial
                    kept.history.forEach { history.putIfAbsent(it.lifeCount, it) }
                    noteHeld()
                }
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (!loop.start(scope)) return
        scope.launch {
            readings.collect { r ->
                if (buffer(r)) keepUnsent()
                // new glucose goes up once an interval has passed since the last upload (entries go at once)
                val since = clock.now().toEpochMilli() - store.data.first().lastOkMs
                if (settingsFlow.first().glucose && since >= intervalMs) loop.kick()
            }
        }
        scope.launch {
            intervalFlow.distinctUntilChanged().collect { min ->
                intervalMs = (min * 60_000).toLong()
                loop.kick()
            }
        }
        scope.launch {
            val now = clock.now()
            treatments.since(now - Duration.ofDays(31)).drop(1).collect { changed() }
        }
        scope.launch { settingsFlow.distinctUntilChanged().drop(1).collect { changed() } }
        scope.launch { role.distinctUntilChanged().drop(1).collect { loop.kick() } }
        // LibreLinkUp's news: the watchdog judges a sign-in at once; an identity it now knows lets the entries go
        scope.launch {
            var known = officialApp.first()?.deviceType
            officialApp.distinctUntilChanged().drop(1).collect { app ->
                mutex.withLock { watchdogLocked() }
                if (app?.deviceType != known) loop.kick()
                known = app?.deviceType
            }
        }
        scope.launch {
            login.account
                .distinctUntilChanged()
                .drop(1)
                .collect { followLogin(it) }
        }
    }

    /**
     * The account changed on the account screen: signed out, the token goes (whatever LibreView got stays remembered,
     * so signing in again still corrects it); signed in, the upload goes on with it.
     */
    internal suspend fun followLogin(account: LibreViewAccountState) {
        if (account == LibreViewAccountState.SignedOut) {
            mutex.withLock {
                dropTokens()
                store.update {
                    it.copy(
                        otherDevice = false,
                        linkOtherDevice = false,
                        refused = false,
                        lastError = null,
                        failures = 0,
                        nextAttemptMs = 0,
                        watch = null,
                        libreAppStopped = 0,
                    )
                }
            }
        }
        loop.kick()
    }

    /**
     * Keeps the newest batch's readings for the next cycle; another sensor's batch replaces them. True when the history
     * changed (what is kept in [unsent] may have to change too).
     */
    internal fun buffer(r: LvReadings): Boolean {
        // a sensor another app reads goes up only as a Libre 1 / 2 (its serial and life count make a Libre's records;
        // any other family would show in LibreView as Abbott data), reduced to LibreView's 5-minute history
        if (r.everyMinute && !LibreSerial.libre12(r.serial)) return false
        val values = if (r.everyMinute) LibreViewQueue.slots(r.history) else r.history
        return synchronized(history) {
            var changed = false
            if (r.serial != bufferSerial) {
                bufferSerial = r.serial
                current = null
                changed = history.isNotEmpty()
                history.clear()
            }
            r.current?.let { c -> if ((current?.lifeCount ?: -1) < c.lifeCount) current = c }
            values.forEach { v ->
                // a slot keeps its first reading, whichever batch brings it
                val keep = history[v.lifeCount]?.takeIf { r.everyMinute && it.epochSec <= v.epochSec } ?: v
                if (history.put(v.lifeCount, keep) != keep) changed = true
            }
            if (history.isNotEmpty()) history.headMap(history.lastKey() - LibreViewQueue.HISTORY_MIN).clear()
            noteHeld()
            changed
        }
    }

    /** The buffer's oldest reading, for *Send earlier glucose* (call holding the buffer's lock). */
    private fun noteHeld() {
        val serial = bufferSerial
        held.value = if (serial == null || history.isEmpty()) null else serial to history.values.minOf { it.epochSec }
    }

    /**
     * *Send earlier glucose*: the current sensor's glucose the phone holds from before the moment glucose was switched
     * on goes up too (the person chose it: another app may have sent it already).
     */
    suspend fun sendEarlier() {
        val oldest = heldFrom.first() ?: return
        mutex.withLock {
            store.update { if (it.glucoseFrom > oldest) it.copy(glucoseFrom = oldest) else it }
        }
        log("libreview: earlier glucose goes up")
        keepUnsent()
        loop.kick()
    }

    /** Writes what of the history LibreView still lacks to [unsent] (only when that changed). */
    internal suspend fun keepUnsent() {
        val (serial, hist) = synchronized(history) { bufferSerial to history.values.toList() }
        val keep = LibreViewQueue.unsent(serial, hist, store.data.first())
        if (unsent.data.first() != keep) unsent.update { keep }
    }

    /** An entry or a setting changed: a refused upload may go now. */
    private suspend fun changed() {
        mutex.withLock { store.update { if (it.refused) it.copy(refused = false, lastError = null) else it } }
        loop.kick()
    }

    // MARK: page actions

    /** *Upload every* [min] minutes. */
    suspend fun setInterval(min: Double) = settings.set(INTERVAL, min)

    /**
     * *Take over*: another device uploads for the account under the identity Sukrara sends under (as the uploader:
     * LibreLink, the Libre 3 app, Juggluco; beside the official app: an older install or Juggluco under the other
     * identity); this phone becomes that identity's uploading device and that one stops uploading. Only when the person
     * chose it, never by itself, and never on the official app's identity ([signInOn]).
     */
    suspend fun takeOver(): LvTakeOver =
        mutex.withLock {
            val credentials = login.credentials() ?: return@withLock LvTakeOver.SignIn
            val id = LvMode.of(role.first(), officialApp.first()).send ?: return@withLock LvTakeOver.Failed(Trouble.Other)
            val st = ensureDevice(id)
            when (val answer = signInOn(id, credentials.email, credentials.password, st.deviceOf(id), takeOver = true)) {
                is LvAuth.SignedIn -> {
                    keep(credentials.email, answer.session)
                    log("libreview: took over as ${id.id}")
                    loop.kick()
                    LvTakeOver.Done
                }

                LvAuth.OtherDevice, LvAuth.Guarded -> {
                    LvTakeOver.Failed(Trouble.Other)
                }

                LvAuth.BadCredentials -> {
                    login.rejected(credentials)
                    LvTakeOver.SignIn
                }

                is LvAuth.Failed -> {
                    LvTakeOver.Failed(answer.why)
                }
            }
        }

    /**
     * *Send again* after the watchdog stopped the upload (the person signed the official app out and in): the next cycle
     * goes on, its next sign-in watched again.
     */
    suspend fun sendAgain() {
        mutex.withLock { store.update { it.copy(libreAppStopped = 0, watch = null) } }
        log("libreview: send again after the Libre app was signed in again")
        loop.kick()
    }

    /**
     * THE one way Sukrara signs in to LibreView's app API ([LibreViewClient.signIn]): never on an identity
     * [LvIdentity.mayUse] refuses now (the role and LibreLinkUp's device type read at this moment), so never on the
     * identity the official app uploads under. A sign-in beside the official app is watched ([LvWatchdog]).
     */
    private suspend fun signInOn(
        id: LvIdentity,
        email: String,
        password: String,
        deviceId: String,
        takeOver: Boolean,
    ): LvAuth {
        val app = officialApp.first()
        val mode = LvMode.of(role.first(), app)
        if (!LvIdentity.mayUse(id, mode.beside, mode.official)) {
            log("libreview: no sign-in as ${id.id}: the Libre app may upload under it")
            return LvAuth.Guarded
        }
        val answer = withContext(io) { client.signIn(email, password, deviceId, takeOver, id) }
        if (answer is LvAuth.SignedIn && mode.beside) {
            store.update { it.copy(watch = LvWatch(clock.now().epochSecond, app?.newest?.epochSecond)) }
        }
        return answer
    }

    /**
     * The watchdog ([LvWatchdog]) on the sign-in being watched: fine, it ends; the official app's readings stopped
     * reaching LibreLinkUp, the upload stops and says why (call holding [mutex]).
     */
    private suspend fun watchdogLocked() {
        val st = store.data.first()
        val w = st.watch ?: return
        val app = officialApp.first()
        when (LvWatchdog.check(w, app?.newest?.epochSecond, app?.answeredAt?.epochSecond, clock.now().epochSecond)) {
            LvWatchdog.Verdict.Watching -> {
                Unit
            }

            LvWatchdog.Verdict.Fine -> {
                store.update { it.copy(watch = null) }
            }

            LvWatchdog.Verdict.Stopped -> {
                log("libreview: LibreLinkUp got nothing from the Libre app after the sign-in: stopped")
                store.update { it.copy(watch = null, libreAppStopped = w.atS) }
            }
        }
    }

    /**
     * Keeps a new token of [email]'s account: the same account as the last one (by LibreView's AccountId; by the email
     * only when one side doesn't know it) keeps what LibreView has; another one starts from nothing (and the other
     * identity's token, of the earlier account, goes). State first, then the token, both or neither: a token never sits
     * under another account's ledger.
     */
    private suspend fun keep(
        email: String,
        session: LvSession,
    ) = withContext(NonCancellable) {
        val e = email.trim()
        val nowS = clock.now().epochSecond
        val s = settingsFlow.first()
        val id = session.accountId.orEmpty()
        var same = true
        store.update {
            same = if (id.isNotEmpty() && it.accountId.isNotEmpty()) it.accountId == id else it.email.equals(e, ignoreCase = true)
            // another account starts from nothing but this install's devices and the sign-in being watched
            val base = if (same) it else LvState(deviceId = it.deviceId, linkDeviceId = it.linkDeviceId, watch = it.watch)
            base.withOther(session.identity, false).copy(
                email = e,
                accountId = id,
                // a new sign-in may bring the profile the sensor registration needs: it is tried at the next cycle
                sensors = base.sensors.map { st -> st.copy(registerAfterMs = 0) },
                refused = false,
                failures = 0,
                nextAttemptMs = 0,
                lastError = null,
                glucoseFrom =
                    if (same) {
                        it.glucoseFrom
                    } else if (s.on && s.glucose) {
                        nowS
                    } else {
                        0
                    },
                entriesFrom =
                    if (same) {
                        it.entriesFrom
                    } else if (s.on && s.treatments) {
                        nowS
                    } else {
                        0
                    },
            )
        }
        if (!same) secure.delete(nameOf(session.identity.other))
        secure.write(nameOf(session.identity), json.encodeToString(LvSession.serializer(), session).toByteArray())
    }

    private suspend fun dropToken(id: LvIdentity) = secure.delete(nameOf(id))

    private suspend fun dropTokens() = LvIdentity.entries.forEach { dropToken(it) }

    suspend fun change(transform: (LvSettings) -> LvSettings) {
        var old = LvSettings()
        var new = LvSettings()
        settings.update(SETTING) {
            old = decode(it)
            new = transform(old)
            encode(new)
        }
        val nowS = clock.now().epochSecond
        mutex.withLock {
            store.update {
                it.copy(
                    // each part goes up from the moment it (or the upload) is switched on
                    glucoseFrom = if (new.on && new.glucose) it.glucoseFrom.takeIf { f -> f > 0 } ?: nowS else 0,
                    entriesFrom = if (new.on && new.treatments) it.entriesFrom.takeIf { f -> f > 0 } ?: nowS else 0,
                    failures = if (new.on && !old.on) 0 else it.failures,
                    nextAttemptMs = if (new.on && !old.on) 0 else it.nextAttemptMs,
                    refused = false,
                    // switched off: a watchdog stop ends with it (on again is the person's choice)
                    libreAppStopped = if (new.on) it.libreAppStopped else 0,
                    watch = if (new.on) it.watch else null,
                )
            }
        }
        loop.kick()
    }

    fun kick() = loop.kick()

    // MARK: the cycle

    internal suspend fun cycle() =
        mutex.withLock {
            try {
                cycleLocked()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // an answer we can't read: retried later with backoff, never a tight loop
                log("libreview cycle: ${e.javaClass.simpleName}")
                save(failed(store.data.first(), clock.now().toEpochMilli(), UserErrors.of(e)))
            }
        }

    private suspend fun cycleLocked() {
        val s = settingsFlow.first()
        val r = role.first()
        if (!s.on || r?.owner != true) return
        val mode = LvMode.of(r, officialApp.first())
        if (mode.beside) {
            // the official app uploads the glucose (LibreLinkUp is the source): only entries go, under the identity it
            // doesn't use (docs/45); none while that identity isn't known, none once the watchdog stopped the upload
            if (!store.data.first().pausedForLibreApp) store.update { it.copy(pausedForLibreApp = true) }
            watchdogLocked()
            if (mode.send == null || store.data.first().libreAppStopped > 0) return
        } else if (store.data.first().pausedForLibreApp) {
            // back from LibreLinkUp (the sensor taken over): a fresh sign-in finds whether the Libre app still uploads.
            // The Libre app sent the glucose until now, so glucose goes up from here (the sensor's backfill would count
            // twice; *Send earlier glucose* is the person's choice); entries keep the identity they went up under.
            dropToken(LvIdentity.Libre3)
            val nowS = clock.now().epochSecond
            store.update {
                it.copy(
                    pausedForLibreApp = false,
                    glucoseFrom = if (it.glucoseFrom > 0) maxOf(it.glucoseFrom, nowS) else 0,
                    watch = null,
                    libreAppStopped = 0,
                )
            }
            keepUnsent()
        }
        // no usable account: the page asks to sign in (again)
        if (login.current() !is LibreViewAccountState.SignedIn) return
        val send = mode.send ?: return
        var state = store.data.first()
        val nowMs = clock.now().toEpochMilli()
        val nowS = nowMs / 1000
        if (nowMs < state.nextAttemptMs) return
        if ((s.glucose && state.glucoseFrom == 0L) || (s.treatments && state.entriesFrom == 0L)) {
            // never a backfill of what was there before the switch: a lost moment starts now
            state =
                state.copy(
                    glucoseFrom = if (s.glucose && state.glucoseFrom == 0L) nowS else state.glucoseFrom,
                    entriesFrom = if (s.treatments && state.entriesFrom == 0L) nowS else state.entriesFrom,
                )
            save(state)
        }
        // the identity new entries go under first, then the other one for the entries that live there (as the uploader:
        // corrections of what went up as LibreLink); never one the official app may use
        for (id in listOf(send, send.other)) {
            if (!LvIdentity.mayUse(id, mode.beside, mode.official)) continue
            if (!uploadOn(id, send, s, glucose = id == LvIdentity.Libre3 && !mode.beside && s.glucose)) return
        }
    }

    /** The entries LibreView lacks that live under [id] ([LibreViewQueue.home], new ones under [send]). */
    private suspend fun entriesFor(
        state: LvState,
        s: LvSettings,
        nowS: Long,
        id: LvIdentity,
        send: LvIdentity,
    ): List<LvEntry> {
        if (state.refused || (!s.treatments && state.ledger.isEmpty())) return emptyList()
        // a switched-off part still corrects what LibreView already has; imported history never goes up (LibreView has it
        // already or got it from the same place: it would count twice)
        val records =
            treatments
                .changedSince(clock.now() - Duration.ofDays(31))
                .filterNot { it.imported || it.fromSite || it.fromLink }
                .filter { LibreViewQueue.home(state.ledger, it.treatment.id, send) == id }
        return LibreViewQueue.entries(records, state.ledger, if (s.treatments) state.entriesFrom else Long.MAX_VALUE, nowS, hold)
    }

    /**
     * One upload under [id]: its entries, and the glucose when [glucose] (the Libre 3 identity as the uploader only).
     * Signs in when there is no token: always for [send] (another device there shows *Take over* before anything is
     * logged), for the other identity only when it has something to send. False when the cycle stops here.
     */
    private suspend fun uploadOn(
        id: LvIdentity,
        send: LvIdentity,
        s: LvSettings,
        glucose: Boolean,
    ): Boolean {
        var state = ensureDevice(id)
        if (state.otherOn(id)) return true
        val nowMs = clock.now().toEpochMilli()
        val nowS = nowMs / 1000
        if (id != send && entriesFor(state, s, nowS, id, send).isEmpty()) return true
        val (session, fresh) = session(state, nowMs, id) ?: return false
        // a new token of another account started from nothing
        if (fresh) state = store.data.first()

        val entries = entriesFor(state, s, nowS, id, send)
        val batch = if (glucose) glucoseBatch(state, nowS) else null
        if (entries.isEmpty() && batch == null) return true

        // glucose goes only for a sensor LibreView registered; treatments never wait for it
        var sensorPart: LvSensor? = null
        var renew = false
        if (batch != null) {
            val sensorState = state.sensors.firstOrNull { it.serial == batch.serial }
            when {
                sensorState?.registered == true -> {
                    sensorPart = batch
                }

                // a token given a moment ago isn't known yet to the registration (docs/04): the next cycle registers
                fresh || nowMs < (sensorState?.registerAfterMs ?: 0L) -> {
                    Unit
                }

                else -> {
                    when (val r = withContext(io) { client.putSensor(session, batch.serial) }) {
                        LvPost.Ok -> {
                            log("libreview: sensor registered")
                            state = state.copy(sensors = LibreViewQueue.registered(state.sensors, batch.serial)).also { save(it) }
                            sensorPart = batch
                        }

                        LvPost.SignInAgain -> {
                            tokenRefused(state, fresh, id)
                            return false
                        }

                        is LvPost.Retry -> {
                            state = registrationFailed(state, batch.serial, nowMs, r.why).also { save(it) }
                        }

                        LvPost.InvalidSession, is LvPost.Rejected -> {
                            log("libreview: sensor not registered (${(r as? LvPost.Rejected)?.why ?: "invalid session"})")
                            state = registrationFailed(state, batch.serial, nowMs, Trouble.Refused).also { save(it) }
                            // still not accepted: the next try signs in again first (a new session, the profile again)
                            val n = state.sensors.first { it.serial == batch.serial }.registerFailures
                            renew = n % RENEW_EVERY == 0
                        }
                    }
                }
            }
        }
        if (entries.isEmpty() && sensorPart == null) {
            if (renew) dropToken(id)
            return true
        }
        val body =
            when (id) {
                LvIdentity.Libre3 -> LibreViewPayload.payload(entries, state.deviceId, session.token, nowS, clock.zone, sensorPart)
                LvIdentity.LibreLink -> LibreViewPayload.linkPayload(entries, state.linkDeviceId, session.token, nowS, clock.zone)
            }
        when (val r = withContext(io) { client.postMeasurements(session, body) }) {
            LvPost.Ok -> {
                val count = entries.size + (sensorPart?.history?.size ?: 0) + (if (sensorPart?.current != null) 1 else 0)
                state =
                    state.copy(
                        ledger = LibreViewQueue.sent(state.ledger, entries, nowS, id),
                        sensors = sensorPart?.let { LibreViewQueue.sentSensor(state.sensors, it) } ?: state.sensors,
                        failures = 0,
                        nextAttemptMs = 0,
                        lastError = null,
                        lastOkMs = nowMs,
                        lastCount = count,
                        // LibreView keeps only the 5-minute values (the current one feeds LibreLinkUp)
                        glucoseUpToS = maxOf(state.glucoseUpToS, sensorPart?.history?.maxOfOrNull { it.epochSec } ?: 0L),
                        // a treatments-only upload says nothing about glucose: its problem stays
                        glucoseError = if (sensorPart != null) null else state.glucoseError,
                    )
                save(state)
                if (sensorPart != null) keepUnsent()
                if (renew) dropToken(id)
                // more than one upload's worth waiting (a long backfill): go on at once
                if (entries.size == LibreViewQueue.MAX_ENTRIES ||
                    (sensorPart?.history?.size ?: 0) == LibreViewQueue.MAX_HISTORY
                ) {
                    loop.kick()
                }
                return true
            }

            LvPost.SignInAgain -> {
                tokenRefused(state, fresh, id)
            }

            // a token given a moment ago: tried again later; an older one: the next cycle signs in again
            LvPost.InvalidSession -> {
                if (fresh) save(failed(state, nowMs, Trouble.Refused)) else tokenRefused(state, fresh = false, id)
            }

            is LvPost.Retry -> {
                save(failed(state, nowMs, r.why))
            }

            is LvPost.Rejected -> {
                log("libreview: upload refused (${r.why})")
                // refused treatments wait for a change (glucose goes on without them); refused glucose backs off
                save(
                    if (entries.isNotEmpty()) {
                        state.copy(
                            refused = true,
                            lastError = Trouble.Refused,
                        )
                    } else {
                        failed(state, nowMs, Trouble.Refused).copy(glucoseError = Trouble.Refused)
                    },
                )
                if (entries.isNotEmpty() && sensorPart != null) loop.kick()
            }
        }
        return false
    }

    /** [serial]'s registration didn't go through ([why]): glucose waits, the registration is tried again with backoff. */
    private fun registrationFailed(
        st: LvState,
        serial: String,
        nowMs: Long,
        why: Trouble,
    ): LvState =
        st.copy(
            sensors =
                LibreViewQueue.registrationFailed(st.sensors, serial) { n ->
                    nowMs + UploadBackoff.ms(n, 30_000, 15 * 60_000L, Random)
                },
            glucoseError = why,
        )

    /**
     * [id]'s token: the saved one, or a new one from the account's own login (`SetDevice:false`: it never takes over by
     * itself; [signInOn]: never on the official app's identity), and whether it is new. Null when nothing can go up now;
     * the state says why (another device uploads, server trouble) or the account does (LibreView refused the saved
     * password: sign in again).
     */
    private suspend fun session(
        state: LvState,
        nowMs: Long,
        id: LvIdentity,
    ): Pair<LvSession, Boolean>? {
        val saved =
            try {
                secure.read(nameOf(id))?.let { json.decodeFromString(LvSession.serializer(), it.decodeToString()) }
            } catch (e: SecretUnreadableException) {
                if (e.transient) {
                    save(failed(state, nowMs, Trouble.Other))
                    return null
                }
                // damaged: the account signs in again
                dropToken(id)
                null
            } catch (e: IllegalArgumentException) {
                dropToken(id)
                null
            }
        if (saved != null && saved.identity == id) return saved to false
        val credentials = login.credentials() ?: return null
        return when (val answer = signInOn(id, credentials.email, credentials.password, state.deviceOf(id), takeOver = false)) {
            is LvAuth.SignedIn -> {
                keep(credentials.email, answer.session)
                log("libreview: signed in as ${id.id} with the saved account")
                answer.session to true
            }

            LvAuth.OtherDevice -> {
                log("libreview: another device uploads as ${id.id} for the account: waiting for Take over")
                save(state.withOther(id, true).copy(lastError = null))
                null
            }

            LvAuth.BadCredentials -> {
                log("libreview: the saved password was refused: sign in again")
                login.rejected(credentials)
                null
            }

            LvAuth.Guarded -> {
                null
            }

            is LvAuth.Failed -> {
                save(failed(state, nowMs, answer.why))
                null
            }
        }
    }

    /**
     * LibreView no longer takes [id]'s token (it expired, or another device took over): the next cycle signs in again
     * with the saved account, which tells which. A token it gave a moment ago refused too: another device uploads.
     */
    private suspend fun tokenRefused(
        state: LvState,
        fresh: Boolean,
        id: LvIdentity,
    ) {
        dropToken(id)
        if (fresh) {
            log("libreview: a new token refused: another device uploads as ${id.id}")
            save(state.withOther(id, true).copy(lastError = null))
        } else {
            log("libreview: token refused: signing in again")
            save(state)
            loop.kick()
        }
    }

    private suspend fun glucoseBatch(
        state: LvState,
        nowS: Long,
    ): LvSensor? {
        val (serial, cur, hist) =
            synchronized(history) { Triple(bufferSerial, current, history.values.toList()) }
        if (serial == null) return null
        val current = sensor.first()?.takeIf { it.serial == serial }
        return LibreViewQueue.sensor(
            serial,
            current?.start?.epochSecond,
            cur,
            hist,
            state.sensors.firstOrNull { it.serial == serial },
            state.glucoseFrom,
            nowS,
            // the length the sensor reports (a Libre 3's 14 days, a 3 Plus's 15), never one size for all
            current?.wearMin ?: 0,
        )
    }

    /** The state with this install's device id under [id] (a random UUID, made once). */
    private suspend fun ensureDevice(id: LvIdentity): LvState {
        val st = store.data.first()
        if (st.deviceOf(id).isNotEmpty()) return st
        store.update {
            when {
                it.deviceOf(id).isNotEmpty() -> it
                id == LvIdentity.Libre3 -> it.copy(deviceId = UUID.randomUUID().toString())
                else -> it.copy(linkDeviceId = UUID.randomUUID().toString())
            }
        }
        return store.data.first()
    }

    private fun failed(
        st: LvState,
        nowMs: Long,
        why: Trouble,
    ): LvState {
        val n = st.failures + 1
        return st.copy(failures = n, nextAttemptMs = nowMs + UploadBackoff.ms(n, 30_000, 15 * 60_000L, Random), lastError = why)
    }

    private suspend fun save(st: LvState) = store.update { st }

    /**
     * The page's status. Glucose that stalled ([behind], epoch s) wins over the last upload: a treatments-only upload
     * never reads as "uploading" while glucose doesn't go.
     */
    private fun status(
        s: LvSettings,
        account: LibreViewAccountState,
        st: LvState,
        r: SourceSpec?,
        behind: Long?,
        other: SourceId?,
        mode: LvMode,
    ): UploadStatus =
        when {
            !s.on || r?.owner != true -> {
                UploadStatus.Off
            }

            // the official app uploads and its identity isn't known: nothing goes, not even a sign-in that could stop it
            mode.beside && mode.send == null -> {
                UploadStatus.OfficialAppUploads
            }

            account !is LibreViewAccountState.SignedIn -> {
                UploadStatus.Setup
            }

            mode.beside && st.libreAppStopped > 0 -> {
                UploadStatus.LibreAppStopped(Instant.ofEpochSecond(st.libreAppStopped))
            }

            mode.send?.let { st.otherOn(it) } == true -> {
                UploadStatus.AuthFailed
            }

            behind != null && !mode.beside -> {
                UploadStatus.Behind(Instant.ofEpochSecond(behind), st.glucoseError ?: st.lastError, other)
            }

            st.lastError != null -> {
                UploadStatus.Failing(st.lastError, st.lastOkMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli))
            }

            s.glucose && !mode.beside && st.glucoseError != null -> {
                UploadStatus.Failing(st.glucoseError, st.lastOkMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli))
            }

            st.lastOkMs > 0 -> {
                UploadStatus.Ok(Instant.ofEpochMilli(st.lastOkMs), st.lastCount)
            }

            else -> {
                UploadStatus.Waiting
            }
        }

    companion object {
        val INTERVAL = SchemaSettings.number("apps.libreviewInterval")

        /** The Libre 3 identity's token (SecureStore). */
        const val NAME = "libreview"

        /** The LibreLink identity's token (SecureStore): its own session, docs/45. */
        const val LINK_NAME = "libreview-librelink"

        fun nameOf(id: LvIdentity) = if (id == LvIdentity.Libre3) NAME else LINK_NAME

        /** Every this many registrations LibreView doesn't accept in a row, the next one signs in again first. */
        const val RENEW_EVERY = 3
        val SETTING = SchemaSettings.text("apps.libreview", default = "")
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): LvSettings =
            text.takeIf { it.isNotEmpty() }?.let { runCatching { json.decodeFromString(LvSettings.serializer(), it) }.getOrNull() }
                ?: LvSettings()

        fun encode(s: LvSettings): String = json.encodeToString(LvSettings.serializer(), s)
    }
}
