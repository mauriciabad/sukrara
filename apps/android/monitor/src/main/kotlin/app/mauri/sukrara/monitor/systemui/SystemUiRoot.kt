// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

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
 * Root and the Sukrara LSPosed module in System UI (Settings › Status bar's *With root*, home's restart card), in
 * every build (#142): [SystemAccess] and the [RootStatusBar] it feeds, drawn from the monitor's state, the settings
 * and its redraw signals. `MonitorModule` builds the one instance.
 */
class SystemUiRoot internal constructor(
    context: Context,
    scope: CoroutineScope,
    io: CoroutineDispatcher,
    clock: Clock,
    hub: Flow<GlucoseState>,
    settings: SettingsRepository,
    graphConfig: Flow<GraphConfig>,
    freshnessTicks: Flow<Unit>,
    /** Posts the glucose notification again (the module filters its icon when System UI rebuilds the icon list). */
    repostNotification: () -> Unit,
) {
    private val systemAccess = SystemAccess(io, scope)

    private val statusBar =
        RootStatusBar(
            context = context,
            scope = scope,
            clock = clock,
            access = systemAccess,
            hub = hub,
            settings = settings,
            graphConfig = graphConfig,
            freshnessTicks = freshnessTicks,
            repostNotification = repostNotification,
        )

    /** Root for Sukrara and the module's state, for Settings, setup and home. */
    val access: SystemAccessPort get() = systemAccess

    /** Starts listening for LSPosed and following the hub (from `MonitorModule.readyUnlocked`). */
    fun start() {
        systemAccess.start()
        statusBar.start()
    }

    /** System UI's module said hello with the code it runs and whether it orders the icons. */
    fun onHello(
        code: String?,
        iconOrder: Boolean,
    ) = statusBar.onHello(code, iconOrder)
}
