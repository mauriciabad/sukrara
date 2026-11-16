// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.designsystem.icons.PackageIcon
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.ServiceIcon
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.integrations.broadcasts.BcStatus
import app.mauri.sukrara.integrations.broadcasts.broadcastStatusText
import app.mauri.sukrara.integrations.garmin.AuraLinkState
import app.mauri.sukrara.integrations.garmin.AuraStatus
import app.mauri.sukrara.integrations.garmin.ConnectIq
import app.mauri.sukrara.integrations.garmin.garminStatusText
import app.mauri.sukrara.integrations.garmin.isProblem
import app.mauri.sukrara.integrations.garmin.ticking
import app.mauri.sukrara.integrations.healthconnect.HcStatus
import app.mauri.sukrara.integrations.healthconnect.healthStatusText
import app.mauri.sukrara.integrations.history.ImportState
import app.mauri.sukrara.integrations.history.importStatusText
import app.mauri.sukrara.integrations.libreview.libreViewStatusText
import app.mauri.sukrara.integrations.localserver.NsApiState
import app.mauri.sukrara.integrations.localserver.ServerReader
import app.mauri.sukrara.integrations.localserver.ServerReaders
import app.mauri.sukrara.integrations.localserver.XdripState
import app.mauri.sukrara.integrations.localserver.nsApiStatusText
import app.mauri.sukrara.integrations.localserver.xdripRowSummary
import app.mauri.sukrara.integrations.tidepool.tidepoolStatusText
import app.mauri.sukrara.integrations.ui.uploadStatusText
import app.mauri.sukrara.integrations.upload.UploadStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/** What Share with apps shows. */
data class AppsUi(
    val wearer: Boolean,
    val xdrip: XdripState,
    /** Installed apps that read the xDrip+ server (Garmin Connect). */
    val readers: List<ServerReader>,
    /** The Nightscout upload's state (wearer only). */
    val nightscout: UploadStatus,
    /** The LibreView upload's state (wearer only). */
    val libreView: UploadStatus = UploadStatus.Off,
    /** The Nightscout API on this phone. */
    val nightscoutApi: NsApiState = NsApiState.Off,
    /** The Tidepool upload's state (both roles). */
    val tidepool: UploadStatus = UploadStatus.Off,
    /** Glucose broadcasts: how many apps are allowed and what was last sent (wearer only). */
    val broadcastApps: Int = 0,
    val broadcasts: BcStatus = BcStatus(null, null),
    /** Health Connect (wearer only). */
    val healthConnect: HcStatus = HcStatus.Off,
    /** Import history (wearer only). */
    val history: ImportState = ImportState.Idle,
    /** Sukrara's Garmin link (the face). */
    val aura: AuraLinkState = AuraLinkState(),
)

class AppsActions(
    val openNightscout: () -> Unit = {},
    val openLibreView: () -> Unit = {},
    val openNightscoutApi: () -> Unit = {},
    val openTidepool: () -> Unit = {},
    val openBroadcasts: () -> Unit = {},
    val openHealthConnect: () -> Unit = {},
    val openGarmin: () -> Unit = {},
    val openXdrip: () -> Unit = {},
)

private data class Others(
    val api: NsApiState,
    val tidepool: UploadStatus,
    val broadcastApps: Int,
    val broadcasts: BcStatus,
    val health: HcStatus,
    val history: ImportState = ImportState.Idle,
    val aura: AuraLinkState = AuraLinkState(),
)

class AppsViewModel(
    private val module: IntegrationsModule,
    context: Context,
    io: CoroutineDispatcher,
) : ViewModel() {
    private val readers = MutableStateFlow<List<ServerReader>>(emptyList())
    val ui: StateFlow<AppsUi?> =
        combine(
            combine(module.wearer, module.xdrip.state) { w, x -> w to x },
            readers,
            module.nightscout.page,
            module.libreView.page,
            combine(
                combine(module.nightscoutApi.state, module.tidepool.status) { api, tp -> api to tp },
                module.broadcasts.settingsFlow,
                module.broadcasts.status,
                module.healthConnect.status,
                combine(module.history.state, module.aura.state) { h, a -> h to a },
            ) { (api, tp), bc, bs, hc, (h, a) -> Others(api, tp, bc.apps.size, bs, hc, h, a) },
        ) { (w, x), r, ns, lv, o ->
            AppsUi(w, x, r, ns.status, lv.status, o.api, o.tidepool, o.broadcastApps, o.broadcasts, o.health, o.history, o.aura)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        val app = context.applicationContext
        viewModelScope.launch { readers.value = withContext(io) { ServerReaders.installed(app) } }
    }
}

@Composable
fun AppsPage(
    viewModel: AppsViewModel,
    now: () -> Instant,
    openNightscout: () -> Unit,
    openLibreView: () -> Unit,
    openNightscoutApi: () -> Unit,
    openTidepool: () -> Unit,
    openBroadcasts: () -> Unit,
    openHealthConnect: () -> Unit,
    openGarmin: () -> Unit,
    openXdrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ui?.let {
        AppsContent(
            it,
            ticking(now),
            AppsActions(
                openNightscout,
                openLibreView,
                openNightscoutApi,
                openTidepool,
                openBroadcasts,
                openHealthConnect,
                openGarmin,
                openXdrip,
            ),
            modifier,
        )
    }
}

/**
 * Settings › Share with apps: LibreView and Tidepool, the Nightscout upload (wearer), *Garmin* under *Watches* (its own
 * page), and the apps on this phone (broadcasts, Health Connect, and the two local servers, Nightscout API and xDrip+
 * server, each on its own page). Every row that stands for another app or service shows that app's icon ([ServiceIcon] /
 * [PackageIcon]), as the PoC did. Export data and Import history are on Settings › Import and export ([DataFileRows]).
 */
@Composable
fun AppsContent(
    ui: AppsUi,
    now: Instant,
    actions: AppsActions,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(stringResource(if (ui.wearer) R.string.in_apps_intro_wearer else R.string.in_apps_intro_follower))
    SettingsGroup(title = stringResource(R.string.in_apps_clinic_group)) {
        if (ui.wearer) {
            NavigationRow(
                stringResource(R.string.in_lv_title),
                actions.openLibreView,
                summary = libreViewStatusText(ui.libreView, now),
                leading = { ServiceIcon(Service.LibreView) },
            )
        }
        NavigationRow(
            stringResource(R.string.in_tp_title),
            actions.openTidepool,
            summary = tidepoolStatusText(ui.tidepool, now),
            leading = { ServiceIcon(Service.Tidepool) },
        )
    }
    if (ui.wearer) {
        SettingsGroup(title = stringResource(R.string.in_apps_nightscout_group)) {
            NavigationRow(
                stringResource(R.string.in_ns_title),
                actions.openNightscout,
                summary = uploadStatusText(ui.nightscout, now),
                leading = { ServiceIcon(Service.Nightscout) },
            )
        }
    }
    SettingsGroup(title = stringResource(R.string.in_apps_watch_group)) {
        val garmin = AuraStatus.of(ui.aura, now)
        NavigationRow(
            stringResource(R.string.in_garmin_title),
            actions.openGarmin,
            summary = garminStatusText(garmin, now),
            leading = { PackageIcon(ConnectIq.GCM) },
            warning = garmin.isProblem(),
        )
    }
    SettingsGroup(title = stringResource(R.string.in_apps_phone_group)) {
        if (ui.wearer) {
            NavigationRow(
                stringResource(R.string.in_bc_title),
                actions.openBroadcasts,
                summary = broadcastStatusText(ui.broadcasts, ui.broadcastApps > 0, now),
                icon = DsR.drawable.ds_ic_send,
            )
            NavigationRow(
                stringResource(R.string.in_hc_title),
                actions.openHealthConnect,
                summary = healthStatusText(ui.healthConnect, now),
                leading = { ServiceIcon(Service.HealthConnect) },
            )
        }
        NavigationRow(
            stringResource(R.string.in_nsapi_title),
            actions.openNightscoutApi,
            summary = nsApiStatusText(ui.nightscoutApi, now),
            leading = { ServiceIcon(Service.Nightscout) },
        )
        NavigationRow(
            stringResource(R.string.in_apps_xdrip),
            actions.openXdrip,
            summary = xdripRowSummary(ui.xdrip, ui.readers),
            leading = { ServiceIcon(Service.Xdrip) },
            warning = ui.xdrip == XdripState.PortBusy,
        )
    }
}

/**
 * Settings › Import and export's data files (docs/35 §7: one-off files, out of Share with apps): Import history on the
 * wearer's phone (with its state) and Export data.
 */
@Composable
fun DataFileRows(
    viewModel: AppsViewModel,
    openImport: () -> Unit,
    openExport: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ui?.let { DataFileRowsContent(it, openImport, openExport) }
}

@Composable
fun DataFileRowsContent(
    ui: AppsUi,
    openImport: () -> Unit,
    openExport: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.in_apps_files_group)) {
        if (ui.wearer) {
            NavigationRow(
                stringResource(R.string.in_import_title),
                openImport,
                summary = importStatusText(ui.history),
                icon = DsR.drawable.ds_ic_download,
            )
        }
        NavigationRow(
            stringResource(R.string.in_export_title),
            openExport,
            summary = stringResource(R.string.in_apps_export_summary),
            icon = DsR.drawable.ds_ic_file_export,
        )
    }
}
