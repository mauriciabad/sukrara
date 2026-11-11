// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Duration

/**
 * The updater's background work (WorkManager): the daily check ([CHECK], while *Check for updates automatically* is
 * on; any network) and one install run at a time ([INSTALL]: *Update* replaces a run in progress; an automatic one
 * waits for an unmetered network and never replaces the person's).
 */
object UpdateWork {
    const val CHECK = "update-check"
    const val INSTALL = "update-install"
    const val KEY_AUTOMATIC = "automatic"

    /** The daily check on or off (its switch); KEEP leaves a scheduled one's time as it is. */
    fun schedule(
        ctx: Context,
        on: Boolean,
    ) {
        val wm = WorkManager.getInstance(ctx)
        if (!on) {
            wm.cancelUniqueWork(CHECK)
            return
        }
        val request =
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(Duration.ofDays(1))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(Duration.ofHours(1))
                .build()
        wm.enqueueUniquePeriodicWork(CHECK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One check now, besides the daily one (a debug build's test server: [UpdatePorts.checkAtStart]). */
    fun checkNow(ctx: Context) {
        val request =
            OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("$CHECK-now", ExistingWorkPolicy.REPLACE, request)
    }

    fun install(
        ctx: Context,
        automatic: Boolean,
    ) {
        val network = if (automatic) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request =
            OneTimeWorkRequestBuilder<UpdateInstallWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, InstallGate.RETRY)
                .setInputData(workDataOf(KEY_AUTOMATIC to automatic))
                .apply { if (!automatic) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
                .build()
        WorkManager
            .getInstance(ctx)
            .enqueueUniqueWork(INSTALL, if (automatic) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelInstall(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(INSTALL)
    }
}

/** The daily check: [UpdateModule.dailyCheck]. */
class UpdateCheckWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val module = UpdateModule.current ?: return Result.retry()
        return if (module.dailyCheck()) Result.success() else Result.retry()
    }
}

/** One install run: [UpdateModule.install]; a held update is retried (WorkManager's backoff from [InstallGate.RETRY]). */
class UpdateInstallWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val module = UpdateModule.current ?: return Result.retry()
        return when (module.install(inputData.getBoolean(UpdateWork.KEY_AUTOMATIC, false))) {
            UpdateModule.Outcome.Done -> Result.success()
            UpdateModule.Outcome.Retry -> Result.retry()
        }
    }
}
