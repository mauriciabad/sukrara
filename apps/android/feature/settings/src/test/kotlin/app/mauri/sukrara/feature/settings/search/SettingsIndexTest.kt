// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.search

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.data.settings.HookPosition
import app.mauri.sukrara.core.data.settings.SettingsFrom
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SensorEndConfig
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.core.model.UpdateState
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.designsystem.rows.ROW_TAG
import app.mauri.sukrara.designsystem.rows.SettingsGroupTitle
import app.mauri.sukrara.designsystem.rows.SettingsHighlight
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.AboutContent
import app.mauri.sukrara.feature.settings.AboutUpdates
import app.mauri.sukrara.feature.settings.AlertKindActions
import app.mauri.sukrara.feature.settings.AlertKindContent
import app.mauri.sukrara.feature.settings.AlertPlayback
import app.mauri.sukrara.feature.settings.AlertThresholdReviewContent
import app.mauri.sukrara.feature.settings.AlertsActions
import app.mauri.sukrara.feature.settings.AlertsSettingsContent
import app.mauri.sukrara.feature.settings.AlertsUi
import app.mauri.sukrara.feature.settings.BackupState
import app.mauri.sukrara.feature.settings.Distribution
import app.mauri.sukrara.feature.settings.GraphPrefs
import app.mauri.sukrara.feature.settings.NightAlertsContent
import app.mauri.sukrara.feature.settings.NightKindContent
import app.mauri.sukrara.feature.settings.OverviewActions
import app.mauri.sukrara.feature.settings.OverviewSharing
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.RangesContent
import app.mauri.sukrara.feature.settings.ReliabilityContent
import app.mauri.sukrara.feature.settings.SettingsOverviewContent
import app.mauri.sukrara.feature.settings.SettingsRoute
import app.mauri.sukrara.feature.settings.advanced.AdvancedActions
import app.mauri.sukrara.feature.settings.advanced.AdvancedContent
import app.mauri.sukrara.feature.settings.advanced.AdvancedState
import app.mauri.sukrara.feature.settings.advanced.AdvancedStats
import app.mauri.sukrara.feature.settings.advanced.LogContent
import app.mauri.sukrara.feature.settings.advanced.RelayRow
import app.mauri.sukrara.feature.settings.follower.SettingsFromActions
import app.mauri.sukrara.feature.settings.follower.SettingsFromContent
import app.mauri.sukrara.feature.settings.follower.SettingsFromState
import app.mauri.sukrara.feature.settings.graph.GraphActions
import app.mauri.sukrara.feature.settings.graph.GraphContent
import app.mauri.sukrara.feature.settings.graph.GraphMarksContent
import app.mauri.sukrara.feature.settings.lock.LockScreenContent
import app.mauri.sukrara.feature.settings.lock.LockWidgetUi
import app.mauri.sukrara.feature.settings.pens.PensContent
import app.mauri.sukrara.feature.settings.pens.PensViewModel
import app.mauri.sukrara.feature.settings.sensor.LibreViewSummary
import app.mauri.sukrara.feature.settings.sensor.OpenOnScan
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsActions
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsContent
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsState
import app.mauri.sukrara.feature.settings.sensor.SensorSummary
import app.mauri.sukrara.feature.settings.sensor.SourceChoice
import app.mauri.sukrara.feature.settings.sensorend.SensorEndActions
import app.mauri.sukrara.feature.settings.sensorend.SensorEndContent
import app.mauri.sukrara.feature.settings.sensorend.SensorEndPrefs
import app.mauri.sukrara.feature.settings.statusbar.RootActions
import app.mauri.sukrara.feature.settings.statusbar.RootSettings
import app.mauri.sukrara.feature.settings.statusbar.RootStatusBarSection
import app.mauri.sukrara.feature.settings.statusbar.StatusBarContent
import app.mauri.sukrara.feature.settings.statusbar.StatusBarPrefs
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.MarkShow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

/**
 * THE search index against the pages (#65): every settings page of this module is rendered (wearer and follower,
 * with the states that show its optional rows) and every row on it must be in [SettingsIndex] under that page, and
 * every entry of the index must be on its page (a row, a group's title, a button). A row whose title is the person's
 * own words (a name, a relay) or a state rather than something to find is listed below with why.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsIndexTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-27T14:00:00Z")
    private val zone = ZoneOffset.UTC
    private val clock = FakeClock(now, zone)

    /** Where a row is: a settings page, or Settings › Sensor (another feature's page drawn here). */
    private val overview = SearchTarget.Page(SettingsRoute.Overview)
    private val sensorPage = SearchTarget.Other(OtherPage.Sensor)

    private class Scene(
        val name: String,
        val at: SearchTarget,
        val content: @Composable () -> Unit,
    )

    /** A phone with notification channels: an alert's page links to its channel. */
    private val channels =
        object : AlertPlayback {
            override fun play(
                kind: AlertKind,
                pack: String,
                alarm: Boolean,
                vibrate: Boolean,
            ) = Unit

            override fun stop() = Unit

            override fun silenced(
                kind: AlertKind,
                alarm: Boolean,
            ) = false

            override fun sendTest(
                kind: AlertKind,
                settings: AlertSettings,
            ) = Unit

            override fun channelId(
                kind: AlertKind,
                alarm: Boolean,
            ) = "channel-${kind.name}"
        }

    private val wearerFrom = SettingsFromState(SettingsFrom.Custom, "Ana", now.minusSeconds(86_400))

    /** The night alerts on and applying now (until 7:00). */
    private val nightUi =
        AlertsUi(
            AlertSettings().let { it.copy(night = it.night.copy(on = true)) },
            AlertUiState(nightUntil = now.plusSeconds(5 * 3600)),
        )

    private val sensor =
        SensorSettingsState(
            SourceChoice.Libre3Direct,
            SensorState.Active(now.plusSeconds(9 * 86_400)),
            SensorSummary("3MH00SYNTH1", now.minusSeconds(5 * 86_400), now.plusSeconds(9 * 86_400)),
            LibreViewSummary("wearer@example.invalid", 2_864_434_397),
            paired = true,
            openOnScan = OpenOnScan(on = true, blocked = false),
        )

    // Built outside the composable (lint ViewModelConstructorInComposable).
    private val pensViewModel by lazy { PensViewModel(InMemorySettingsRepository(), null) }

    private val scenes: List<Scene> =
        listOf(
            Scene("overview, wearer", overview) {
                SettingsOverviewContent(
                    true,
                    AlertUiState(),
                    AlertReadiness.Ready,
                    clock,
                    OverviewActions(pens = true),
                    closedTips = 2,
                    sharing = OverviewSharing(name = "Alex", backup = BackupState.NotSaved),
                ) {}
            },
            Scene("overview, follower", overview) {
                SettingsOverviewContent(
                    true,
                    AlertUiState(),
                    AlertReadiness.Ready,
                    clock,
                    OverviewActions(),
                    sharing = OverviewSharing(wearer = false),
                    settingsFrom = wearerFrom,
                ) {}
            },
            Scene("alerts", SearchTarget.Page(SettingsRoute.Alerts)) {
                AlertsSettingsContent(AlertsUi(AlertSettings(), AlertUiState()), null, clock, AlertsActions()) {}
            },
            // a follower's Alerts page: its own escalation switch instead of the wearer's two rows (#39)
            Scene("alerts (follower)", SearchTarget.Page(SettingsRoute.Alerts)) {
                AlertsSettingsContent(AlertsUi(AlertSettings(), AlertUiState(), follower = true), null, clock, AlertsActions()) {}
            },
            *AlertKind.entries
                .map { kind ->
                    Scene("alert $kind", SearchTarget.Page(SettingsRoute.AlertKindPage(kind))) {
                        AlertKindContent(kind, AlertsUi(AlertSettings(), AlertUiState()), clock, AlertKindActions(playback = channels)) {}
                    }
                }.toTypedArray(),
            // Alerts › Night alerts while they apply (its state on top), and each alert's night rule
            Scene("night alerts", SearchTarget.Page(SettingsRoute.NightAlerts)) {
                NightAlertsContent(nightUi, {}, {}) {}
            },
            *AlertKind.entries
                .map { kind ->
                    Scene("night $kind", SearchTarget.Page(SettingsRoute.NightKindPage(kind))) { NightKindContent(kind, nightUi, {}) {} }
                }.toTypedArray(),
            // Alert reliability with everything on (its banner, the test), and with items missing
            Scene("reliability", SearchTarget.Page(SettingsRoute.Reliability)) {
                ReliabilityContent(AlertReadiness(emptySet(), unlockedSound = UnlockedSound.Heard), {}, alerts = AlertSettings()) {}
            },
            Scene("reliability, missing", SearchTarget.Page(SettingsRoute.Reliability)) {
                ReliabilityContent(AlertReadiness(setOf(AlertRequirement.FullScreenIntent, AlertRequirement.BatteryExemption)), {}) {}
            },
            Scene("ranges", SearchTarget.Page(SettingsRoute.Ranges)) { RangesContent(GraphPrefs(), { _, _ -> }, {}) },
            // changed: Reset shows
            Scene("ranges, changed", SearchTarget.Page(SettingsRoute.Ranges)) {
                RangesContent(GraphPrefs(listOf(54.0, 70.0, 120.0, 180.0, 250.0, 300.0)), { _, _ -> }, {})
            },
            Scene("graph", SearchTarget.Page(SettingsRoute.Graph)) { GraphContent(GraphConfig(), GraphActions(), {}) },
            Scene(
                "graph, three rings",
                SearchTarget.Page(SettingsRoute.Graph),
            ) { GraphContent(GraphConfig(ringCount = 3), GraphActions(), {}) },
            Scene("graph marks", SearchTarget.Page(SettingsRoute.GraphMarks)) { GraphMarksContent(GraphConfig(), GraphActions(), {}) },
            Scene("graph marks, changed", SearchTarget.Page(SettingsRoute.GraphMarks)) {
                GraphMarksContent(GraphConfig(marks = MarkShow.DEFAULT.withoutLabels()), GraphActions(), {})
            },
            Scene("lock screen", SearchTarget.Page(SettingsRoute.LockScreen)) {
                LockScreenContent(20.0, {}, {}, LockWidgetUi(on = true, look = LockLook()))
            },
            Scene("pens", SearchTarget.Page(SettingsRoute.Pens)) {
                val ui by pensViewModel.ui.collectAsState()
                ui?.let { PensContent(it, pensViewModel, {}) }
            },
            Scene("status bar", SearchTarget.Page(SettingsRoute.StatusBar)) {
                StatusBarContent(StatusBarPrefs(chip = true, numberIcon = true), true, {}, {}, {}, {}) {
                    RootStatusBarSection(
                        RootSettings(on = true, position = HookPosition.DEFAULT, color = true, hideIcon = true),
                        SystemAccessState(root = RootAccess.Granted, module = ModuleState.Active, framework = "LSPosed"),
                        busy = false,
                        problem = null,
                        choosing = false,
                        onChoosing = {},
                        actions = RootActions({}, {}, {}, {}, {}, {}, {}),
                    )
                }
            },
            Scene("sensor end", SearchTarget.Page(SettingsRoute.SensorEnd)) {
                SensorEndContent(
                    SensorEndPrefs(SensorEndConfig(), 8, SensorState.Active(now.plusSeconds(86_400 + 3600))),
                    now,
                    SensorEndActions(),
                    onBack = {},
                )
            },
            Scene(
                "about",
                SearchTarget.Page(SettingsRoute.About),
            ) {
                AboutContent(
                    "0.12.0",
                    Distribution.GitHub,
                    UpdateState.UpToDate,
                    AboutUpdates({}, {}, {}, {}, automaticChecks = true, automaticInstall = false, {}, {}),
                ) {}
            },
            Scene("threshold review", SearchTarget.Page(SettingsRoute.ThresholdReview)) {
                AlertThresholdReviewContent(AlertSettings(), { _, _ -> }, {}, {})
            },
            Scene("advanced, wearer", SearchTarget.Page(SettingsRoute.Advanced)) {
                AdvancedContent(
                    AdvancedState(
                        relays = listOf(RelayRow("wss://relay.example.invalid/", connected = true)),
                        identity = "npub1qqqqsynth…xyzxyz",
                        epoch = 3,
                        stats = AdvancedStats(live = 12, chunks = 3, posted = 14, skipped = 2),
                    ),
                    AdvancedActions(),
                ) {}
            },
            Scene("advanced, follower", SearchTarget.Page(SettingsRoute.Advanced)) {
                AdvancedContent(
                    AdvancedState(follower = true, identity = "npub1qqqqsynth…abcabc", stats = AdvancedStats(stored = 900, refused = 1)),
                    AdvancedActions(),
                ) {}
            },
            // Advanced › Log: the log's lines, no rows to find (its row on Advanced is)
            Scene("log", SearchTarget.Page(SettingsRoute.Log)) { LogContent(listOf("12:00 synthetic line"), {}) },
            Scene("settings from", SearchTarget.Page(SettingsRoute.SettingsFrom)) {
                SettingsFromContent(wearerFrom.copy(wearerName = null), clock, SettingsFromActions(), onBack = {})
            },
            Scene("sensor", sensorPage) { SensorSettingsContent(sensor, now, zone, SensorSettingsActions()) {} },
            Scene("sensor, juggluco", sensorPage) {
                SensorSettingsContent(sensor.copy(source = SourceChoice.Juggluco), now, zone, SensorSettingsActions()) {}
            },
        )

    /** Rows whose title isn't a fixed text (the index can't hold it), by the scene they're on, with why. */
    private fun ownWords(
        scene: String,
        row: String,
    ): Boolean =
        when {
            // the wearer's profile row is titled with their name; the closed tips row is an action of the overview itself
            scene == "overview, wearer" -> {
                row == "Alex" || row == string(R.string.st_show_closed_tips)
            }

            // relays are listed by their host
            scene == "advanced, wearer" -> {
                row == "relay.example.invalid"
            }

            // not following over the internet: a state, not a row to find
            scene == "advanced, follower" -> {
                row == string(R.string.st_adv_no_relays_follower)
            }

            // "Use the low alert": the alert's main switch says its kind in the sentence (the page's title is indexed)
            scene.startsWith("alert ") -> {
                pattern(R.string.st_kind_use).matches(row)
            }

            // "Main slot: period": a slot's own rows, found by the slot's row
            scene.startsWith("graph") -> {
                listOf(R.string.st_slot_period, R.string.st_slot_faded, R.string.st_slot_what).any { pattern(it).matches(row) }
            }

            // an empty list, the person's own reminders, the update check's answer: states, not rows to find
            scene == "pens" -> {
                row == string(R.string.st_pens_none)
            }

            scene == "sensor end" -> {
                row in setOf("1 day before", "On the end day")
            }

            scene == "about" -> {
                row == string(R.string.st_update_none)
            }

            // each threshold is its alert's (found by the alert's name, which opens its page)
            scene == "threshold review" -> {
                SettingsIndex.entries.any {
                    (it.target as? SearchTarget.Page)?.route is SettingsRoute.AlertKindPage &&
                        string(it.title) == row
                }
            }

            // "Use the low alert" at night: the night rule's main switch, like an alert's own page
            scene.startsWith("night ") && scene != "night alerts" -> {
                pattern(R.string.st_kind_use).matches(row)
            }

            else -> {
                false
            }
        }

    /** A format string as a pattern: "Use the %1$s alert" matches "Use the low alert". */
    private fun pattern(id: Int) = Regex(Regex.escape(string(id)).replace(Regex("%\\d\\$[sd]"), "\\\\E.+\\\\Q"))

    /** Index entries that aren't a row title on their page, with why. */
    private val notTitles: Set<Int> =
        setOf(
            // the row is titled with the person's name ("Alex", or "Add your name")
            R.string.st_profile_title,
        )

    private fun string(id: Int) = context.getString(id)

    /** Each row's title: the first text inside the row (SPA's rows draw the title before the summary). */
    private fun titles(): List<String> =
        compose
            .onAllNodesWithTag(ROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { firstText(it) }

    private fun firstText(node: SemanticsNode): String? =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            ?: node.children.firstNotNullOfOrNull { firstText(it) }

    /** Every text on the page (rows, group titles, buttons, radio choices): what an entry may point at. */
    private fun texts(): List<String> =
        compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    @Test
    fun everyRowIsInTheIndexAndEveryEntryIsARow() {
        var current by mutableStateOf(scenes.first().content)
        val shown = mutableMapOf<SearchTarget, MutableSet<String>>()
        val missing = mutableListOf<String>()
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { current() } }
        for (scene in scenes) {
            current = scene.content
            compose.waitForIdle()
            val rows = titles()
            assertTrue("${scene.name} shows no rows", rows.isNotEmpty() || scene.name in setOf("threshold review", "log"))
            shown.getOrPut(scene.at) { mutableSetOf() } += texts()
            val indexed =
                SettingsIndex.entries
                    .filter { it.on == scene.at }
                    .map { string(it.title) }
                    .toSet()
            for (row in rows) {
                if (row !in indexed && !ownWords(scene.name, row)) missing += "${scene.name}: \"$row\""
            }
        }
        assertTrue("rows the search can't find (add them to SettingsIndex):\n" + missing.joinToString("\n"), missing.isEmpty())

        val stale =
            SettingsIndex.entries
                .filter { it.on in shown && it.title !in notTitles }
                .filter { string(it.title) !in shown.getValue(it.on) }
                .map { "${it.on}: \"${string(it.title)}\"" }
        assertTrue("index entries their page doesn't show:\n" + stale.joinToString("\n"), stale.isEmpty())
    }

    /** The title of the group around [node] (null: a group without one). */
    private fun groupOf(node: SemanticsNode): String? =
        generateSequence(node.parent) { it.parent }.firstNotNullOfOrNull { it.config.getOrNull(SettingsGroupTitle) }

    /** Each row's group on a page: title -> the groups it is in (null: a group without a title). */
    private fun groups(): Map<String, Set<String?>> =
        compose
            .onAllNodesWithTag(ROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { n -> firstText(n)?.let { it to groupOf(n) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }

    /**
     * #181: a result names where its row is ("Alerts › Quiet after treatments"): every entry's group is the group its
     * row is in on the page (a group's title as a result: that group).
     */
    @Test
    fun everyEntrySaysItsGroup() {
        var current by mutableStateOf(scenes.first().content)
        val seen = mutableMapOf<SearchTarget, MutableMap<String, MutableSet<String?>>>()
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { current() } }
        for (scene in scenes) {
            current = scene.content
            compose.waitForIdle()
            val page = seen.getOrPut(scene.at) { mutableMapOf() }
            for ((title, groups) in groups()) page.getOrPut(title) { mutableSetOf() } += groups
        }
        val wrong =
            SettingsIndex.entries
                .filter { it.on in seen }
                .mapNotNull { e ->
                    val title = string(e.title)
                    val group = e.group?.let(::string)
                    val groups = seen.getValue(e.on)[title] ?: return@mapNotNull null // a group's title or a button
                    "${e.on}: \"$title\" is in ${groups.joinToString()}, the index says $group".takeIf { group !in groups }
                }
        assertTrue("entries in the wrong group:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    /**
     * #181: every result opens its page at its row, flashed (SPA's HighlightBox): for each entry, its page drawn after
     * the result's request has a row (or group title, or section button) with that title, and flashes it
     * ([SettingsHighlight.shown]). A result that is a page itself asks for nothing, and its page exists here.
     */
    @Test
    fun everyResultFlashesItsRow() {
        var current by mutableStateOf(scenes.first().content)
        var draw by mutableIntStateOf(0)
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { key(draw) { current() } } }
        val texts = mutableMapOf<String, Set<String>>()
        for (scene in scenes) {
            current = scene.content
            draw++
            compose.waitForIdle()
            texts[scene.name] = texts().toSet()
        }
        val missed = mutableListOf<String>()
        for (e in SettingsIndex.entries.filter { e -> scenes.any { it.at == e.on } }) {
            val row = e.highlight?.let(::string)
            if (row == null) {
                if (e.target is SearchTarget.Page && scenes.none { it.at == e.target }) missed += "${e.target} has no page here"
                continue
            }
            val scene = scenes.firstOrNull { it.at == e.on && row in texts.getValue(it.name) }
            if (scene == null) {
                missed += "${e.on}: \"$row\" is on none of its pages"
                continue
            }
            SettingsHighlight.request(row)
            current = scene.content
            draw++
            compose.waitForIdle()
            if (SettingsHighlight.shown != row) missed += "${scene.name}: \"$row\" isn't flashed"
        }
        SettingsHighlight.request(null)
        assertTrue("results that don't flash their row:\n" + missed.joinToString("\n"), missed.isEmpty())
    }
}
