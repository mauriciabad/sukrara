// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.monitor.RootFeatures
import app.mauri.sukrara.monitor.RootInputs

/** The full build's [RootFeatures]: [SystemAccess] and the [RootStatusBar] it feeds. */
class SystemUiRoot private constructor(
    inputs: RootInputs,
) : RootFeatures {
    private val systemAccess = SystemAccess(inputs.io, inputs.scope)

    private val statusBar =
        RootStatusBar(
            context = inputs.context,
            scope = inputs.scope,
            clock = inputs.clock,
            access = systemAccess,
            hub = inputs.hub,
            settings = inputs.settings,
            graphConfig = inputs.graphConfig,
            freshnessTicks = inputs.freshnessTicks,
            repostNotification = inputs.repostNotification,
        )

    override val access: SystemAccessPort get() = systemAccess

    override fun start() {
        systemAccess.start()
        statusBar.start()
    }

    override fun onHello(
        code: String?,
        iconOrder: Boolean,
    ) = statusBar.onHello(code, iconOrder)

    /** What the full build's app passes to `MonitorModule`. */
    companion object : RootFeatures.Factory {
        override fun create(inputs: RootInputs): RootFeatures = SystemUiRoot(inputs)
    }
}
