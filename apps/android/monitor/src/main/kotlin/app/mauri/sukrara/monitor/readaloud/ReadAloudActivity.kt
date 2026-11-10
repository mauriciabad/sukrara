// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readaloud

import android.app.Activity
import android.os.Bundle
import android.util.Log
import app.mauri.sukrara.monitor.monitorHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The *Read glucose aloud* shortcut's target ([ReadAloud]): says the glucose, or stops it when it is speaking, and
 * closes at once (no screen of its own, so the person stays where they were). Not exported: only Sukrara's own
 * shortcut (started by the launcher as Sukrara) opens it.
 */
class ReadAloudActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = applicationContext.monitorHost
        val res = applicationContext.resources
        scope.launch {
            try {
                if (withTimeoutOrNull(READY_MS) { host.awaitReady() } == null) {
                    Log.w(TAG, "not ready")
                    return@launch
                }
                val monitor = host.monitor
                val config = monitor.graphConfig.first()
                val text = ReadAloud.sentence(monitor.hubImpl.state.value, config.bands, config.unit, monitor.clock.now().epochSecond, res)
                launch(Dispatchers.Main) {
                    monitor.readAloud.toggle(text, res.configuration.locales[0])
                    monitor.readAloud.used()
                }
            } catch (e: Exception) {
                Log.w(TAG, "read aloud failed: ${e.javaClass.simpleName}")
            }
        }
        finish()
    }

    private companion object {
        const val TAG = "Sukrara.readaloud"
        const val READY_MS = 10_000L

        /** Outlives the activity, which closes at once. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
