// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.localserver

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
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.MainSwitchRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.integrations.R
import app.mauri.sukrara.integrations.garmin.ticking
import app.mauri.sukrara.integrations.ui.since
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

/** An installed app that reads the xDrip+ server: its package (for its icon) and its name. */
data class ServerReader(
    val pkg: String,
    val label: String,
)

/**
 * Installed apps known to read glucose from the xDrip+ server rather than broadcasts. Garmin watch apps can't open
 * sockets: Garmin Connect (or the Connect IQ app) makes the request for them. Each package is in the manifest's
 * `<queries>`. Which app asked isn't knowable on loopback, so these are the apps that can, not a log.
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

/** What the xDrip+ server page shows. */
data class XdripUi(
    val on: Boolean,
    val state: XdripState,
    val readers: List<ServerReader> = emptyList(),
)

class XdripViewModel(
    private val server: XdripServer,
    context: Context,
    io: CoroutineDispatcher,
) : ViewModel() {
    private val readers = MutableStateFlow<List<ServerReader>>(emptyList())
    val ui: StateFlow<XdripUi?> =
        combine(server.on, server.state, readers, ::XdripUi).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        val app = context.applicationContext
        viewModelScope.launch { readers.value = withContext(io) { ServerReaders.installed(app) } }
    }

    fun setOn(on: Boolean) {
        viewModelScope.launch { server.setOn(on) }
    }
}

@Composable
fun XdripPage(
    viewModel: XdripViewModel,
    now: () -> Instant,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ui?.let { XdripContent(it, ticking(now), viewModel::setOn, modifier) }
}

/**
 * Settings › Share with apps › *xDrip+ server* (also reached from the Garmin page, for other faces and data fields): its
 * main switch with what it is doing under it, the apps on this phone that read it (a list, not switches: the server is
 * one switch for all of them), and the footer that says any app on this phone can read it while it is on.
 */
@Composable
fun XdripContent(
    ui: XdripUi,
    now: Instant,
    setOn: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(stringResource(R.string.in_xdrip_intro))
    MainSwitchRow(
        stringResource(R.string.in_xdrip_switch),
        ui.on,
        setOn,
        note =
            when (val x = ui.state) {
                XdripState.Off -> {
                    null
                }

                XdripState.PortBusy -> {
                    stringResource(R.string.in_xdrip_busy)
                }

                XdripState.LeftToJuggluco -> {
                    stringResource(R.string.in_xdrip_juggluco)
                }

                is XdripState.Listening -> {
                    x.lastAsked?.let { stringResource(R.string.in_xdrip_last_asked, since(it, now)) }
                        ?: stringResource(R.string.in_xdrip_not_asked)
                }
            },
    )
    SettingsGroup(title = stringResource(R.string.in_xdrip_readers)) {
        if (ui.readers.isEmpty()) {
            InfoRow(
                stringResource(R.string.in_xdrip_no_reader),
                summary = stringResource(R.string.in_xdrip_no_reader_summary),
                icon = DsR.drawable.ds_ic_apps,
            )
        }
        for (r in ui.readers) InfoRow(r.label, leading = { PackageIcon(r.pkg) })
    }
    FooterText(stringResource(R.string.in_xdrip_footer))
}

/** The xDrip+ server row's line (Share with apps, the Garmin page): what it does now, and who reads it. */
@Composable
fun xdripRowSummary(
    state: XdripState,
    readers: List<ServerReader>,
): String =
    when (state) {
        XdripState.Off -> {
            stringResource(R.string.in_xdrip_off)
        }

        XdripState.PortBusy -> {
            stringResource(R.string.in_xdrip_busy_short)
        }

        XdripState.LeftToJuggluco -> {
            stringResource(R.string.in_xdrip_juggluco_short)
        }

        is XdripState.Listening -> {
            if (readers.isEmpty()) {
                stringResource(R.string.in_xdrip_on)
            } else {
                stringResource(R.string.in_xdrip_on_read_by, readers.joinToString { it.label })
            }
        }
    }
