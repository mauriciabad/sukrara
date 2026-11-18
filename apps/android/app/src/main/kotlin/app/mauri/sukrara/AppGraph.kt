// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Application
import android.nfc.Tag
import android.view.View
import app.mauri.sukrara.alerts.AlertEvent
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.AlertSettingsCodec
import app.mauri.sukrara.core.data.DataModule
import app.mauri.sukrara.core.data.readings.TrendReadings
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.data.userUnlocked
import app.mauri.sukrara.core.model.AppUpdates
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.DefaultDispatchers
import app.mauri.sukrara.core.model.DispatcherProvider
import app.mauri.sukrara.core.model.LifeSettings
import app.mauri.sukrara.core.model.LinkPath
import app.mauri.sukrara.core.model.PenDuplicates
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.ReadingSource
import app.mauri.sukrara.core.model.SensorTiming
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.SystemClock
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.model.storage.UploadHold
import app.mauri.sukrara.designsystem.version.AppBuildInfo
import app.mauri.sukrara.feature.home.PenRead
import app.mauri.sukrara.feature.home.PhoneAppProblem
import app.mauri.sukrara.feature.home.banners.BannerKind
import app.mauri.sukrara.feature.home.banners.BlockedTaps
import app.mauri.sukrara.feature.sensor.SensorUiModule
import app.mauri.sukrara.feature.settings.AlertPlayback
import app.mauri.sukrara.feature.settings.AlertSettingsStore
import app.mauri.sukrara.feature.settings.AppOutput
import app.mauri.sukrara.feature.settings.BackupState
import app.mauri.sukrara.feature.settings.OverviewSharing
import app.mauri.sukrara.feature.settings.SettingsModule
import app.mauri.sukrara.feature.settings.sensor.LifeRows
import app.mauri.sukrara.feature.sharing.EngineOwnerSharing
import app.mauri.sukrara.feature.sharing.SetupModule
import app.mauri.sukrara.feature.sharing.SetupPens
import app.mauri.sukrara.feature.sharing.SharingDeps
import app.mauri.sukrara.feature.sharing.ended
import app.mauri.sukrara.feature.sharing.setup.SetupRecord
import app.mauri.sukrara.feature.sharing.setup.SetupStep
import app.mauri.sukrara.feature.sharing.setup.label
import app.mauri.sukrara.feature.sharing.setup.summary
import app.mauri.sukrara.feature.treatments.AndroidDraftNotice
import app.mauri.sukrara.feature.treatments.DialogPen
import app.mauri.sukrara.feature.treatments.DraftNotificationPorts
import app.mauri.sukrara.feature.treatments.TreatmentDraft
import app.mauri.sukrara.feature.treatments.TreatmentsModule
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphSettings
import app.mauri.sukrara.integrations.CurrentSensor
import app.mauri.sukrara.integrations.IntegrationsModule
import app.mauri.sukrara.integrations.broadcasts.BcState
import app.mauri.sukrara.integrations.broadcasts.LiveReading
import app.mauri.sukrara.integrations.garmin.ConnectIq
import app.mauri.sukrara.integrations.healthconnect.HcState
import app.mauri.sukrara.integrations.librelinkup.LluSourceConfig
import app.mauri.sukrara.integrations.librelinkup.LluSourceSettings
import app.mauri.sukrara.integrations.librelinkup.LluSourceState
import app.mauri.sukrara.integrations.libreview.LvGlucose
import app.mauri.sukrara.integrations.libreview.LvReadings
import app.mauri.sukrara.integrations.libreview.LvState
import app.mauri.sukrara.integrations.libreview.LvUnsent
import app.mauri.sukrara.integrations.localserver.LanPin
import app.mauri.sukrara.integrations.localserver.LanState
import app.mauri.sukrara.integrations.nightscout.NsState
import app.mauri.sukrara.integrations.nightscout.site.NsSourceConfig
import app.mauri.sukrara.integrations.nightscout.site.NsSourceSettings
import app.mauri.sukrara.integrations.nightscout.site.NsSourceState
import app.mauri.sukrara.integrations.tidepool.TpState
import app.mauri.sukrara.monitor.MonitorModule
import app.mauri.sukrara.monitor.alerts.AlertRecord
import app.mauri.sukrara.monitor.alerts.AlertsModule
import app.mauri.sukrara.monitor.hub.MonitorRecord
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.NotificationGroups
import app.mauri.sukrara.monitor.notify.NotificationIds
import app.mauri.sukrara.monitor.notify.SharingLine
import app.mauri.sukrara.monitor.sensorend.CalendarRecord
import app.mauri.sukrara.monitor.sensorend.SensorCalendar
import app.mauri.sukrara.monitor.sensorend.SensorEndMemory
import app.mauri.sukrara.monitor.service.Readiness
import app.mauri.sukrara.monitor.service.StartReason
import app.mauri.sukrara.pen.PenLog
import app.mauri.sukrara.pen.PenNotificationPorts
import app.mauri.sukrara.pen.PenTag
import app.mauri.sukrara.pen.PenTapHint
import app.mauri.sukrara.pen.PensModule
import app.mauri.sukrara.protocol.event.AlertCodec
import app.mauri.sukrara.protocol.event.SensorLife
import app.mauri.sukrara.sensor.SensorInfo
import app.mauri.sukrara.sensor.SensorModule
import app.mauri.sukrara.sensor.ble.SensorGlucose
import app.mauri.sukrara.sensor.juggluco.JugglucoStatus
import app.mauri.sukrara.sensor.pairing.ReceiverIdState
import app.mauri.sukrara.sensor.source.SensorSourceKind
import app.mauri.sukrara.sensor.source.SensorStatus
import app.mauri.sukrara.settings.SettingsSpec
import app.mauri.sukrara.share.FollowerModule
import app.mauri.sukrara.share.ShareOwnerModule
import app.mauri.sukrara.share.ble.NearbyStatus
import app.mauri.sukrara.share.follower.FollowState
import app.mauri.sukrara.share.follower.FollowerNotifications
import app.mauri.sukrara.share.follower.WearerSettingsCache
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.share.owner.SeenWraps
import app.mauri.sukrara.share.owner.followSource
import app.mauri.sukrara.share.owner.requests.AndroidRequestNotifications
import app.mauri.sukrara.widgets.WidgetPlacements
import app.mauri.sukrara.widgets.WidgetsModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The app's object graph (manual DI, docs/ARCHITECTURE.md §Android DI, apps/android/README.md "DI").
 *
 * - Each area owns ONE module class in its own Gradle module (e.g. `DataModule` in `:core:data`, `MonitorModule` in
 *   `:monitor`) that builds its objects from constructor parameters. `AppGraph` only composes them, in dependency
 *   order (data → source → sensor → monitor → alerts → home → …): one `val x by lazy { XModule(…) }` block per module.
 * - Built in [App.onCreate] on the main thread, so the constructor does no I/O: everything is `lazy`, and work
 *   that reads disk happens in [monitorReady] and [ready] on the IO dispatcher.
 * - [scope] lives as long as the process; a failing child doesn't cancel the others.
 * - **Before the first unlock after a restart** ([unlocked] false, docs/30-direct-boot.md, #160) only the part that
 *   needs nothing but device-protected storage runs: the source, the hub (its readings from `HubReadings`), the sensor
 *   source, the monitor (service, glucose notification) and the alert runtime, on the wearer's phone reading its own
 *   sensor ([monitorsLocked]). Their credential-encrypted inputs (treatments, sharing, the follower engine) wait
 *   ([whenUnlocked]); nothing else is built. At the unlock ([onUnlocked]) the readings taken before it go into the
 *   history and the same graph becomes the full one: the service, the hub, the source and the alerts go on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGraph(
    val app: Application,
    val dispatchers: DispatcherProvider = DefaultDispatchers,
    val clock: Clock = SystemClock,
    /** The phone was unlocked since it started (credential-encrypted storage is readable). */
    private val userUnlocked: () -> Boolean = { app.userUnlocked },
) : SharingDeps {
    val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val appVersion: String = BuildConfig.VERSION_NAME

    /** What this launch may reach (release: the public relays; debug: only the `RELAYS` command's choice). */
    val launch by lazy { LaunchPlan.of(BuildConfig.DEBUG, BuildVariant.relayChoice(app)) }

    /** The app's languages; [App] sets it on a language change so words made outside Compose are made again. */
    val locales = MutableStateFlow(app.resources.configuration.locales)

    // ---- modules (one line each; construction does no I/O) ----

    val data by lazy { DataModule(app, appVersion, dispatchers, clock, userUnlocked = userUnlocked) }

    /**
     * The credential-encrypted storage is open to the graph: from the start on an unlocked phone; after a start before
     * the first unlock, from [onUnlocked] (the readings taken before it are in the history by then).
     */
    val unlocked: StateFlow<Boolean> get() = data.unlocked

    /** [after]'s values once [unlocked], [before] until then (an input kept in credential-encrypted storage). */
    private fun <T> whenUnlocked(
        before: T,
        after: () -> Flow<T>,
    ): Flow<T> = unlocked.flatMapLatest { if (it) after() else flowOf(before) }

    /** [after]'s values once [unlocked], nothing until then. */
    private fun <T> onceUnlocked(after: () -> Flow<T>): Flow<T> = unlocked.flatMapLatest { if (it) after() else emptyFlow() }

    /**
     * A Sukrara stream: how the other phone's readings come now (home's header mark), from the Bluetooth link and the
     * relays; null for every other source and before the unlock (the follower engine is credential-encrypted).
     */
    private fun followPath(): Flow<LinkPath?> =
        combine(spec, unlocked) { r, u -> r?.stream == true && u }
            .distinctUntilChanged()
            .flatMapLatest { on ->
                if (!on) {
                    flowOf(null)
                } else {
                    combine(follower.engine.nearbyStatus, follower.engine.state) { n, s ->
                        val relays = (s as? FollowState.Following)?.relays?.takeIf { it.isNotEmpty() }
                        LinkPath.of(n is NearbyStatus.Connected, relays?.values?.count { it.connected })
                    }
                }
            }

    /** Another glucose source was chosen or the sensor forgotten: the monitoring service picks its source again. */
    private val sourceChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * The readings (the current person's: a change of whose glucose deletes the others), with THE trend: what the
     * history, the report, the export and the uploads read.
     */
    val readings by lazy { TrendReadings(data.readings, trendSetting) }

    /**
     * The *Trend arrow* in use (`glucose.trend`; a follower's per *Settings from*): every read of [readings] and the
     * hub's readings comes with THE trend under it ([TrendReadings], contracts/trend).
     */
    private val trendSetting by lazy { TrendReadings.setting(followerSettings.settings) }

    /** THE hub's readings with THE trend: what the hub, and the xDrip+ broadcast's live reading, read. */
    private val hubTrendReadings by lazy { TrendReadings(hubReadings, trendSetting) }

    /**
     * THE hub's readings ([app.mauri.sukrara.core.data.readings.HubReadings]): [readings] once [unlocked], before that
     * this phone's own recent readings in device-protected storage; every own reading also stays there for the next
     * restart. A change of whose glucose clears it too.
     */
    val hubReadings by lazy { data.hubReadings() }

    /**
     * Whose glucose and where from (docs/44 "Person × Source"), as answered in setup or Settings › Source (written with
     * `source.update`); empty = not answered. Two copies, so one damaged file (reported on home) never stops
     * monitoring ([SourceStore]).
     */
    val source by lazy {
        SourceStore(
            primary = data.typedStore("source", SourceRecord.serializer(), SourceRecord(), whenCorrupt = SourceRecord()),
            backup = data.typedStore("source-backup", SourceRecord.serializer(), SourceRecord(), whenCorrupt = SourceRecord()),
        )
    }

    /**
     * What this phone is already set up to read while no answer is stored ([inferSource]); null inside = nothing (a new
     * install: setup asks), a null value = not decided yet.
     */
    private val inferred = MutableStateFlow<Inferred?>(null)

    private data class Inferred(val spec: SourceSpec?)

    /**
     * The person's side of [kind]'s sensor life (docs/44 decision 10, the source file): whose glucose THE source is, the
     * source's wear length and the manual start (only while [kind] is THE source).
     */
    fun lifeSettings(kind: SourceKind): Flow<LifeSettings> =
        combine(spec, source.data) { running, record -> record.lifeOf(kind, running) }.distinctUntilChanged()

    /** [spec] once read ([SpecLoad.spec] null = no source): screens that wait for it before drawing. */
    val specLoaded: Flow<SpecLoad> by lazy { spec.map(::SpecLoad) }

    /**
     * THE source everything runs on: the answer, else what the phone is already set up to read (an install updated
     * from before the question, a damaged file): monitoring never stops for want of an answer, and setup asks once
     * more, preselected with it. Waits for that decision (made in [monitorReady]), so nothing sees "no source" first.
     */
    val spec: Flow<SourceSpec?> by lazy {
        source.data
            .map { it.spec }
            .distinctUntilChanged()
            .flatMapLatest { s -> if (s != null) flowOf(s) else inferred.filterNotNull().map { it.spec } }
            .distinctUntilChanged()
    }

    /**
     * What this phone reads when no answer is stored ([inferredSource]): following another Sukrara (once unlocked: the
     * follow is credential-encrypted), what it read itself last (once unlocked: the history), a sensor pairing,
     * Juggluco on this phone after setup began (its welcome answered), else whose the history is. Not a migration:
     * today's facts of the modules that hold them, read again at every start, so a damaged source file never stops
     * monitoring either. Nothing found = a new install (setup asks).
     */
    private suspend fun inferSource() {
        if (source.data.first().spec != null) return run { inferred.value = Inferred(null) }
        val open = unlocked.value
        val found =
            inferredSource(
                following = { open && follower.engine.isFollowing() },
                readings = if (open) data.readings else null,
                paired = { sensor.pairings.exists() },
                jugglucoAfterSetup = {
                    open &&
                        setup.setupStore.data
                            .first()
                            .completed
                            .isNotEmpty() &&
                        jugglucoInstalled()
                },
            )
        inferred.value = Inferred(found)
    }

    /**
     * The sensor screens chose another source (pairing a sensor, *Use Juggluco*): stored for the same person when it can
     * bring that person's glucose, else as yours (setup's rules: a change of whose glucose deletes the other's history).
     */
    private suspend fun setSourceKind(kind: SourceKind) {
        val person = spec.first()?.person ?: Person.Yours
        setup.sources.set(SourceSpec.of(person, kind) ?: SourceSpec(Person.Yours, kind))
    }

    /** The Nightscout source's site and treatments switch, kept in [source] next to the answer. */
    val nightscoutSourceConfig: NsSourceConfig =
        object : NsSourceConfig {
            override val data: Flow<NsSourceSettings> =
                source.data.map { NsSourceSettings(it.nightscoutUrl, it.nightscoutTreatments) }.distinctUntilChanged()

            override suspend fun set(settings: NsSourceSettings) =
                source.update { it.copy(nightscoutUrl = settings.url, nightscoutTreatments = settings.treatments) }
        }

    /** Whom the LibreLinkUp source reads, kept in [source] next to the answer. */
    val libreLinkUpConfig: LluSourceConfig =
        object : LluSourceConfig {
            override val data: Flow<LluSourceSettings> =
                source.data.map { LluSourceSettings(it.libreLinkUpPatient) }.distinctUntilChanged()

            override val person: Flow<Person?> = source.data.map { it.spec?.person }.distinctUntilChanged()

            override suspend fun set(settings: LluSourceSettings) = source.update { it.copy(libreLinkUpPatient = settings.patient) }
        }

    private fun jugglucoInstalled(): Boolean = app.packageManager.getLaunchIntentForPackage(JUGGLUCO_PACKAGE) != null

    val sensor: SensorModule by lazy {
        SensorModule(
            context = app,
            secure = data.secureStore,
            // losing it only asks for the LibreView sign-in again
            receiverIds = data.typedStore("receiver-ids", ReceiverIdState.serializer(), ReceiverIdState(), ReceiverIdState()),
            libreViewLogin = data.libreViewLogin,
            // Juggluco on this phone is read through the sensor module's source; every other source reads the sensor
            sourceKind = spec.map { if (it?.kind == SourceKind.PhoneApp) SensorSourceKind.Juggluco else SensorSourceKind.Direct },
            http = data.http,
            clock = clock,
            dispatchers = dispatchers,
            // a takeover from the Libre app: LibreLinkUp's sensor start, should the sensor's answer lack one
            knownStart = {
                if (spec.first()?.kind == SourceKind.LibreLinkUp) {
                    integrations.libreLinkUpSource.sensorLife
                        .first()
                        ?.start
                        ?.epochSecond
                } else {
                    null
                }
            },
            libreViewServer = BuildVariant.libreLinkUpServer(app),
            jugglucoWearMin = { jugglucoWear.value },
        )
    }

    /**
     * How long a sensor read through Juggluco lasts (Settings › Sensor › *Lasts*): read at each connection; a change
     * while Juggluco is the source reconnects, so the end moves at once.
     */
    private val jugglucoWear: StateFlow<Int> by lazy {
        var read = false
        lifeSettings(SourceKind.PhoneApp)
            .map { it.wearMin }
            .distinctUntilChanged()
            .onEach {
                // a change, not the first read at start
                if (read && spec.first()?.kind == SourceKind.PhoneApp) sensor.source.retry()
                read = true
            }.stateIn(scope, SharingStarted.Eagerly, SensorTiming.DEFAULT_WEAR_MIN)
    }

    val monitor: MonitorModule by lazy {
        MonitorModule(
            context = app,
            scope = scope,
            dispatchers = dispatchers,
            clock = clock,
            // THE trend on every reading the hub reads (the card, every surface, the glucose notification, the alerts)
            readings = hubTrendReadings,
            // a damaged record keeps the no-data alarm armed (from now) instead of silently disarming it
            record =
                data.typedStore(
                    "monitor-record",
                    MonitorRecord.serializer(),
                    MonitorRecord(),
                    whenCorrupt = MonitorRecord(startedAtEpochMs = clock.now().toEpochMilli()),
                ),
            spec = spec,
            source = ::readingSource,
            readiness = ::sourceReadiness,
            settings = followerSettings.settings,
            // before the unlock the treatments are locked away: the card shows none (they come at the unlock)
            treatments = recentTreatments,
            graphConfig = graphConfig,
            // cold: the owner engine is built when the service first draws the notification (not before the unlock: the
            // sharing identity is credential-encrypted)
            sharing =
                whenUnlocked(null) {
                    combine(
                        flow {
                            val owner = shareOwner.ownerEngine
                            emitAll(
                                combine(owner.state, owner.nearbyLinks) { s, links ->
                                    (s as? OwnerShareState.Sharing)?.let { SharingLine(it.followers.count { f -> f.active }, links.size) }
                                },
                            )
                        },
                        // the Nightscout API open to the home Wi-Fi: said on the notification while it listens there
                        flow { emitAll(integrations.nightscoutApi.lan) }.map { it is LanState.Listening },
                    ) { line, wifi -> if (line == null && !wifi) null else (line ?: SharingLine(0, 0)).copy(wifi = wifi) }
                        .distinctUntilChanged()
                },
            sourceChanges = sourceChanges,
        )
    }

    val alerts: AlertsModule by lazy {
        AlertsModule(
            context = app,
            hub = monitor.hub,
            alertSettings = followerSettings.settings,
            // a damaged record starts the machine over; the hub re-arms it as soon as the service runs
            alertRecord = data.typedStore("alert_state", AlertRecord.serializer(), AlertRecord(), whenCorrupt = AlertRecord()),
            // a damaged memory can only repeat a sensor-end warning
            sensorEndRecord = data.typedStore("sensor_end", SensorEndMemory.serializer(), SensorEndMemory(), SensorEndMemory()),
            // before the unlock no dose or carbs mute an alert (they are locked away): an alert may ring that a treatment
            // would have muted, never the other way round
            treatments = recentTreatments,
            // the alert alarm found the service dead: bring it back (a refusal shows "Monitoring stopped")
            restarter = { startMonitoring(StartReason.AlertCheck) },
            clock = clock,
            // the diagnostic log behind Copy report; every line reaches logcat too (AlertsModule)
            diagLog = { data.diagLog.log("alerts", it) },
            scope = scope,
            dispatchers = dispatchers,
            needsBluetooth = needsBluetooth(),
            // a follower's runtime: the mirrored values *Settings from* chooses and the wearer's `alert` events (its own
            // mute stays here)
            wearerSettings = followerSettings.alertMirror,
            remoteEvents =
                onceUnlocked {
                    flow {
                        emitAll(
                            follower.engine.alerts,
                        )
                    }
                }.mapNotNull { AlertEvent.decode(AlertCodec.encode(it)) },
            // the wearer's `alert` events (what its rules decide without this phone's actions, escalations included) to
            // every follower at once, over the relays and Bluetooth (PROTOCOL-v2 "Alerts"); none before the unlock (the
            // sharing identity is credential-encrypted): followers then get the readings at the unlock
            events = { e ->
                if (unlocked.value) {
                    scope.launch { runCatching { shareOwner.ownerEngine.publishAlert(AlertCodec.decode(AlertEvent.encode(e))) } }
                }
            },
            // a low-side alert's *Log carbs* only where it opens the dialog (never before the unlock: the log is locked)
            canLogTreatments = { userUnlocked() && runCatching { treatments.canAdd() }.getOrDefault(false) },
            // the glucose notification IS the alert while one is up (docs/36), unless *Separate alert notifications*
            glucose = monitor.notification,
        )
    }

    /**
     * A follower's *Settings from*: the settings the cards, the notification and the alerts use (the wearer's, the
     * defaults or this phone's own for the mirrored rows). Inputs are cold, so building it builds nothing else.
     */
    val followerSettings: FollowerSettingsBridge by lazy {
        FollowerSettingsBridge(
            own = data.settings,
            alerts =
                object : FollowerSettingsBridge.AlertsAccess {
                    override val settings get() = alerts.store.settings

                    override suspend fun update(transform: (AlertSettings) -> AlertSettings) = alerts.store.updateSettings(transform)
                },
            role = spec,
            unlocked = unlocked,
            wearer = flow { emitAll(follower.engine.wearerSettings) },
            wearerName = flow { emitAll(monitor.hub.state) }.map { it.link?.wearerName }.distinctUntilChanged(),
        )
    }

    /**
     * THE updater of the GitHub version (#188, `:update`: the daily check, the notification, the checked install); null
     * in the Google Play build (Play brings the updates). [App] starts it.
     */
    val updates: AppUpdates? by lazy { Store.updates(this) }

    /**
     * The suggestion to turn the updater's daily check on (it is opt-in): setup's step, home's and the Settings
     * overview's banner; null without an updater (the Google Play build).
     */
    val updateSuggestion: UpdateSuggestion? by lazy {
        updates?.let { UpdateSuggestion(data.settings, { home.bannerMemory }, clock, scope) }
    }

    val settingsUi by lazy {
        SettingsModule(
            // the pages show the settings in use (a follower's mirrored rows per *Settings from*); writes go to this phone's own
            settings = followerSettings.settings,
            alertSettings =
                object : AlertSettingsStore {
                    override val settings = alerts.store.settings

                    override suspend fun update(transform: (AlertSettings) -> AlertSettings) = alerts.store.updateSettings(transform)
                },
            alertControls = alerts.controls,
            alertState = monitor.hub.state.map { it.alerts },
            readiness = alerts.readiness,
            appVersion = appVersion,
            appBuild = AppBuildInfo.build(app),
            clock = clock,
            sensor = monitor.hub.state.map { it.sensor },
            recent =
                monitor.hub.state
                    .map { it.recent }
                    .distinctUntilChanged(),
            systemAccess = monitor.root.access,
            distribution = Store.distribution,
            updates = { updates },
            pensAvailable = { pens.available() },
            pens = { pens.port.takeIf { pens.available() } },
            clinicBanner =
                combine(integrations.clinicUploadSet, home.bannerMemory.data) { set, memory ->
                    set == false && !memory.dismissed(BannerKind.ShareWithClinic, clock.now())
                },
            dismissClinicBanner = { scope.launch { home.bannerMemory.update { it.dismiss(BannerKind.ShareWithClinic, clock.now()) } } },
            restoreClinicBanner = { scope.launch { home.bannerMemory.update { it.restore(BannerKind.ShareWithClinic) } } },
            // while Settings' Finish setup names the updates step, its own banner doesn't ask the same again
            updatesBanner =
                updateSuggestion?.banner?.let { banner ->
                    combine(banner, setup.extrasLeft) { on, left -> on && SetupStep.Updates !in left }
                } ?: flowOf(false),
            turnOnUpdates = { updateSuggestion?.turnOn() },
            dismissUpdatesBanner = { updateSuggestion?.dismiss() },
            restoreUpdatesBanner = { updateSuggestion?.restore() },
            closedTips = home.bannerMemory.data.map { it.closed },
            // setup's optional steps left for later (#181): Settings' *Finish setup* row
            setupExtras = setup.extrasLeft.map { left -> left.takeIf { it.isNotEmpty() }?.summary(app) },
            restoreClosedTips = { scope.launch { home.bannerMemory.update { it.restoreAll() } } },
            sensorCalendar = { sensorCalendar },
            // Share with apps' rows, which the settings search finds too (their strings are :integrations')
            moreSearch = AppsSearch.entries,
            source = spec,
            sourceName = flow { emitAll(monitor.hub.state) }.map { it.link?.wearerName }.distinctUntilChanged(),
            advanced = {
                AppAdvanced(
                    spec,
                    shareOwner.ownerEngine,
                    follower.engine,
                    shareOwner.crypto,
                    data.diagLog,
                    // an imported alert settings value passes their codec (SPEC §3) or is refused
                    data.settingsTransfer(mapOf("alerts:alerts" to { t -> AlertSettingsCodec.decode(t)?.let(AlertSettingsCodec::encode) })),
                    monitor.notification.counts,
                    { data.readings.spanOf(setOf(SourceId.Relay)).count },
                )
            },
            overviewSharing =
                combine(spec, shareOwner.ownerEngine.state) { r, own ->
                    val sharing = own as? OwnerShareState.Sharing
                    val backup =
                        when (own) {
                            is OwnerShareState.Sharing -> if (own.backedUp) BackupState.Saved else BackupState.NotSaved
                            is OwnerShareState.IdentityUnreadable -> BackupState.Unreadable
                            is OwnerShareState.AnotherAppSharingAsYou -> BackupState.Paused
                            OwnerShareState.Loading, OwnerShareState.NoIdentity -> BackupState.NoIdentity
                        }
                    OverviewSharing(r?.owner != false, sharing?.profile?.name, sharing?.profile?.avatar, backup)
                },
            alertPlayback =
                object : AlertPlayback {
                    override fun play(
                        kind: AlertKind,
                        pack: String,
                        alarm: Boolean,
                        vibrate: Boolean,
                    ) = alerts.tryOut.play(kind, pack, alarm, vibrate)

                    override fun stop() = alerts.tryOut.stop()

                    override fun silenced(
                        kind: AlertKind,
                        alarm: Boolean,
                    ) = alerts.tryOut.silenced(kind, alarm)

                    override fun soundChosenInAndroid(
                        kind: AlertKind,
                        alarm: Boolean,
                    ) = alerts.tryOut.soundChosenInAndroid(kind, alarm)

                    override fun sendTest(
                        kind: AlertKind,
                        settings: AlertSettings,
                    ) = alerts.tryOut.sendTest(kind, settings)

                    override fun channelId(
                        kind: AlertKind,
                        alarm: Boolean,
                    ) = alerts.tryOut.channelId(kind, alarm)
                },
            featureChecks = { featureChecks },
            appsShared =
                integrations.summary.map { s ->
                    setOfNotNull(
                        AppOutput.LibreView.takeIf { s.libreView },
                        AppOutput.NightscoutUpload.takeIf { s.nightscoutUpload },
                        AppOutput.XdripServer.takeIf { s.xdripServer },
                        AppOutput.NightscoutApi.takeIf { s.nightscoutApi },
                        AppOutput.Tidepool.takeIf { s.tidepool },
                        AppOutput.Broadcasts.takeIf { s.broadcastApps > 0 },
                        AppOutput.HealthConnect.takeIf { s.healthConnect },
                    )
                },
            settingsFrom = followerSettings,
        )
    }

    /** Uploads, the local xDrip+ server and export (each off until switched on in Settings › Share with apps). */
    val integrations by lazy {
        IntegrationsModule(
            context = app,
            settings = data.settings,
            secure = data.secureStore,
            // losing it only costs one comparison with the site (nothing is sent twice: the site is checked first)
            nightscoutState = data.typedStore("nightscout-upload", NsState.serializer(), NsState(), whenCorrupt = NsState()),
            // a lost state re-sends nothing old (each part starts again from now) but can't retract versions sent before
            libreViewState = data.typedStore("libreview-upload", LvState.serializer(), LvState(), whenCorrupt = LvState()),
            // a lost one only loses history the sensor's own backfill may bring back
            libreViewUnsent = data.typedStore("libreview-unsent", LvUnsent.serializer(), LvUnsent(), whenCorrupt = LvUnsent()),
            // a lost state re-sends what the phone keeps; Tidepool replaces each datum by its origin id (no duplicates)
            tidepoolState = data.typedStore("tidepool-upload", TpState.serializer(), TpState(), whenCorrupt = TpState()),
            // a lost state may send the current reading once more (the throttle and life count start again)
            broadcastState = data.typedStore("broadcasts", BcState.serializer(), BcState(), whenCorrupt = BcState()),
            // a lost state writes the stored history again (idempotent: one record per minute)
            healthState = data.typedStore("health-connect", HcState.serializer(), HcState(), whenCorrupt = HcState()),
            // a lost pin pauses the home-network option ("another Wi-Fi") until Allow on this Wi-Fi: never a silent move
            nightscoutLan = data.typedStore("nightscout-lan", LanPin.serializer(), LanPin(), whenCorrupt = LanPin()),
            // a lost state forgets which site entries were taken (read again: same ids, no copies) and which of this
            // phone's went up (sent again: replaced by identifier)
            nightscoutSourceState =
                data.typedStore("nightscout-source", NsSourceState.serializer(), NsSourceState(), whenCorrupt = NsSourceState()),
            nightscoutSourceConfig = nightscoutSourceConfig,
            // a lost state forgets the person's name and the sensor LibreLinkUp said: both come back with the next call
            libreLinkUpState =
                data.typedStore("librelinkup-source", LluSourceState.serializer(), LluSourceState(), whenCorrupt = LluSourceState()),
            libreLinkUpConfig = libreLinkUpConfig,
            sensorLife = ::lifeSettings,
            appVersion = appVersion,
            readings = readings,
            treatments = data.treatments,
            historyImport = data.historyImport,
            sensor =
                combine(
                    sensor.source.sensorInfo,
                    // the family the uploads name (a sensor read through Juggluco counts as a Libre 3, as before)
                    sensor.source.detail
                        .map { d ->
                            when (d) {
                                is SensorStatus.Direct -> d.profile.source
                                else -> null
                            }
                        }.distinctUntilChanged(),
                ) { info, source ->
                    info?.let {
                        CurrentSensor(
                            it.serial,
                            Instant.ofEpochSecond(it.startEpoch),
                            Instant.ofEpochSecond(it.endEpoch),
                            source ?: SourceId.Libre3,
                            // the length the sensor itself reports; Juggluco reports none (its is the person's setting)
                            if (source != null) it.wearMin else 0,
                        )
                    }
                },
            libreReadings =
                sensor.source.sensorGlucose.map { b ->
                    fun g(x: SensorGlucose) = LvGlucose(x.lifeCount, x.mgdl, x.epochSec, x.rate)
                    LvReadings(b.serial, b.current?.let(::g), b.history.map(::g), b.everyMinute)
                },
            // the Libre 3's live reading with THE trend the hub has for it (contracts/trend; never the sensor's own rate),
            // keeping the serial and life count only the sensor bridge knows (never Juggluco's: it broadcasts them itself)
            libreLive =
                combine(sensor.source.sensorGlucose.filter { !it.everyMinute }, hubTrendReadings.latest()) { b, latest ->
                    b.current?.takeIf { latest != null && latest.t.epochSecond == it.epochSec }?.let {
                        LiveReading(it.mgdl, latest?.rate, Instant.ofEpochSecond(it.epochSec), b.serial, it.lifeCount)
                    }
                }.filterNotNull().distinctUntilChanged(),
            libreViewLogin = data.libreViewLogin,
            role = spec,
            jugglucoIsSource = spec.map { it?.kind == SourceKind.PhoneApp },
            http = data.http,
            clock = clock,
            dispatchers = dispatchers,
            log = { data.diagLog.log("integrations", it) },
            // a pen entry waits for its session to close (the pens module is built on first use)
            uploadHold = UploadHold { pens.uploadHold.until(it) },
            // Sukrara Aura shows what the card and the glucose notification show
            glucose = flow { emitAll(monitor.hub.state) },
            connectIq =
                BuildVariant.connectIq(app) { data.diagLog.log("integrations", it) }
                    ?: ConnectIq(app) { data.diagLog.log("integrations", it) },
            libreLinkUpServer = BuildVariant.libreLinkUpServer(app),
        )
    }

    val sensorUi by lazy {
        SensorUiModule(
            app,
            sensor,
            ::setSourceKind,
            clock,
            scope,
            glucose = monitor.hub.state,
            graphConfig = graphConfig,
            startMonitoring = { startMonitoring(StartReason.App) },
            takeoverFromLibreApp = { spec.first()?.officialAppUploads == true },
        )
    }

    override val setup by lazy {
        SetupModule(
            context = app,
            setupStore = data.typedStore("setup", SetupRecord.serializer(), SetupRecord(), whenCorrupt = SetupRecord()),
            // the hub's readings: a change of whose glucose also clears this phone's recent readings kept for the next restart
            sources = AppSources(source, spec, hubReadings, data.treatments),
            hub = monitor.hub,
            sharing =
                EngineOwnerSharing(shareOwner.ownerEngine, scope, data.settings, diag = { data.diagLog.log("sharing", it) }) {
                    clock.now().epochSecond
                },
            alertSettings = AppAlertSettings(alerts.store),
            settings = data.settings,
            resetAlerts = alerts::reset,
            startMonitoring = { scope.launch { startMonitoring(StartReason.App) } },
            // THE alert checklist: the same instance home's reliability row and Settings › Alert reliability read
            readiness = alerts.readiness,
            clock = clock,
            follower =
                AppFollowerSharing(
                    follower.engine,
                    resetMonitoring = ::resetMonitoring,
                    startMonitoring = { startMonitoring(StartReason.App) },
                    clearRole = {
                        inferred.value = Inferred(null)
                        source.update { it.with(null) }
                    },
                ),
            // the updates step (the GitHub version only)
            updates = updateSuggestion?.setup(),
            // the optional insulin pens step (a phone with NFC)
            pens = SetupPens(pens.setupAnswer, pens::turnOnReading),
            // the root step and the status bar text
            systemAccess = monitor.root.access,
            // the Nightscout source's Connect step and *Bring back 90 days*
            site = AppSiteSource(integrations.nightscoutAccount, integrations.nightscoutSource, nightscoutSourceConfig, clock),
            // LibreLinkUp's Connect step (its own account; *Sign in as …* with THE LibreView account)
            llu = AppLluSource(integrations.libreLinkUpSource, data.libreViewLogin),
        )
    }

    /** A Sukrara stream: joins, the other phone's stream while the service runs, Stop following. */
    val follower by lazy {
        val info = NotificationGroups.Group.INFO
        val notifications =
            FollowerNotifications(app, Channels.REQUESTS, info.key) {
                NotificationGroups.post(app, NotificationIds.FOLLOWING, it, info, Channels.REQUESTS)
            }
        FollowerModule(
            context = app,
            secure = data.secureStore,
            http = data.http,
            // the database first: before the unlock it refuses, and this block runs again at the next use (the typed
            // store below can be made only once)
            readings = data.readings,
            treatments = data.treatments,
            // a damaged copy reads as none: the record (when readable) has the same settings
            wearerSettings =
                data.typedStore("wearer_settings", WearerSettingsCache.serializer(), WearerSettingsCache(), WearerSettingsCache()),
            hub = monitor.hub,
            clock = clock,
            dispatchers = dispatchers,
            notices = notifications::show,
            relayAccess = launch.relays,
        )
    }

    /**
     * No alarm armed from what came before (another person's glucose, a follow that ended): alert state and "monitoring since"
     * go. [fence]: an ending (a follow ended or stopped, nearby codes that differed): readings still on their way from
     * it can't arm no data again (#128); never on a join.
     */
    private suspend fun resetMonitoring(fence: Boolean = false) {
        alerts.reset()
        monitor.hubImpl.resetMonitoring(fence)
    }

    /**
     * THE sensor's life where it is read beside the hub (*Sensor end in calendar*, followers' sensor life): the sensor
     * module's (a sensor Sukrara reads, Juggluco), or a Nightscout site's *Sensor Start* (docs/44 step 1).
     */
    private val currentSensorInfo: Flow<SensorInfo?> by lazy {
        spec
            .map { it?.kind }
            .distinctUntilChanged()
            .flatMapLatest { kind ->
                val online =
                    when (kind) {
                        SourceKind.Nightscout -> integrations.nightscoutSource.sensorLife
                        SourceKind.LibreLinkUp -> integrations.libreLinkUpSource.sensorLife
                        else -> null
                    }
                online?.map { life ->
                    life?.let { SensorInfo(it.id, it.start.epochSecond, it.end.epochSecond, it.warmupEnd.epochSecond) }
                } ?: sensor.source.sensorInfo
            }
    }

    /** *Sensor end in calendar*: the wearer's events and the settings rows. */
    val sensorCalendar by lazy {
        val record = data.typedStore("sensor-calendar", CalendarRecord.serializer(), CalendarRecord(), whenCorrupt = CalendarRecord())
        AppSensorCalendar(
            app,
            SensorCalendar(app, record),
            record,
            data.settings,
            spec,
            currentSensorInfo,
            clock,
            scope,
            dispatchers.io,
        )
    }

    val sensorSettings by lazy {
        SensorSettingsBridge(
            sensors = SensorModulePort(sensor),
            spec = spec,
            setKind = ::setSourceKind,
            scope = scope,
            service =
                monitor.hub.state
                    .map { it.service }
                    .distinctUntilChanged(),
            startMonitoring = { startMonitoring(StartReason.App) },
            resetMonitoring = { monitor.hubImpl.resetMonitoring(fence = true) },
            sourceChanged = { sourceChanges.tryEmit(Unit) },
            hubSensor =
                monitor.hub.state
                    .map { it.sensor }
                    .distinctUntilChanged(),
            life = lifeRows,
            saveWear = { minutes -> spec.first()?.kind?.let { kind -> source.update { it.withWear(kind, minutes) } } },
            saveStart = { start -> source.update { it.copy(manualStart = start?.epochSecond ?: 0) } },
        )
    }

    /**
     * Settings › Sensor's *Started*, *Lasts* and *Ends* for THE source when it doesn't say all of its sensor's life
     * (docs/44 decision 10): an online source's sensor (its start or yours), Juggluco's, with the source's wear length.
     */
    private val lifeRows: Flow<LifeRows?> by lazy {
        spec.distinctUntilChanged().flatMapLatest { running ->
            val kind = running?.kind
            val wearKey = kind?.wear
            if (running == null || kind == null || wearKey == null) return@flatMapLatest flowOf(null)
            val said: Flow<Triple<Instant?, Instant?, Boolean>> =
                when (kind) {
                    SourceKind.Nightscout -> integrations.nightscoutSource.sensorLife
                    SourceKind.LibreLinkUp -> integrations.libreLinkUpSource.sensorLife
                    else -> null
                }?.map { Triple(it?.start, it?.end, it?.manual == true) }
                    ?: sensor.source.sensorInfo.map { i ->
                        Triple(i?.startEpoch?.let(Instant::ofEpochSecond), i?.endEpoch?.let(Instant::ofEpochSecond), false)
                    }
            val wearId = SettingsSpec.SETTINGS.first { it.android == "state:$wearKey" }.id
            combine(said, lifeSettings(kind)) { (start, end, manual), lf ->
                LifeRows(start, end, manual, kind.life.manualStart && running.person == Person.Yours, lf.wearMin, wearId)
            }
        }
    }

    /** The person's graph settings: THE [GraphConfig] every card surface draws with (home, widgets). */
    val graphConfig: Flow<GraphConfig> by lazy {
        GraphSettings.config(followerSettings.settings).shareIn(scope, SharingStarted.WhileSubscribed(SHARE_STOP_MS), replay = 1)
    }

    /**
     * THE blocked-features checklist (docs/40): home's banner, Settings' rows and setup read this one instance. Each
     * feature's state comes from the module that has it, read only on the source that has it.
     */
    val featureChecks by lazy {
        AppFeatureChecks(
            app,
            scope,
            spec,
            sharing =
                AppFeatureChecks.Port(
                    { shareOwner.ownerEngine.nearbyStatus },
                    { shareOwner.ownerEngine.refreshNearby() },
                    {
                        // as the switch on Settings › Sharing: Pair nearby closes with it
                        data.settings.set(ShareOwnerModule.NearbySharing, false)
                        shareOwner.ownerEngine.closeNearbyPairing()
                    },
                ),
            following = AppFeatureChecks.Port({ follower.engine.nearbyStatus }, { follower.engine.refreshNearby() }),
            healthConnect =
                AppFeatureChecks.Port(
                    { integrations.healthConnect.status },
                    { integrations.healthConnect.recheck() },
                    { integrations.healthConnect.setOn(false) },
                ),
            homeNetwork =
                AppFeatureChecks.Port(
                    { integrations.nightscoutApi.lan },
                    { integrations.nightscoutApi.recheckLan() },
                    { integrations.nightscoutApi.setLan(false) },
                ),
        )
    }

    val home by lazy {
        HomeModule(
            data = data,
            clock = clock,
            scope = scope,
            glucose = monitor.hub.state,
            graphConfig = graphConfig,
            storedHistory = monitor.storedHistory,
            readinessSource = alerts.readiness,
            alertControls = alerts.controls,
            resume = { scope.launch { startMonitoring(StartReason.Resume) } },
            nextSetupStep = combine(setup.card, locales) { card, _ -> card?.label(app) },
            refreshSetup = setup::refresh,
            widgetPlaced = {
                widgets.presence.refresh()
                widgets.presence.placed.value
            },
            widgetPinnable = { widgets.presence.canPin() },
            treatmentLog = { treatments.log },
            sharing = { shareOwner.ownerEngine.state },
            nearbyFollowers = { shareOwner.ownerEngine.nearbyLinks.map { links -> links.mapTo(HashSet()) { it.follower } } },
            followPath = ::followPath,
            tapsBlocked = { BlockedTaps(pen = pens.tapsBlockedWhileOn(), sensor = sensor.tapLaunch.onButBlocked()) },
            featureChecks = featureChecks,
            penEntryOf = { pens.port.entry(it) },
            penPort = { pens.port.takeIf { pens.available() } },
            penQuestionsOf = { pens.homeQuestions },
            penReadOf = { pens.lastRead.map { r -> r?.let { PenRead(it.first, it.second) } } },
            clinicUploadSet = { integrations.clinicUploadSet },
            clinicBehind = { integrations.libreView.glucoseBehind },
            updateChecksOff = { updateSuggestion?.checksOff ?: emptyFlow() },
            clinicMarks = { integrations.libreView.entryMarks.map { m -> m?.let(::LibreViewMarks) } },
            untestedSensor =
                sensor.source.detail
                    .map { d -> (d as? SensorStatus.Direct)?.profile?.takeUnless { it.verified }?.label }
                    .distinctUntilChanged(),
            systemAccess = monitor.root.access.state,
            build = BuildConfig.VERSION_CODE.toLong(),
            phoneAppLink = {
                sensor.source.detail
                    .map { d ->
                        when ((d as? SensorStatus.Juggluco)?.status) {
                            is JugglucoStatus.Unreachable -> PhoneAppProblem.Unreachable
                            JugglucoStatus.Refused -> PhoneAppProblem.Refused
                            else -> null
                        }
                    }.distinctUntilChanged()
            },
            phoneAppInstalled = ::jugglucoInstalled,
        )
    }

    val widgets by lazy {
        WidgetsModule(
            context = app,
            scope = scope,
            clock = clock,
            glucose = monitor.hub.state,
            treatments = recentTreatments,
            graphConfig = graphConfig,
            storedHistory = monitor.storedHistory,
            freshnessTicks = monitor.freshnessTicks,
            store = data.typedStore("widgets", WidgetPlacements.serializer(), WidgetPlacements(), whenCorrupt = WidgetPlacements()),
            lockLook = SurfaceSettings.lockLook(data.settings),
        )
    }

    /**
     * Starts the monitoring service once the graph is loaded (app open, the home's Resume, the alert alarm; before the
     * first unlock once its device-protected part is). A graph that failed to load is logged and the start is skipped;
     * the next trigger tries again in a new process.
     */
    suspend fun startMonitoring(reason: StartReason) {
        runCatching { awaitMonitor() }.onFailure {
            android.util.Log.e("Sukrara.app", "graph not ready: ${it.javaClass.simpleName}")
            return
        }
        monitor.starter.start(reason)
    }

    /**
     * [ready] on an unlocked phone; before the first unlock, [monitorReady] (all that runs then). Android's unlock
     * decides, not [unlocked] (which opens only once the readings taken before it are moved): the boot broadcast right
     * after the first unlock took [monitorReady] while that move ran, so it started monitoring with the source as known
     * while locked, and a source only known after the unlock (a follow, an install with no answer) started nothing until
     * the app was opened or the 15-minute health check. [ready] waits for that move, then decides the source again.
     */
    suspend fun awaitMonitor() = if (userUnlocked()) ready.await() else monitorReady.await()

    /**
     * The wearer's sharing (relays, followers, treatments and settings to them): runs while the phone is the wearer
     * ([ready]'s `followRole`; creating or restoring the identity starts it too). The access-request notification
     * answers through it and never starts or stops it.
     */
    val shareOwner by lazy {
        val info = NotificationGroups.Group.INFO
        ShareOwnerModule(
            context = app,
            secure = data.secureStore,
            http = data.http,
            settings = data.settings,
            treatments = data.treatments,
            seen =
                object : SeenWraps {
                    override suspend fun seen(id: String) = data.wrapSeen(id)

                    override suspend fun mark(id: String, at: Long) = data.markWrapSeen(id, at)
                },
            hub = monitor.hub,
            clock = clock,
            dispatchers = dispatchers,
            scope = scope,
            requestNotifications =
                AndroidRequestNotifications(
                    context = app,
                    channel = Channels.REQUESTS,
                    group = info.key,
                    time = { ClockText.time(it) },
                    post = { NotificationGroups.post(app, NotificationIds.REQUESTS, it, info, Channels.REQUESTS) },
                    remove = { NotificationGroups.cancel(app, NotificationIds.REQUESTS, info) },
                ),
            // what followers on *Settings from: Wearer* use (cold: the alert runtime is built when the engine reads it)
            mirrored = WearerSettingsSource(data.settings, flow { emitAll(alerts.store.settings) }),
            relays = launch.relays,
            // the live events' ss / se / sw (PROTOCOL-v2 "Sensor life"): followers see a warm-up and an end with no reading
            sensor = currentSensorInfo.map { it?.let { i -> SensorLife(i.startEpoch, i.endEpoch, i.warmupEndEpoch) } },
        )
    }

    val treatments: TreatmentsModule by lazy {
        TreatmentsModule(
            data.treatments,
            ShareTreatmentSync(shareOwner, follower.engine, spec, scope),
            clock,
            home.glucose,
            data.settings,
            scope,
            hints =
                AppAlertHints(alerts.store.settings.stateIn(scope, SharingStarted.Eagerly, null), home.glucose, clock) { kind ->
                    scope.launch { alerts.runtime.acknowledge(kind) }
                },
            // read when the dialog saves (the pens module is built on first use, after this one)
            penDuplicates = PenDuplicates { kind, at -> pens.duplicates.near(kind, at) },
            drafts =
                data.typedStore(
                    "treatment-draft",
                    TreatmentDraft.serializer(),
                    TreatmentDraft.EMPTY,
                    whenCorrupt = TreatmentDraft.EMPTY,
                ),
            draftNotice =
                AndroidDraftNotice(
                    app,
                    NotificationGroups.Group.INFO.let { info ->
                        DraftNotificationPorts(
                            channel = Channels.TREATMENT_DRAFT,
                            group = info.key,
                            post = { n ->
                                NotificationGroups.post(app, NotificationIds.TREATMENT_DRAFT, n, info, Channels.TREATMENT_DRAFT)
                            },
                            cancel = { NotificationGroups.cancel(app, NotificationIds.TREATMENT_DRAFT, info) },
                        )
                    },
                ),
            // a pen held to the phone while the dialog is open (the wearer's phone with pens)
            pen = { pens.takeIf { it.available() }?.let(::DialogPenOf) },
        )
    }

    /** Add treatment's pen read over the pens module. */
    private class DialogPenOf(
        private val pens: PensModule,
    ) : DialogPen {
        override suspend fun readable() = pens.dialogReadable()

        override fun mightBePen(tag: Tag) = PenTag.isPen(tag)

        override suspend fun read(
            tag: Tag,
            view: View,
            reading: () -> Unit,
        ) = pens.readInDialog(tag, view, reading)

        override suspend fun undo() = pens.pens.undoLast().also { if (it) pens.notices.cancelRead() }

        override suspend fun tapHintDue() = pens.tapHintDue()

        override fun tapHintShown() = pens.tapHintShown()
    }

    /**
     * Insulin pens (NFC) on the wearer's phone: the ledger (in the database, written with its entries in one
     * transaction; damaged → empty: a known pen's next tap is a first scan that continues after its newest logged
     * entry) and its writes into the treatment log.
     */
    val pens: PensModule by lazy {
        val info = NotificationGroups.Group.INFO

        fun id(kind: PenNotificationPorts.Kind) =
            if (kind == PenNotificationPorts.Kind.Read) NotificationIds.PEN_RESULTS else NotificationIds.PEN_CHECK

        fun channel(kind: PenNotificationPorts.Kind) =
            if (kind ==
                PenNotificationPorts.Kind.Read
            ) {
                Channels.PEN_RESULTS
            } else {
                Channels.PEN_CHECK
            }
        PensModule(
            context = app,
            logChanges = data.treatments.since(Instant.EPOCH),
            // a lost one only shows *Next time, just tap your pen* again (at most 3 times)
            tapHints = data.typedStore("pen-hints", PenTapHint.serializer(), PenTapHint(), whenCorrupt = PenTapHint()),
            log =
                object : PenLog {
                    override suspend fun ledger() = data.penLedger.ledger()

                    override suspend fun all() = data.penLedger.insulin()

                    override fun canWrite() = treatments.log.penMayWrite()

                    override suspend fun commit(
                        ledger: String,
                        records: List<TreatmentRecord>,
                    ) = treatments.log.storeFromPen(records) { data.penLedger.commit(ledger, it) }
                },
            settings = data.settings,
            clock = clock,
            roleSource = spec,
            scope = scope,
            ports =
                PenNotificationPorts(
                    readsChannel = Channels.PEN_RESULTS,
                    checkChannel = Channels.PEN_CHECK,
                    group = info.key,
                    post = { kind, n -> NotificationGroups.post(app, id(kind), n, info, channel(kind)) },
                    cancel = { kind -> NotificationGroups.cancel(app, id(kind), info) },
                ),
        )
    }

    /**
     * The part that needs only device-protected storage, so it also runs before the first unlock after a restart: the
     * source and the hub. [App] starts it; the service and the receivers that run then wait for it ([awaitMonitor]).
     */
    val monitorReady: Deferred<Unit> =
        scope.async(dispatchers.io, CoroutineStart.LAZY) {
            step("source") { source.heal() }
            // never left undecided: [spec] waits for it
            try {
                step("infer") { inferSource() }
            } finally {
                if (inferred.value == null) inferred.value = Inferred(null)
            }
            step("monitor") { monitor.ready() }
        }

    /**
     * Before the first unlock after a restart: whether this phone monitors now, decided once per process (nothing can
     * change the source or the pairing while it's locked). Your own sensor read here does: the pairing, the alert settings
     * and state are device-protected (#160). A Sukrara stream doesn't (its sharing identity stays credential-encrypted),
     * nor Juggluco (it doesn't run before the unlock either): the locked-boot notice and its *No data* alarm stand in for
     * them.
     */
    val monitorsLocked: Deferred<Boolean> =
        scope.async(dispatchers.io, CoroutineStart.LAZY) {
            monitorReady.await()
            val s = spec.first()
            !unlocked.value && s?.readsSensor == true && sourceReadiness(s) == Readiness.Ready
        }

    /**
     * THE alerts module when alerts run in this process now: always once [unlocked]; before, on a phone that monitors
     * then ([monitorsLocked]). Null otherwise: the locked-boot notice and its *No data* alarm stand in.
     */
    suspend fun runningAlerts(): AlertsModule? = if (unlocked.value || monitorsLocked.await()) alerts else null

    /** [runningAlerts] without waiting (System UI's binder thread): null while that isn't decided yet, too. */
    val runningAlertsNow: AlertsModule?
        get() = if (unlocked.value || (monitorsLocked.isCompleted && monitorsLocked.getCompleted())) alerts else null

    /**
     * The phone was unlocked after a start before it: the readings taken before the unlock go into the history, the
     * waiting inputs open ([unlocked]) and [ready] loads the rest. Idempotent; off the main thread.
     */
    suspend fun onUnlocked() {
        if (!userUnlocked()) return
        data.unlock()
    }

    /**
     * Loads what the modules need from disk, once, off the main thread. [App] starts it; anything that reads state
     * first does `graph.ready.await()` (cheap once done). After a start before the first unlock it waits for the unlock.
     */
    val ready: Deferred<Unit> =
        scope.async(dispatchers.io, CoroutineStart.LAZY) {
            unlocked.first { it }
            // a step that fails is logged and the next one still runs: a damaged database must not keep the hub
            // (and so the service, the notification and the alerts) from starting
            step("data") { data.ready() }
            // readings a process that died before the unlock left in device-protected storage go into the history
            step("unlock") { data.unlock() }
            // a version change replaced the database with an empty one: Import history is offered again
            step("history") { if (data.databaseReplaced.value) setup.offerHistoryAgain() }
            monitorReady.await()
            // the follow and the setup record are readable now: what an unanswered install reads is decided again
            step("infer") { inferSource() }
            step("monitor") { monitor.readyUnlocked() }
            step("widgets") { widgets.start() }
            step("integrations") {
                integrations.load()
                integrations.start(scope)
            }
            step("pens") { pens.start() }
            // the sensor's end in the calendar (opt-in, the wearer's phone)
            step("calendar") { scope.launch { sensorCalendar.keepInLine() } }
            // a Libre tap opens the new-sensor flow only on the wearer's phone reading the sensor itself (the row
            // shows only then: never on and hidden)
            step("sensor tap") {
                scope.launch {
                    spec
                        .map { it?.owner == true && it.readsSensor }
                        .distinctUntilChanged()
                        .collect { allowed -> if (!allowed) sensor.tapLaunch.set(false) }
                }
            }
            // the alerts' source follows *Settings from* (the wearer's alerts, or rules run here)
            step("settingsFrom") { scope.launch { followerSettings.keepAlertSource() } }
            // sharing runs while this phone reads your glucose first-hand (needs an identity: created / restored in
            // Settings › Sharing)
            step("share") { scope.launch { shareOwner.ownerEngine.followSource(spec, scope) } }
            step("follower") {
                follower.engine.load()
                // a follow that ended (declined, invite used, removed) arms no "no data" alarm for a stream that won't come
                scope.launch {
                    follower.engine.state.map { it.ended }.distinctUntilChanged().collect { ended ->
                        if (ended && spec.first()?.stream == true) resetMonitoring(fence = true)
                    }
                }
            }
        }

    /**
     * The source's one reading source, or null when nothing is set up to monitor (then the service doesn't run). The
     * sensor or Juggluco: a debug build's synthetic source when switched on, else the sensor source once a sensor is
     * paired (or Juggluco is the source). A Sukrara stream: the follower engine.
     */
    private suspend fun readingSource(spec: SourceSpec): ReadingSource? =
        when (spec.kind) {
            SourceKind.Sensor, SourceKind.PhoneApp -> BuildVariant.wearerSource(this) ?: sensorSource(spec)

            // its token is credential-encrypted: nothing to ask the site before the unlock
            SourceKind.Nightscout -> if (unlocked.value) integrations.nightscoutSource else null

            // its account is credential-encrypted: nothing to ask LibreLinkUp before the unlock
            SourceKind.LibreLinkUp -> if (unlocked.value) integrations.libreLinkUpSource else null

            // the follower engine's identity and state are credential-encrypted: nothing to follow before the unlock
            SourceKind.Sukrara -> if (unlocked.value) follower.engine.takeIf { it.isFollowing() } else null
        }

    /**
     * Whether [readingSource] gives a source, without building one: no pairing decrypted, no made-up source built (the
     * starter asks on every start and health check). Nothing set up (no sensor paired, no Nightscout site, not
     * following) is [Readiness.Nothing]: this phone isn't monitoring then; Juggluco, Nightscout and a stream before the
     * first unlock only wait.
     */
    private suspend fun sourceReadiness(spec: SourceSpec): Readiness =
        when (spec.kind) {
            SourceKind.Sensor, SourceKind.PhoneApp -> {
                when {
                    BuildVariant.hasWearerSource(this) || readsSensor(spec) -> Readiness.Ready
                    spec.kind == SourceKind.PhoneApp -> Readiness.AfterUnlock
                    else -> Readiness.Nothing
                }
            }

            SourceKind.Nightscout -> {
                when {
                    !unlocked.value -> Readiness.AfterUnlock

                    nightscoutSourceConfig.data
                        .first()
                        .url
                        .isNotEmpty() -> Readiness.Ready

                    else -> Readiness.Nothing
                }
            }

            // someone picked after a sign-in (the Connect step); a refused password later is the source's own state
            SourceKind.LibreLinkUp -> {
                when {
                    !unlocked.value -> Readiness.AfterUnlock

                    libreLinkUpConfig.data
                        .first()
                        .patient
                        .isNotEmpty() -> Readiness.Ready

                    else -> Readiness.Nothing
                }
            }

            SourceKind.Sukrara -> {
                when {
                    !unlocked.value -> Readiness.AfterUnlock
                    follower.engine.isFollowing() -> Readiness.Ready
                    else -> Readiness.Nothing
                }
            }
        }

    /** Whether the source talks to the sensor over Bluetooth: the sensor Sukrara reads (not Juggluco, not a stream). */
    private fun needsBluetooth(): Flow<Boolean> = spec.map { it?.readsSensor == true }

    /**
     * THE treatments of the last [TREATMENTS_WINDOW] (and any later), the window moving forward every hour: one query
     * for the glucose notification, the alert runtime and the widgets. None before the unlock (they are
     * credential-encrypted).
     */
    private val recentTreatments: Flow<List<Treatment>> by lazy {
        whenUnlocked(emptyList()) {
            flow {
                while (true) {
                    emit(clock.now() - TREATMENTS_WINDOW)
                    delay(Duration.ofHours(1).toMillis())
                }
            }.flatMapLatest { from -> data.treatments.since(from) }
        }.shareIn(scope, SharingStarted.WhileSubscribed(SHARE_STOP_MS), replay = 1)
    }

    private suspend fun step(
        name: String,
        load: suspend () -> Unit,
    ) {
        try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("Sukrara.app", "$name not loaded: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun sensorSource(spec: SourceSpec): ReadingSource? =
        if (readsSensor(spec)) RunWhileAttached(sensor.source, sensor.source::start, sensor.source::stop) else null

    /**
     * The sensor module has something to read: Juggluco is the source (not before the first unlock: it doesn't run
     * then, nor does its secret open), or a sensor is paired (a damaged pairing too: its source shows "scan the sensor
     * again").
     */
    private suspend fun readsSensor(spec: SourceSpec): Boolean =
        if (spec.kind ==
            SourceKind.PhoneApp
        ) {
            unlocked.value
        } else {
            sensor.pairings.exists()
        }

    /** [spec] as read: the source, or null when there is none. */
    data class SpecLoad(val spec: SourceSpec?)

    private companion object {
        val TREATMENTS_WINDOW: Duration = Duration.ofHours(25)

        /** A config nobody draws with stops being read after this (a screen rotation keeps it). */
        const val SHARE_STOP_MS = 5_000L

        /** Juggluco's package: its launcher shows it is installed (what an unanswered install reads, [inferSource]). */
        const val JUGGLUCO_PACKAGE = "tk.glucodata"
    }
}
