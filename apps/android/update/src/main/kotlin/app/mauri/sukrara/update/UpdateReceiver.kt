// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The updater's broadcasts (not exported): the notification's *Update* / *Try again* and *Cancel*, the install
 * session's status from Android, and this app replaced by a new version (`MY_PACKAGE_REPLACED`: `:monitor`'s
 * BootReceiver starts monitoring from the same broadcast).
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val module = UpdateModule.current ?: return
        when (intent.action) {
            ACTION_UPDATE -> {
                module.update()
            }

            ACTION_CANCEL -> {
                module.cancel()
            }

            ACTION_STATUS -> {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val prompt = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                async("status") { module.onStatus(status, prompt) }
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                async("replaced") { module.onReplaced() }
            }
        }
    }

    private fun async(
        what: String,
        block: suspend () -> Unit,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(BUDGET_MS) { block() } ?: Log.w(TAG, "$what timed out")
            } catch (e: Exception) {
                Log.w(TAG, "$what failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_UPDATE = "app.mauri.sukrara.update.UPDATE"
        const val ACTION_CANCEL = "app.mauri.sukrara.update.CANCEL"
        const val ACTION_STATUS = "app.mauri.sukrara.update.STATUS"
        const val EXTRA_VERSION = "app.mauri.sukrara.update.VERSION"
        private const val TAG = "Sukrara.update"
        private const val BUDGET_MS = 9_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
