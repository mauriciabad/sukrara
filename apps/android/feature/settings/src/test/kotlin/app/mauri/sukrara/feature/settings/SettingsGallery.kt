// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.data.settings.HookPosition
import app.mauri.sukrara.core.data.settings.SettingsFrom
import app.mauri.sukrara.core.model.AlertHold
import app.mauri.sukrara.core.model.AlertNote
import app.mauri.sukrara.core.model.AlertNoteType
import app.mauri.sukrara.core.model.AlertQuietReason
import app.mauri.sukrara.core.model.AlertQuietSide
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.AppBuild
import app.mauri.sukrara.core.model.DndStatus
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SensorEndConfig
import app.mauri.sukrara.core.model.SensorReminder
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SoundTest
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.core.model.UpdateFailure
import app.mauri.sukrara.core.model.UpdateRelease
import app.mauri.sukrara.core.model.UpdateState
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.advanced.AdvancedActions
import app.mauri.sukrara.feature.settings.advanced.AdvancedContent
import app.mauri.sukrara.feature.settings.advanced.AdvancedState
import app.mauri.sukrara.feature.settings.advanced.AdvancedStats
import app.mauri.sukrara.feature.settings.advanced.ImportExportContent
import app.mauri.sukrara.feature.settings.advanced.LogContent
import app.mauri.sukrara.feature.settings.advanced.RelayRow
import app.mauri.sukrara.feature.settings.advanced.SettingsFileActions
import app.mauri.sukrara.feature.settings.follower.SettingsFromActions
import app.mauri.sukrara.feature.settings.follower.SettingsFromContent
import app.mauri.sukrara.feature.settings.follower.SettingsFromNote
import app.mauri.sukrara.feature.settings.follower.SettingsFromState
import app.mauri.sukrara.feature.settings.graph.GraphActions
import app.mauri.sukrara.feature.settings.graph.GraphContent
import app.mauri.sukrara.feature.settings.graph.GraphMarksContent
import app.mauri.sukrara.feature.settings.graph.LastTreatmentContent
import app.mauri.sukrara.feature.settings.graph.MetricContent
import app.mauri.sukrara.feature.settings.graph.MetricSlot
import app.mauri.sukrara.feature.settings.graph.MetricsContent
import app.mauri.sukrara.feature.settings.graph.Reorder
import app.mauri.sukrara.feature.settings.graph.SensorTimeContent
import app.mauri.sukrara.feature.settings.graph.TimeShownContent
import app.mauri.sukrara.feature.settings.graph.TirRingsContent
import app.mauri.sukrara.feature.settings.lock.LockScreenContent
import app.mauri.sukrara.feature.settings.lock.LockWidgetUi
import app.mauri.sukrara.feature.settings.sensor.LibreViewSummary
import app.mauri.sukrara.feature.settings.sensor.OpenOnScan
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsActions
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsContent
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsState
import app.mauri.sukrara.feature.settings.sensor.SensorSummary
import app.mauri.sukrara.feature.settings.sensor.SourceChoice
import app.mauri.sukrara.feature.settings.sensor.SourceLink
import app.mauri.sukrara.feature.settings.sensorend.SensorCalendarPort
import app.mauri.sukrara.feature.settings.sensorend.SensorCalendarState
import app.mauri.sukrara.feature.settings.sensorend.SensorEndActions
import app.mauri.sukrara.feature.settings.sensorend.SensorEndContent
import app.mauri.sukrara.feature.settings.sensorend.SensorEndPrefs
import app.mauri.sukrara.feature.settings.statusbar.NotificationPageActions
import app.mauri.sukrara.feature.settings.statusbar.NotificationPagePrefs
import app.mauri.sukrara.feature.settings.statusbar.PlacementDialog
import app.mauri.sukrara.feature.settings.statusbar.StatusBarContent
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPlace
import app.mauri.sukrara.graph.RingSpec
import app.mauri.sukrara.graph.stats.TirRange
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/**
 * The settings pages for the UX review (synthetic state only): light + dark × default + largest font, one phone
 * size, plus Spanish. Images go to build/outputs/roborazzi (copied to the review folder; no goldens are committed).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsGallery {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-10-27T14:00:00Z")
    private val zone = ZoneOffset.UTC
    private val clock = FakeClock(now, zone)
    private val muted = AlertUiState(mutedUntil = now.plusSeconds(90 * 60))
    private val lowMuted =
        AlertUiState(
            quiet =
                listOf(
                    AlertQuietSide(
                        "low",
                        matching = true,
                        reason = AlertQuietReason.Muted,
                        until = now.plusSeconds(1800),
                        relaxMin = 0,
                        canRemindLess = false,
                    ),
                ),
        )
    private val release =
        UpdateRelease("0.12.1", "https://github.com/mauriciabad/sukrara/releases/tag/v0.12.1", installable = true)

    /** About's updater with the daily check on and automatic installs off (the defaults). */
    private val updates = AboutUpdates({}, {}, {}, {}, automaticChecks = true, automaticInstall = false, {}, {})

    private fun about(state: UpdateState?) = @Composable {
        AboutContent(
            "0.12.0",
            Distribution.GitHub,
            AppBuild.Release,
            state,
            updates,
        ) {}
    }

    private val sensorWorking =
        SensorSettingsState(
            SourceChoice.Libre3Direct,
            SensorState.Active(now.plusSeconds(9 * 86_400 + 4 * 3600)),
            SensorSummary("3MH00SYNTH1", now.minusSeconds(5 * 86_400 + 20 * 3600), now.plusSeconds(9 * 86_400 + 4 * 3600)),
            LibreViewSummary("wearer@example.invalid", 2_864_434_397),
        )

    /** A follower of a made-up wearer whose settings arrived yesterday. */
    private val fromWearer = SettingsFromState(SettingsFrom.Wearer, "Ana", now.minusSeconds(86_400))

    private val pages: List<Pair<String, @Composable () -> Unit>> =
        listOf(
            "overview" to
                {
                    SettingsOverviewContent(
                        true,
                        AlertUiState(),
                        AlertReadiness(setOf(AlertRequirement.ExactAlarm)),
                        clock,
                        OverviewActions(),
                    ) {}
                },
            "overview-clinic" to {
                SettingsOverviewContent(
                    true,
                    AlertUiState(),
                    AlertReadiness.Ready,
                    clock,
                    OverviewActions(),
                    clinicBanner = true,
                    closedTips = 2,
                ) {}
            },
            "overview-muted" to { SettingsOverviewContent(true, muted, AlertReadiness.Ready, clock, OverviewActions()) {} },
            "overview-follower" to {
                SettingsOverviewContent(true, AlertUiState(), AlertReadiness.Ready, clock, OverviewActions(), settingsFrom = fromWearer) {}
            },
            "from-wearer" to { SettingsFromContent(fromWearer, clock, SettingsFromActions(), onBack = {}) },
            "from-wearer-waiting" to
                { SettingsFromContent(fromWearer.copy(wearerChangedAt = null), clock, SettingsFromActions(), onBack = {}) },
            "from-default" to
                { SettingsFromContent(fromWearer.copy(from = SettingsFrom.Default), clock, SettingsFromActions(), onBack = {}) },
            "from-custom" to {
                SettingsFromContent(
                    fromWearer.copy(from = SettingsFrom.Custom),
                    clock,
                    SettingsFromActions(),
                    onBack = {},
                )
            },
            "from-custom-nothing-yet" to {
                SettingsFromContent(
                    SettingsFromState(SettingsFrom.Custom, wearerName = null, wearerChangedAt = null),
                    clock,
                    SettingsFromActions(),
                    onBack = {},
                )
            },
            // a follower on the wearer's settings: the mirrored rows read-only under the note, no threshold review
            "alerts-follower" to {
                AlertsSettingsContent(
                    AlertsUi(AlertSettings(), AlertUiState(), mirrored = true),
                    null,
                    clock,
                    AlertsActions(),
                    header = { SettingsFromNote(fromWearer, {}) },
                ) {}
            },
            "alerts" to { AlertsSettingsContent(AlertsUi(AlertSettings(), AlertUiState()), null, clock, AlertsActions()) {} },
            "alerts-lately" to {
                val lately =
                    listOf(
                        AlertNote(now.minusSeconds(120), AlertNoteType.AllUnmuted),
                        AlertNote(now.minusSeconds(900), AlertNoteType.Muted, "low", minutes = 30),
                        AlertNote(now.minusSeconds(960), AlertNoteType.Fired, "low", mgdl = 66),
                        AlertNote(now.minusSeconds(7200), AlertNoteType.Cleared, "high", remote = true),
                        AlertNote(now.minusSeconds(9000), AlertNoteType.AllMuted),
                    )
                AlertsSettingsContent(AlertsUi(AlertSettings(), AlertUiState(lately = lately)), null, clock, AlertsActions()) {}
            },
            // Active now: a muted low side (Unmute, Change to) and slowed falling-fast reminders (Remind as usual)
            "alerts-active-now" to {
                val quiet =
                    lowMuted.copy(
                        quiet = lowMuted.quiet + AlertQuietSide("falling_fast", true, null, null, relaxMin = 30, canRemindLess = true),
                    )
                AlertsSettingsContent(AlertsUi(AlertSettings(), quiet), null, clock, AlertsActions()) {}
            },
            "alerts-muted-forever" to {
                AlertsSettingsContent(
                    AlertsUi(AlertSettings(), AlertUiState(mutedUntil = AlertUiState.UNTIL_TURNED_OFF)),
                    now,
                    clock,
                    AlertsActions(),
                ) {}
            },
            "alert-kind-low-muted" to {
                AlertKindContent(
                    AlertKind.LOW,
                    AlertsUi(AlertSettings(), lowMuted),
                    clock,
                    AlertKindActions(),
                ) {}
            },
            "alert-kind-follower" to {
                AlertKindContent(
                    AlertKind.LOW,
                    AlertsUi(AlertSettings(), AlertUiState(), mirrored = true),
                    clock,
                    AlertKindActions(),
                    header = { SettingsFromNote(fromWearer, {}) },
                ) {}
            },
            "alert-kind-very-low" to
                { AlertKindContent(AlertKind.VERY_LOW, AlertsUi(AlertSettings(), AlertUiState()), clock, AlertKindActions()) {} },
            "threshold-review" to { AlertThresholdReviewContent(AlertSettings(), { _, _ -> }, {}, {}) },
            "threshold-review-off" to {
                val s = AlertSettings()
                AlertThresholdReviewContent(
                    s.withRule(AlertKind.VERY_HIGH, s.rule(AlertKind.VERY_HIGH).copy(on = false)),
                    { _, _ -> },
                    {},
                    {},
                )
            },
            "readings" to { ReadingsContent(GraphPrefs(), { _, _ -> }, {}) },
            "readings-changed" to { ReadingsContent(GraphPrefs(listOf(54.0, 70.0, 120.0, 180.0, 250.0, 300.0)), { _, _ -> }, {}) },
            "readings-follower" to {
                ReadingsContent(GraphPrefs(listOf(54.0, 70.0, 120.0, 180.0, 250.0, 300.0)), { _, _ -> }, {}, readOnly = true) {
                    SettingsFromNote(fromWearer, {})
                }
            },
            "graph" to { GraphContent(GraphConfig(), GraphActions(), {}) },
            "metrics" to { MetricsContent(GraphConfig(), GraphActions(), {}) },
            // the third metric on, no metric without a recent treatment, the last treatment off, very good, no rewards
            "metrics-custom" to { MetricsContent(customMetrics, GraphActions(), {}) },
            "metrics-follower" to {
                MetricsContent(GraphConfig(), GraphActions(), {}, readOnly = true) { SettingsFromNote(fromWearer, {}) }
            },
            "metric-main" to { MetricContent(MetricSlot.Main, GraphConfig(), GraphActions(), {}) },
            "metric-third" to { MetricContent(MetricSlot.Third, GraphConfig(), GraphActions(), {}) },
            "metric-third-on" to { MetricContent(MetricSlot.Third, customMetrics, GraphActions(), {}) },
            "metric-no-recent-last-off" to { MetricContent(MetricSlot.NoRecent, customMetrics, GraphActions(), {}) },
            "last-treatment" to { LastTreatmentContent(GraphConfig(), GraphActions(), {}) },
            "last-treatment-off" to { LastTreatmentContent(customMetrics, GraphActions(), {}) },
            "tir-rings" to { TirRingsContent(GraphConfig(), GraphActions(), {}) },
            "tir-rings-custom" to { TirRingsContent(customMetrics, GraphActions(), {}) },
            "finetuning" to { FineTuningContent(AlertsUi(AlertSettings(), AlertUiState()), {}) {} },
            "finetuning-changed" to {
                FineTuningContent(
                    AlertsUi(AlertSettings().let { it.copy(actions = it.actions.copy(muteMin = 60)) }, AlertUiState()),
                    {},
                ) {}
            },
            "files" to {
                ImportExportContent(SettingsFileActions(), onBack = {}) { DataLinks(DataPage.Files, wearer = true) {} }
            },
            "files-follower" to {
                ImportExportContent(SettingsFileActions(), onBack = {}) { DataLinks(DataPage.Files, wearer = false) {} }
            },
            "graph-follower" to {
                GraphContent(GraphConfig(lines = listOf(80, 160)), GraphActions(), {}, readOnly = true) { SettingsFromNote(fromWearer, {}) }
            },
            "graph-custom" to {
                GraphContent(
                    GraphConfig(
                        lines = listOf(80, 100, 160),
                        placeHours = mapOf(GraphPlace.NOTIFICATION to 3),
                        sensorPlace = mapOf(GraphPlace.WIDGETS to false),
                    ),
                    GraphActions(),
                    {},
                )
            },
            // a setting the app sets that places can change (docs/38): one place differs, then both, and the sensor's
            "graph-one-place" to { GraphContent(GraphConfig(placeHours = mapOf(GraphPlace.NOTIFICATION to 3)), GraphActions(), {}) },
            "graph-places" to { GraphContent(placesConfig, GraphActions(), {}) },
            "time-shown" to {
                TimeShownContent(GraphConfig(placeHours = mapOf(GraphPlace.NOTIFICATION to 3)), GraphActions(), {})
            },
            "time-shown-follower" to {
                TimeShownContent(GraphConfig(placeHours = mapOf(GraphPlace.NOTIFICATION to 3)), GraphActions(), {}, readOnly = true) {
                    SettingsFromNote(fromWearer, {})
                }
            },
            "sensor-time" to { SensorTimeContent(GraphConfig(sensorPlace = mapOf(GraphPlace.WIDGETS to false)), GraphActions(), {}) },
            "graph-marks" to { GraphMarksContent(GraphConfig(), GraphActions(), {}) },
            "lock" to { LockScreenContent(20.0, {}, {}) },
            // the lock-screen widget: shown (the module runs), waiting for a System UI restart, off
            "lock-widget" to {
                LockScreenContent(20.0, {}, {}, LockWidgetUi(true, LockLook(), SystemAccessState(module = ModuleState.Active)))
            },
            "lock-widget-restart" to {
                LockScreenContent(20.0, {}, {}, LockWidgetUi(true, LockLook(), SystemAccessState(module = ModuleState.Restart)))
            },
            "lock-widget-off" to {
                LockScreenContent(20.0, {}, {}, LockWidgetUi(false, LockLook(), SystemAccessState(module = ModuleState.Missing)))
            },
            "reliability" to
                {
                    ReliabilityContent(
                        AlertReadiness(setOf(AlertRequirement.FullScreenIntent, AlertRequirement.BatteryExemption)),
                        {},
                        alerts = AlertSettings(),
                    ) {}
                },
            // every check on: SPA's status banner, one card in both themes (#181)
            "reliability-ready" to
                {
                    ReliabilityContent(AlertReadiness(emptySet(), unlockedSound = UnlockedSound.Heard), {}, alerts = AlertSettings()) {}
                },
            // a failed alert step (Copy report) and the unanswered Vibrate only when unlocked
            "reliability-delivery-unlocked" to {
                ReliabilityContent(
                    AlertReadiness(
                        setOf(AlertRequirement.AlertDelivery, AlertRequirement.SoundWhenUnlocked),
                        failedSteps = setOf("Alarm SecurityException"),
                    ),
                    {},
                ) {}
            },
            // an alert that couldn't be shown (it went out plain; Copy report), first in the list
            "reliability-alert-not-shown" to {
                ReliabilityContent(
                    AlertReadiness(setOf(AlertRequirement.AlertShown), failedSteps = setOf("Show:level NotFoundException")),
                    {},
                ) {}
            },
            // the GitHub version's update, one picture per state (#188)
            "about-update" to about(UpdateState.Available(release)),
            "about-update-page" to about(UpdateState.Available(release.copy(installable = false))),
            "about-uptodate" to about(UpdateState.UpToDate),
            "about-offline" to about(UpdateState.Offline),
            "about-downloading" to about(UpdateState.Downloading(release, 0.42f)),
            "about-waiting" to about(UpdateState.Waiting(release, AlertHold(setOf("very_low"), veryLow = true))),
            "about-confirm" to about(UpdateState.Confirm(release)),
            "about-installing" to about(UpdateState.Installing(release)),
            "about-failed" to about(UpdateState.Failed(release, UpdateFailure.Signature)),
            "about-auto-off" to
                {
                    AboutContent(
                        "0.12.0",
                        Distribution.GitHub,
                        AppBuild.Release,
                        UpdateState.UpToDate,
                        AboutUpdates({}, {}, {}, {}, false, true, {}, {}),
                    ) {}
                },
            // the Google Play build: no updater
            "about-play" to { AboutContent("0.12.0", Distribution.GooglePlay, AppBuild.Release, null, null) {} },
            "sensor" to { SensorSettingsContent(sensorWorking, now, zone, SensorSettingsActions()) {} },
            "sensor-this-phone" to {
                SensorSettingsContent(
                    sensorWorking.copy(paired = true, openOnScan = OpenOnScan(on = true, blocked = true)),
                    now,
                    zone,
                    SensorSettingsActions(),
                ) {}
            },
            "overview-sharing" to {
                SettingsOverviewContent(
                    true,
                    AlertUiState(),
                    AlertReadiness.Ready,
                    clock,
                    OverviewActions(),
                    sharing = OverviewSharing(name = "Alex", backup = BackupState.NotSaved),
                ) {}
            },
            // the identity can't be read / another app shares as the wearer: Backup says so (#134)
            "overview-backup-unreadable" to {
                SettingsOverviewContent(
                    true,
                    AlertUiState(),
                    AlertReadiness.Ready,
                    clock,
                    OverviewActions(),
                    sharing = OverviewSharing(backup = BackupState.Unreadable),
                ) {}
            },
            "overview-backup-paused" to {
                SettingsOverviewContent(
                    true,
                    AlertUiState(),
                    AlertReadiness.Ready,
                    clock,
                    OverviewActions(),
                    sharing = OverviewSharing(name = "Alex", backup = BackupState.Paused),
                ) {}
            },
            // search: an alert's own rows lead to that alert ("Remind every · In Alerts › Low"), and no match (#134)
            "overview-search" to {
                SettingsOverviewContent(true, AlertUiState(), AlertReadiness.Ready, clock, OverviewActions(), searchQuery = "low remind") {}
            },
            "overview-search-none" to {
                SettingsOverviewContent(true, AlertUiState(), AlertReadiness.Ready, clock, OverviewActions(), searchQuery = "volcano") {}
            },
            "advanced" to {
                AdvancedContent(
                    AdvancedState(
                        relays =
                            listOf(
                                RelayRow("wss://relay.example.invalid/", connected = true),
                                RelayRow("wss://relay2.example.invalid", connected = false, error = "timeout"),
                            ),
                        identity = "npub1qqqqsynth…xyzxyz",
                        epoch = 3,
                        stats = AdvancedStats(live = 1204, chunks = 58, posted = 1310, skipped = 342),
                    ),
                    AdvancedActions(),
                ) {}
            },
            "advanced-none" to { AdvancedContent(AdvancedState(follower = true), AdvancedActions()) {} },
            "advanced-follower-stats" to {
                AdvancedContent(
                    AdvancedState(
                        follower = true,
                        identity = "npub1qqqqsynth…abcabc",
                        stats = AdvancedStats(stored = 8920, refused = 1, posted = 1290),
                    ),
                    AdvancedActions(),
                ) {}
            },
            "log" to {
                LogContent(listOf("2026-10-28T10:00:00Z share relay connected", "2026-10-28T09:59:00Z sensor link live"), {})
            },
            "sensor-ended" to
                { SensorSettingsContent(sensorWorking.copy(state = SensorState.Ended), now, zone, SensorSettingsActions()) {} },
            "sensor-juggluco-signed-out" to {
                SensorSettingsContent(
                    sensorWorking.copy(source = SourceChoice.Juggluco, sensor = null, account = null),
                    now,
                    zone,
                    SensorSettingsActions(),
                ) {
                }
            },
            "sensor-juggluco-unreachable" to {
                SensorSettingsContent(
                    sensorWorking.copy(source = SourceChoice.Juggluco, link = SourceLink.JugglucoUnreachable),
                    now,
                    zone,
                    SensorSettingsActions(),
                ) {
                }
            },
            // docs/36 §11: the one choice, then only the chosen one's rows
            "statusbar" to { notificationPage(prefs(NotificationStyle.Chip)) },
            "statusbar-live-updates-off" to { notificationPage(prefs(NotificationStyle.Chip), liveUpdates = false) },
            "statusbar-icon" to { notificationPage(prefs(NotificationStyle.Icon)) },
            "statusbar-root" to { notificationPage(prefs(NotificationStyle.Root), rootActive) },
            // #94: the value hidden on the lock screen
            "statusbar-lock-screen-off" to { notificationPage(prefs(NotificationStyle.Chip).copy(lockScreen = false)) },
            // #178: asking, root on the phone but not allowed, refused, no root at all
            "statusbar-root-asking" to {
                notificationPage(
                    prefs(NotificationStyle.Root),
                    SystemAccessState(asking = true, rootSeen = true, module = ModuleState.Restart),
                )
            },
            "statusbar-root-not-allowed" to {
                notificationPage(prefs(NotificationStyle.Root), noRoot.copy(rootSeen = true, module = ModuleState.Restart))
            },
            "statusbar-root-refused" to {
                notificationPage(prefs(NotificationStyle.Root), noRoot.copy(root = RootAccess.Refused, rootSeen = true))
            },
            "statusbar-root-missing" to { notificationPage(prefs(NotificationStyle.Root), noRoot) },
            "statusbar-root-icons" to {
                notificationPage(
                    prefs(NotificationStyle.Root),
                    SystemAccessState(root = RootAccess.Granted, module = ModuleState.Missing, framework = null),
                )
            },
            "statusbar-root-restart" to {
                notificationPage(
                    prefs(NotificationStyle.Root).copy(position = HookPosition.AFTER_CLOCK),
                    SystemAccessState(root = RootAccess.Granted, module = ModuleState.Update),
                )
            },
            "statusbar-root-placement" to {
                notificationPage(prefs(NotificationStyle.Root), SystemAccessState(root = RootAccess.Granted, module = ModuleState.Missing))
                PlacementDialog(HookPosition.DEFAULT, module = false, onClose = {}, onPick = {})
            },
            "sensorend" to {
                SensorEndContent(
                    SensorEndPrefs(SensorEndConfig(), 8, SensorState.Active(now.plusSeconds(86_400 + 3600))),
                    now,
                    SensorEndActions(),
                    onBack = {},
                )
            },
            "sensorend-calendar" to {
                SensorEndContent(
                    SensorEndPrefs(SensorEndConfig(), 8, SensorState.Active(now.plusSeconds(86_400 + 3600))),
                    now,
                    SensorEndActions(),
                    onBack = {},
                    calendar = SensorCalendarState(on = true, own = true, googleCalendar = true) to SensorCalendarPort.None,
                )
            },
            "sensorend-custom" to {
                SensorEndContent(
                    SensorEndPrefs(
                        SensorEndConfig(
                            reminders = listOf(SensorReminder(3, 20 * 60), SensorReminder(1, 9 * 60), SensorReminder(0, 7 * 60)),
                            countdown = false,
                            evening = false,
                        ),
                        4,
                        SensorState.WarmingUp(now.plusSeconds(1800)),
                    ),
                    now,
                    SensorEndActions(),
                    onBack = {},
                )
            },
            "sensor-none" to {
                SensorSettingsContent(
                    SensorSettingsState(SourceChoice.Libre3Direct, SensorState.None, null, null),
                    now,
                    zone,
                    SensorSettingsActions(),
                ) {}
            },
        )

    private fun gallery(
        dark: Boolean,
        fontScale: Float,
        prefix: String = "",
        only: Set<String>? = null,
    ) {
        var current by mutableStateOf(pages.first().second)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SukraraTheme(dark = dark, dynamic = false) { current() }
            }
        }
        val theme = if (dark) "dark" else "light"
        val font = if (fontScale > 1f) "font2x" else "font1x"
        val repeats = mutableListOf<String>()
        pages.filter { only == null || it.first in only }.forEach { (name, page) ->
            current = page
            compose.waitForIdle()
            // once per page: no row icon twice on one page
            if (!dark && fontScale == 1f && prefix.isEmpty()) {
                compose.repeatedRowIcons().takeIf { it.isNotEmpty() }?.let { repeats += "$name repeats $it" }
            }
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-$prefix$name-$theme-$font.png")
        }
        assertTrue(repeats.joinToString("\n"), repeats.isEmpty())
    }

    /**
     * *Sound while the phone is unlocked* (#183): the answers come from a test alert (they show once it went out), the
     * one that only vibrated offers Android's setting, and the answer can change at any time.
     */
    @Test fun unlockedSoundIsAnsweredFromATestAlert() {
        val opened = mutableListOf<AlertRequirement>()
        var tests = 0
        var answer by mutableStateOf(UnlockedSound.Unchecked)
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                val missing = if (answer == UnlockedSound.Unchecked) setOf(AlertRequirement.SoundWhenUnlocked) else emptySet()
                ReliabilityContent(
                    AlertReadiness(missing, unlockedSound = answer),
                    { opened += it },
                    { answer = it },
                    { tests++ > -1 },
                ) {}
            }
        }
        compose.onNodeWithText("I heard it").assertDoesNotExist()
        compose.onNodeWithText("Test the sound").performScrollTo().performClick()
        assertEquals(1, tests)
        compose.onNodeWithText("Sent. Listen, then say what it did.").assertExists()
        compose.onNodeWithText("It only vibrated").performScrollTo().performClick()
        assertEquals(UnlockedSound.OnlyVibrated, answer)
        compose.onNodeWithText("Open Android's setting").performScrollTo().performClick()
        assertEquals(listOf(AlertRequirement.SoundWhenUnlocked), opened)
        compose.onNodeWithText("I heard it").performScrollTo().performClick()
        assertEquals(UnlockedSound.Heard, answer)
        compose.onNodeWithText("Open Android's setting").assertDoesNotExist()
    }

    /** The test waits while it couldn't show Android's behaviour (a mode on): it says why, and can't be sent. */
    @Test fun theUnlockedTestWaitsForTheModeToEnd() {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                ReliabilityContent(AlertReadiness(setOf(AlertRequirement.SoundWhenUnlocked), soundTest = SoundTest.ModeOn), {}) {}
            }
        }
        compose.onNodeWithText("Test the sound").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Do Not Disturb is on: turn it off for the test", substring = true).assertExists()
    }

    /** *Do Not Disturb* (#184): a plain statement, never Missing, and one button that flips it both ways. */
    @Test fun doNotDisturbIsAStatementWithOneButton() {
        var dnd by mutableStateOf(DndStatus())
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                ReliabilityContent(AlertReadiness(dnd = dnd, unlockedSound = UnlockedSound.Heard), {}, onLetThroughDnd = {
                    dnd = dnd.copy(through = it)
                }) {}
            }
        }
        compose.onNodeWithText("Sukrara doesn't alert in Do Not Disturb").performScrollTo().assertExists()
        compose.onNodeWithText("Missing").assertDoesNotExist()
        compose.onNodeWithText("Let Sukrara through").performScrollTo().performClick()
        compose.onNodeWithText("Sukrara alerts in Do Not Disturb").performScrollTo().assertExists()
        compose.onNodeWithText("Don't let Sukrara through").performScrollTo().performClick()
        compose.onNodeWithText("Sukrara doesn't alert in Do Not Disturb").performScrollTo().assertExists()
        dnd = DndStatus(through = false, heldNow = DndStatus.Held.All)
        compose.onNodeWithText("Do Not Disturb is on now: Sukrara's alerts make no sound until it ends.").performScrollTo().assertExists()
    }

    @Test fun lightDefault() = gallery(dark = false, fontScale = 1f)

    @Test fun darkDefault() = gallery(dark = true, fontScale = 1f)

    @Test fun lightLargest() = gallery(dark = false, fontScale = 2f)

    @Test fun darkLargest() = gallery(dark = true, fontScale = 2f)

    @Test
    @Config(qualifiers = "es")
    fun spanish() =
        gallery(
            dark = true,
            fontScale = 1f,
            prefix = "es-",
            only =
                setOf(
                    "alerts",
                    "alert-kind-low-muted",
                    "threshold-review",
                    "threshold-review-off",
                    "about-update",
                    "sensor",
                    "sensor-juggluco-signed-out",
                    "sensor-juggluco-unreachable",
                    "from-wearer",
                    "from-custom",
                    "overview-search",
                    "advanced",
                    "statusbar-root-unasked",
                    "statusbar-root-not-allowed",
                    "overview",
                    "readings",
                    "metrics",
                    "metric-main",
                    "last-treatment",
                    "tir-rings",
                    "finetuning",
                    "files",
                    "sensorend",
                    "graph-custom",
                    "graph-places",
                    "time-shown",
                    "sensor-time",
                ),
        )

    /**
     * Metrics changed: the third metric on, nothing without a recent treatment, the last treatment off, the ring's number
     * very good without rewards, very high first in the colour order.
     */
    private val customMetrics =
        GraphConfig(
            slots = GraphConfig.DEFAULT_SLOTS.map { it.copy(on = true) },
            lastOn = false,
            lastElse = GraphConfig.DEFAULT_LAST_ELSE.copy(on = false),
            ringRange = TirRange.TIGHT,
            ringMarks = false,
            ringOrder = Reorder.moved(RingSpec.DEFAULT_RING_ORDER, 5, 0),
        )

    /** The notification and the widgets with their own time shown, and the widgets without the sensor time (docs/38). */
    private val placesConfig =
        GraphConfig(
            placeHours = mapOf(GraphPlace.NOTIFICATION to 3, GraphPlace.WIDGETS to 6),
            sensorPlace = mapOf(GraphPlace.WIDGETS to false),
        )

    /** A place's dialog over its page (the whole screen: dialogs are windows of their own). */
    private fun placeDialog(
        name: String,
        dark: Boolean,
        row: String,
        page: @Composable () -> Unit,
    ) {
        compose.setContent { SukraraTheme(dark = dark, dynamic = false) { page() } }
        compose.onNodeWithText(row).performScrollTo().performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/settings-$name-${if (dark) "dark" else "light"}-font1x.png")
    }

    /** Graph scrolled to the sensor time's row: the Time shown and Sensor time left rows together (docs/38). */
    @Test fun graphPlaceRows() {
        var theme by mutableStateOf(false)
        compose.setContent { SukraraTheme(dark = theme, dynamic = false) { GraphContent(placesConfig, GraphActions(), {}) } }
        for (dark in listOf(false, true)) {
            theme = dark
            compose.onNodeWithText("Sensor time left").performScrollTo()
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(
                "build/outputs/roborazzi/settings-graph-place-rows-${if (dark) "dark" else "light"}-font1x.png",
            )
        }
    }

    /** Time shown in Spanish on the gallery's phone size (the whole page: the app's values and the places). */
    @Test
    @Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
    fun spanishTimeShown() {
        compose.setContent {
            SukraraTheme(dark = true, dynamic = false) {
                TimeShownContent(GraphConfig(placeHours = mapOf(GraphPlace.NOTIFICATION to 3)), GraphActions(), {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-es-time-shown-phone-dark-font1x.png")
    }

    @Test fun placeDialogs() {
        placeDialog("time-shown-dialog", dark = false, row = "Notification") {
            TimeShownContent(GraphConfig(placeHours = mapOf(GraphPlace.NOTIFICATION to 3)), GraphActions(), {})
        }
    }

    @Test fun sensorPlaceDialog() {
        placeDialog("sensor-time-dialog", dark = true, row = "Widgets") {
            SensorTimeContent(GraphConfig(sensorPlace = mapOf(GraphPlace.WIDGETS to false)), GraphActions(), {})
        }
    }
}

private val noRoot = SystemAccessState(root = RootAccess.Missing, module = ModuleState.Missing)
private val rootActive = SystemAccessState(root = RootAccess.Granted, module = ModuleState.Active, framework = "LSPosed 2.0")

private fun prefs(style: NotificationStyle) =
    NotificationPagePrefs(style, numberIcon = true, lockScreen = true, colorize = false, position = HookPosition.DEFAULT, rootColor = true)

/** Settings › Glucose notification with made-up access states (no root, no LSPosed: pictures only). */
@Composable
private fun notificationPage(
    prefs: NotificationPagePrefs,
    access: SystemAccessState = SystemAccessState(),
    liveUpdates: Boolean = true,
) = StatusBarContent(prefs, rootOffered = true, liveUpdates, access, busy = false, problem = null, NotificationPageActions()) {}
