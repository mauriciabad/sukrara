// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.data.readings.TrendReadings
import app.mauri.sukrara.core.data.settings.UpdateSettings
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.designsystem.rows.SettingsChrome
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.advanced.AdvancedScreen
import app.mauri.sukrara.feature.settings.advanced.ImportExportScreen
import app.mauri.sukrara.feature.settings.advanced.LogScreen
import app.mauri.sukrara.feature.settings.follower.SettingsFromActions
import app.mauri.sukrara.feature.settings.follower.SettingsFromContent
import app.mauri.sukrara.feature.settings.follower.SettingsFromNote
import app.mauri.sukrara.feature.settings.follower.SettingsFromViewModel
import app.mauri.sukrara.feature.settings.follower.readOnly
import app.mauri.sukrara.feature.settings.graph.GraphActions
import app.mauri.sukrara.feature.settings.graph.GraphContent
import app.mauri.sukrara.feature.settings.graph.GraphMarksContent
import app.mauri.sukrara.feature.settings.graph.GraphViewModel
import app.mauri.sukrara.feature.settings.graph.PlaceSetting
import app.mauri.sukrara.feature.settings.graph.RingsContent
import app.mauri.sukrara.feature.settings.graph.SensorTimeContent
import app.mauri.sukrara.feature.settings.graph.TimeShownContent
import app.mauri.sukrara.feature.settings.lock.LockScreenContent
import app.mauri.sukrara.feature.settings.lock.LockScreenViewModel
import app.mauri.sukrara.feature.settings.pens.DosesReadPage
import app.mauri.sukrara.feature.settings.pens.PenPage
import app.mauri.sukrara.feature.settings.pens.PenViewModel
import app.mauri.sukrara.feature.settings.pens.PensContent
import app.mauri.sukrara.feature.settings.pens.PensViewModel
import app.mauri.sukrara.feature.settings.search.BuildFeature
import app.mauri.sukrara.feature.settings.search.RecentSearch
import app.mauri.sukrara.feature.settings.search.RecentSearches
import app.mauri.sukrara.feature.settings.sensorend.SensorEndActions
import app.mauri.sukrara.feature.settings.sensorend.SensorEndContent
import app.mauri.sukrara.feature.settings.sensorend.SensorEndViewModel
import app.mauri.sukrara.feature.settings.statusbar.NotificationPageActions
import app.mauri.sukrara.feature.settings.statusbar.StatusBarContent
import app.mauri.sukrara.feature.settings.statusbar.StatusBarViewModel
import app.mauri.sukrara.feature.settings.statusbar.liveUpdatesAllowed
import app.mauri.sukrara.feature.settings.statusbar.openLiveUpdateSettings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** The settings pages (the app's Nav3 entries map one key to each). */
sealed interface SettingsRoute {
    data object Overview : SettingsRoute

    data object Alerts : SettingsRoute

    data class AlertKindPage(val kind: AlertKind) : SettingsRoute

    /** Settings › Alerts › Night alerts (#38). */
    data object NightAlerts : SettingsRoute

    /** Settings › Alerts › Night alerts › one alert's night rule. */
    data class NightKindPage(val kind: AlertKind) : SettingsRoute

    /** Settings › Alerts › one quiet rule (its switch, its length, what it does). */
    data class QuietRulePage(val which: QuietKind) : SettingsRoute

    /** Settings › Alerts › Fine-tuning: the rarely touched rows (docs/35: no global advanced mode). */
    data object AlertsFineTuning : SettingsRoute

    /** Settings › Readings: the unit, the ranges, the trend arrow (docs/35; once "Ranges"). */
    data object Readings : SettingsRoute

    /** Settings › Readings › Trend arrow (#204). */
    data object TrendArrow : SettingsRoute

    /** Settings › Graph: the chart only. */
    data object Graph : SettingsRoute

    /** Settings › Rings: rings and numbers beside the value, the last treatment, the ring order (docs/35). */
    data object Rings : SettingsRoute

    /** Settings › Import and export: Export data, Import history, the settings file (docs/35 §7). */
    data object ImportExport : SettingsRoute

    /** Settings › Graph › Treatments on the graph. */
    data object GraphMarks : SettingsRoute

    /** A setting's page with its places (docs/38): Graph › Time shown, Sensor end › Sensor time left (also from Graph). */
    data class PlacePage(val setting: PlaceSetting) : SettingsRoute

    data object LockScreen : SettingsRoute

    /** Settings › Insulin pens (the wearer's phone with NFC). */
    data object Pens : SettingsRoute

    /** Settings › Insulin pens › one pen. */
    data class Pen(val serial: String) : SettingsRoute

    /** Settings › Insulin pens › one pen › Doses read; [review]: only its last big read, with *Undo this read*. */
    data class PenDoses(val serial: String, val review: Boolean = false) : SettingsRoute

    data object Reliability : SettingsRoute

    data object StatusBar : SettingsRoute

    data object SensorEnd : SettingsRoute

    data object About : SettingsRoute

    /** The threshold review (also the setup's "Review alert thresholds" step). */
    data object ThresholdReview : SettingsRoute

    /** Settings › Advanced: settings file, relays, log, identity. */
    data object Advanced : SettingsRoute

    /** Settings › Advanced › Log. */
    data object Log : SettingsRoute

    /** Settings › Settings from (a follower). */
    data object SettingsFrom : SettingsRoute
}

/**
 * Renders [route] with its ViewModel from [deps]. [navigate] opens another settings page, [onBack] leaves this one;
 * Sensor and Sharing are other features' screens. [onReviewFinish] gets the review's result (setup continues on it;
 * from settings it just goes back).
 */
@Composable
fun SettingsScreen(
    deps: SettingsDeps,
    route: SettingsRoute,
    navigate: (SettingsRoute) -> Unit,
    onBack: () -> Unit,
    openSensor: () -> Unit,
    openSharing: () -> Unit,
    openApps: () -> Unit = {},
    openClinicUpload: () -> Unit = {},
    openClinicTidepool: () -> Unit = {},
    openProfile: () -> Unit = {},
    openBackup: () -> Unit = {},
    openExport: () -> Unit = {},
    openImport: () -> Unit = {},
    /** Settings' *Finish setup*: setup's optional steps still open. */
    openFinishSetup: () -> Unit = {},
    onReviewFinish: (ThresholdReviewResult) -> Unit = { onBack() },
    /** The live card the Readings, Graph and Rings pages show on top (the app's own panel; nothing in tests). */
    preview: @Composable () -> Unit = {},
    /** Import and export's data rows (Import history, Export data: `:integrations`' rows, drawn by the app). */
    dataRows: @Composable () -> Unit = {},
) {
    // the pages are drawn in SPA's SettingsTheme; the glucose card keeps the app's theme (the one renderer, its colours)
    val cardPreview: @Composable () -> Unit = { SukraraTheme { preview() } }
    // every page and its dialogs in SPA's SettingsTheme (the pages' scaffold sets it too, for pages placed by the app)
    SettingsChrome {
        SettingsPages(
            deps,
            route,
            navigate,
            onBack,
            openSensor,
            openSharing,
            openApps,
            openClinicUpload,
            openClinicTidepool,
            openProfile,
            openBackup,
            openExport,
            openImport,
            openFinishSetup,
            onReviewFinish,
            cardPreview,
            dataRows,
        )
    }
}

@Composable
private fun SettingsPages(
    deps: SettingsDeps,
    route: SettingsRoute,
    navigate: (SettingsRoute) -> Unit,
    onBack: () -> Unit,
    openSensor: () -> Unit,
    openSharing: () -> Unit,
    openApps: () -> Unit,
    openClinicUpload: () -> Unit,
    openClinicTidepool: () -> Unit,
    openProfile: () -> Unit,
    openBackup: () -> Unit,
    openExport: () -> Unit,
    openImport: () -> Unit,
    openFinishSetup: () -> Unit,
    onReviewFinish: (ThresholdReviewResult) -> Unit,
    cardPreview: @Composable () -> Unit,
    dataRows: @Composable () -> Unit,
) {
    val clock = deps.clock
    when (route) {
        SettingsRoute.Overview -> {
            val vm = alertsViewModel(deps)
            val ui by vm.ui.collectAsStateWithLifecycle()
            val readiness by deps.readiness.readiness.collectAsStateWithLifecycle()
            val apps by deps.appsShared.collectAsStateWithLifecycle(emptySet())
            val clinic by deps.clinicBanner.collectAsStateWithLifecycle(false)
            val updatesBanner by deps.updatesBanner.collectAsStateWithLifecycle(false)
            val closedTips by deps.closedTips.collectAsStateWithLifecycle(0)
            val extras by deps.setupExtras.collectAsStateWithLifecycle(null)
            val sharing by deps.overviewSharing.collectAsStateWithLifecycle(OverviewSharing())
            val from by deps.settingsFrom.state.collectAsStateWithLifecycle(null)
            val recent = recentSearches(deps)
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { deps.readiness.refresh() }
            SettingsOverviewContent(
                ui?.settings?.on ?: true,
                ui?.alerts ?: AlertUiState(),
                readiness,
                clock,
                OverviewActions(
                    open = navigate,
                    openSensor = openSensor,
                    openSharing = openSharing,
                    openApps = openApps,
                    unmuteAll = vm::unmuteAll,
                    openClinicUpload = openClinicUpload,
                    openClinicTidepool = openClinicTidepool,
                    dismissClinicBanner = deps::dismissClinicBanner,
                    restoreClinicBanner = deps::restoreClinicBanner,
                    turnOnUpdates = deps::turnOnUpdates,
                    dismissUpdatesBanner = deps::dismissUpdatesBanner,
                    restoreUpdatesBanner = deps::restoreUpdatesBanner,
                    restoreClosedTips = deps::restoreClosedTips,
                    pens = deps.pensAvailable(),
                    build =
                        buildSet {
                            if (deps.systemAccess != null) add(BuildFeature.Root)
                            if (deps.updates != null) add(BuildFeature.UpdateCheck)
                        },
                    openProfile = openProfile,
                    openBackup = openBackup,
                    openExport = openExport,
                    openImport = openImport,
                    openFinishSetup = openFinishSetup,
                ),
                apps,
                clinic,
                closedTips,
                sharing,
                from,
                recent,
                moreSearch = deps.moreSearch,
                updatesBanner = updatesBanner,
                setupExtras = extras,
                onBack = onBack,
            )
        }

        SettingsRoute.Alerts -> {
            val vm = alertsViewModel(deps)
            val ui by vm.ui.collectAsStateWithLifecycle()
            // one flow per page, not one per recomposition (a new flow restarted the collection each time)
            val confirmedAt by remember(deps.settings) { SchemaPrefs.thresholdsConfirmedAt(deps.settings) }
                .collectAsStateWithLifecycle(null)
            val from by deps.settingsFrom.state.collectAsStateWithLifecycle(null)
            ui?.let {
                AlertsSettingsContent(
                    it,
                    confirmedAt,
                    clock,
                    AlertsActions(
                        vm::edit,
                        vm::muteAll,
                        vm::unmuteAll,
                        { k -> navigate(SettingsRoute.AlertKindPage(k)) },
                        { navigate(SettingsRoute.ThresholdReview) },
                        deps.alertPlayback,
                        vm::quietExit,
                        vm::muteSide,
                        { navigate(SettingsRoute.NightAlerts) },
                        openFineTuning = { navigate(SettingsRoute.AlertsFineTuning) },
                        openReliability = { navigate(SettingsRoute.Reliability) },
                        openReadings = { navigate(SettingsRoute.Readings) },
                        openQuiet = { q -> navigate(SettingsRoute.QuietRulePage(q)) },
                        setSeparate = vm::setSeparate,
                    ),
                    header = { SettingsFromNote(from, { navigate(SettingsRoute.SettingsFrom) }) },
                    onBack = onBack,
                )
            }
        }

        is SettingsRoute.AlertKindPage -> {
            val vm = alertsViewModel(deps)
            val ui by vm.ui.collectAsStateWithLifecycle()
            val from by deps.settingsFrom.state.collectAsStateWithLifecycle(null)
            ui?.let {
                AlertKindContent(
                    route.kind,
                    it,
                    clock,
                    AlertKindActions(vm::edit, vm::unmute, vm::remindAsUsual, deps.alertPlayback),
                    header = { if (it.mirrors(route.kind)) SettingsFromNote(from, { navigate(SettingsRoute.SettingsFrom) }) },
                    trend = { if (route.kind in RateKinds) RateTrendRow(deps, from.readOnly) },
                    onBack = onBack,
                )
            }
        }

        is SettingsRoute.QuietRulePage -> {
            val vm = alertsViewModel(deps)
            val ui by vm.ui.collectAsStateWithLifecycle()
            val from by deps.settingsFrom.state.collectAsStateWithLifecycle(null)
            ui?.let {
                QuietRuleContent(
                    route.which,
                    it,
                    vm::edit,
                    header = { if (it.mirrored) SettingsFromNote(from, { navigate(SettingsRoute.SettingsFrom) }) },
                    onBack = onBack,
                )
            }
        }

        SettingsRoute.AlertsFineTuning -> {
            val vm = alertsViewModel(deps)
            val ui by vm.ui.collectAsStateWithLifecycle()
            val from by deps.settingsFrom.state.collectAsStateWithLifecycle(null)
            ui?.let {
                FineTuningContent(
                    it,
                    vm::edit,
                    header = { SettingsFromNote(from, { navigate(SettingsRoute.SettingsFrom) }) },
                    onBack = onBack,
                )
            }
        }

        SettingsRoute.NightAlerts -> {
            val vm = alertsViewModel(deps)
            val ui by vm.ui.collectAsStateWithLifecycle()
            val from by deps.settingsFrom.state.collectAsStateWithLifecycle(null)
            ui?.let {
                NightAlertsContent(
                    it,
                    vm::edit,
                    { k -> navigate(SettingsRoute.NightKindPage(k)) },
                    header = { SettingsFromNote(from, { navigate(SettingsRoute.SettingsFrom) }) },
                    // THE night keeps the wearer's sensor reminders quiet too (Sensor end edits the same hours)
                    openSensorEnd = if (it.follower) null else ({ navigate(SettingsRoute.SensorEnd) }),
                    onBack = onBack,
                )
            }
        }

        is SettingsRoute.NightKindPage -> {
            val vm = alertsViewModel(deps)
            val ui by vm.ui.collectAsStateWithLifecycle()
            ui?.let { NightKindContent(route.kind, it, vm::edit, onBack) }
        }

        SettingsRoute.Readings -> {
            val vm = viewModel { GraphPrefsViewModel(deps.settings) }
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            val from by deps.settingsFrom.state.collectAsStateWithLifecycle(null)
            prefs?.let {
                ReadingsContent(
                    it,
                    vm::set,
                    onBack,
                    vm::reset,
                    readOnly = from.readOnly,
                    onUnit = vm::setUnit,
                    onTrend = { navigate(SettingsRoute.TrendArrow) },
                    onAlerts = { navigate(SettingsRoute.Alerts) },
                ) {
                    SettingsFromNote(from, { navigate(SettingsRoute.SettingsFrom) }, preview = cardPreview)
                }
            }
        }

        SettingsRoute.TrendArrow -> {
            val vm = viewModel { TrendArrowViewModel(deps.settings, deps.recent, deps.clock) }
            val ui by vm.ui.collectAsStateWithLifecycle()
            val from by deps.settingsFrom.state.collectAsStateWithLifecycle(null)
            ui?.let {
                TrendArrowContent(it, vm::choose, onBack, readOnly = from.readOnly) {
                    SettingsFromNote(from, { navigate(SettingsRoute.SettingsFrom) })
                }
            }
        }

        SettingsRoute.Graph, SettingsRoute.GraphMarks, SettingsRoute.Rings, is SettingsRoute.PlacePage -> {
            val vm = viewModel { GraphViewModel(deps.settings) }
            val config by vm.config.collectAsStateWithLifecycle()
            val from by deps.settingsFrom.state.collectAsStateWithLifecycle(null)
            val withNote: @Composable () -> Unit = {
                SettingsFromNote(from, { navigate(SettingsRoute.SettingsFrom) }, preview = cardPreview)
            }
            val actions =
                GraphActions(
                    number = vm::set,
                    bool = vm::set,
                    lines = vm::lines,
                    slots = vm::slots,
                    lastPlace = vm::lastPlace,
                    lastElse = vm::lastElse,
                    ringRange = vm::ringRange,
                    ringOrder = vm::ringOrder,
                    marks = vm::marks,
                    placeHours = vm::placeHours,
                    sensorTime = vm::sensorTime,
                    sensorPlace = vm::sensorPlace,
                    openPlaces = { navigate(SettingsRoute.PlacePage(it)) },
                    reset = vm::reset,
                    openMarks = { navigate(SettingsRoute.GraphMarks) },
                    openReadings = { navigate(SettingsRoute.Readings) },
                )
            config?.let {
                when (route) {
                    SettingsRoute.Graph -> {
                        GraphContent(it, actions, onBack, from.readOnly, withNote)
                    }

                    SettingsRoute.Rings -> {
                        RingsContent(it, actions, onBack, from.readOnly, withNote)
                    }

                    is SettingsRoute.PlacePage -> {
                        when (route.setting) {
                            PlaceSetting.Hours -> TimeShownContent(it, actions, onBack, readOnly = from.readOnly, preview = withNote)
                            PlaceSetting.SensorTime -> SensorTimeContent(it, actions, onBack)
                        }
                    }

                    else -> {
                        GraphMarksContent(it, actions, onBack, from.readOnly, withNote)
                    }
                }
            }
        }

        SettingsRoute.Pens -> {
            val vm = viewModel { PensViewModel(deps.settings, deps.pens) }
            val ui by vm.ui.collectAsStateWithLifecycle()
            ui?.let { PensContent(it, vm, onBack) { s -> navigate(SettingsRoute.Pen(s)) } }
        }

        is SettingsRoute.Pen -> {
            val port = deps.pens
            if (port == null) {
                val back by rememberUpdatedState(onBack)
                LaunchedEffect(Unit) { back() }
            } else {
                val vm = viewModel(key = "pen-${route.serial}") { PenViewModel(port, route.serial, deps.settings) }
                PenPage(
                    vm,
                    onBack,
                    openDoses = { navigate(SettingsRoute.PenDoses(route.serial)) },
                    openReview = { navigate(SettingsRoute.PenDoses(route.serial, review = true)) },
                )
            }
        }

        is SettingsRoute.PenDoses -> {
            val port = deps.pens
            if (port == null) {
                val back by rememberUpdatedState(onBack)
                LaunchedEffect(Unit) { back() }
            } else {
                val vm = viewModel(key = "pen-${route.serial}") { PenViewModel(port, route.serial, deps.settings) }
                DosesReadPage(vm, onBack, review = route.review)
            }
        }

        SettingsRoute.LockScreen -> {
            val vm = viewModel { LockScreenViewModel(deps.settings, deps.systemAccess) }
            val seconds by vm.seconds.collectAsStateWithLifecycle()
            val widget by vm.widget.collectAsStateWithLifecycle()
            // the person may come back from LSPosed (module enabled, System UI added): ask it again on resume
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
            // the whole page at once: in a build with root features the widget's rows are there from the first frame
            val time = seconds
            if (time != null && (deps.systemAccess == null || widget != null)) LockScreenContent(time, vm::set, onBack, widget, vm.actions)
        }

        SettingsRoute.Reliability -> {
            val readiness by deps.readiness.readiness.collectAsStateWithLifecycle()
            val alerts by deps.alertSettings.settings.collectAsStateWithLifecycle(null)
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { deps.readiness.refresh() }
            ReliabilityContent(
                readiness,
                deps.readiness::fix,
                deps.readiness::answerUnlockedSound,
                deps.readiness::testUnlockedSound,
                deps.readiness::letThroughDnd,
                alerts = alerts,
                playback = deps.alertPlayback,
                openAlerts = { navigate(SettingsRoute.Alerts) },
                onBack = onBack,
            )
        }

        SettingsRoute.StatusBar -> {
            val vm = viewModel { StatusBarViewModel(deps.settings, deps.systemAccess) }
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            val access by vm.access.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            val problem by vm.problem.collectAsStateWithLifecycle()
            val context = LocalContext.current
            // the person may come back from Android's Live Updates switch, KernelSU or LSPosed: read them again on resume
            var allowed by remember { mutableStateOf(context.liveUpdatesAllowed()) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                allowed = context.liveUpdatesAllowed()
                vm.refresh()
            }
            prefs?.let {
                StatusBarContent(
                    it,
                    vm.rootOffered,
                    allowed,
                    access,
                    busy,
                    problem,
                    NotificationPageActions(
                        setStyle = vm::setStyle,
                        setNumberIcon = vm::setNumberIcon,
                        setColorize = vm::setColorize,
                        setLockScreen = vm::setLockScreen,
                        setPosition = vm::setPosition,
                        setRootColor = vm::setRootColor,
                        allowLiveUpdates = context::openLiveUpdateSettings,
                        checkRoot = vm::checkRoot,
                        requestScope = vm::requestScope,
                        restart = vm::restart,
                    ),
                    onBack,
                )
            }
        }

        SettingsRoute.SensorEnd -> {
            val vm = viewModel { SensorEndViewModel(deps.settings, deps.sensor, deps.alertSettings) }
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            val calendar by deps.sensorCalendar.state.collectAsStateWithLifecycle(null)
            val from by deps.settingsFrom.state.collectAsStateWithLifecycle(null)
            prefs?.let {
                SensorEndContent(
                    it,
                    clock.now(),
                    SensorEndActions(
                        vm::change,
                        vm::setCountdownHours,
                        vm::setNight,
                        vm::setSensorTime,
                        openSensorPlaces = { navigate(SettingsRoute.PlacePage(PlaceSetting.SensorTime)) },
                        openSensor = openSensor,
                        openNight = { navigate(SettingsRoute.NightAlerts) },
                    ),
                    onBack,
                    calendar = calendar?.let { c -> c to deps.sensorCalendar },
                    countdownReadOnly = from.readOnly,
                    countdownNote = { SettingsFromNote(from, { navigate(SettingsRoute.SettingsFrom) }) },
                )
            }
        }

        SettingsRoute.About -> {
            // the Google Play build has no updater (deps.updates null): Play brings the updates
            val updates = deps.updates
            LaunchedEffect(updates) { updates?.check() }
            val state by (updates?.state ?: remember { MutableStateFlow(null) }).collectAsStateWithLifecycle()
            val checks by deps.settings
                .flow(
                    UpdateSettings.AutomaticChecks,
                ).collectAsStateWithLifecycle(UpdateSettings.AutomaticChecks.default)
            val install by deps.settings
                .flow(
                    UpdateSettings.AutomaticInstall,
                ).collectAsStateWithLifecycle(UpdateSettings.AutomaticInstall.default)
            val scope = rememberCoroutineScope()
            AboutContent(
                deps.appVersion,
                deps.distribution,
                deps.appBuild,
                state,
                updates?.let {
                    AboutUpdates(
                        check = it::check,
                        update = it::update,
                        cancel = it::cancel,
                        confirm = it::confirm,
                        automaticChecks = checks,
                        automaticInstall = install,
                        setAutomaticChecks = { on -> scope.launch { deps.settings.set(UpdateSettings.AutomaticChecks, on) } },
                        setAutomaticInstall = { on -> scope.launch { deps.settings.set(UpdateSettings.AutomaticInstall, on) } },
                    )
                },
                onBack,
            )
        }

        SettingsRoute.Advanced -> {
            AdvancedScreen(deps.advanced, { navigate(SettingsRoute.Log) }, onBack)
        }

        SettingsRoute.ImportExport -> {
            val sharing by deps.overviewSharing.collectAsStateWithLifecycle(OverviewSharing())
            ImportExportScreen(
                deps.advanced,
                { LocalDate.now(clock.zone) },
                { navigate(SettingsRoute.ThresholdReview) },
                onBack,
                dataRows,
            ) {
                DataLinks(DataPage.Files, sharing.wearer) { page ->
                    when (page) {
                        DataPage.Sharing -> openSharing()
                        DataPage.Apps -> openApps()
                        DataPage.Backup -> openBackup()
                        DataPage.Files -> Unit
                    }
                }
            }
        }

        SettingsRoute.Log -> {
            LogScreen(deps.advanced, onBack)
        }

        SettingsRoute.SettingsFrom -> {
            val vm = viewModel { SettingsFromViewModel(deps.settingsFrom) }
            val state by vm.state.collectAsStateWithLifecycle()
            state?.let { SettingsFromContent(it, clock, SettingsFromActions(vm::choose, vm::copyWearer, vm::resetToDefault), onBack) }
        }

        SettingsRoute.ThresholdReview -> {
            val vm = viewModel { ThresholdReviewViewModel(deps.alertSettings, deps.settings, deps.clock) }
            val draft by vm.draft.collectAsStateWithLifecycle()
            // the review ends once: a double tap on Confirm (or Cancel during the save) leaves once
            val finish by rememberUpdatedState(onReviewFinish)
            LaunchedEffect(vm) { finish(vm.result.filterNotNull().first()) }
            draft?.let { d: AlertSettings ->
                AlertThresholdReviewContent(d, vm::setThreshold, vm::confirm, vm::cancel)
            }
        }
    }
}

/** The alerts that use the trend arrow (falling fast, rising fast). */
private val RateKinds = setOf(AlertKind.FALLING_FAST, AlertKind.RISING_FAST)

/**
 * The fast alerts' *Trend arrow* row (docs/35 §7): the same setting as Readings › Trend arrow (`glucose.trend`, one
 * store), read-only while a follower mirrors the wearer's.
 */
@Composable
private fun RateTrendRow(
    deps: SettingsDeps,
    readOnly: Boolean,
) {
    val scope = rememberCoroutineScope()
    val choice by remember(deps.settings) { TrendReadings.setting(deps.settings) }.collectAsStateWithLifecycle(null)
    choice?.let { c -> TrendChoiceRow(c, { s -> scope.launch { deps.settings.set(TrendReadings.TrendArrow, s.id) } }, readOnly) }
}

/**
 * The search's recent queries (`settings.searchRecent`), remembered and cleared on this phone only. A query is
 * remembered as its result opens another page: the write starts at once and finishes even though the overview leaves.
 */
@Composable
private fun recentSearches(deps: SettingsDeps): RecentSearch {
    val scope = rememberCoroutineScope()
    val text by remember(deps) { deps.settings.flow(SchemaPrefs.SearchRecent) }.collectAsStateWithLifecycle("")

    fun write(change: (String) -> String) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { deps.settings.update(SchemaPrefs.SearchRecent, change) }
        }
    }
    return remember(text, deps) {
        RecentSearch(
            RecentSearches.decode(text),
            remember = { q -> write { RecentSearches.encode(RecentSearches.add(RecentSearches.decode(it), q)) } },
            clear = { write { "" } },
        )
    }
}

@Composable
private fun alertsViewModel(
    deps: SettingsDeps,
): AlertsViewModel = viewModel(factory = viewModelFactory { initializer { AlertsViewModel(deps) } })
