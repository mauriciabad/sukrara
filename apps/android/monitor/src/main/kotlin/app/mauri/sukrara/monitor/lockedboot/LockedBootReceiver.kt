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
import android.content.pm.PackageManager
import android.util.Log
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.core.model.SystemClock
import app.mauri.sukrara.graph.Clock
import app.mauri.sukrara.monitor.R
import app.mauri.sukrara.monitor.alerts.AlertCategory
import app.mauri.sukrara.monitor.alerts.AlertChannels
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.NotificationIds
import app.mauri.sukrara.monitor.service.AndroidAlarms

/**
 * The only receiver that runs before the first unlock after a restart (`directBootAware`, not exported): the locked
 * boot, its own alarm, and the boot after the unlock (which clears what it showed). It reads nothing but the
 * [LockedBootStore] in device-protected storage and never touches the app graph (credential-encrypted storage);
 * the Application doesn't build the graph before the unlock either. [LockedBoot] decides.
 */
class LockedBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val ctx = context.applicationContext
        try {
            val boot = lockedBoot(ctx)
            when (intent.action) {
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> boot.onLockedBoot()
                ACTION_RING -> boot.onAlarm()
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_USER_UNLOCKED -> boot.onUnlocked()
            }
        } catch (e: Exception) {
            // never take the process down before the unlock: the boot after it still runs the normal path
            Log.w(TAG, "${intent.action} failed: ${e.javaClass.simpleName}")
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
                secure = { ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure ?: true },
                unlocked = { ctx.userUnlocked },
                now = { SystemClock.now().epochSecond },
            )
        }
    }
}

/**
 * [LockedBootSurface] as one notification ([NotificationIds.LOCKED_BOOT]): public on the lock screen (no health data
 * in it), the tap opens the app, which asks for the unlock first. Before it rings it is a silent `status` notice; when
 * it rings it moves to *No data*'s alert channel (its sound through the direct-boot-aware sound provider).
 */
class AndroidLockedBootSurface(
    private val ctx: Context,
) : LockedBootSurface {
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)

    override fun showWaiting() {
        val n =
            builder(Channels.STATUS, ctx.getString(R.string.monitor_locked_boot_title), ctx.getString(R.string.monitor_locked_boot_text))
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
            builder(AlertChannels.id(ctx, AlertKind.NO_DATA, plan.alarm), ctx.getString(R.string.monitor_locked_boot_alarm_title), text)
                .setCategory(AlertCategory.of(plan.alarm, AlertCategory.Dnd.of(nm)))
                .build()
        nm.notify(NotificationIds.LOCKED_BOOT, n)
    }

    override fun clear() = nm.cancel(NotificationIds.LOCKED_BOOT)

    private fun builder(
        channel: String,
        title: String,
        text: String,
    ): Notification.Builder =
        Notification
            .Builder(ctx, channel)
            .setSmallIcon(R.drawable.monitor_ic_no_readings)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setContentIntent(openApp())

    /**
     * The launcher activity: from the lock screen Android asks for the unlock first, which is the way out. Resolved with
     * the direct-boot-unaware match, since before the unlock the plain lookup only sees direct-boot-aware activities.
     */
    private fun openApp(): PendingIntent {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(ctx.packageName)
        val flags = PackageManager.MATCH_DIRECT_BOOT_AWARE.toLong() or PackageManager.MATCH_DIRECT_BOOT_UNAWARE.toLong()
        val activity =
            ctx.packageManager
                .queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(flags))
                .firstOrNull()
                ?.activityInfo
        val launch = if (activity == null) main else main.setClassName(activity.packageName, activity.name)
        return PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        const val SILENT_GROUP = "locked_boot"
    }
}
