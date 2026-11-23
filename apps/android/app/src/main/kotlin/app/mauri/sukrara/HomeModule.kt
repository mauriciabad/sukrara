// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.data.CorruptFile
import app.mauri.sukrara.core.data.DataModule
import app.mauri.sukrara.core.data.StoreKind
import app.mauri.sukrara.core.model.AlertControls
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertReadinessSource
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.FeatureChecksSource
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.LinkPath
import app.mauri.sukrara.core.model.MaintenanceWindow
import app.mauri.sukrara.core.model.MoveRules
import app.mauri.sukrara.core.model.PenAnswer
import app.mauri.sukrara.core.model.PenEntryInfo
import app.mauri.sukrara.core.model.PenQuestionInfo
import app.mauri.sukrara.core.model.PensPort
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import app.mauri.sukrara.feature.home.DamagedStore
import app.mauri.sukrara.feature.home.DataProblem
import app.mauri.sukrara.feature.home.HomeAlertAction
import app.mauri.sukrara.feature.home.HomeDeps
import app.mauri.sukrara.feature.home.HomeProfile
import app.mauri.sukrara.feature.home.PenRead
import app.mauri.sukrara.feature.home.PhoneAppProblem
import app.mauri.sukrara.feature.home.UploadMarks
import app.mauri.sukrara.feature.home.banners.BannerMemory
import app.mauri.sukrara.feature.home.banners.BlockedTaps
import app.mauri.sukrara.feature.home.banners.GarminTip
import app.mauri.sukrara.feature.home.banners.GarminUpdateTip
import app.mauri.sukrara.feature.home.banners.HomeEnvironment
import app.mauri.sukrara.feature.treatments.TreatmentLog
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.share.owner.OwnerShareState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The home screen's ports, built from the modules `AppGraph` composes: [glucose] is the hub's state and [readiness]
 * THE alert checklist (`AlertsModule.readiness`, the same instance settings and setup render).
 */
class HomeModule(
    private val data: DataModule,
    override val clock: Clock,
    private val scope: CoroutineScope,
    override val glucose: StateFlow<GlucoseState>,
    override val graphConfig: Flow<GraphConfig>,
    override val storedHistory: Flow<List<Reading>>,
    private val readinessSource: AlertReadinessSource,
    /** The alert runtime's actions (Mute alerts, a side's Unmute…), run on [scope]. */
    private val alertControls: AlertControls,
    private val resume: () -> Unit,
    /** First-run setup's first unfinished row, for the Finish-setup banner; null = done. */
    nextSetupStep: Flow<String?>,
    private val refreshSetup: () -> Unit,
    /** Whether a graph widget is on the home screen, checked now (null = unknown: the banner stays hidden). */
    private val widgetPlaced: () -> Boolean? = { null },
    /** Whether the launcher can place the widget from the app, checked now (false = the banner's hand-made way). */
    private val widgetPinnable: () -> Boolean = { true },
    /** The phone's treatment writes (writer check, versioning, sync); a function: the treatments module needs home. */
    private val treatmentLog: () -> TreatmentLog,
    /** The wearer's sharing state (profile row, "Not backed up"); a function: sharing is built on first use. */
    private val sharing: () -> Flow<OwnerShareState> = { emptyFlow() },
    /** The followers whose device is connected to this phone over Bluetooth now (the header's nearby count). */
    private val nearbyFollowers: () -> Flow<Set<String>> = { flowOf(emptySet()) },
    /** A follower's phone: how the wearer's readings come now (the header's mark). */
    private val followPath: () -> Flow<LinkPath?> = { emptyFlow() },
    /** The NFC taps that are on but blocked by Android (home's banner); read on every resume. */
    private val tapsBlocked: suspend () -> BlockedTaps = { BlockedTaps() },
    /** The features Android blocks (home's banner after the taps one, docs/40). */
    override val featureChecks: FeatureChecksSource = FeatureChecksSource.None,
    /** A pen entry with its doses (any role), for home's pen entry sheet. */
    private val penEntryOf: suspend (String) -> PenEntryInfo? = { null },
    /** The pens on this phone while they can be used (the wearer's with NFC), else null: the sheet is read only. */
    private val penPort: () -> PensPort? = { null },
    /** The open pen questions while this phone reads pens (none on a follower); a function: pens are built later. */
    private val penQuestionsOf: () -> Flow<List<PenQuestionInfo>> = { flowOf(emptyList()) },
    /** The newest pen read while reading is On (the pen hints). */
    private val penReadOf: () -> Flow<PenRead?> = { flowOf(null) },
    /** The wearer's clinic upload is set (null on a follower): the *Share your data with your clinic* banner. */
    private val clinicUploadSet: () -> Flow<Boolean?> = { emptyFlow() },
    /** Since when LibreView lacks the wearer's glucose while its upload has stalled (null: it hasn't, or a follower). */
    private val clinicBehind: () -> Flow<Instant?> = { emptyFlow() },
    /** The sign-in after which the Libre app stopped uploading, so the LibreView upload stopped (null: it didn't). */
    private val libreAppStopped: () -> Flow<Instant?> = { emptyFlow() },
    /** The daily update check is off (the GitHub version's suggestion banner); null = no updater in this build. */
    private val updateChecksOff: () -> Flow<Boolean?> = { emptyFlow() },
    /** What the clinic upload has of the entries (the list's cloud marks); a function: integrations start later. */
    private val clinicMarks: () -> Flow<UploadMarks?> = { emptyFlow() },
    /** The family of the sensor this phone reads directly while its support is untested (the feedback card). */
    override val untestedSensor: Flow<String?> = flowOf(null),
    /** Root and the System UI module (the *Restart System UI* card). */
    private val systemAccess: Flow<SystemAccessState> = emptyFlow(),
    /** This build's version code: a closed restart card comes back with a new build. */
    private val build: Long = 0,
    /** Why Juggluco on this phone answers nothing while it is the source (its web server's state), else null. */
    private val phoneAppLink: () -> Flow<PhoneAppProblem?> = { flowOf(null) },
    /** Whether Juggluco is installed, checked now (re-read on every resume: the person may install it meanwhile). */
    private val phoneAppInstalled: () -> Boolean = { true },
    /** The next LibreView maintenance window of the LibreLinkUp source's region (home's quiet line). */
    private val maintenanceAhead: () -> Flow<MaintenanceWindow?> = { flowOf(null) },
    /** Your glucose from the Libre app, its sensor of a family Sukrara has tested directly ([MoveRules]). */
    private val moveFamily: () -> Flow<Boolean> = { flowOf(false) },
    /** A loop or pump app that may hold the CGM is installed ([MoveRules.LOOP_APPS]; re-read on every resume). */
    private val loopAppInstalled: () -> Boolean = { true },
    /** Sukrara's apps on a watch while sending to it is off (the Garmin tip); a function: integrations start later. */
    private val garminOff: () -> Flow<GarminTip?> = { flowOf(null) },
    private val garminUpdate: () -> Flow<GarminUpdateTip?> = { flowOf(null) },
) : HomeDeps {
    override val treatments: TreatmentsRepository get() = data.treatments

    // the stored record carries the pen keys on every role (a follower stores them as received)
    override suspend fun penEntries(ids: Collection<String>): Set<String> =
        ids.filterTo(HashSet()) { id -> data.treatments.get(id)?.penJson != null }

    override suspend fun penEntry(id: String): PenEntryInfo? = penEntryOf(id)

    override suspend fun siteEntries(ids: Collection<String>): Set<String> =
        ids.filterTo(HashSet()) { id -> data.treatments.get(id)?.fromSite == true }

    override fun pensEditable(): Boolean = penPort() != null

    override suspend fun correctPen(
        shown: PenEntryInfo,
        primed: List<Boolean>,
    ): PenEntryInfo? = penPort()?.correct(shown, primed)

    override suspend fun restorePen(was: PenEntryInfo): Boolean = penPort()?.restore(was) == true

    override val penQuestions: Flow<List<PenQuestionInfo>> get() = penQuestionsOf()

    override suspend fun answerPen(
        question: PenQuestionInfo,
        answer: PenAnswer,
    ): Boolean = penPort()?.answer(question, answer) == true

    override val penRead: Flow<PenRead?> get() = penReadOf()

    override val readiness: StateFlow<AlertReadiness?> get() = readinessSource.readiness

    private val env = MutableStateFlow(HomeEnvironment())
    override val environment: StateFlow<HomeEnvironment> = env

    private val appInstalled = MutableStateFlow(true)
    private val loopApp = MutableStateFlow(true)

    init {
        scope.launch { nextSetupStep.collect { step -> env.update { it.copy(nextSetupStep = step) } } }
        scope.launch {
            systemAccess.collect { a ->
                // as Settings › Status bar: only with root confirmed (asked while the status-bar value is on)
                val restart = a.module.takeIf { it.needsRestart && a.granted }
                env.update { it.copy(systemUiRestart = restart, build = build) }
            }
        }
        // "Not backed up" only for a wearer whose identity exists and lives on this phone alone
        scope.launch {
            sharing().collect { s -> env.update { it.copy(backedUp = (s as? OwnerShareState.Sharing)?.backedUp) } }
        }
        scope.launch { clinicUploadSet().collect { set -> env.update { it.copy(clinicUploadSet = set) } } }
        scope.launch { clinicBehind().collect { at -> env.update { it.copy(clinicBehind = at) } } }
        scope.launch { libreAppStopped().collect { at -> env.update { it.copy(libreAppStopped = at) } } }
        scope.launch { updateChecksOff().collect { off -> env.update { it.copy(updateChecksOff = off) } } }
        scope.launch { featureChecks.blocked.collect { b -> env.update { it.copy(blocked = b) } } }
        scope.launch { followPath().collect { p -> env.update { it.copy(followPath = p) } } }
        scope.launch { maintenanceAhead().collect { w -> env.update { it.copy(maintenanceAhead = w) } } }
        scope.launch { garminOff().collect { t -> env.update { it.copy(garminOff = t) } } }
        scope.launch { garminUpdate().collect { t -> env.update { it.copy(garminUpdate = t) } } }
        // LibreView maintenance's *Read the sensor in Sukrara*: only where MoveRules allow it
        scope.launch {
            combine(moveFamily(), loopApp) { family, loop -> MoveRules.mayOffer(yours = family, testedFamily = family, loopApp = loop) }
                .collect { m -> env.update { it.copy(moveOffered = m) } }
        }
        // Juggluco missing says so (not "no readings from the sensor"); else what its web server answers
        scope.launch {
            combine(appInstalled, phoneAppLink()) { installed, link -> if (installed) link else PhoneAppProblem.Missing }
                .collect { p -> env.update { it.copy(phoneApp = p) } }
        }
    }

    override val profile: Flow<HomeProfile?> by lazy {
        combine(sharing(), nearbyFollowers()) { s, near ->
            (s as? OwnerShareState.Sharing)?.let {
                val active = it.followers.filter { f -> f.active }
                HomeProfile(it.profile?.name.orEmpty(), it.profile?.avatar, active.size, active.count { f -> f.key in near })
            }
        }
    }

    override val uploadMarks: Flow<UploadMarks?> = flow { emitAll(clinicMarks()) }

    override val bannerMemory: StateStore<BannerMemory> by lazy {
        data.typedStore("home-banners", BannerMemory.serializer(), BannerMemory(), whenCorrupt = BannerMemory())
    }

    override val dataProblems: StateFlow<List<DataProblem>> =
        data.corruption.files
            .map { files -> files.map { it.toProblem() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Re-reads what can change outside the app (the activity calls it on every resume). */
    fun refresh() {
        readinessSource.refresh()
        refreshSetup()
        featureChecks.refresh()
        env.update { it.copy(widgetPlaced = widgetPlaced(), widgetPinnable = widgetPinnable()) }
        appInstalled.value = phoneAppInstalled()
        loopApp.value = loopAppInstalled()
        scope.launch { runCatching { tapsBlocked() }.getOrNull()?.let { b -> env.update { it.copy(tapsBlocked = b) } } }
    }

    override fun acknowledge(problem: DataProblem) {
        data.corruption.files.value
            .firstOrNull { it.toProblem() == problem }
            ?.let(data.corruption::acknowledge)
    }

    override fun alert(action: HomeAlertAction) {
        scope.launch {
            when (action) {
                is HomeAlertAction.MuteAll -> alertControls.muteAll(action.minutes)
                HomeAlertAction.UnmuteAll -> alertControls.unmuteAll()
                is HomeAlertAction.EndQuiet -> alertControls.unmute(action.kind)
                is HomeAlertAction.RemindAsUsual -> alertControls.remindAsUsual(action.kind)
                is HomeAlertAction.MuteSide -> alertControls.mute(action.kind, action.minutes)
            }
        }
    }

    override fun resumeMonitoring() = resume()

    override fun fix(requirement: AlertRequirement) {
        readinessSource.fix(requirement)
    }

    override suspend fun deleteTreatment(id: String): Treatment? = treatmentLog().delete(id)

    override suspend fun editNote(
        id: String,
        note: String,
    ): Treatment? {
        val stored =
            data.treatments
                .get(id)
                ?.treatment
                ?.takeUnless { it.deleted } ?: return null
        return treatmentLog().edit(stored.copy(note = note))
    }

    override suspend fun undoTreatment(previous: Treatment) {
        treatmentLog().undo(previous)
    }

    override suspend fun mayChangeTreatment(id: String): Boolean = treatmentLog().mayChange(id)

    override suspend fun deliveries(ids: Collection<String>): Map<String, TreatmentDelivery> =
        ids
            .mapNotNull { id ->
                data.treatments
                    .get(id)
                    ?.delivery
                    ?.takeIf { it == TreatmentDelivery.Pending || it == TreatmentDelivery.Failed }
                    ?.let { id to it }
            }.toMap()

    override suspend fun retryTreatment(id: String) {
        treatmentLog().retry(id)
    }

    private fun CorruptFile.toProblem() =
        DataProblem(
            kind =
                when (kind) {
                    StoreKind.Settings -> DamagedStore.Settings
                    StoreKind.Database -> DamagedStore.History
                    StoreKind.Secret -> DamagedStore.Keys
                    StoreKind.File -> DamagedStore.Other
                },
            store = store,
            reason = reason,
            keptAs = quarantinedTo?.name,
            at = at,
        )
}
