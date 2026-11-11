// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.AlertHold
import app.mauri.sukrara.core.model.AppUpdates
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.feature.settings.Distribution
import app.mauri.sukrara.monitor.RootFeatures
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.NotificationGroups
import app.mauri.sukrara.monitor.notify.NotificationIds
import app.mauri.sukrara.monitor.systemui.SystemUiRoot
import app.mauri.sukrara.update.UpdateEndpoint
import app.mauri.sukrara.update.UpdateModule
import app.mauri.sukrara.update.UpdateNotificationPorts
import app.mauri.sukrara.update.UpdatePorts
import app.mauri.sukrara.update.UpdateRecord
import kotlinx.coroutines.flow.first

/**
 * The GitHub build (GitHub releases, Obtainium; docs/20-publishing.md › Flavors): root and the LSPosed module in
 * System UI, and the updater (#188: the daily check, the update notification, the checked install; `:update`). The
 * play flavor's `Store` has no updater.
 */
internal object Store {
    val distribution = Distribution.GitHub

    val rootFeatures: RootFeatures.Factory? = SystemUiRoot

    /** The updater ([AppGraph.updates] keeps THE one; construction does no I/O, [start] runs it). */
    fun updates(graph: AppGraph): AppUpdates? = UpdateModule(graph.app, ports(graph))

    /** At app start: the daily check follows its switch once the phone is unlocked. */
    fun start(graph: AppGraph) = (graph.updates as UpdateModule).start()

    private fun ports(graph: AppGraph): UpdatePorts {
        val app = graph.app
        val info = NotificationGroups.Group.INFO
        // release builds: always the GitHub releases and the release key (a debug build's UPDATES command may point it
        // at a local test server: BuildVariant)
        val testServer = BuildVariant.updateServer(app)
        return UpdatePorts(
            installed = graph.appVersion,
            installedCode = BuildConfig.VERSION_CODE.toLong(),
            prerelease = BuildConfig.PRERELEASE,
            endpoint = testServer?.let { (base, cert) -> UpdateEndpoint.testServer(base, cert) } ?: UpdateEndpoint.GitHub,
            http = graph.data.http,
            io = graph.dispatchers.io,
            scope = graph.scope,
            settings = graph.data.settings,
            record = graph.data.typedStore("update", UpdateRecord.serializer(), UpdateRecord(), whenCorrupt = UpdateRecord()),
            unlocked = { graph.unlocked.first { it } },
            // before the unlock no alert runtime runs here, and no update installs then (WorkManager waits for it)
            hold = { graph.runningAlerts()?.hold() ?: AlertHold() },
            notifications =
                UpdateNotificationPorts(
                    channel = Channels.UPDATES,
                    channelGroup = Channels.Group.INFO.id,
                    group = info.key,
                    post = { NotificationGroups.post(app, NotificationIds.UPDATE, it, info, Channels.UPDATES) },
                    cancel = { NotificationGroups.cancel(app, NotificationIds.UPDATE, info) },
                    openAbout = { android.content.Intent(app, MainActivity::class.java).setAction(NavTargets.ACTION_ABOUT_PAGE) },
                ),
            log = { graph.data.diagLog.log("update", it) },
            checkAtStart = testServer != null,
        )
    }
}
