// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import android.content.Context
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.model.AlertReadinessSource
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.core.model.storage.StoredSpan
import app.mauri.sukrara.feature.sharing.setup.AlertSettingsAccess
import app.mauri.sukrara.feature.sharing.setup.AlertThresholds
import app.mauri.sukrara.feature.sharing.setup.RestoreGate
import app.mauri.sukrara.feature.sharing.setup.SetupCard
import app.mauri.sukrara.feature.sharing.setup.SetupFacts
import app.mauri.sukrara.feature.sharing.setup.SetupMachine
import app.mauri.sukrara.feature.sharing.setup.SetupRecord
import app.mauri.sukrara.feature.sharing.setup.SetupStep
import app.mauri.sukrara.feature.sharing.setup.ThresholdField
import app.mauri.sukrara.feature.sharing.share.LaterRequests
import app.mauri.sukrara.share.owner.OwnerShareState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/** The phone's role (`AppGraph`'s own `role` store, read by the hub and the alert runtime). */
interface RoleStore {
    val role: Flow<Role?>

    /** What [set] with [role] would delete from this phone; null = nothing (no confirmation needed). */
    suspend fun loss(role: Role): RoleLoss?

    /** Stores [role]; the other role's readings (and, when the role changed, every treatment) are deleted. */
    suspend fun set(role: Role)
}

/**
 * The history a role change deletes (the previous person's): [readings] glucose readings and [treatments] logged
 * treatments, together spanning [from]..[to]. The role step asks before deleting it (#125).
 */
data class RoleLoss(
    val readings: Int,
    val treatments: Int,
    val from: Instant,
    val to: Instant,
) {
    companion object {
        /** Null when both spans are empty: nothing to ask about. */
        fun of(
            readings: StoredSpan,
            treatments: StoredSpan,
        ): RoleLoss? {
            val firsts = listOfNotNull(readings.first, treatments.first)
            val lasts = listOfNotNull(readings.last, treatments.last)
            if (readings.count + treatments.count == 0 || firsts.isEmpty() || lasts.isEmpty()) return null
            return RoleLoss(readings.count, treatments.count, firsts.min(), lasts.max())
        }
    }
}

/**
 * What the setup flow and the sharing screens need from the app: `AppGraph` implements it by returning its
 * [SetupModule]. Navigation to other features (the sensor flow) goes through callbacks the app passes to the screens.
 */
interface SharingDeps {
    val setup: SetupModule
}

/**
 * The first-run setup and sharing graph (A4: one line in `AppGraph`). Nothing is read from disk in the constructor.
 *
 * - [setupStore]: typed store `setup` ([SetupRecord]); a damaged file only shows skipped steps again (the role is
 *   in [roles]).
 * - [resetAlerts]: the alert runtime's `AlertsModule.reset()`, called when the role is chosen or changed — before any
 *   monitoring — so no phantom no-data alarm survives. Not on identity "Start fresh": by then the sensor runs, and a
 *   reset stops alerts until the hub reports a role again.
 * - [readiness]: THE alert checklist, the same instance home's reliability row reads.
 */
class SetupModule(
    context: Context,
    val setupStore: StateStore<SetupRecord>,
    val roles: RoleStore,
    val hub: GlucoseHub,
    val sharing: OwnerSharing,
    val alertSettings: AlertSettingsAccess,
    /** The app's schema settings: setup records `alerts.thresholdsConfirmedAt` (shared with Settings › Alerts). */
    private val settings: SettingsRepository,
    val resetAlerts: suspend () -> Unit,
    /** Starts monitoring once the role is stored (the monitor decides whether a source exists yet). */
    val startMonitoring: () -> Unit,
    val readiness: AlertReadinessSource,
    val clock: Clock,
    /** The follower role (join, stop following). */
    val follower: FollowerSharing = FollowerSharing.None,
) {
    /** An invite link opened on this phone before it follows anyone: setup's join step starts with it. */
    val pendingInvite = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** When the alert thresholds were confirmed (setup or Settings › Alerts), null = never. */
    val alertsConfirmedAt: Flow<Instant?> by lazy {
        settings.flow(THRESHOLDS_CONFIRMED_AT).map { text ->
            text.takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
        }
    }

    /**
     * P4: the shown thresholds become the runtime's (merged into the current settings, so nothing changed meanwhile in
     * Settings › Alerts is lost), then the confirmation is recorded.
     */
    suspend fun confirmAlerts(shown: AlertThresholds) {
        val current = alertSettings.settings.first()
        val merged =
            ThresholdField.entries.fold(current) { s, f -> s.withRule(f.kind, s.rule(f.kind).copy(threshold = shown.value(f))) }
        alertSettings.save(merged)
        settings.set(THRESHOLDS_CONFIRMED_AT, clock.now().toString())
    }

    /** Access requests put off with Later in the approve dialog (process memory). */
    val laterRequests = LaterRequests()

    /** Import history still to offer on its own ([SetupMachine.historyDue]): the app opens that one step once per launch. */
    val historyDue: Flow<Boolean> by lazy {
        progress.map { SetupMachine.historyDue(it.record, it.facts) }.distinctUntilChanged()
    }

    /** The database was replaced by an empty one (a version change): offer Import history again. */
    suspend fun offerHistoryAgain() = setupStore.update { SetupMachine.reopen(it, SetupStep.History) }

    /** Home and setup call this on resume: permissions change in the system's settings. */
    fun refresh() {
        readiness.refresh()
    }

    /**
     * What the person answered with the facts it is read against: home's banner ([card]), the history step at launch
     * ([historyDue]) and setup's own start (resume the walk, or walk the skipped steps) read this one flow.
     */
    val progress: Flow<SetupProgress> by lazy {
        combine(
            setupStore.data,
            roles.role,
            hub.state,
            combine(sharing.state, follower.state, ::Pair),
            combine(alertsConfirmedAt, readiness.readiness, ::Pair),
        ) {
            record,
            role,
            glucose,
            (share, follow),
            (confirmed, checklist),
            ->
            SetupProgress(
                record,
                SetupFacts(
                    role = role,
                    following = follow.following,
                    ownSensorLive = RestoreGate.ownSensorLive(glucose, clock.now()),
                    hasIdentity = share is OwnerShareState.Sharing || share is OwnerShareState.AnotherAppSharingAsYou,
                    alertsConfirmed = confirmed != null,
                    alertsReady = checklist.setupReady,
                ),
            )
        }
    }

    /** The home "Finish setup" banner ([SetupMachine.card]); null before a role and once nothing is left. */
    val card: Flow<SetupCard?> by lazy { progress.map { SetupMachine.card(it.record, it.facts) }.distinctUntilChanged() }

    private companion object {
        val THRESHOLDS_CONFIRMED_AT = SchemaSettings.text("alerts.thresholdsConfirmedAt", default = "")
    }
}

/** The setup record and the facts it is read against, at one moment ([SetupModule.progress]). */
data class SetupProgress(
    val record: SetupRecord,
    val facts: SetupFacts,
)
