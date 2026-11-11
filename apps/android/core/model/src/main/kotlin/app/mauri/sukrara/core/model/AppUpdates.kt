// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * A newer release of the GitHub version (#188): its [version] ("0.14.0"), its release page [notesUrl] (always one of
 * the project's own release pages), whether it is a release candidate, and whether it has an APK Sukrara can check
 * and install itself ([installable]; otherwise *Update* opens the release page).
 */
data class UpdateRelease(
    val version: String,
    val notesUrl: String,
    val prerelease: Boolean,
    val installable: Boolean,
)

/** Why an update didn't install. In every case the running version stays as it was. */
enum class UpdateFailure {
    /** The download failed or stopped (offline, the server refused, too big). */
    Download,

    /** The file isn't the release's: its SHA-256 differs from the one GitHub lists. */
    Hash,

    /** Not signed by Sukrara's release key (or not Sukrara, or not newer): refused. */
    Signature,

    /** Android refused or stopped the installation (no space, cancelled, interrupted). */
    Install,
}

/** What alerts an install must wait for (#188): it never installs while one is up or a very low is on. */
data class AlertHold(
    /** Alert kinds (contract ids) whose notification is up: ringing, or shown and not answered yet. */
    val showing: Set<String> = emptySet(),
    /** A very low is on now (an episode at very low, answered or not). */
    val veryLow: Boolean = false,
) {
    val quiet: Boolean get() = showing.isEmpty() && !veryLow
}

/**
 * The GitHub version's update flow, where Settings › About and its search see it (docs/FEATURES.md › Updates
 * (Android, GitHub version)). Every state has its way out on About; the notification shows the same.
 */
sealed interface UpdateState {
    data object Checking : UpdateState

    data object UpToDate : UpdateState

    /** The check failed (offline, GitHub refused): About links to the releases page. */
    data object Offline : UpdateState

    data class Available(val release: UpdateRelease) : UpdateState

    /** [progress] 0..1, null while the size isn't known. */
    data class Downloading(val release: UpdateRelease, val progress: Float?) : UpdateState

    /** Downloaded and checked; waits until no alert is showing and no very low is on ([hold]), then installs. */
    data class Waiting(val release: UpdateRelease, val hold: AlertHold) : UpdateState

    /** Android asks the person to confirm the install (or to allow installs from Sukrara, once). */
    data class Confirm(val release: UpdateRelease) : UpdateState

    data class Installing(val release: UpdateRelease) : UpdateState

    data class Failed(val release: UpdateRelease, val reason: UpdateFailure) : UpdateState
}

/** The GitHub version's updater (`:update`); the Google Play build has none (Play brings the updates). */
interface AppUpdates {
    /** Null until the first check of this process (About checks when it opens). */
    val state: StateFlow<UpdateState?>

    /** Checks GitHub now (About opening, *Check again*). */
    fun check()

    /** Downloads, checks and installs the available release (or opens its page when it has no APK). */
    fun update()

    /** Stops a download or a wait; the release is offered again. */
    fun cancel()

    /** Shows Android's install prompt again ([UpdateState.Confirm]), from a screen of the app in front. */
    fun confirm()
}
