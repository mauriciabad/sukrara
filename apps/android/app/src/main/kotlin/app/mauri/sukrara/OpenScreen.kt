// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/** Android's settings screen [action] (Bluetooth, date & time), or the phone's Settings on a phone without it. */
internal fun openSystemScreen(
    context: Context,
    action: String,
) = open(context, Intent(action), Intent(Settings.ACTION_SETTINGS))

/**
 * Starts the first of [intents] the phone can open (inside this app when it names an action of ours). When none
 * opens, a short message says so: the tap never just does nothing.
 */
internal fun open(
    context: Context,
    vararg intents: Intent,
) {
    val opened =
        intents.any { intent ->
            if (intent.action?.startsWith("app.mauri.sukrara.") == true) intent.setPackage(context.packageName)
            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    if (!opened) Toast.makeText(context, R.string.app_open_failed, Toast.LENGTH_LONG).show()
}
