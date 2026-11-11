// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.content.Context
import app.mauri.sukrara.core.model.ReadingSource

/**
 * Release builds: readings only ever come from the real sensor, the relays are the public ones and updates come from the
 * GitHub releases signed with the release key (the debug variant of this file adds test sources, a relay and an
 * update-server choice).
 */
internal object BuildVariant {
    @Suppress("UNUSED_PARAMETER")
    fun relayChoice(context: Context): String? = null

    /** The updater always reads the GitHub releases and trusts only the release key (#188). */
    @Suppress("UNUSED_PARAMETER")
    fun updateServer(context: Context): Pair<String, String>? = null

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun hasWearerSource(graph: AppGraph): Boolean = false

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun wearerSource(graph: AppGraph): ReadingSource? = null
}
