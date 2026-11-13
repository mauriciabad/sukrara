// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.data.settings.UpdateSettings
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.feature.home.banners.BannerKind
import app.mauri.sukrara.feature.home.banners.BannerMemory
import app.mauri.sukrara.feature.sharing.SetupUpdates
import app.mauri.sukrara.feature.sharing.setup.UpdateChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The GitHub version's suggestion to turn on the daily update check, which is opt-in (F-Droid's rule for updates that
 * bypass a store; docs/FEATURES.md › Updates (Android, GitHub version)): setup's *Keep Sukrara up to date* step, home's
 * banner and the Settings overview's. One answer for all three: *Turn on* sets the switch; *Not now* (any of them)
 * closes the tip in home's banner memory like every other tip (the snackbar's *Undo* and Settings › *Show closed tips*
 * bring it back); turning the switch off in Settings › About closes it too, so a deliberate *off* is never asked about
 * again. Only in a build with the updater (`AppGraph.updateSuggestion` is null in the Google Play build).
 */
class UpdateSuggestion(
    private val settings: SettingsRepository,
    /** Home's banner memory (a function: home's module reads setup, whose step reads this). */
    private val memory: () -> StateStore<BannerMemory>,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val checks: Flow<Boolean> = settings.flow(UpdateSettings.AutomaticChecks).distinctUntilChanged()

    /** The daily check is off (home's banner reads it with its own memory). */
    val checksOff: Flow<Boolean> = checks.map { !it }

    /** Where the check stands: on, off on purpose, or never answered. */
    val choice: Flow<UpdateChoice> =
        flow {
            emitAll(combine(checks, memory().data) { on, m -> choiceOf(on, m.dismissed(BannerKind.TurnOnUpdates, clock.now())) })
        }.distinctUntilChanged()

    /** The Settings overview's banner: the same condition as home's. */
    val banner: Flow<Boolean> = choice.map { it == UpdateChoice.Open }

    /** *Turn on* (home's or the overview's banner). */
    fun turnOn() {
        scope.launch { settings.set(UpdateSettings.AutomaticChecks, true) }
    }

    /** *Not now* (setup): off, and the tip closed. */
    suspend fun notNow() {
        settings.set(UpdateSettings.AutomaticChecks, false)
        close()
    }

    /** *Not now* on the overview's banner (home's goes through its own memory, the same store). */
    fun dismiss() {
        scope.launch { close() }
    }

    /** The snackbar's *Undo* after [dismiss]. */
    fun restore() {
        scope.launch { memory().update { it.restore(BannerKind.TurnOnUpdates) } }
    }

    /** Setup's port to the same answer. */
    fun setup() = SetupUpdates(choice, turnOn = { settings.set(UpdateSettings.AutomaticChecks, true) }, notNow = ::notNow)

    /** At app start: the switch turned off later (Settings › About, an imported settings file) closes the tip. */
    fun start() {
        scope.launch { checks.drop(1).filter { !it }.collect { close() } }
    }

    private suspend fun close() = memory().update { it.dismiss(BannerKind.TurnOnUpdates, clock.now()) }

    companion object {
        /** On wins; off is *Off* once the tip was closed, else still *Open* (never answered). */
        fun choiceOf(
            on: Boolean,
            closed: Boolean,
        ): UpdateChoice =
            when {
                on -> UpdateChoice.On
                closed -> UpdateChoice.Off
                else -> UpdateChoice.Open
            }
    }
}
