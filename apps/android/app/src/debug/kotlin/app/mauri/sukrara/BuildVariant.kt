// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.content.Context
import app.mauri.sukrara.core.model.ReadingSource
import app.mauri.sukrara.debug.DebugRelays
import app.mauri.sukrara.debug.SyntheticSource
import kotlinx.coroutines.withContext

/**
 * Debug builds: the DebugFakes `SYNTHETIC` source replaces the sensor while it's switched on, and the relays are the
 * `RELAYS` command's choice (release: never; the real sensor and the public relays).
 */
internal object BuildVariant {
    /** What the `RELAYS` command saved (null = nothing: `LaunchPlan` connects to no relay). */
    fun relayChoice(context: Context): String? = DebugRelays.choice(context)

    suspend fun wearerSource(graph: AppGraph): ReadingSource? =
        withContext(graph.dispatchers.io) {
            if (SyntheticSource.isOn(graph.app)) {
                SyntheticSource(
                    graph.clock,
                    SyntheticSource.shift(graph.app),
                    frozen = { SyntheticSource.isFrozen(graph.app) },
                    endsAt = SyntheticSource.endsAt(graph.app) ?: (graph.clock.now() + java.time.Duration.ofDays(10)),
                    readyAt = SyntheticSource.readyAt(graph.app),
                )
            } else {
                null
            }
        }
}
