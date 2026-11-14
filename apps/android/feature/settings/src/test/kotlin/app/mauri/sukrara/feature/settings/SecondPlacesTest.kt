// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.SensorEndConfig
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.TrendSetting
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.advanced.ImportExportContent
import app.mauri.sukrara.feature.settings.advanced.SettingsFileActions
import app.mauri.sukrara.feature.settings.graph.GraphActions
import app.mauri.sukrara.feature.settings.graph.GraphContent
import app.mauri.sukrara.feature.settings.graph.RingsContent
import app.mauri.sukrara.feature.settings.search.SettingsIndex
import app.mauri.sukrara.feature.settings.sensorend.SensorEndActions
import app.mauri.sukrara.feature.settings.sensorend.SensorEndContent
import app.mauri.sukrara.feature.settings.sensorend.SensorEndPrefs
import app.mauri.sukrara.feature.settings.sensorend.SensorEndViewModel
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPlace
import app.mauri.sukrara.graph.GraphSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

/**
 * #205 (docs/35 §7): a setting editable from more than one page is the same setting (one store, never a copy), and the
 * pages that link to each other open the right page. As AOSP Settings does it: an entry that opens another screen
 * (SettingsLib SPA `SettingsEntryBuilder.createLinkFrom`), and one preference key shown on two screens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SecondPlacesTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-10-27T14:00:00Z")
    private val clock = FakeClock(now, ZoneOffset.UTC)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private var current by mutableStateOf<@Composable () -> Unit>({})
    private var started = false

    /** Shows [content] (one composition per test: a later call swaps the page). */
    private fun show(content: @Composable () -> Unit) {
        current = content
        if (!started) {
            started = true
            compose.setContent { SukraraTheme(dark = false, dynamic = false) { current() } }
        }
        compose.waitForIdle()
    }

    @Test
    fun `THE night - Sensor end edits the night alerts' window, and shows it`() {
        val alerts = FakeAlertSettingsStore()
        val settings = InMemorySettingsRepository()
        val vm = SensorEndViewModel(settings, MutableStateFlow(SensorState.None), alerts)
        val first = runBlocking { vm.prefs.filterNotNull().first() }
        // the defaults are the night alerts' (22:00–07:00)
        assertEquals(22 * 60 to 7 * 60, first.config.nightStart to first.config.nightEnd)
        vm.setNight(from = 21 * 60 + 30)
        vm.setNight(to = 6 * 60)
        assertEquals(21 * 60 + 30, alerts.state.value.night.fromMin)
        assertEquals(6 * 60, alerts.state.value.night.toMin)
        // a change in Night alerts is the Sensor end page's night too
        runBlocking { alerts.update { it.copy(night = it.night.copy(fromMin = 23 * 60)) } }
        val shown = runBlocking { vm.prefs.filterNotNull().first { it.config.nightStart == 23 * 60 } }
        assertEquals(6 * 60, shown.config.nightEnd)
    }

    @Test
    fun `the sensor's time left - Sensor end's row is Graph's setting`() {
        val settings = InMemorySettingsRepository()
        val vm = SensorEndViewModel(settings, MutableStateFlow(SensorState.None), FakeAlertSettingsStore())
        vm.setSensorTime(false)
        assertFalse(runBlocking { settings.flow(GraphSettings.SensorTime).first() })
        runBlocking { settings.set(GraphSettings.SensorTimePlace.getValue(GraphPlace.WIDGETS), "on") }
        val prefs = runBlocking { vm.prefs.filterNotNull().first { !it.sensorTime && it.sensorPlace.isNotEmpty() } }
        assertEquals(mapOf(GraphPlace.WIDGETS to true), prefs.sensorPlace)
        // Sensor end's row says the place that differs and opens the same page as Graph's
        var opened = 0
        show { SensorEndContent(prefs, now, SensorEndActions(openSensorPlaces = { opened++ }), onBack = {}) }
        compose.onNodeWithText("On on\u00A0widgets").performScrollTo().performClick()
        assertEquals(1, opened)
        assertTrue(runBlocking { GraphSettings.config(settings).first() }.sensorTime(GraphPlace.WIDGETS))
    }

    @Test
    fun `the fast alerts' trend row chooses the trend arrow`() {
        var chosen: TrendSetting? = null
        show {
            AlertKindContent(
                AlertKind.FALLING_FAST,
                AlertsUi(AlertSettings(), AlertUiState()),
                clock,
                AlertKindActions(),
                trend = { TrendChoiceRow(TrendSetting.Balanced, { chosen = it }) },
            ) {}
        }
        compose.onNodeWithText("Trend arrow").performScrollTo().performClick()
        compose.onNodeWithText("Quick").performClick()
        assertEquals(TrendSetting.Quick, chosen)
    }

    @Test
    fun `Alerts opens Fine-tuning and links Alert reliability and Readings`() {
        val opened = mutableListOf<String>()
        show {
            AlertsSettingsContent(
                AlertsUi(AlertSettings(), AlertUiState()),
                null,
                clock,
                AlertsActions(
                    openFineTuning = { opened += "fine" },
                    openReliability = { opened += "reliability" },
                    openReadings = { opened += "readings" },
                ),
            ) {}
        }
        compose.onNodeWithText("Fine-tuning").performScrollTo().performClick()
        compose.onNodeWithText("Alert reliability").performScrollTo().performClick()
        compose.onNodeWithText("Readings").performScrollTo().performClick()
        assertEquals(listOf("fine", "reliability", "readings"), opened)
        // the moved rows are on Fine-tuning, not on Alerts
        compose.onNodeWithText("Notification buttons").assertDoesNotExist()
    }

    @Test
    fun `Fine-tuning's reset asks first and keeps a mirrored wait`() {
        val store = FakeAlertSettingsStore(AlertSettings().let { it.copy(actions = it.actions.copy(muteMin = 60)) })
        var ui by mutableStateOf(AlertsUi(store.state.value, AlertUiState()))
        show {
            FineTuningContent(ui, { t ->
                runBlocking { store.update(t) }
                ui = AlertsUi(store.state.value, AlertUiState())
            }) {}
        }
        compose.onNodeWithText("Reset fine-tuning").performScrollTo().performClick()
        assertEquals(60, store.state.value.actions.muteMin)
        compose.onNodeWithText("Reset").performClick()
        assertEquals(AlertSettings().actions, store.state.value.actions)
    }

    @Test
    fun `Readings links Alerts, Graph's Ranges row and Rings' link open Readings`() {
        var alerts = 0
        var readings = 0
        show { ReadingsContent(GraphPrefs(), { _, _ -> }, {}, onAlerts = { alerts++ }) }
        compose.onNodeWithText("Alerts").performScrollTo().performClick()
        assertEquals(1, alerts)
        show { GraphContent(GraphConfig(), GraphActions(openReadings = { readings++ }), {}) }
        compose.onNodeWithText("Ranges").performClick()
        show { RingsContent(GraphConfig(), GraphActions(openReadings = { readings++ }), {}) }
        compose.onNodeWithText("Readings").performScrollTo().performClick()
        assertEquals(2, readings)
    }

    @Test
    fun `Sensor end links Sensor and Night alerts, Night alerts links Sensor end on the wearer's phone`() {
        val opened = mutableListOf<String>()
        show {
            SensorEndContent(
                SensorEndPrefs(SensorEndConfig(), 8, SensorState.None),
                now,
                SensorEndActions(openSensor = { opened += "sensor" }, openNight = { opened += "night" }),
                onBack = {},
            )
        }
        compose.onNodeWithText("Sensor").performScrollTo().performClick()
        compose.onNodeWithText("Night alerts").performScrollTo().performClick()
        assertEquals(listOf("sensor", "night"), opened)
        show { NightAlertsContent(AlertsUi(AlertSettings(), AlertUiState()), {}, {}, openSensorEnd = { opened += "end" }) {} }
        compose.onNodeWithText("Sensor end").performScrollTo().performClick()
        assertEquals("end", opened.last())
        // a follower: no sensor reminders, no link
        show { NightAlertsContent(AlertsUi(AlertSettings(), AlertUiState(), follower = true), {}, {}) {} }
        compose.onNodeWithText("Sensor end").assertDoesNotExist()
    }

    @Test
    fun `the data pages link the other three, Backup only on the wearer's phone`() {
        val opened = mutableListOf<DataPage>()
        show { ImportExportContent(SettingsFileActions(), onBack = {}) { DataLinks(DataPage.Files, wearer = true) { opened += it } } }
        compose.onNodeWithText("Sharing").performScrollTo().performClick()
        compose.onNodeWithText("Share with apps").performScrollTo().performClick()
        compose.onNodeWithText("Backup").performScrollTo().performClick()
        assertEquals(listOf(DataPage.Sharing, DataPage.Apps, DataPage.Backup), opened)
        show { ImportExportContent(SettingsFileActions(), onBack = {}) { DataLinks(DataPage.Files, wearer = false) {} } }
        compose.onNodeWithText("Backup").assertDoesNotExist()
        show { DataLinks(DataPage.Apps, wearer = true) {} }
        compose.onNodeWithText("Share with apps").assertDoesNotExist()
        compose.onNodeWithText("Import and export").assertExists()
    }

    @Test
    fun `the overview lists the sections in docs-35's order`() {
        show {
            SettingsOverviewContent(true, AlertUiState(), AlertReadiness.Ready, clock, OverviewActions(pens = true)) {}
        }
        for (title in listOf("Readings", "Graph", "Rings", "Sensor end", "Import and export")) {
            compose.onNodeWithText(title).performScrollTo()
        }
        compose.onNodeWithText("Export data").assertDoesNotExist()
    }

    @Test
    fun `search opens the moved pages through their parents`() {
        assertEquals(listOf(SettingsRoute.Readings, SettingsRoute.TrendArrow), SettingsIndex.path(SettingsRoute.TrendArrow))
        assertEquals(listOf(SettingsRoute.Alerts, SettingsRoute.AlertsFineTuning), SettingsIndex.path(SettingsRoute.AlertsFineTuning))
        // Sensor end keeps its own overview row (docs/35 §7): no parent page
        assertEquals(listOf(SettingsRoute.SensorEnd), SettingsIndex.path(SettingsRoute.SensorEnd))
        assertEquals(listOf(SettingsRoute.Rings), SettingsIndex.path(SettingsRoute.Rings))
    }
}
