// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.graph.GraphConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The home-screen widgets' and the lock-screen widget's objects (apps/android/README.md "DI"): built by `AppGraph`
 * from what the widgets read. [glucose] = the hub's state; [treatments] = recent treatments; [graphConfig] = the graph
 * settings; [freshnessTicks] = monitor's freshness steps; [store] = the typed state file "widgets"; [lockLook] = the
 * lock-screen widget's background and appearance. [start] once storage is ready.
 */
class WidgetsModule(
    private val context: Context,
    private val scope: CoroutineScope,
    clock: Clock,
    glucose: Flow<GlucoseState>,
    treatments: Flow<List<Treatment>>,
    graphConfig: Flow<GraphConfig>,
    storedHistory: Flow<List<Reading>>,
    freshnessTicks: Flow<Unit>,
    store: StateStore<WidgetPlacements>,
    lockLook: Flow<LockLook>,
) {
    val settings = WidgetSettingsStore(store)

    val presence = WidgetPresence(context, scope)

    /** The lock-screen widget's ids, as last read (the redraw alarm runs while one is hosted too). */
    @Volatile private var lockIds: Set<Int> = emptySet()

    private val redrawAlarm = WidgetRedrawAlarm(context) { lockIds.isNotEmpty() }

    val updater =
        WidgetUpdater(
            scope = scope,
            clock = clock,
            glucose = glucose,
            treatments = treatments,
            config = graphConfig,
            placements = settings.placements,
            stored = storedHistory,
            freshnessTicks = freshnessTicks,
            screenOn = screenOn(context),
            dark = { isDark(context) },
            redraw = ::redrawPlaced,
            nextRedraw = redrawAlarm::set,
            lockLook = lockLook,
        )

    fun start() {
        presence.refresh()
        updater.start()
        scope.launch { settings.placements.collect { lockIds = it.lock } }
        // the picker's generated previews (Android 15+), drawn by the renderer from a sample day
        scope.launch { publishPreviews(context) }
    }

    /**
     * System UI's module hosts the lock-screen widget under [appWidgetIds] (it bound one, or sent its options): remember
     * them, once storage is [ready], so every redraw reaches them.
     */
    fun lockHosted(
        appWidgetIds: Collection<Int>,
        ready: suspend () -> Unit,
    ) {
        if (appWidgetIds.isEmpty()) return
        scope.launch {
            ready()
            settings.rememberLock(appWidgetIds)
        }
    }

    /** A placement was removed (storage is ready): forget its settings, recheck the banner. */
    suspend fun forget(appWidgetId: Int) {
        settings.forget(intArrayOf(appWidgetId))
        presence.refresh()
    }

    /** One placement's settings changed (Reconfigure): save them, then the updater redraws it. */
    suspend fun saveOverrides(
        appWidgetId: Int,
        overrides: WidgetOverrides,
    ) = settings.set(appWidgetId, overrides)

    /**
     * Redraws every placed widget, one update per placement (never a blanket updateAll per reading), and the lock-screen
     * widget by its remembered ids (Glance's own list never has a widget System UI's module bound, docs/17); an id the
     * system no longer knows is forgotten.
     */
    private suspend fun redrawPlaced() {
        val manager = GlanceAppWidgetManager(context)
        for (kind in WidgetKind.entries) {
            val widget = kind.widget()
            for (id in manager.getGlanceIds(widget.javaClass)) {
                runCatching { widget.update(context, id) }.onFailure { Log.w(TAG, "redraw ${kind.name}: $it") }
            }
        }
        val lock = LockWidget()
        for (id in settings.placements.first().lock) {
            runCatching { lock.update(context, manager.getGlanceIdBy(id)) }.onFailure {
                Log.w(TAG, "redraw lock screen widget: ${it.javaClass.simpleName}")
                settings.forget(intArrayOf(id))
            }
        }
    }

    private companion object {
        const val TAG = "Sukrara.widgets"

        /** Screen on / off from the system broadcasts (registered while the app runs), starting from now. */
        fun screenOn(context: Context): Flow<Boolean> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            c: Context,
                            intent: Intent,
                        ) {
                            trySend(intent.action == Intent.ACTION_SCREEN_ON)
                        }
                    }
                val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                trySend(context.getSystemService(PowerManager::class.java).isInteractive)
                awaitClose { context.unregisterReceiver(receiver) }
            }.distinctUntilChanged()
    }
}

/** The `Application` gives the widgets' Android components (receivers, the Reconfigure activity) their module. */
interface WidgetsHost {
    val widgets: WidgetsModule

    /** Completes when the app's storage and modules are ready. */
    suspend fun awaitReady()
}

internal val Context.widgetsHost: WidgetsHost get() = applicationContext as WidgetsHost
