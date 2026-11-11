// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.core.data.nfc.TagIntents
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.feature.home.DataProblemScreen
import app.mauri.sukrara.feature.home.HomeAction
import app.mauri.sukrara.feature.home.HomeScreen
import app.mauri.sukrara.feature.home.HomeViewModel
import app.mauri.sukrara.feature.home.PanelPreview
import app.mauri.sukrara.feature.sensor.libreview.LibreViewSignInRoute
import app.mauri.sukrara.feature.sensor.newsensor.NewSensorRoute
import app.mauri.sukrara.feature.settings.AlertThresholdRows
import app.mauri.sukrara.feature.settings.SettingsDeps
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.feature.settings.SettingsRoute
import app.mauri.sukrara.feature.settings.SettingsScreen
import app.mauri.sukrara.feature.settings.TestAlert
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsScreen
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsViewModel
import app.mauri.sukrara.feature.sharing.backup.BackupViewModel
import app.mauri.sukrara.feature.sharing.backup.ReplaceIdentityPage
import app.mauri.sukrara.feature.sharing.backup.ReplaceIdentityViewModel
import app.mauri.sukrara.feature.sharing.follower.FollowingPage
import app.mauri.sukrara.feature.sharing.follower.FollowingViewModel
import app.mauri.sukrara.feature.sharing.following
import app.mauri.sukrara.feature.sharing.setup.SetupScreen
import app.mauri.sukrara.feature.sharing.setup.SetupStep
import app.mauri.sukrara.feature.sharing.setup.SetupViewModel
import app.mauri.sukrara.feature.sharing.share.ApproveHost
import app.mauri.sukrara.feature.sharing.share.BackupPage
import app.mauri.sukrara.feature.sharing.share.InviteHereScreen
import app.mauri.sukrara.feature.sharing.share.ProfilePage
import app.mauri.sukrara.feature.sharing.share.ShareScreen
import app.mauri.sukrara.feature.sharing.share.SharingScreen
import app.mauri.sukrara.feature.sharing.share.SharingViewModel
import app.mauri.sukrara.integrations.AppsPage
import app.mauri.sukrara.integrations.broadcasts.BroadcastsPage
import app.mauri.sukrara.integrations.export.ExportPage
import app.mauri.sukrara.integrations.export.ExportPeriod
import app.mauri.sukrara.integrations.healthconnect.HealthConnectPage
import app.mauri.sukrara.integrations.history.ImportPage
import app.mauri.sukrara.integrations.libreview.LibreViewPage
import app.mauri.sukrara.integrations.localserver.NightscoutApiPage
import app.mauri.sukrara.integrations.nightscout.NightscoutPage
import app.mauri.sukrara.integrations.tidepool.TidepoolPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean
import app.mauri.sukrara.feature.settings.R as SettingsR
import app.mauri.sukrara.feature.sharing.R as SharingR
import app.mauri.sukrara.integrations.R as IntegrationsR

/** The app's screens. Each is a key on the back stack (Navigation 3); predictive back comes with NavDisplay. */
@Serializable
data object HomeRoute : NavKey

@Serializable
data object SetupRoute : NavKey

/** Settings › Sharing: followers, requests, the identity conflict. */
@Serializable
data object SharingRoute : NavKey

@Serializable
data object ShareRoute : NavKey

/** An invite link opened on a phone that can't follow with it (it wears the sensor, or follows someone already). */
@Serializable
data class InviteHereRoute(
    val link: String,
) : NavKey

/** "How you appear": the wearer's name and picture for followers (home's profile row, Settings › Sharing). */
@Serializable
data object ProfileRoute : NavKey

/** Backup: password manager, recovery code, relay backup (home's "Not backed up" card, Settings › Sharing). */
@Serializable
data object BackupRoute : NavKey

/** Backup › Replace this identity: restore another or start fresh over the working one (confirmed). */
@Serializable
data object ReplaceIdentityRoute : NavKey

/** Setup opened on one [step] (Settings › Sharing's Set up opens the identity step); Back returns where it was opened. */
@Serializable
data class SetupStepRoute(
    val step: SetupStep,
) : NavKey

@Serializable
data object DataProblemsRoute : NavKey

/** Settings › Share with apps (LibreView, Nightscout upload, the xDrip+ server, Export data). */
@Serializable
data object AppsRoute : NavKey

/** Settings › Share with apps › Nightscout upload. */
@Serializable
data object NightscoutUploadRoute : NavKey

/** Settings › Share with apps › Export data. */
@Serializable
data object ExportRoute : NavKey

/** Settings › Share with apps › Import history (also setup's *Bring your history back*). */
@Serializable
data object ImportRoute : NavKey

/** Export data on everything this phone keeps: setup's *Export CSV first* before a role change deletes it. */
@Serializable
data object ExportAllRoute : NavKey

/** Settings › Share with apps › LibreView. */
@Serializable
data object LibreViewRoute : NavKey

/** Settings › Share with apps › Tidepool. */
@Serializable
data object TidepoolRoute : NavKey

/** Settings › Share with apps › Nightscout API on this phone. */
@Serializable
data object NightscoutApiRoute : NavKey

/** Settings › Share with apps › Send glucose to apps. */
@Serializable
data object BroadcastsRoute : NavKey

/** Settings › Share with apps › Health Connect (also what Health Connect opens as the privacy rationale). */
@Serializable
data object HealthConnectRoute : NavKey

/** One settings page ([SettingsRoute]) on the back stack: [page] names it, [kind] is an alert kind page's contract id. */
@Serializable
data class SettingsKey(
    val page: String,
    val kind: String? = null,
    val serial: String? = null,
    val review: Boolean = false,
) : NavKey {
    fun route(): SettingsRoute =
        when (page) {
            "pen" -> serial?.let(SettingsRoute::Pen) ?: SettingsRoute.Pens
            "pendoses" -> serial?.let { SettingsRoute.PenDoses(it, review) } ?: SettingsRoute.Pens
            "alerts" -> SettingsRoute.Alerts
            "kind" -> kind?.let(AlertKind::fromWire)?.let(SettingsRoute::AlertKindPage) ?: SettingsRoute.Alerts
            "night" -> SettingsRoute.NightAlerts
            "nightkind" -> kind?.let(AlertKind::fromWire)?.let(SettingsRoute::NightKindPage) ?: SettingsRoute.NightAlerts
            "ranges" -> SettingsRoute.Ranges
            "graph" -> SettingsRoute.Graph
            "marks" -> SettingsRoute.GraphMarks
            "lock" -> SettingsRoute.LockScreen
            "pens" -> SettingsRoute.Pens
            "reliability" -> SettingsRoute.Reliability
            "statusbar" -> SettingsRoute.StatusBar
            "sensorend" -> SettingsRoute.SensorEnd
            "about" -> SettingsRoute.About
            "review" -> SettingsRoute.ThresholdReview
            "advanced" -> SettingsRoute.Advanced
            "log" -> SettingsRoute.Log
            "from" -> SettingsRoute.SettingsFrom
            else -> SettingsRoute.Overview
        }

    companion object {
        fun of(route: SettingsRoute): SettingsKey =
            when (route) {
                SettingsRoute.Overview -> SettingsKey("overview")
                SettingsRoute.Alerts -> SettingsKey("alerts")
                is SettingsRoute.AlertKindPage -> SettingsKey("kind", route.kind.wire)
                SettingsRoute.NightAlerts -> SettingsKey("night")
                is SettingsRoute.NightKindPage -> SettingsKey("nightkind", route.kind.wire)
                SettingsRoute.Ranges -> SettingsKey("ranges")
                SettingsRoute.Graph -> SettingsKey("graph")
                SettingsRoute.GraphMarks -> SettingsKey("marks")
                SettingsRoute.LockScreen -> SettingsKey("lock")
                SettingsRoute.Pens -> SettingsKey("pens")
                is SettingsRoute.Pen -> SettingsKey("pen", serial = route.serial)
                is SettingsRoute.PenDoses -> SettingsKey("pendoses", serial = route.serial, review = route.review)
                SettingsRoute.Reliability -> SettingsKey("reliability")
                SettingsRoute.StatusBar -> SettingsKey("statusbar")
                SettingsRoute.SensorEnd -> SettingsKey("sensorend")
                SettingsRoute.About -> SettingsKey("about")
                SettingsRoute.ThresholdReview -> SettingsKey("review")
                SettingsRoute.Advanced -> SettingsKey("advanced")
                SettingsRoute.Log -> SettingsKey("log")
                SettingsRoute.SettingsFrom -> SettingsKey("from")
            }
    }
}

/** A pen page asked for from outside (the read card, a notification): the pen, and whether its big read's Review. */
data class PenTarget(
    val serial: String,
    val review: Boolean = false,
)

/** The new-sensor flow: home's Pair sensor, the "Sensor ended" notification, [SensorSetupActivity]. */
@Serializable
data object NewSensorKey : NavKey

@Serializable
data object LibreViewSignInKey : NavKey

/** Settings › Sensor (the settings overview's Sensor row; the new-sensor flow's "change it in Settings › Sensor"). */
@Serializable
data object SensorSettingsKey : NavKey

/** Invite links (`sukrara:` / `https://sukrara.mauri.app/join…`) open setup's join step (or say where they work). */
fun isInviteLink(uri: Uri?): Boolean =
    uri != null &&
        (uri.scheme == "sukrara" || (uri.scheme == "https" && uri.host == "sukrara.mauri.app" && uri.path.orEmpty().startsWith("/join")))

@Composable
fun SukraraNavigation(
    graph: AppGraph,
    deepLink: Uri?,
    openNewSensor: Boolean,
    onNewSensorShow: () -> Unit,
    modifier: Modifier = Modifier,
    openPen: PenTarget? = null,
    onPenShow: () -> Unit = {},
    openHealthConnect: Boolean = false,
    onHealthConnectShow: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(HomeRoute)
    // Health Connect's "why does this app need it" link: the page that says what is written and why
    val healthShown by rememberUpdatedState(onHealthConnectShow)
    LaunchedEffect(openHealthConnect) {
        if (!openHealthConnect) return@LaunchedEffect
        if (backStack.last() != HealthConnectRoute) {
            if (AppsRoute !in backStack) backStack.add(AppsRoute)
            backStack.add(HealthConnectRoute)
        }
        healthShown()
    }
    // the pen read card's *Check it* / *Review*: Settings › Insulin pens › that pen (› its last big read), on top of
    // what is open; Back walks up through the pen's pages
    val penShown by rememberUpdatedState(onPenShow)
    LaunchedEffect(openPen) {
        val target = openPen ?: return@LaunchedEffect
        val pen = SettingsKey("pen", serial = target.serial)
        val page = if (target.review) SettingsKey("pendoses", serial = target.serial, review = true) else pen
        if (backStack.last() != page) {
            if (SettingsKey("pens") !in backStack) backStack.add(SettingsKey("pens"))
            if (backStack.last() != pen) backStack.add(pen)
            if (page != pen) backStack.add(page)
        }
        penShown()
    }
    // a request (a notification tap, also while the app is open) opens the flow once, on top of home
    val newSensorShown by rememberUpdatedState(onNewSensorShow)
    LaunchedEffect(openNewSensor) {
        if (openNewSensor) {
            if (backStack.last() != NewSensorKey) backStack.add(NewSensorKey)
            newSensorShown()
        }
    }
    // first launch (no role yet): setup opens on top of home; Back on its first step leaves it ("setup incomplete").
    // Once a role exists setup never opens by itself: home's Finish setup banner resumes a walk left half-way
    LaunchedEffect(Unit) {
        if (graph.role.data
                .first()
                .role == null && SetupRoute !in backStack
        ) {
            backStack.add(SetupRoute)
        }
    }
    // a wearer whose only step left is Bring your history back (setup done before it existed, or the database was
    // replaced by an update): that one step, once per launch (not again after a rotation or a restored process), never
    // over setup itself (which offers it as its last step)
    var historyOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (historyOffered) return@LaunchedEffect
        graph.setup.historyDue.first { it }
        historyOffered = true
        if (backStack.none { it is SetupRoute || it is SetupStepRoute }) backStack.push(SetupStepRoute(SetupStep.History))
    }
    // an invite link says where it works, on top of whatever is open (Back returns there), once per link: a recreated
    // activity keeps it
    var handledLink by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(deepLink) {
        val link = deepLink?.toString()
        if (link != null && isInviteLink(deepLink) && link != handledLink) {
            handledLink = link
            val role =
                graph.role.data
                    .first()
                    .role
            val follow = graph.follower.engine.state.value
            if (role == Role.Wearer || (role == Role.Follower && follow.following)) {
                backStack.push(InviteHereRoute(link))
            } else {
                // a phone that can follow: setup's join step starts with the link (the role step first if none yet)
                graph.setup.pendingInvite.value = link
                if (role ==
                    Role.Follower
                ) {
                    backStack.push(SetupStepRoute(SetupStep.Join))
                } else if (SetupRoute !in
                    backStack
                ) {
                    backStack.add(SetupRoute)
                }
            }
        }
    }
    val context = LocalContext.current
    Box(modifier) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider =
                entryProvider {
                    entry<HomeRoute> {
                        HomeScreen(
                            viewModel = viewModel(factory = HomeViewModel.factory(graph.home)),
                            onAction = { route(it, backStack, context, graph.home) },
                            onOpenDataProblems = { backStack.add(DataProblemsRoute) },
                        )
                    }
                    entry<SharingRoute> {
                        val close = { backStack.pop(SharingRoute) }
                        val role by remember(graph.role) { graph.role.data.map { it.role } }
                            .collectAsStateWithLifecycle(null)
                        if (role == Role.Follower) {
                            SettingsPage(stringResource(SharingR.string.sh_following_title), close, scroll = false) {
                                FollowingPage(
                                    viewModel = viewModel(factory = FollowingViewModel.factory(graph.setup.follower)),
                                    onJoinAgain = { backStack.push(SetupStepRoute(SetupStep.Join)) },
                                    // the role is cleared: setup again, on top of home
                                    onLeft = {
                                        backStack.retainAll { it == HomeRoute }
                                        backStack.add(SetupRoute)
                                    },
                                )
                            }
                            return@entry
                        }
                        // the settings page's title and Back (system / predictive back comes with NavDisplay)
                        SettingsPage(stringResource(SettingsR.string.st_sharing), close, scroll = false) {
                            SharingScreen(
                                viewModel = viewModel(factory = SharingViewModel.factory(graph.setup.sharing)),
                                onSetUpSharing = { backStack.push(SetupStepRoute(SetupStep.Identity)) },
                                onShare = { backStack.push(ShareRoute) },
                                onReview = graph.setup.laterRequests::review,
                                onProfile = { backStack.push(ProfileRoute) },
                                onBackup = { backStack.push(BackupRoute) },
                                onClose = close,
                            )
                        }
                    }
                    entry<InviteHereRoute> { key ->
                        SettingsPage(stringResource(SharingR.string.sh_invite_here_page), { backStack.pop(key) }, scroll = false) {
                            InviteHereScreen(key.link)
                        }
                    }
                    entry<ProfileRoute> {
                        val close = { backStack.pop(ProfileRoute) }
                        SettingsPage(stringResource(SharingR.string.sh_profile_title), close) {
                            ProfilePage(
                                viewModel = viewModel(factory = SharingViewModel.factory(graph.setup.sharing)),
                                onDone = close,
                                onSetUpSharing = { backStack.push(SetupStepRoute(SetupStep.Identity)) },
                            )
                        }
                    }
                    entry<BackupRoute> {
                        SettingsPage(stringResource(SharingR.string.sh_backup_title), { backStack.pop(BackupRoute) }) {
                            BackupPage(
                                viewModel = viewModel(factory = BackupViewModel.factory(graph.setup.sharing)),
                                onSetUpSharing = { backStack.push(SetupStepRoute(SetupStep.Identity)) },
                                onReplace = { backStack.push(ReplaceIdentityRoute) },
                            )
                        }
                    }
                    entry<ReplaceIdentityRoute> {
                        val vm: ReplaceIdentityViewModel = viewModel(factory = ReplaceIdentityViewModel.factory(graph.setup))
                        val close = { backStack.pop(ReplaceIdentityRoute) }
                        SettingsPage(stringResource(SharingR.string.sh_replace_title), { vm.back(close) }) {
                            ReplaceIdentityPage(
                                viewModel = vm,
                                onClose = close,
                                // back to Backup (it is under this page)
                                onBackUp = close,
                                onInvite = {
                                    backStack.pop(ReplaceIdentityRoute)
                                    backStack.push(ShareRoute)
                                },
                            )
                        }
                    }
                    entry<ShareRoute> {
                        val vm: SharingViewModel = viewModel(factory = SharingViewModel.factory(graph.setup.sharing))
                        val invite by vm.invite.collectAsStateWithLifecycle()
                        val travel by vm.nearby.collectAsStateWithLifecycle()
                        ShareScreen(
                            invite,
                            onCreate = vm::createInvite,
                            onAnother = vm::newInvite,
                            internetOff = !travel.internet,
                            onTurnOnInternet = { vm.setInternet(true) },
                        )
                    }
                    entry<SetupRoute> {
                        SetupScreen(
                            viewModel = viewModel(factory = SetupViewModel.factory(graph.setup)),
                            // the sensor step embeds the new-sensor flow; its Done / Cancel lands back on setup
                            onOpenSensorSetup = { backStack.add(NewSensorKey) },
                            thresholdRows = { settings, onThreshold -> AlertThresholdRows(settings, onThreshold) },
                            onExportHistory = { backStack.push(ExportAllRoute) },
                            onDone = { backStack.remove(SetupRoute) },
                            sendTestAlert = rememberSendTestAlert(graph.settingsUi.deps),
                            onOpenHistoryImport = {
                                backStack.remove(SetupRoute)
                                backStack.push(ImportRoute)
                            },
                        )
                    }
                    entry<SetupStepRoute> { key ->
                        SetupScreen(
                            viewModel = viewModel(factory = SetupViewModel.factory(graph.setup, startAt = key.step)),
                            onOpenSensorSetup = { backStack.add(NewSensorKey) },
                            thresholdRows = { settings, onThreshold -> AlertThresholdRows(settings, onThreshold) },
                            onExportHistory = { backStack.push(ExportAllRoute) },
                            onDone = { backStack.pop(key) },
                            sendTestAlert = rememberSendTestAlert(graph.settingsUi.deps),
                            onOpenHistoryImport = {
                                backStack.pop(key)
                                backStack.push(ImportRoute)
                            },
                        )
                    }
                    entry<NewSensorKey> {
                        BareScreen {
                            NewSensorRoute(
                                viewModel = viewModel(factory = graph.sensorUi.newSensorViewModels),
                                onSignIn = { backStack.add(LibreViewSignInKey) },
                                onDone = { backStack.remove(NewSensorKey) },
                            )
                        }
                    }
                    entry<LibreViewSignInKey> {
                        BareScreen {
                            LibreViewSignInRoute(
                                viewModel = viewModel(factory = graph.sensorUi.libreViewViewModels),
                                onBack = { backStack.remove(LibreViewSignInKey) },
                            )
                        }
                    }
                    entry<SettingsKey> { key ->
                        SettingsScreen(
                            deps = graph.settingsUi.deps,
                            route = key.route(),
                            navigate = { backStack.push(SettingsKey.of(it)) },
                            onBack = { backStack.pop(key) },
                            openSensor = { backStack.push(SensorSettingsKey) },
                            // Settings › Sharing: share / QR, followers, requests (no identity yet: its Set up opens setup)
                            openSharing = { backStack.push(SharingRoute) },
                            openApps = { backStack.push(AppsRoute) },
                            openClinicUpload = { backStack.push(LibreViewRoute) },
                            openClinicTidepool = { backStack.push(TidepoolRoute) },
                            openProfile = { backStack.push(ProfileRoute) },
                            openBackup = { backStack.push(BackupRoute) },
                            openExport = { backStack.push(ExportRoute) },
                            // the Ranges and Graph pages show home's own panel, redrawn as each setting changes
                            preview = { PanelPreview(viewModel(factory = HomeViewModel.factory(graph.home))) },
                        )
                    }
                    entry<AppsRoute> {
                        SettingsPage(stringResource(IntegrationsR.string.in_apps_title), { backStack.pop(AppsRoute) }) {
                            AppsPage(
                                viewModel = viewModel(factory = graph.integrations.appsViewModels),
                                now = graph.clock::now,
                                openNightscout = { backStack.push(NightscoutUploadRoute) },
                                openExport = { backStack.push(ExportRoute) },
                                openLibreView = { backStack.push(LibreViewRoute) },
                                openNightscoutApi = { backStack.push(NightscoutApiRoute) },
                                openTidepool = { backStack.push(TidepoolRoute) },
                                openBroadcasts = { backStack.push(BroadcastsRoute) },
                                openHealthConnect = { backStack.push(HealthConnectRoute) },
                                openImport = { backStack.push(ImportRoute) },
                            )
                        }
                    }
                    entry<ImportRoute> {
                        SettingsPage(stringResource(IntegrationsR.string.in_import_title), { backStack.pop(ImportRoute) }) {
                            ImportPage(
                                viewModel(factory = graph.integrations.importViewModels),
                                openTidepool = { backStack.push(TidepoolRoute) },
                            )
                        }
                    }
                    entry<BroadcastsRoute> {
                        SettingsPage(stringResource(IntegrationsR.string.in_bc_title), { backStack.pop(BroadcastsRoute) }) {
                            BroadcastsPage(viewModel(factory = graph.integrations.broadcastsViewModels), graph.clock::now)
                        }
                    }
                    entry<HealthConnectRoute> {
                        SettingsPage(stringResource(IntegrationsR.string.in_hc_title), { backStack.pop(HealthConnectRoute) }) {
                            HealthConnectPage(viewModel(factory = graph.integrations.healthConnectViewModels), graph.clock::now)
                        }
                    }
                    entry<TidepoolRoute> {
                        SettingsPage(stringResource(IntegrationsR.string.in_tp_title), { backStack.pop(TidepoolRoute) }) {
                            TidepoolPage(viewModel(factory = graph.integrations.tidepoolViewModels))
                        }
                    }
                    entry<NightscoutApiRoute> {
                        SettingsPage(stringResource(IntegrationsR.string.in_nsapi_title), { backStack.pop(NightscoutApiRoute) }) {
                            NightscoutApiPage(viewModel(factory = graph.integrations.nightscoutApiViewModels), graph.clock::now)
                        }
                    }
                    entry<NightscoutUploadRoute> {
                        val role by graph.role.data.collectAsStateWithLifecycle(null)
                        SettingsPage(stringResource(IntegrationsR.string.in_ns_title), { backStack.pop(NightscoutUploadRoute) }) {
                            role?.let { r ->
                                NightscoutPage(viewModel(factory = graph.integrations.nightscoutViewModels(r.role == Role.Wearer)))
                            }
                        }
                    }
                    entry<LibreViewRoute> {
                        val role by graph.role.data.collectAsStateWithLifecycle(null)
                        SettingsPage(stringResource(IntegrationsR.string.in_lv_title), { backStack.pop(LibreViewRoute) }) {
                            role?.let { r ->
                                LibreViewPage(viewModel(factory = graph.integrations.libreViewViewModels(r.role == Role.Wearer)))
                            }
                        }
                    }
                    entry<ExportRoute> {
                        val role by graph.role.data.collectAsStateWithLifecycle(null)
                        SettingsPage(stringResource(IntegrationsR.string.in_export_title), { backStack.pop(ExportRoute) }) {
                            role?.let { r -> ExportPage(viewModel(factory = graph.integrations.exportViewModels(r.role == Role.Follower))) }
                        }
                    }
                    entry<ExportAllRoute> {
                        val role by graph.role.data.collectAsStateWithLifecycle(null)
                        SettingsPage(stringResource(IntegrationsR.string.in_export_title), { backStack.pop(ExportAllRoute) }) {
                            role?.let { r ->
                                ExportPage(
                                    viewModel(factory = graph.integrations.exportViewModels(r.role == Role.Follower, ExportPeriod.All)),
                                )
                            }
                        }
                    }
                    entry<SensorSettingsKey> {
                        SensorSettingsScreen(
                            viewModel = viewModel(factory = SensorSettingsViewModel.factory(graph.sensorSettings)),
                            clock = graph.clock,
                            onPairNewSensor = { backStack.add(NewSensorKey) },
                            onSignIn = { backStack.add(LibreViewSignInKey) },
                            onBack = { backStack.remove(SensorSettingsKey) },
                        )
                    }
                    entry<DataProblemsRoute> {
                        val problems by graph.home.dataProblems.collectAsStateWithLifecycle()
                        LaunchedEffect(problems.isEmpty()) { if (problems.isEmpty()) backStack.remove(DataProblemsRoute) }
                        DataProblemScreen(
                            problems = problems,
                            onCopyReport = { copy(context, it) },
                            onAcknowledge = graph.home::acknowledge,
                            onBack = { backStack.removeLastOrNull() },
                            // damaged settings lead straight to the alert levels
                            onOpenAlertSettings = { backStack.push(SettingsKey.of(SettingsRoute.Alerts)) },
                        )
                    }
                },
        )
        // an access request shows over whatever is open, except where the wearer is busy setting up or pairing
        val busy = backStack.lastOrNull().let { it is SetupRoute || it is SetupStepRoute || it is NewSensorKey || it is LibreViewSignInKey }
        if (!busy) {
            ApproveHost(viewModel(factory = SharingViewModel.factory(graph.setup.sharing)), graph.setup.laterRequests)
        }
    }
}

/** Adds [key] on top unless it's already there (a double tap opens one page, not two). */
private fun NavBackStack<NavKey>.push(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/**
 * Setup's *Send a test alert*: Settings' real test alert ([TestAlert.kind], the same notification as an alert's page and
 * Alert reliability); null while alerts are off or none is on.
 */
@Composable
private fun rememberSendTestAlert(deps: SettingsDeps): (() -> Unit)? {
    val alerts by deps.alertSettings.settings.collectAsStateWithLifecycle(null)
    val settings = alerts ?: return null
    val kind = TestAlert.kind(settings) ?: return null
    return remember(settings, kind) { { deps.alertPlayback.sendTest(kind, settings) } }
}

/** Removes the top-most [key] (equal keys can be lower in the stack; never pop one of those). */
private fun NavBackStack<NavKey>.pop(key: NavKey) {
    val i = lastIndexOf(key)
    if (i >= 0) removeAt(i)
}

/** Where each home action leads: an in-app destination or the system / another feature's screen. */
private fun route(
    action: HomeAction,
    backStack: NavBackStack<NavKey>,
    context: Context,
    home: HomeModule,
) {
    when (action) {
        // the walk where it was left, or each skipped step (setup decides); a double tap opens it once
        HomeAction.OpenSetup -> {
            backStack.push(SetupRoute)
        }

        HomeAction.OpenSettings -> {
            backStack.push(SettingsKey.of(SettingsRoute.Overview))
        }

        HomeAction.PairSensor -> {
            backStack.add(NewSensorKey)
        }

        // no readings from a running sensor: its state, source and account, not a new pairing
        HomeAction.OpenSensor -> {
            backStack.add(SensorSettingsKey)
        }

        HomeAction.AddTreatment -> {
            open(context, Intent(NavTargets.ACTION_QUICK_TREATMENT)) {}
        }

        is HomeAction.EditTreatment -> {
            open(context, Intent(NavTargets.ACTION_QUICK_TREATMENT).putExtra(NavTargets.EXTRA_TREATMENT_ID, action.id)) {}
        }

        is HomeAction.Treat -> {
            open(context, Intent(NavTargets.ACTION_QUICK_TREATMENT).putExtra(NavTargets.EXTRA_FOCUS_CARBS, action.carbs)) {}
        }

        HomeAction.OpenNightAlerts -> {
            backStack.push(SettingsKey.of(SettingsRoute.NightAlerts))
        }

        HomeAction.OpenAlertSettings -> {
            backStack.push(SettingsKey.of(SettingsRoute.Alerts))
        }

        HomeAction.OpenAlertReliability -> {
            backStack.push(SettingsKey.of(SettingsRoute.Reliability))
        }

        HomeAction.EnableBluetooth -> {
            open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) {}
        }

        HomeAction.OpenDateSettings -> {
            open(context, Intent(Settings.ACTION_DATE_SETTINGS)) {}
        }

        HomeAction.ResumeMonitoring -> {
            home.resumeMonitoring()
        }

        // the launcher's own dialog places the combined widget; home re-checks on resume and the pin callback. A
        // refused request turns the banner into the hand-made way (touch & hold the home screen)
        HomeAction.AddWidget -> {
            (context as? Activity)?.let {
                context.graph.widgets.presence
                    .pin(it) { context.graph.home.refresh() }
            }
        }

        HomeAction.OpenBackup -> {
            backStack.push(BackupRoute)
        }

        HomeAction.OpenProfile -> {
            backStack.push(ProfileRoute)
        }

        HomeAction.Share -> {
            backStack.push(ShareRoute)
        }

        HomeAction.OpenClinicUpload -> {
            backStack.push(LibreViewRoute)
        }

        HomeAction.OpenTidepool -> {
            backStack.push(TidepoolRoute)
        }

        HomeAction.AllowTaps -> {
            TagIntents.openAllow(context)
        }

        is HomeAction.OpenPen -> {
            backStack.push(SettingsKey("pens"))
            backStack.push(SettingsKey("pen", serial = action.serial))
        }

        // the module's state changes by itself once System UI says hello again; a failure is said in a toast
        HomeAction.RestartSystemUi -> {
            val graph = context.graph
            val app = context.applicationContext
            // one restart at a time: a second tap while it runs would kill System UI again
            if (restartingSystemUi.compareAndSet(false, true)) {
                graph.scope.launch {
                    val problem =
                        try {
                            graph.monitor.root
                                ?.access
                                ?.restartSystemUi()
                        } finally {
                            restartingSystemUi.set(false)
                        } ?: return@launch
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, app.getString(R.string.app_restart_systemui_failed, problem), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // only what is blocked goes off (pen reading, *Open Sukrara when you scan a sensor*); Settings turns it on again
        is HomeAction.TurnOffTaps -> {
            val graph = context.graph
            graph.scope.launch {
                if (action.taps.pen) graph.pens.turnOff()
                if (action.taps.sensor) graph.sensor.tapLaunch.set(false)
                home.refresh()
            }
        }
    }
}

/** A *Restart System UI* from home is running. */
private val restartingSystemUi = AtomicBoolean(false)

/** Starts [intent] (inside this app when it names an action of ours) or runs [otherwise] when nothing handles it. */
private fun open(
    context: Context,
    intent: Intent,
    otherwise: () -> Unit,
) {
    if (intent.action?.startsWith("app.mauri.sukrara.") == true) intent.setPackage(context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        otherwise()
    }
}

private fun copy(
    context: Context,
    text: String,
) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Sukrara", text))
}

/**
 * A screen without its own page scaffold (New sensor, LibreView sign-in): the theme's background and content colour
 * under it and its content clear of the status and navigation bars (without it the window's dark background showed
 * through in the light theme and the step bar sat under the status bar).
 */
@Composable
private fun BareScreen(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) { content() }
    }
}
