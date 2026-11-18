// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.follower

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.share.R
import java.util.concurrent.TimeUnit

/** The application: the follower's [LeaveSender] for [LeaveWorker] (null when there is none in this process). */
interface LeaveHost {
    suspend fun leaveSender(): LeaveSender?
}

/**
 * The `leave` *Stop following* couldn't hand to two relays yet (PROTOCOL-v2 "Leave"): WorkManager runs it once a
 * network is up, also with the app closed and after a restart (not after an uninstall), and again with a growing
 * backoff until [LeaveSender.flush] says nothing is left.
 */
class LeaveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sender = (applicationContext as? LeaveHost)?.leaveSender() ?: return Result.retry()
        return if (sender.flush()) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "sukrara.leave"

        /** One job at a time: a waiting or running one is kept (it retries by itself). */
        fun schedule(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<LeaveWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}

/**
 * *Ana hasn't been told yet* (on [channel] in the app's info group [group], alerting once): this phone stopped following
 * and no relay or the wearer's phone took its `leave` within the first attempt. Tapping opens the app. Posted and
 * cancelled by the app.
 */
class LeaveNotifications(
    private val context: Context,
    private val channel: String,
    private val group: String,
) {
    fun untold(wearer: String): Notification {
        val name = Profile.shownName(wearer).ifEmpty { context.getString(R.string.share_follow_someone) }
        val text = context.getString(R.string.share_leave_untold_text)
        val builder =
            Notification
                .Builder(context, channel)
                .setSmallIcon(context.applicationInfo.icon)
                .setCategory(Notification.CATEGORY_STATUS)
                .setGroup(group)
                .setOnlyAlertOnce(true)
                .setContentTitle(context.getString(R.string.share_leave_untold_title, name))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            builder.setContentIntent(PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE))
        }
        return builder.build()
    }
}
