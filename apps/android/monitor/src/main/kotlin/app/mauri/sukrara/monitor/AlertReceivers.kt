// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.mauri.sukrara.alerts.AlertLimits
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.monitor.alerts.AlertsModule
import app.mauri.sukrara.monitor.notify.NotificationGroups
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Application implements this, so receivers, the service and the sound provider reach the one [AlertsModule].
 * Before the first unlock after a restart the alerts run only when this phone monitors then: the wearer reading their
 * own sensor (docs/30-direct-boot.md, #160); otherwise only the locked-boot notice and its *No data* alarm do.
 */
interface AlertsHost {
    /** The one alerts module, or null when alerts don't run in this process now; decides that first (reads the role). */
    suspend fun awaitAlerts(): AlertsModule?

    /** [awaitAlerts] without waiting: null while it isn't decided yet, too. */
    val alertsNow: AlertsModule?
}

/** The one [AlertsModule], or null before the first unlock when this phone doesn't monitor then. */
internal suspend fun Context.awaitAlertsModule(): AlertsModule? = (applicationContext as? AlertsHost)?.awaitAlerts()

/** [awaitAlertsModule] without waiting (System UI's binder thread). */
internal val Context.alertsModuleNow: AlertsModule?
    get() = (applicationContext as? AlertsHost)?.alertsNow

private const val TAG = "Sukrara.alerts"

/**
 * The alert notification's buttons (non-exported; immutable PendingIntents except the direct reply's): *Mute…* /
 * *Change time* (a chip like "1 h" or a typed length), *Remind less*, *Remind as usual*, *Unmute*, and the swipe's
 * tidy-up. They work from the lock screen without opening the app. A reply that isn't a length in range changes
 * nothing and shows the notification again, which also takes the reply's spinner away.
 */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(KIND) ?: return
        runAsync("alert action") {
            val module = context.awaitAlertsModule() ?: return@runAsync
            val runtime = module.runtime
            when (intent.action) {
                MUTE -> {
                    val typed = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(TYPED)?.toString()
                    val minutes = typed?.let(Durations::parse)?.takeIf { it > 0 && AlertLimits.ACTION_MUTE.valid(it.toDouble()) }
                    if (minutes != null) runtime.mute(kind, minutes) else runtime.reshow(kind)
                }

                REMIND_LESS -> {
                    runtime.remindLess(kind)
                }

                REMIND_AS_USUAL -> {
                    runtime.remindAsUsual(kind)
                }

                UNMUTE -> {
                    runtime.unmute(kind)
                }

                TIDY -> {
                    // a swipe is an answer on this phone (#39), then the group's tidy-up
                    runtime.acknowledge(kind)
                    NotificationGroups.tidy(context, NotificationGroups.Group.ALERTS)
                }
            }
        }
    }

    companion object {
        const val MUTE = "app.mauri.sukrara.alert.MUTE"
        const val REMIND_LESS = "app.mauri.sukrara.alert.REMIND_LESS"
        const val REMIND_AS_USUAL = "app.mauri.sukrara.alert.REMIND_AS_USUAL"
        const val UNMUTE = "app.mauri.sukrara.alert.UNMUTE"
        const val TIDY = "app.mauri.sukrara.alert.TIDY"
        val ACTIONS = listOf(MUTE, REMIND_LESS, REMIND_AS_USUAL, UNMUTE, TIDY)
        const val KIND = "kind"
        const val TYPED = "minutes"
    }
}

/**
 * The alert checks' exact alarms (non-exported: only `AndroidAlarms.alertCheck` sets them). Evaluates from the saved
 * record **without** the monitoring service (no data still alerts on time when Android killed it), re-arms the next
 * alarm, checks the sensor end, then asks the starter to bring the service back (it shows "Monitoring stopped —
 * tap to resume" when Android refuses). Boot, app update and clock changes reach the same check through
 * [BootReceiver], which starts the service once.
 */
class AlertCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            ACTION_CHECK -> {
                runAsync(action) {
                    val module = context.awaitAlertsModule() ?: return@runAsync
                    withTimeoutOrNull(ALERT_CHECK_BUDGET_MS) { module.check() } ?: Log.w(TAG, "alert check timed out")
                    module.restarter.restart()
                }
            }

            // a sensor countdown swiped away: kept away for this sensor
            ACTION_COUNTDOWN_DISMISSED, ACTION_WARMUP_DISMISSED -> {
                runAsync(action) { context.awaitAlertsModule()?.sensorEnd?.dismissed(warmup = action == ACTION_WARMUP_DISMISSED) }
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "app.mauri.sukrara.alert.CHECK"
        const val ACTION_COUNTDOWN_DISMISSED = "app.mauri.sukrara.sensor.COUNTDOWN_DISMISSED"
        const val ACTION_WARMUP_DISMISSED = "app.mauri.sukrara.sensor.WARMUP_DISMISSED"
    }
}
