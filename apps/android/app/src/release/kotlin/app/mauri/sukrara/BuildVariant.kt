// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.content.Context
import app.mauri.sukrara.core.model.ReadingSource

/**
 * Release builds: readings only ever come from the real sensor and the relays are the public ones (the debug variant of
 * this file adds test sources and a relay choice).
 */
internal object BuildVariant {
    @Suppress("UNUSED_PARAMETER")
    fun relayChoice(context: Context): String? = null

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun wearerSource(graph: AppGraph): ReadingSource? = null
}
