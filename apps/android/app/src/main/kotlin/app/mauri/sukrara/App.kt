// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.LocaleList
import app.mauri.sukrara.feature.treatments.TreatmentsHost
import app.mauri.sukrara.feature.treatments.TreatmentsModule
import app.mauri.sukrara.graph.RoundedFont
import app.mauri.sukrara.monitor.AlertsHost
import app.mauri.sukrara.monitor.MonitorHost
import app.mauri.sukrara.monitor.MonitorModule
import app.mauri.sukrara.monitor.alerts.AlertsModule
import app.mauri.sukrara.monitor.lockedboot.LockedBootReceiver
import app.mauri.sukrara.monitor.lockedboot.userUnlocked
import app.mauri.sukrara.pen.PensHost
import app.mauri.sukrara.pen.PensModule
import app.mauri.sukrara.share.ShareOwnerModule
import app.mauri.sukrara.share.owner.requests.ShareOwnerHost
import app.mauri.sukrara.widgets.WidgetsHost
import app.mauri.sukrara.widgets.WidgetsModule
import kotlinx.coroutines.launch

/**
 * Builds the [AppGraph] and hands the runtime modules to their Android components (service, receivers): one `XxxHost`
 * interface per runtime module, each member one line delegating to the graph.
 */
class App :
    Application(),
    MonitorHost,
    AlertsHost,
    TreatmentsHost,
    ShareOwnerHost,
    WidgetsHost,
    PensHost {
    private val graphOnce =
        lazy {
            // before the first unlock after a restart the app's storage is unreadable: nothing may build the graph then
            check(userUnlocked) { "the app graph needs the unlocked storage" }
            AppGraph(this).also { it.ready.start() }
        }

    /** Built at the first use once the phone is unlocked (at once in [onCreate] when it already is). */
    val graph: AppGraph get() = graphOnce.value

    override val monitor: MonitorModule get() = graph.monitor

    override val alerts: AlertsModule get() = graph.alerts

    override suspend fun awaitReady() = graph.ready.await()

    override fun onCreate() {
        super.onCreate()
        // the cards' rounded face (the value, the rings) on every surface, the app's panel included
        RoundedFont.init(this)
        locales = resources.configuration.locales
        night = resources.configuration.isNight
        if (userUnlocked) graph else afterUnlock()
    }

    /**
     * The process started before the first unlock after a restart (only the direct-boot-aware `LockedBootReceiver` and
     * sound provider run then: docs/30-direct-boot.md). At the unlock: what it showed goes and the graph is built; the
     * boot broadcast that follows runs the alert check and starts monitoring as after any restart.
     */
    private fun afterUnlock() {
        val unlock =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    unregisterReceiver(this)
                    LockedBootReceiver.lockedBoot(this@App).onUnlocked()
                    graph
                }
            }
        registerReceiver(unlock, IntentFilter(Intent.ACTION_USER_UNLOCKED), RECEIVER_NOT_EXPORTED)
    }

    private var locales: LocaleList? = null

    private var night = false

    /**
     * A language change renames the notification channels, redraws the glucose notification and home's setup step at
     * once; a switch between the light and dark theme redraws the widgets and the notification's card (pictures in one
     * palette) without waiting for the next reading.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val language = newConfig.locales != locales
        val theme = newConfig.isNight != night
        locales = newConfig.locales
        night = newConfig.isNight
        // before the first unlock there is no graph: it starts with the language and theme of its time
        if (!(language || theme) || !graphOnce.isInitialized()) return
        if (language) graph.locales.value = newConfig.locales
        graph.scope.launch {
            graph.ready.await()
            if (language) graph.monitor.onLocaleChanged() else graph.monitor.onThemeChanged()
        }
    }

    private val Configuration.isNight get() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    override val treatments: TreatmentsModule get() = graph.treatments

    override val shareOwner: ShareOwnerModule get() = graph.shareOwner

    override val widgets: WidgetsModule get() = graph.widgets

    override val pens: PensModule get() = graph.pens
}

val Context.graph: AppGraph get() = (applicationContext as App).graph
