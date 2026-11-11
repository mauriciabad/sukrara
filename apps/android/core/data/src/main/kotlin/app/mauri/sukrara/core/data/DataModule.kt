// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.mauri.sukrara.core.data.db.QuarantiningOpenHelperFactory
import app.mauri.sukrara.core.data.db.RoomHistoryImport
import app.mauri.sukrara.core.data.db.RoomPenLedgerRepository
import app.mauri.sukrara.core.data.db.RoomReadingsRepository
import app.mauri.sukrara.core.data.db.RoomTreatmentsRepository
import app.mauri.sukrara.core.data.db.SukraraDatabase
import app.mauri.sukrara.core.data.diag.DiagLog
import app.mauri.sukrara.core.data.files.FileCodec
import app.mauri.sukrara.core.data.files.FileStore
import app.mauri.sukrara.core.data.json.SukraraJson
import app.mauri.sukrara.core.data.libreview.LibreViewLogin
import app.mauri.sukrara.core.data.net.Http
import app.mauri.sukrara.core.data.readings.HubReadings
import app.mauri.sukrara.core.data.readings.RecentReadings
import app.mauri.sukrara.core.data.secure.KeystoreSecretBox
import app.mauri.sukrara.core.data.secure.KeystoreSecureStore
import app.mauri.sukrara.core.data.secure.SecretBox
import app.mauri.sukrara.core.data.settings.DataStoreSettingsRepository
import app.mauri.sukrara.core.data.settings.PreferenceStores
import app.mauri.sukrara.core.data.settings.SettingsTransfer
import app.mauri.sukrara.core.data.state.TypedStore
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.DispatcherProvider
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.storage.HistoryImportStore
import app.mauri.sukrara.core.model.storage.PenLedgerRepository
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.SecureStore
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.time.Duration

/** How long history is kept. */
data class Retention(
    val readings: Duration = Duration.ofDays(90),
    val seenWraps: Duration = Duration.ofDays(14),
    val rejects: Duration = Duration.ofDays(14),
    val sentOutbox: Duration = Duration.ofDays(14),
)

/**
 * Storage and network for the whole app (docs/ARCHITECTURE.md "Storage"). `AppGraph` builds it on the main thread,
 * so construction does no I/O: every member is lazy and [ready] opens the database on the IO dispatcher.
 *
 * Layout under the app's private dirs (none of it in backups), each file in credential-encrypted storage unless
 * [DeviceProtected] lists it (then the same path under the device-protected dir, `/data/user_de/…`):
 * - `datastore/<store>.preferences_pb`: settings, one file per schema store;
 * - `files/state/<name>.json`: typed state ([typedStore]);
 * - `files/secure/<name>.bin`: secrets ([secureStore]);
 * - `databases/sukrara.db`: readings, treatments, sharing ledger, the pen ledger;
 * - `files/diag/`: the diagnostic log;
 * - device-protected only: `files/recent-readings.json` and `files/locked-readings.csv` ([hubReadings]).
 *
 * Before the first unlock after a restart ([userUnlocked] false) only the device-protected files can be used: every
 * credential-encrypted one throws [LockedStorageException] when read or written (never "missing"), the diagnostic
 * log waits in memory, and the history opens at [unlock].
 */
class DataModule(
    private val context: Context,
    private val appVersion: String,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
    private val retention: Retention = Retention(),
    private val secretBox: SecretBox = KeystoreSecretBox(),
    /** The phone was unlocked since it started (credential-encrypted storage is readable). */
    private val userUnlocked: () -> Boolean = { context.userUnlocked },
) {
    /** Lives as long as the process: DataStore instances must never be closed and reopened on the same file. */
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val typedStores = mutableMapOf<String, TypedStore<*>>()

    /** Every damaged file found; non-empty = the home screen shows the problem. */
    val corruption = CorruptionLog()

    val json: Json = SukraraJson

    /** The device-protected `files/` dir: readable before the first unlock ([DeviceProtected]). */
    private val deviceFiles: File by lazy { context.createDeviceProtectedStorageContext().filesDir }

    /** The credential-encrypted `files/` dir; refused before the first unlock. */
    private val credentialFiles: File
        get() {
            requireUnlocked("files/")
            return context.filesDir
        }

    private fun filesDir(deviceProtected: Boolean): File = if (deviceProtected) deviceFiles else credentialFiles

    private fun datastoreDir(deviceProtected: Boolean): File = File(filesDir(deviceProtected).parentFile, "datastore")

    private fun secureDir(deviceProtected: Boolean): File = File(filesDir(deviceProtected), "secure")

    /** Throws [LockedStorageException] for [what] before the first unlock after a restart. */
    private fun requireUnlocked(what: String) {
        if (!userUnlocked()) throw LockedStorageException(what)
    }

    /** The history is open to this process ([hubReadings] reads it): at once when unlocked, else from [unlock]. */
    private val historyOpen = MutableStateFlow(userUnlocked())

    /**
     * False from a start before the first unlock until [unlock] ran: whatever needs credential-encrypted storage (the
     * history, treatments, sharing, uploads) waits for it.
     */
    val unlocked: StateFlow<Boolean> = historyOpen.asStateFlow()

    val http: OkHttpClient by lazy { Http.client(appVersion) }

    val diagLog: DiagLog by lazy {
        DiagLog({ File(credentialFiles, "diag") }, clock, dispatchers.io, scope, writable = userUnlocked)
    }

    private val preferenceStores by lazy {
        PreferenceStores({ store -> datastoreDir(store in DeviceProtected.SETTINGS) }, corruption, clock, scope)
    }

    val settings: SettingsRepository by lazy { DataStoreSettingsRepository(preferenceStores) }

    /** Settings › Advanced's settings file; [normalize] checks JSON values by `store:key` (the alert settings). */
    fun settingsTransfer(
        normalize: Map<String, (String) -> String?>,
    ): SettingsTransfer = SettingsTransfer(preferenceStores, appVersion, normalize)

    val secureStore: SecureStore by lazy {
        KeystoreSecureStore({ name -> secureDir(name in DeviceProtected.SECRETS) }, secretBox, corruption, clock, dispatchers.io)
    }

    /** THE LibreView account (one per app): the sensor, the LibreView upload and Import history use it. */
    val libreViewLogin: LibreViewLogin by lazy { LibreViewLogin(secureStore) }

    /**
     * True once this process opened a database of another version and replaced it with an empty one (no migrations,
     * [SukraraDatabase]): setup offers Import history again.
     */
    val databaseReplaced = MutableStateFlow(false)

    val database: SukraraDatabase by lazy {
        requireUnlocked("the database")
        Room
            .databaseBuilder(context, SukraraDatabase::class.java, SukraraDatabase.FILE)
            .openHelperFactory(QuarantiningOpenHelperFactory(::quarantineDatabase))
            // no migrations: another version, older or newer, is dropped (every table) and created afresh. This one call
            // covers downgrades too (fallbackToDestructiveMigrationOnDowngrade would require migrations on upgrades again)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        databaseReplaced.value = true
                        diagLog.log("database", "another version was replaced by an empty database")
                    }
                },
            ).setQueryCoroutineContext(dispatchers.io)
            .build()
    }

    /** Every stored reading, whoever it is from: for the follower engine's own wipes and writes. */
    val readings: ReadingsRepository by lazy { RoomReadingsRepository(database.readings()) }

    /**
     * The readings as the phone's current [role] sees them: never another role's sources ([SourceId.notOf]). What
     * the hub, the history, the report, the export and the uploads read.
     */
    fun readingsFor(role: Flow<Role?>): ReadingsRepository = RoomReadingsRepository(database.readings(), role.map(SourceId::notOf))

    val treatments: TreatmentsRepository by lazy { RoomTreatmentsRepository(database.treatments()) }

    /** The sharing ledger: marks gift wrap [id] seen at [at]; false when it was already (the wearer's inbox handles each once). */
    suspend fun markWrapSeen(
        id: String,
        at: Long,
    ): Boolean = database.seenWraps().markSeen(id, at)

    private val recentReadings by lazy {
        RecentReadings(
            File(deviceFiles, DeviceProtected.RECENT_READINGS),
            File(deviceFiles, DeviceProtected.LOCKED_READINGS),
            corruption,
            clock,
            dispatchers.io,
        )
    }

    private var hub: HubReadings? = null

    /**
     * THE glucose hub's readings ([HubReadings]): the history once it is open ([readingsFor] [role]), this phone's own
     * recent readings in device-protected storage before. One per process.
     */
    @Synchronized
    fun hubReadings(role: Flow<Role?>): HubReadings {
        check(hub == null) { "one hub per process: share the instance" }
        return HubReadings({ readingsFor(role) }, recentReadings, historyOpen) { diagLog.log("readings", it) }.also { hub = it }
    }

    /**
     * The phone is unlocked: the readings taken before it go into the history, which opens ([unlocked]). Also at every
     * start of an unlocked phone (a process that died before the unlock may have left some). Call off the main thread.
     */
    suspend fun unlock() {
        requireUnlocked("the history")
        val h = synchronized(this) { hub }
        if (h != null) {
            h.unlock()
        } else {
            recentReadings.mergeInto(readings)
            historyOpen.value = true
        }
    }

    /** THE import path (Import history): every importer stores through it, deduplicated (`HistoryImport`). */
    val historyImport: HistoryImportStore by lazy { RoomHistoryImport(database.readings(), database.treatments()) }

    /** The pen ledger, stored with the treatments of each pen change in one transaction (`:pen`). */
    val penLedger: PenLedgerRepository by lazy { RoomPenLedgerRepository(database.penLedger()) }

    /**
     * The typed state file `files/state/<name>.json`. [whenCorrupt] is what the store continues from if the file is
     * damaged (it is also reported); choose it on purpose. One call per name for the process.
     */
    @Synchronized
    fun <T> typedStore(
        name: String,
        serializer: KSerializer<T>,
        initial: T,
        whenCorrupt: T,
    ): TypedStore<T> {
        require(name !in typedStores) { "typed store '$name' already exists: share the instance" }
        val deviceProtected = name in DeviceProtected.STATES
        val store =
            TypedStore(
                { File(filesDir(deviceProtected), "state/$name.json") },
                serializer,
                initial,
                whenCorrupt,
                json,
                corruption,
                clock,
                scope,
                open = { if (!deviceProtected) requireUnlocked("typed state '$name'") },
            )
        typedStores[name] = store
        return store
    }

    /** A plain file under `files/` with atomic writes and quarantine on damage (for formats DataStore doesn't fit). */
    fun <T> fileStore(
        relativePath: String,
        codec: FileCodec<T>,
    ): FileStore<T> = FileStore(File(credentialFiles, relativePath), codec, corruption, clock, dispatchers.io)

    /**
     * Re-reports damaged files earlier runs kept and nobody acknowledged, opens the database and applies retention,
     * off the main thread. Call from `AppGraph.ready()`, once the phone is unlocked.
     */
    suspend fun ready() =
        withContext(dispatchers.io) {
            corruption.rescan(
                listOf(false, true).flatMap { deviceProtected ->
                    listOf(
                        QuarantineRoot(filesDir(deviceProtected), StoreKind.File),
                        QuarantineRoot(secureDir(deviceProtected), StoreKind.Secret),
                        QuarantineRoot(datastoreDir(deviceProtected), StoreKind.Settings),
                    )
                } + QuarantineRoot(context.getDatabasePath(SukraraDatabase.FILE).parentFile!!, StoreKind.Database),
            )
            database.openHelper.writableDatabase
            prune()
        }

    /** SQLite found the database damaged and is about to recreate it: keep a copy (and its WAL) and report it. */
    private fun quarantineDatabase(db: File) {
        val now = clock.now()
        val kept =
            listOf(db, File(db.path + "-wal"))
                .filter { it.exists() }
                .map { it.copyTo(FileStore.quarantineTarget(it, now.toEpochMilli()), overwrite = true) }
        corruption.report(CorruptFile(StoreKind.Database, db.name, kept.firstOrNull(), now, "sqlite corruption"))
    }

    /** Deletes history older than [retention]; pending outbox entries are never deleted. */
    suspend fun prune() {
        val now = clock.now()
        database.readings().deleteBefore((now - retention.readings).toEpochMilli())
        database.seenWraps().deleteBefore((now - retention.seenWraps).toEpochMilli())
        database.rejects().deleteBefore((now - retention.rejects).toEpochMilli())
        database.outbox().deleteSentBefore((now - retention.sentOutbox).toEpochMilli())
    }
}
