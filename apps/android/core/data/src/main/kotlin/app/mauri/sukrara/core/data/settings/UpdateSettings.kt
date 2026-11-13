// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.settings

/**
 * The GitHub version's update switches (#188), declared once: the updater (`:update`, GitHub build only) and Settings ›
 * About read the same objects. The Mac keeps the same two rows in Sparkle's defaults.
 */
object UpdateSettings {
    /**
     * Once a day, look for a newer release and say so in a notification. Off until the person turns it on (the
     * schema's Android default: F-Droid's rule, updates that bypass a store are opt-in); setup's step and the
     * suggestion banner offer it.
     */
    val AutomaticChecks = SchemaSettings.bool("update.automaticChecks", default = false)

    /** Download, check and install a newer release by itself, once no alert is showing (off until the person turns it on). */
    val AutomaticInstall = SchemaSettings.bool("update.automaticInstall", default = false)
}
