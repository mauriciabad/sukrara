// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Is one of the graph widgets on the home screen? The home screen offers "Add the widget" while none is placed
 * (FEATURES.md "Add-the-widget banner"). Placed = the launcher bound an id to the combined or the graph widget
 * (AppWidgetManager.getAppWidgetIds); the small value widget isn't the main surface. Checked when the home screen
 * resumes ([refresh]), when a widget is added or removed, and on the pin request's success callback.
 *
 * Placing it from the app is `requestPinAppWidget` with the widget's generated preview ([pinPreview]), so the
 * launcher's dialog shows the real card instead of a blank one; once placed, its *Widget settings* open as after
 * placing it from the launcher's picker ([PinnedReceiver]). When the launcher can't pin (not supported, or it
 * refused the request) [canPin] is false and the banner says how to add the widget by hand.
 */
class WidgetPresence(
    private val context: Context,
    /** The app's scope: the preview is composed before the request goes. */
    private val scope: CoroutineScope,
) {
    private val placedState = MutableStateFlow<Boolean?>(null)

    /** Null until the first [refresh] (nothing flashes before it). */
    val placed: StateFlow<Boolean?> = placedState.asStateFlow()

    /** The launcher refused a pin request in this process (a profile restricted on the home screen, …). */
    @Volatile private var refused = false

    fun refresh() {
        val mgr = AppWidgetManager.getInstance(context)
        placedState.value =
            BIG.any { receiver ->
                runCatching { mgr.getAppWidgetIds(ComponentName(context, receiver)) }.getOrNull()?.isNotEmpty() == true
            }
    }

    /** Whether the launcher can place a widget from the app ([pin]); else the banner explains how to add it by hand. */
    fun canPin(): Boolean =
        !refused && runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false)

    /**
     * Asks the launcher to place the combined widget, its preview in the dialog. Needs [activity] in the foreground
     * (the request goes once the preview is composed, a few ms). [onRefused] runs when the launcher refused at once
     * ([canPin] is false from then on); success arrives later on [PinnedReceiver], with the new widget's id (the
     * callback is mutable so the system can add it; it's explicit, to this app's private receiver).
     */
    fun pin(
        activity: Activity,
        onRefused: () -> Unit,
    ) {
        scope.launch(Dispatchers.Main.immediate) {
            val extras = pinPreview(activity, WidgetKind.COMBINED)
            val callback =
                PendingIntent.getBroadcast(
                    activity,
                    0,
                    Intent(activity, PinnedReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            // only the launcher's own "no" counts as refused (a request that failed, e.g. the screen was left
            // meanwhile, keeps the Add button for the next try)
            val ok =
                runCatching {
                    AppWidgetManager.getInstance(activity).requestPinAppWidget(ComponentName(activity, BIG.first()), extras, callback)
                }.onFailure { Log.w(TAG, "pin: $it") }
                    .getOrNull() ?: return@launch
            if (!ok) {
                refused = true
                onRefused()
            }
        }
    }

    /**
     * requestPinAppWidget's success callback: the widget was placed. The banner goes, and the new widget's *Widget
     * settings* open over the app the person is in (back or *Cancel* keeps the app's settings), as when it's placed
     * from the launcher's picker.
     */
    class PinnedReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            context.widgetsHost.widgets.presence
                .refresh()
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            settingsIntent(context, id)?.let { runCatching { context.startActivity(it) }.onFailure { e -> Log.w(TAG, "settings: $e") } }
        }
    }

    companion object {
        private const val TAG = "Sukrara.widgets"

        /** The *Widget settings* of the placed widget [appWidgetId] (null: no id came with the callback). */
        internal fun settingsIntent(
            context: Context,
            appWidgetId: Int,
        ): Intent? =
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .takeIf { appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID }

        /** The widgets that make the banner unnecessary, the one the banner places first. */
        val BIG: List<Class<*>> = listOf(CombinedWidgetReceiver::class.java, GraphWidgetReceiver::class.java)
    }
}
