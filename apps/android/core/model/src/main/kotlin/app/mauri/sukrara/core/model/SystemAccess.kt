// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import kotlinx.coroutines.flow.StateFlow

/** The Sukrara LSPosed module in System UI (the status-bar text, colour and order, the lock screen widget). */
enum class ModuleState {
    /** Waiting for LSPosed (it answers within a moment of the app starting). */
    Checking,

    /** No LSPosed, or the Sukrara module isn't enabled in it. */
    Missing,

    /** Enabled, but System UI isn't in its scope. */
    NoScope,

    /** In scope, but System UI doesn't run it (enabled since System UI started, or it failed): restart System UI. */
    Restart,

    /** System UI runs the module from an older build whose System UI code differs: a restart loads the new code. */
    Update,

    Active,
    ;

    /** System UI runs the module (maybe an older build of it). */
    val active: Boolean get() = this == Active || this == Update

    val needsRestart: Boolean get() = this == Restart || this == Update
}

/**
 * Root for Sukrara: what the last `su -c id -u` said. Only that request proves it, and it is made only when the person
 * asks for a root feature or one they turned on is running (Magisk shows its prompt the first time). KernelSU and
 * APatch never prompt: an app they haven't allowed doesn't even see `su` ([Missing]).
 */
enum class RootAccess {
    /** Not asked in this run of the app. */
    Unknown,

    /** `su` answered as root. */
    Granted,

    /** Sukrara can't start `su`: no root, or a root manager that hides it from apps it hasn't allowed. */
    Missing,

    /** `su` ran and refused (the prompt's Deny, or Sukrara set to deny in the root manager). */
    Refused,

    /** `su` didn't answer in time (a prompt left open). */
    NoAnswer,
}

/**
 * Root for Sukrara ([root]: the last request's answer; [asking]: a request is running, the root manager's prompt may
 * be showing, while [root] keeps the last answer so a running status bar doesn't blink); whether the phone shows root
 * without asking ([rootSeen]: LSPosed answered, System UI runs the module, or a `su` file is visible; it picks the
 * hint, never the state); the module's state; [framework] = LSPosed's name and version.
 */
data class SystemAccessState(
    val root: RootAccess = RootAccess.Unknown,
    val asking: Boolean = false,
    val rootSeen: Boolean = false,
    val module: ModuleState = ModuleState.Checking,
    val framework: String? = null,
) {
    val granted: Boolean get() = root == RootAccess.Granted
}

/** Root and the System UI module, for the status-bar page and the home card (`:monitor` implements it). */
interface SystemAccessPort {
    val state: StateFlow<SystemAccessState>

    /**
     * Asks `su` for root ([SystemAccessState.asking] until it answers). Only on the person's action (turning a root
     * feature on, *Check again*) or while a root feature they turned on runs: never in the background otherwise.
     */
    suspend fun checkRoot()

    /** Looks for signs of root again and asks LSPosed again, without `su` (back on a page, after a scope change). */
    fun refresh()

    /** Restarts System UI with root so it loads the module; null when it worked, else why not. */
    suspend fun restartSystemUi(): String?

    /** LSPosed shows its own prompt to add System UI to the module's scope; null when approved, else why not. */
    suspend fun requestScope(): String?
}
