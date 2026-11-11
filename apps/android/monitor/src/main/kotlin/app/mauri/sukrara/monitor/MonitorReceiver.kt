// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.mauri.sukrara.monitor.service.StartReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The monitor's broadcasts (not exported; system broadcasts still arrive):
 *  - boot, app update, time / time-zone change → evaluate alerts and the sensor end from the saved state, reschedule
 *    the health check, start the service once (each is an allowed background start); a time / time-zone change (also
 *    Settings' 12/24-hour switch) redraws the widgets' times;
 *  - language change → channel names, the glucose notification and the widgets in the new language;
 *  - exact-alarm permission changed → check alerts (which arms the alert alarm again) and reschedule the health check;
 *  - the health check alarm → start the service if it should run, schedule the next check;
 *  - the freshness step → redraw the glucose surfaces;
 *  - the glucose notification was swiped away → post it again.
 * The last three (`HealthCheckReceiver`) are direct-boot aware: they run before the first unlock while the wearer's
 * phone monitors then (docs/30-direct-boot.md, #160).
 */
abstract class MonitorReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        runAsync(action) { handle(context, action) }
    }

    /**
     * Boot, update and clock changes check alerts alongside the rest (the alert runtime is one object under a mutex, so
     * the service starting meanwhile can't post twice): a slow check never costs the health check or the start.
     */
    private suspend fun handle(
        context: Context,
        action: String,
    ) = coroutineScope {
        if (checksAlerts(action)) launch { checkAlerts(context) }
        // a failing start or health check must not cancel the alert check running alongside
        try {
            handleMonitor(context, action)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$action failed: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun handleMonitor(
        context: Context,
        action: String,
    ) {
        val host = context.monitorHost
        host.awaitMonitor()
        val monitor = host.monitor
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                restart(monitor, StartReason.Boot)
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                restart(monitor, StartReason.PackageReplaced)
            }

            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                monitor.onClockChanged()
                restart(monitor, StartReason.TimeChanged)
            }

            Intent.ACTION_LOCALE_CHANGED -> {
                monitor.onLocaleChanged()
            }

            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                monitor.healthCheck.schedule()
            }

            ACTION_HEALTH_CHECK -> {
                monitor.healthCheck.onFire()
            }

            ACTION_FRESHNESS_STEP -> {
                monitor.onFreshnessStep()
            }

            ACTION_GLUCOSE_DISMISSED -> {
                // the service posts it again as it is (a forced post, with its card)
                if (MonitoringService.isRunning) monitor.repost()
            }
        }
    }

    /** Alerts first (they don't wait for the graph): no data, a mute ending or a sensor end while the phone was off. */
    private suspend fun checkAlerts(context: Context) {
        val alerts = context.awaitAlertsModule() ?: return
        withTimeoutOrNull(ALERT_CHECK_BUDGET_MS) { alerts.check() } ?: Log.w(TAG, "alert check timed out")
    }

    private suspend fun restart(
        monitor: MonitorModule,
        reason: StartReason,
    ) {
        monitor.healthCheck.schedule()
        monitor.starter.start(reason)
    }

    companion object {
        private const val TAG = "Sukrara.monitor"

        /**
         * Boot, app update, clock and time-zone changes and the exact-alarm permission's change: the alert check's other
         * triggers besides its own alarm (it arms that alarm again). USE_EXACT_ALARM can't be revoked today, but should the
         * app ever hold SCHEDULE_EXACT_ALARM instead, a permission granted again must re-arm the alert check too (#44).
         */
        private val SYSTEM_EVENTS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            )

        internal fun checksAlerts(action: String) = action in SYSTEM_EVENTS

        const val ACTION_HEALTH_CHECK = "app.mauri.sukrara.monitor.HEALTH_CHECK"
        const val ACTION_FRESHNESS_STEP = "app.mauri.sukrara.monitor.FRESHNESS_STEP"
        const val ACTION_GLUCOSE_DISMISSED = "app.mauri.sukrara.monitor.GLUCOSE_DISMISSED"
    }
}

/** The system's broadcasts: boot, app update, time / time-zone / language change, exact-alarm permission change. */
class BootReceiver : MonitorReceiver()

/** The monitor's own alarms and intents: the health check, the freshness step, the glucose notification swiped away. */
class HealthCheckReceiver : MonitorReceiver()
