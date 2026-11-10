// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * The widgets' own redraw alarm (#98): at the drawn picture's next freshness step, [WidgetRedrawReceiver] redraws them,
 * starting the app's process if it died meanwhile. Non-waking (`RTC`): nobody looks at a widget while the phone
 * sleeps, and it fires as soon as the phone wakes. Exact when allowed, else within a minute. Only while a widget is
 * placed; null cancels it.
 */
internal class WidgetRedrawAlarm(
    private val context: Context,
) {
    fun set(at: Instant?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, WidgetRedrawReceiver::class.java).setAction(WidgetRedrawReceiver.ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        runCatching {
            when {
                at == null || !placed() -> am.cancel(pi)
                am.canScheduleExactAlarms() -> am.setExact(AlarmManager.RTC, at.toEpochMilli(), pi)
                else -> am.setWindow(AlarmManager.RTC, at.toEpochMilli(), WINDOW_MS, pi)
            }
        }.onFailure { Log.w(TAG, "redraw alarm: ${it.javaClass.simpleName}") }
    }

    private fun placed(): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return WidgetKind.entries.any { kind ->
            manager.getAppWidgetIds(ComponentName(context, kind.receiver)).isNotEmpty()
        }
    }

    private companion object {
        const val TAG = "Sukrara.widgets"
        const val WINDOW_MS = 60_000L
    }
}

/** [WidgetRedrawAlarm]'s receiver (not exported: only this app's explicit alarm reaches it). */
class WidgetRedrawReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION) return
        val host = context.widgetsHost
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(BUDGET_MS) {
                    host.awaitReady()
                    host.widgets.updater.tick()
                }
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val ACTION = "app.mauri.sukrara.widgets.REDRAW"

        /** A receiver gets 10 s before Android calls it unresponsive. */
        const val BUDGET_MS = 8_000L
    }
}
