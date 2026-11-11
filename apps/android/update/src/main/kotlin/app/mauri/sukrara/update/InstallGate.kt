// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import app.mauri.sukrara.core.model.AlertHold
import java.time.Duration

/**
 * When an update may install (#188). Installing stops Sukrara for a few seconds (Android replaces the app, then
 * `MY_PACKAGE_REPLACED` starts monitoring again), so it never happens while an alert is up (ringing, or shown and not
 * answered) or a very low is on: the person must hear the next repeat and see the alert. It waits and asks again.
 */
object InstallGate {
    /** How long a waiting update waits before it asks again (WorkManager's backoff grows from here). */
    val RETRY: Duration = Duration.ofMinutes(5)

    fun mayInstall(hold: AlertHold): Boolean = hold.quiet

    /**
     * Whether to ask Android to install without its prompt (`USER_ACTION_NOT_REQUIRED`): only for an automatic update
     * the person turned on, and only once they allowed installs from Sukrara (without that, Android must ask anyway).
     * Android may still ask (another app is the installer of record, an update-ownership rule): the flow then shows
     * *Android asks you to confirm* instead of installing.
     */
    fun silent(
        automatic: Boolean,
        mayInstallPackages: Boolean,
    ): Boolean = automatic && mayInstallPackages
}
