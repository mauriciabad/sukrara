// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor

import android.content.Context
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.graph.GraphConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Root and the Sukrara LSPosed module in System UI (Settings › Status bar's *With root*, home's restart card). Only
 * the full build has them (docs/20-publishing.md › Flavors): its app passes `systemui.SystemUiRoot` to
 * [MonitorModule]; the Google Play build passes nothing, so nothing reaches the `systemui` package and R8 leaves it
 * out (consumer-rules.pro keeps the daemon and the hook only while it is in).
 */
interface RootFeatures {
    /** Root for Sukrara and the module's state, for Settings and home. */
    val access: SystemAccessPort

    /** Starts listening for LSPosed and following the hub (from [MonitorModule.ready]). */
    fun start()

    /** System UI's module said hello with the code it runs and whether it orders the icons. */
    fun onHello(
        code: String?,
        iconOrder: Boolean,
    )

    fun interface Factory {
        fun create(inputs: RootInputs): RootFeatures
    }
}

/** What the root status bar is drawn from: the monitor's state, the settings and its redraw signals. */
class RootInputs(
    val context: Context,
    val scope: CoroutineScope,
    val io: CoroutineDispatcher,
    val clock: Clock,
    val hub: Flow<GlucoseState>,
    val settings: SettingsRepository,
    val graphConfig: Flow<GraphConfig>,
    val freshnessTicks: Flow<Unit>,
    /** Posts the glucose notification again (the module filters its icon when System UI rebuilds the icon list). */
    val repostNotification: () -> Unit,
)
