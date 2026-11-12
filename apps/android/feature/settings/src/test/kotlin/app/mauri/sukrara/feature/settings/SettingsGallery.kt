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
import app.mauri.sukrara.core.model.DndStatus
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.model.ModuleState
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
import app.mauri.sukrara.feature.settings.advanced.LogContent
import app.mauri.sukrara.feature.settings.advanced.RelayRow
import app.mauri.sukrara.feature.settings.follower.SettingsFromActions
import app.mauri.sukrara.feature.settings.follower.SettingsFromContent
import app.mauri.sukrara.feature.settings.follower.SettingsFromNote
import app.mauri.sukrara.feature.settings.follower.SettingsFromState
import app.mauri.sukrara.feature.settings.graph.GraphActions
import app.mauri.sukrara.feature.settings.graph.GraphContent
import app.mauri.sukrara.feature.settings.graph.GraphMarksContent
import app.mauri.sukrara.feature.settings.graph.Reorder
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
import app.mauri.sukrara.feature.settings.statusbar.RootActions
import app.mauri.sukrara.feature.settings.statusbar.RootSettings
import app.mauri.sukrara.feature.settings.statusbar.RootStatusBarSection
import app.mauri.sukrara.feature.settings.statusbar.StatusBarContent
import app.mauri.sukrara.feature.settings.statusbar.StatusBarPrefs
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPlace
import app.mauri.sukrara.graph.RingSpec
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
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
        UpdateRelease("0.12.1", "https://github.com/mauriciabad/sukrara/releases/tag/v0.12.1", prerelease = true, installable = true)

    /** About's updater with the daily check on and automatic installs off (the defaults). */
    private val updates = AboutUpdates({}, {}, {}, {}, automaticChecks = true, automaticInstall = false, {}, {})

    private fun about(state: UpdateState?) = @Composable { AboutContent("0.12.0", Distribution.GitHub, state, updates) {} }

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
            "ranges" to { RangesContent(GraphPrefs(), { _, _ -> }, {}) },
            "ranges-changed" to { RangesContent(GraphPrefs(listOf(54.0, 70.0, 120.0, 180.0, 250.0, 300.0)), { _, _ -> }, {}) },
            "ranges-follower" to {
                RangesContent(GraphPrefs(listOf(54.0, 70.0, 120.0, 180.0, 250.0, 300.0)), { _, _ -> }, {}, readOnly = true) {
                    SettingsFromNote(fromWearer, {})
                }
            },
            "graph" to { GraphContent(GraphConfig(), GraphActions(), {}) },
            "graph-follower" to {
                GraphContent(GraphConfig(lines = listOf(80, 160)), GraphActions(), {}, readOnly = true) { SettingsFromNote(fromWearer, {}) }
            },
            "graph-custom" to {
                GraphContent(
                    GraphConfig(
                        lines = listOf(80, 100, 160),
                        placeHours = mapOf(GraphPlace.NOTIFICATION to 3),
                        ringCount = 3,
                        lastElse = null,
                        ringOrder = Reorder.moved(RingSpec.DEFAULT_RING_ORDER, 5, 0),
                        sensorHidden = setOf(GraphPlace.WIDGETS),
                    ),
                    GraphActions(),
                    {},
                )
            },
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
                { AboutContent("0.12.0", Distribution.GitHub, UpdateState.UpToDate, AboutUpdates({}, {}, {}, {}, false, true, {}, {})) {} },
            // the Google Play build: no updater
            "about-play" to { AboutContent("0.12.0", Distribution.GooglePlay, null, null) {} },
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
            "statusbar" to
                { StatusBarContent(StatusBarPrefs(chip = true, numberIcon = true), true, {}, {}, {}, {}) { root(rootOn, noRoot) } },
            "statusbar-live-updates-off" to { StatusBarContent(StatusBarPrefs(chip = true, numberIcon = false), false, {}, {}, {}, {}) },
            "statusbar-chip-off" to { StatusBarContent(StatusBarPrefs(chip = false, numberIcon = true), true, {}, {}, {}, {}) },
            // #94: the value hidden on the lock screen
            "statusbar-lock-screen-off" to {
                StatusBarContent(StatusBarPrefs(chip = true, numberIcon = true, lockScreen = false), true, {}, {}, {}, {})
            },
            // #178: never asked (the value off), asking, root on the phone but not allowed, refused
            "statusbar-root-unasked" to {
                StatusBarContent(StatusBarPrefs(chip = true, numberIcon = true), true, {}, {}, {}, {}) {
                    root(rootOn.copy(on = false), SystemAccessState(rootSeen = true, module = ModuleState.Restart))
                }
            },
            "statusbar-root-asking" to {
                StatusBarContent(StatusBarPrefs(chip = true, numberIcon = true), true, {}, {}, {}, {}) {
                    root(rootOn, SystemAccessState(asking = true, rootSeen = true, module = ModuleState.Restart))
                }
            },
            "statusbar-root-not-allowed" to {
                StatusBarContent(StatusBarPrefs(chip = true, numberIcon = true), true, {}, {}, {}, {}) {
                    root(rootOn, noRoot.copy(rootSeen = true, module = ModuleState.Restart))
                }
            },
            "statusbar-root-refused" to {
                StatusBarContent(StatusBarPrefs(chip = true, numberIcon = true), true, {}, {}, {}, {}) {
                    root(rootOn, noRoot.copy(root = RootAccess.Refused, rootSeen = true))
                }
            },
            "statusbar-root-icons" to {
                StatusBarContent(StatusBarPrefs(chip = false, numberIcon = true), true, {}, {}, {}, {}) {
                    root(rootOn, SystemAccessState(root = RootAccess.Granted, module = ModuleState.Missing, framework = null))
                }
            },
            "statusbar-root-restart" to {
                StatusBarContent(StatusBarPrefs(chip = false, numberIcon = true), true, {}, {}, {}, {}) {
                    root(
                        rootOn.copy(position = HookPosition.AFTER_CLOCK),
                        SystemAccessState(root = RootAccess.Granted, module = ModuleState.Update),
                    )
                }
            },
            "statusbar-root-active" to {
                StatusBarContent(StatusBarPrefs(chip = false, numberIcon = true), true, {}, {}, {}, {}) {
                    root(rootOn, SystemAccessState(root = RootAccess.Granted, module = ModuleState.Active, framework = "LSPosed 2.0"))
                }
            },
            "statusbar-root-placement" to {
                StatusBarContent(StatusBarPrefs(chip = false, numberIcon = true), true, {}, {}, {}, {}) {
                    root(rootOn, SystemAccessState(root = RootAccess.Granted, module = ModuleState.Missing), choosing = true)
                }
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
        pages.filter { only == null || it.first in only }.forEach { (name, page) ->
            current = page
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-$prefix$name-$theme-$font.png")
        }
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
                ),
        )
}

private val rootOn = RootSettings(on = true, position = HookPosition.DEFAULT, color = true, hideIcon = true)
private val noRoot = SystemAccessState(root = RootAccess.Missing, module = ModuleState.Missing)

/** The status bar page's root section with made-up access states (no root, no LSPosed: pictures only). */
@Composable
private fun root(
    s: RootSettings,
    access: SystemAccessState,
    choosing: Boolean = false,
) = RootStatusBarSection(s, access, busy = false, problem = null, choosing = choosing, onChoosing = {
}, actions = RootActions({}, {}, {}, {}, {}, {}, {}))
