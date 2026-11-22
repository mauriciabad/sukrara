// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.mauri.sukrara.core.data.settings.NotificationStyleSettings
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.model.AlertReadinessSource
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.core.model.storage.StoredSpan
import app.mauri.sukrara.feature.sharing.setup.AlertSettingsAccess
import app.mauri.sukrara.feature.sharing.setup.AlertThresholds
import app.mauri.sukrara.feature.sharing.setup.LluSourcePort
import app.mauri.sukrara.feature.sharing.setup.RestoreGate
import app.mauri.sukrara.feature.sharing.setup.SetupCard
import app.mauri.sukrara.feature.sharing.setup.SetupFacts
import app.mauri.sukrara.feature.sharing.setup.SetupMachine
import app.mauri.sukrara.feature.sharing.setup.SetupRecord
import app.mauri.sukrara.feature.sharing.setup.SetupStep
import app.mauri.sukrara.feature.sharing.setup.SiteSourcePort
import app.mauri.sukrara.feature.sharing.setup.ThresholdField
import app.mauri.sukrara.feature.sharing.setup.UpdateChoice
import app.mauri.sukrara.feature.sharing.share.LaterRequests
import app.mauri.sukrara.share.owner.OwnerShareState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Whose glucose and where from (`AppGraph`'s own `source` store, read by the hub and the alert runtime; docs/44
 * "Person × Source").
 */
interface SourcePort {
    /** The person's answer; null = setup hasn't one yet. */
    val stored: Flow<SourceSpec?>

    /**
     * What this phone runs on: the answer, else what it is already set up to read (an install updated from before the
     * question, a damaged file): setup asks once more, preselected with it, and monitoring never stops meanwhile.
     */
    val running: Flow<SourceSpec?>

    /** What [set] with [spec] would delete (a person change: every reading and treatment); null = nothing. */
    suspend fun loss(spec: SourceSpec): HistoryLoss?

    /** Stores [spec]; a person change deletes the previous person's readings and treatments, a source change keeps them. */
    suspend fun set(spec: SourceSpec)
}

/**
 * The history a person change deletes (the previous person's): [glucoseMinutes] of glucose (`StoredSpan.glucoseMinutes`,
 * the export's measure) and [treatments] logged treatments, together spanning [from]..[to]. Setup asks before deleting
 * it (#125).
 */
data class HistoryLoss(
    val glucoseMinutes: Long,
    val treatments: Int,
    val from: Instant,
    val to: Instant,
) {
    companion object {
        /** Null when both spans are empty: nothing to ask about. */
        fun of(
            readings: StoredSpan,
            treatments: StoredSpan,
        ): HistoryLoss? {
            val firsts = listOfNotNull(readings.first, treatments.first)
            val lasts = listOfNotNull(readings.last, treatments.last)
            if (readings.count + treatments.count == 0 || firsts.isEmpty() || lasts.isEmpty()) return null
            return HistoryLoss(readings.glucoseMinutes, treatments.count, firsts.min(), lasts.max())
        }
    }
}

/**
 * The GitHub version's update suggestion as setup's *Keep Sukrara up to date* step sees it (the app's
 * `UpdateSuggestion`; null in the Google Play build, which has no updater).
 */
class SetupUpdates(
    val choice: Flow<UpdateChoice>,
    /** *Turn on*: the daily check is on. */
    val turnOn: suspend () -> Unit,
    /** *Not now*: the daily check stays off and the suggestion is closed (home's banner doesn't ask again). */
    val notNow: suspend () -> Unit,
)

/**
 * Insulin pens as setup's optional *Insulin pens* step sees them (the pen module).
 */
class SetupPens(
    /** Reading is answered (on or off, anywhere); false = still *Ask at the first pen tap*; null = no NFC here. */
    val answered: Flow<Boolean?>,
    /** *Read my pens*: reading on. */
    val turnOn: suspend () -> Unit,
)

/**
 * Sukrara's Garmin apps as setup's optional *Garmin* step sees them (the integrations' Garmin link, #199).
 */
class SetupGarmin(
    /** Garmin Connect is on this phone or a watch has one of Sukrara's apps: the step is offered. */
    val offered: Flow<Boolean>,
    /** *Send to Sukrara on your watch* (`apps.garmin`, on by default). */
    val on: Flow<Boolean>,
    val setOn: suspend (Boolean) -> Unit,
    /** Each of Sukrara's watch apps, in the registry's order. */
    val apps: Flow<List<SetupWatchApp>>,
)

/**
 * One of Sukrara's watch apps on setup's Garmin step: its [name] and what it gives in one line ([what]; the
 * integrations' strings), its kind's Connect IQ [icon], its Connect IQ store page for a language ([url]: its listing,
 * or the store's search until it has an id, #247) and whether a watch has it already ([onWatch]).
 */
data class SetupWatchApp(
    val key: String,
    @param:StringRes val name: Int,
    @param:StringRes val what: Int,
    @param:DrawableRes val icon: Int,
    val url: (language: String) -> String,
    val onWatch: Boolean,
)

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
 * - [setupStore]: typed store `setup` ([SetupRecord]); a damaged file only shows skipped steps again (the source is
 *   in [sources]).
 * - [resetAlerts]: the alert runtime's `AlertsModule.reset()`, called when whose glucose (or how it alerts) changes,
 *   before any monitoring, so no phantom no-data alarm survives. Not on identity "Start fresh": by then the sensor
 *   runs, and a reset stops alerts until the hub reports a source again.
 * - [readiness]: THE alert checklist, the same instance home's reliability row reads.
 */
class SetupModule(
    context: Context,
    val setupStore: StateStore<SetupRecord>,
    val sources: SourcePort,
    val hub: GlucoseHub,
    val sharing: OwnerSharing,
    val alertSettings: AlertSettingsAccess,
    /** The app's schema settings: setup records `alerts.thresholdsConfirmedAt` (shared with Settings › Alerts). */
    private val settings: SettingsRepository,
    val resetAlerts: suspend () -> Unit,
    /** Starts monitoring once the source is stored (the monitor decides whether it can read anything yet). */
    val startMonitoring: () -> Unit,
    val readiness: AlertReadinessSource,
    val clock: Clock,
    /** A Sukrara stream (join, stop following). */
    val follower: FollowerSharing = FollowerSharing.None,
    /** The updates step's port; null = this build has no updater (Google Play): no step. */
    val updates: SetupUpdates? = null,
    /** The insulin pens step's port; null = a build without pens (no step). */
    val pens: SetupPens? = null,
    /** The Garmin step's port; null = none in this build or test (no step). */
    val garmin: SetupGarmin? = null,
    /** Root and the LSPosed module (the root step, the status bar text). */
    val systemAccess: SystemAccessPort,
    /** The online source's site (Connect, *Bring back 90 days*); none in a build or test without one. */
    val site: SiteSourcePort = SiteSourcePort.None,
    /** The LibreLinkUp source (sign in, whose glucose); none in a build or test without one. */
    val llu: LluSourcePort = LluSourcePort.None,
    /** A sensor is paired on this phone (what a source change says stays). */
    val sensorPaired: suspend () -> Boolean = { false },
    /** Leaving the Sukrara link (one person, one link): what it asks and the leave itself. */
    val link: LinkPort = LinkPort.None,
    /**
     * A follow this phone left whose `leave` no relay or wearer's phone took yet (offline): the wearer's name ("" =
     * unknown), null = none; setup says so until it goes (PROTOCOL-v2 "Leave").
     */
    val untoldLeave: Flow<String?> = flowOf(null),
) {
    /** Root as setup's facts see it ([SetupFacts.root]): Sukrara has root. */
    val rootGranted: Flow<Boolean> by lazy { systemAccess.state.map { it.granted }.distinctUntilChanged() }

    /** Root and the module now (the root step's card). */
    val access: Flow<SystemAccessState> get() = systemAccess.state

    /** THE glucose notification choice, as Settings › Glucose notification stores it. */
    val notificationStyle: Flow<NotificationStyle> by lazy { NotificationStyleSettings.flow(settings) }

    suspend fun setNotificationStyle(style: NotificationStyle) = NotificationStyleSettings.set(settings, style)

    /** Pens on this phone ([SetupFacts.pens]); null = it can't read them. */
    val pensAnswered: Flow<Boolean?> by lazy { pens?.answered ?: flowOf(null) }

    /** The Garmin step's fact ([SetupFacts.garmin]): the switch while the step is offered, else null. */
    val garminFact: Flow<Boolean?> by lazy {
        garmin?.let { g -> combine(g.offered, g.on) { offered, on -> on.takeIf { offered } }.distinctUntilChanged() } ?: flowOf(null)
    }

    /** The daily update check's answer ([SetupFacts.updates]); null = no updater in this build. */
    val updateChoice: Flow<UpdateChoice?> by lazy { updates?.choice ?: flowOf(null) }

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

    /** The Garmin step's apps (none without the port). */
    val garminApps: Flow<List<SetupWatchApp>> by lazy { garmin?.apps ?: flowOf(emptyList()) }

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
            combine(sources.stored, sources.running, ::Pair),
            hub.state,
            combine(sharing.state, follower.state, ::Pair),
            combine(combine(alertsConfirmedAt, readiness.readiness, ::Pair), updateChoice, pensAnswered, rootGranted, garminFact) {
                (c, r),
                u,
                p,
                root,
                g,
                ->
                Answers(c, r, u, p, root, g)
            },
        ) {
            record,
            (spec, running),
            glucose,
            (share, follow),
            answers,
            ->
            val (confirmed, checklist, updates, pens, root) = answers
            SetupProgress(
                record,
                SetupFacts(
                    spec = spec,
                    running = running,
                    following = follow.following,
                    ownSensorLive = RestoreGate.ownSensorLive(glucose, clock.now()),
                    hasIdentity = share is OwnerShareState.Sharing || share is OwnerShareState.AnotherAppSharingAsYou,
                    alertsConfirmed = confirmed != null,
                    alertsReady = checklist.setupReady,
                    updates = updates,
                    pens = pens,
                    root = root,
                    garmin = answers.garmin,
                ),
            )
        }
    }

    /** Settings' *Finish setup*: the optional steps still open ([SetupMachine.extrasLeft]); empty hides the row. */
    val extrasLeft: Flow<List<SetupStep>> by lazy {
        progress.map { SetupMachine.extrasLeft(it.record, it.facts) }.distinctUntilChanged()
    }

    /** Settings' *Finish setup*: the walk goes on into the open extras (the app then opens setup). */
    suspend fun continueExtras() = setupStore.update(SetupMachine::continueExtras)

    /** The home "Finish setup" banner ([SetupMachine.card]); null before a source and once nothing is left. */
    val card: Flow<SetupCard?> by lazy { progress.map { SetupMachine.card(it.record, it.facts) }.distinctUntilChanged() }

    private companion object {
        val THRESHOLDS_CONFIRMED_AT = SchemaSettings.text("alerts.thresholdsConfirmedAt", default = "")
    }
}

/** The answers kept elsewhere that setup's facts read (thresholds, checklist, updates, pens, root, Garmin). */
private data class Answers(
    val confirmed: Instant?,
    val checklist: app.mauri.sukrara.core.model.AlertReadiness,
    val updates: UpdateChoice?,
    val pens: Boolean?,
    val root: Boolean,
    val garmin: Boolean?,
)

/** The setup record and the facts it is read against, at one moment ([SetupModule.progress]). */
data class SetupProgress(
    val record: SetupRecord,
    val facts: SetupFacts,
)
