// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.lockedboot

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.core.data.userUnlocked
import app.mauri.sukrara.core.model.SystemClock
import app.mauri.sukrara.graph.Clock
import app.mauri.sukrara.monitor.ALERT_CHECK_BUDGET_MS
import app.mauri.sukrara.monitor.AlertsHost
import app.mauri.sukrara.monitor.MonitorHost
import app.mauri.sukrara.monitor.R
import app.mauri.sukrara.monitor.alerts.AlertCategory
import app.mauri.sukrara.monitor.alerts.AlertChannels
import app.mauri.sukrara.monitor.notify.AppActivities
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.NotificationIds
import app.mauri.sukrara.monitor.runAsync
import app.mauri.sukrara.monitor.service.AndroidAlarms
import app.mauri.sukrara.monitor.service.StartReason
import app.mauri.sukrara.monitor.service.StartResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The locked boot's receiver (`directBootAware`, not exported): the locked boot, the *No data* alarm of a phone that
 * doesn't monitor before the unlock, and the boot after the unlock (which clears what it showed). At the locked boot
 * the Application says whether this phone monitors before the unlock (its graph reads the role and the pairing from
 * device-protected storage, #160): then monitoring starts here as after any restart; otherwise only the
 * [LockedBootStore] is read. [LockedBoot] decides what shows.
 */
class LockedBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val ctx = context.applicationContext
        val action = intent.action ?: return
        try {
            val boot = lockedBoot(ctx)
            when (action) {
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    if (ctx is AlertsHost && ctx is MonitorHost) {
                        runAsync(action) { lockedBootCompleted(ctx, boot) }
                    } else {
                        boot.onLockedBoot(monitoring = false)
                    }
                }

                ACTION_RING -> {
                    boot.onAlarm()
                }

                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_USER_UNLOCKED -> {
                    boot.onUnlocked()
                }
            }
        } catch (e: Exception) {
            // never take the process down before the unlock: the boot after it still runs the normal path
            Log.w(TAG, "$action failed: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val ACTION_RING = "app.mauri.sukrara.lockedboot.RING"
        private const val TAG = "Sukrara.lockedboot"

        fun lockedBoot(ctx: Context): LockedBoot {
            val store = LockedBootStore.of(ctx)
            return LockedBoot(
                plan = store::read,
                surface = AndroidLockedBootSurface(ctx),
                alarm = AndroidAlarms(ctx).lockedBoot(SystemClock),
                secure = { secure(ctx) },
                unlocked = { ctx.userUnlocked },
                now = { SystemClock.now().epochSecond },
            )
        }

        /**
         * The locked boot on a phone with a screen lock: when this phone monitors before the unlock (the wearer reading
         * their own sensor), monitoring starts as `BootReceiver` starts it after an unlock (the alert check from the
         * saved state, the health check, the service) and the lock screen says so; otherwise, or when anything here
         * fails, the waiting notice and its *No data* alarm. A start Android refuses shows *Monitoring stopped*, and the
         * alert check's alarm rings no data and tries again.
         */
        suspend fun <H> lockedBootCompleted(
            ctx: H,
            boot: LockedBoot,
        ) where H : Context, H : AlertsHost, H : MonitorHost {
            if (ctx.userUnlocked || !secure(ctx)) return
            val result =
                try {
                    val alerts = ctx.awaitAlerts()
                    if (alerts == null) {
                        StartResult.NotNeeded
                    } else {
                        withTimeoutOrNull(ALERT_CHECK_BUDGET_MS) { alerts.check() } ?: Log.w(TAG, "alert check timed out")
                        ctx.monitor.healthCheck.schedule()
                        ctx.monitor.starter.start(StartReason.LockedBoot)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "monitoring before the unlock failed: ${e.javaClass.simpleName}")
                    StartResult.NotNeeded
                }
            when (result) {
                StartResult.Started -> boot.onLockedBoot(monitoring = true)
                StartResult.Denied -> Unit
                StartResult.NotNeeded -> boot.onLockedBoot(monitoring = false)
            }
        }

        private fun secure(ctx: Context) = ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure ?: true
    }
}

/**
 * [LockedBootSurface] as one notification ([NotificationIds.LOCKED_BOOT]): public on the lock screen (no health data
 * in it), the tap opens the app, which asks for the unlock first. Its notices are silent `status` ones; when the
 * waiting one rings it moves to *No data*'s alert channel (its sound through the direct-boot-aware sound provider).
 */
class AndroidLockedBootSurface(
    private val ctx: Context,
) : LockedBootSurface {
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)

    override fun showWaiting() =
        notice(
            R.string.monitor_locked_boot_title,
            R.string.monitor_locked_boot_text,
            R.drawable.monitor_ic_no_readings,
        )

    override fun showMonitoring() =
        notice(R.string.monitor_locked_boot_monitoring_title, R.string.monitor_locked_boot_monitoring_text, R.drawable.monitor_ic_app)

    private fun notice(
        @StringRes title: Int,
        @StringRes text: Int,
        @DrawableRes icon: Int,
    ) {
        val n =
            builder(Channels.STATUS, ctx.getString(title), ctx.getString(text), icon)
                .setCategory(Notification.CATEGORY_STATUS)
                // silent as NotificationCompat.setSilent: a group child that leaves alerting to a summary that never comes
                .setGroup(SILENT_GROUP)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                .build()
        nm.notify(NotificationIds.LOCKED_BOOT, n)
    }

    override fun ring(plan: LockedBootPlan) {
        val since = plan.lastReadingAt?.let { Clock.time(it) }
        val text =
            if (since == null) {
                ctx.getString(R.string.monitor_locked_boot_text)
            } else {
                ctx.getString(R.string.monitor_locked_boot_alarm_text, since)
            }
        val n =
            builder(
                AlertChannels.id(ctx, AlertKind.NO_DATA, plan.alarm),
                ctx.getString(R.string.monitor_locked_boot_alarm_title),
                text,
                R.drawable.monitor_ic_no_readings,
            ).setCategory(AlertCategory.of(plan.alarm, AlertCategory.Dnd.of(nm)))
                .build()
        nm.notify(NotificationIds.LOCKED_BOOT, n)
    }

    override fun clear() = nm.cancel(NotificationIds.LOCKED_BOOT)

    private fun builder(
        channel: String,
        title: String,
        text: String,
        @DrawableRes icon: Int,
    ): Notification.Builder =
        Notification
            .Builder(ctx, channel)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setContentIntent(openApp())

    /** The launcher activity: from the lock screen Android asks for the unlock first, which is the way out. */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(ctx, 0, AppActivities.launch(ctx), PendingIntent.FLAG_IMMUTABLE)

    private companion object {
        const val SILENT_GROUP = "locked_boot"
    }
}
