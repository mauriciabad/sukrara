// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.settings

/**
 * The GitHub version's update switches (#188), declared once: the updater (`:update`, GitHub build only) and Settings ›
 * About read the same objects. The Mac keeps the same two rows in Sparkle's defaults.
 */
object UpdateSettings {
    /** Once a day, look for a newer release and say so in a notification. */
    val AutomaticChecks = SchemaSettings.bool("update.automaticChecks", default = true)

    /** Download, check and install a newer release by itself, once no alert is showing (off until the person turns it on). */
    val AutomaticInstall = SchemaSettings.bool("update.automaticInstall", default = false)
}
