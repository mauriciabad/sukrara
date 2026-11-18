// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.monitor.R
import java.time.Instant

/**
 * The one heads-up an hour before a LibreView maintenance window stops LibreLinkUp (docs/45 "In Sukrara",
 * contracts/sources/librelinkup/status.json `notify_s`): silent, on its own channel (the person turns it off there),
 * gone by itself when the window ends; the tap opens the app. Never during the window: home and the glucose
 * notification say it then.
 */
object MaintenanceNotice {
    private const val TAG = "Sukrara"
    private const val REQUEST_OPEN = 340

    fun post(
        ctx: Context,
        start: Instant,
        end: Instant,
        now: Instant = Instant.now(),
    ) {
        runCatching {
            Channels.create(ctx)
            val from = ClockText.time(start.epochSecond)
            val to = ClockText.time(end.epochSecond)
            val open = PendingIntent.getActivity(ctx, REQUEST_OPEN, AppActivities.launch(ctx), PendingIntent.FLAG_IMMUTABLE)
            val n =
                Notification
                    .Builder(ctx, Channels.MAINTENANCE)
                    .setSmallIcon(app.mauri.sukrara.core.designsystem.R.drawable.ds_ic_status_sukrara)
                    .setContentTitle(ctx.getString(R.string.monitor_maintenance_title, from))
                    .setContentText(ctx.getString(R.string.monitor_maintenance_text, from, to))
                    .setStyle(Notification.BigTextStyle().bigText(ctx.getString(R.string.monitor_maintenance_text, from, to)))
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setTimeoutAfter((end.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(60_000L))
                    .setGroup(NotificationGroups.Group.INFO.key)
                    .build()
            NotificationGroups.post(ctx, NotificationIds.MAINTENANCE, n, NotificationGroups.Group.INFO, Channels.MAINTENANCE)
        }.onFailure { Log.w(TAG, "maintenance notice: ${it.javaClass.simpleName}") }
    }
}
