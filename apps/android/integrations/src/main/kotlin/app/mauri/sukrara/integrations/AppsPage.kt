// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations

import android.content.Context
import android.content.pm.PackageManager
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
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.integrations.broadcasts.BcStatus
import app.mauri.sukrara.integrations.broadcasts.broadcastStatusText
import app.mauri.sukrara.integrations.healthconnect.HcStatus
import app.mauri.sukrara.integrations.healthconnect.healthStatusText
import app.mauri.sukrara.integrations.history.ImportState
import app.mauri.sukrara.integrations.history.importStatusText
import app.mauri.sukrara.integrations.libreview.libreViewStatusText
import app.mauri.sukrara.integrations.localserver.NsApiState
import app.mauri.sukrara.integrations.localserver.XdripState
import app.mauri.sukrara.integrations.localserver.nsApiStatusText
import app.mauri.sukrara.integrations.tidepool.tidepoolStatusText
import app.mauri.sukrara.integrations.ui.ago
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
    val xdripOn: Boolean,
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
)

class AppsActions(
    val setXdrip: (Boolean) -> Unit = {},
    val openNightscout: () -> Unit = {},
    val openExport: () -> Unit = {},
    val openLibreView: () -> Unit = {},
    val openNightscoutApi: () -> Unit = {},
    val openTidepool: () -> Unit = {},
    val openBroadcasts: () -> Unit = {},
    val openHealthConnect: () -> Unit = {},
    val openImport: () -> Unit = {},
)

/** An installed app that reads the xDrip+ server: its package (for its icon) and its name. */
data class ServerReader(
    val pkg: String,
    val label: String,
)

/**
 * Installed apps known to read glucose from the xDrip+ server rather than broadcasts. Garmin watch apps can't open
 * sockets: Garmin Connect (or the Connect IQ app) makes the request for them. Each package is in the manifest's
 * `<queries>`.
 */
object ServerReaders {
    val PACKAGES =
        listOf(
            // Garmin Connect
            "com.garmin.android.apps.connectmobile",
            // the Connect IQ store app (older watches / setups)
            "com.garmin.connectiq",
        )

    /** The installed ones, by name (PackageManager: call off the main thread). */
    fun installed(context: Context): List<ServerReader> {
        val pm = context.packageManager
        return PACKAGES
            .mapNotNull { p ->
                runCatching { pm.getApplicationInfo(p, PackageManager.ApplicationInfoFlags.of(0)) }
                    .getOrNull()
                    ?.let { ServerReader(p, pm.getApplicationLabel(it).toString()) }
            }.sortedBy { it.label.lowercase() }
    }
}

private data class Others(
    val api: NsApiState,
    val tidepool: UploadStatus,
    val broadcastApps: Int,
    val broadcasts: BcStatus,
    val health: HcStatus,
    val history: ImportState = ImportState.Idle,
)

class AppsViewModel(
    private val module: IntegrationsModule,
    context: Context,
    io: CoroutineDispatcher,
) : ViewModel() {
    private val readers = MutableStateFlow<List<ServerReader>>(emptyList())
    val ui: StateFlow<AppsUi?> =
        combine(
            combine(module.wearer, module.xdrip.on, module.xdrip.state) { w, on, x -> Triple(w, on, x) },
            readers,
            module.nightscout.page,
            module.libreView.page,
            combine(
                combine(module.nightscoutApi.state, module.tidepool.status) { api, tp -> api to tp },
                module.broadcasts.settingsFlow,
                module.broadcasts.status,
                module.healthConnect.status,
                module.history.state,
            ) { (api, tp), bc, bs, hc, h -> Others(api, tp, bc.apps.size, bs, hc, h) },
        ) { (w, on, x), r, ns, lv, o ->
            AppsUi(w, on, x, r, ns.status, lv.status, o.api, o.tidepool, o.broadcastApps, o.broadcasts, o.health, o.history)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        val app = context.applicationContext
        viewModelScope.launch { readers.value = withContext(io) { ServerReaders.installed(app) } }
    }

    fun setXdrip(on: Boolean) {
        viewModelScope.launch { module.xdrip.setOn(on) }
    }
}

@Composable
fun AppsPage(
    viewModel: AppsViewModel,
    now: () -> Instant,
    openNightscout: () -> Unit,
    openExport: () -> Unit,
    openLibreView: () -> Unit,
    openNightscoutApi: () -> Unit,
    openTidepool: () -> Unit,
    openBroadcasts: () -> Unit,
    openHealthConnect: () -> Unit,
    openImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ui?.let {
        AppsContent(
            it,
            now(),
            AppsActions(
                viewModel::setXdrip,
                openNightscout,
                openExport,
                openLibreView,
                openNightscoutApi,
                openTidepool,
                openBroadcasts,
                openHealthConnect,
                openImport,
            ),
            modifier,
        )
    }
}

/**
 * Settings › Share with apps: LibreView and the Nightscout upload (wearer), the xDrip+ server for Garmin watches with who reads it and
 * when it was last asked (and each installed app that reads it, with that server's switch), and Export data. Every row
 * that stands for another app or service shows that app's icon ([ServiceIcon] / [PackageIcon]), as the PoC did.
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
        SwitchRow(
            stringResource(R.string.in_apps_xdrip),
            ui.xdripOn,
            actions.setXdrip,
            summary = xdripSummary(ui, now),
            leading = { ServiceIcon(Service.Xdrip) },
        )
        // where the person looks for Garmin: each installed app that reads the server, with the server's own switch
        for (r in ui.readers) {
            SwitchRow(
                r.label,
                ui.xdripOn,
                actions.setXdrip,
                summary = stringResource(R.string.in_apps_reader_summary),
                leading = { PackageIcon(r.pkg) },
            )
        }
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
    }
    SettingsGroup(title = stringResource(R.string.in_apps_files_group)) {
        if (ui.wearer) {
            NavigationRow(
                stringResource(R.string.in_import_title),
                actions.openImport,
                summary = importStatusText(ui.history),
                icon = DsR.drawable.ds_ic_download,
            )
        }
        NavigationRow(
            stringResource(R.string.in_export_title),
            actions.openExport,
            summary = stringResource(R.string.in_apps_export_summary),
            icon = DsR.drawable.ds_ic_file_export,
        )
    }
    FooterText(stringResource(R.string.in_apps_footer))
}

@Composable
private fun xdripSummary(
    ui: AppsUi,
    now: Instant,
): String {
    val parts = ArrayList<String>()
    when (val x = ui.xdrip) {
        XdripState.Off -> {
            parts += stringResource(R.string.in_apps_xdrip_off)
        }

        XdripState.PortBusy -> {
            parts += stringResource(R.string.in_apps_xdrip_busy)
        }

        XdripState.LeftToJuggluco -> {
            parts += stringResource(R.string.in_apps_xdrip_juggluco)
        }

        is XdripState.Listening -> {
            parts += stringResource(R.string.in_apps_xdrip_on)
            parts +=
                x.lastAsked?.let { stringResource(R.string.in_apps_xdrip_last_asked, ago(it, now)) }
                    ?: stringResource(R.string.in_apps_xdrip_not_asked)
        }
    }
    if (ui.readers.isNotEmpty()) parts += stringResource(R.string.in_apps_xdrip_read_by, ui.readers.joinToString { it.label })
    return parts.joinToString(" · ")
}
