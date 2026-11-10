// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.debug

import android.content.Context

/**
 * Where a debug build's relay connections go: the `RELAYS` command's choice, read by `LaunchPlan` at launch (nothing
 * saved = no relay). Debug builds only; release builds have no such choice and always use the public relays.
 */
object DebugRelays {
    private const val PREFS = "debug_relays"
    private const val KEY = "relays"

    fun choice(context: Context): String? = prefs(context).getString(KEY, null)

    fun save(
        context: Context,
        choice: String,
    ) = prefs(context).edit().putString(KEY, choice).commit()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
