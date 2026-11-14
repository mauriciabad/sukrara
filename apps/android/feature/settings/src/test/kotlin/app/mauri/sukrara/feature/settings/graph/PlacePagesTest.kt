// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.core.data.settings.Setting
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPlace
import app.mauri.sukrara.graph.GraphSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #209 (docs/38): a setting the app sets that the notification and the widgets can change. Graph's row says the app's
 * value and only the places that differ, and opens the setting's page: the app's value first, then each place, its own
 * value or *Same as the app*; a place's choice offers *Same as the app (…)* first. The places are each phone's own: a
 * follower edits them in every *Settings from* mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PlacePagesTest {
    @get:Rule val compose = createComposeRule()

    private var current by mutableStateOf<@Composable () -> Unit>({})
    private var started = false

    private fun show(content: @Composable () -> Unit) {
        current = content
        if (!started) {
            started = true
            compose.setContent { SukraraTheme(dark = false, dynamic = false) { current() } }
        }
        compose.waitForIdle()
    }

    /** A choice in the open dialog (the page behind can show the same words). */
    private fun choice(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun `Graph says the app's value and only the places that differ, and opens the page`() {
        val opened = mutableListOf<PlaceSetting>()
        val actions = GraphActions(openPlaces = { opened += it })
        show { GraphContent(GraphConfig(), actions, {}) }
        compose.onNodeWithText("12 h").assertExists()
        compose.onNodeWithText("Notification").assertDoesNotExist()
        show { GraphContent(GraphConfig(placeHours = mapOf(GraphPlace.NOTIFICATION to 3)), actions, {}) }
        compose.onNodeWithText("12 h · 3 h in the notification").performScrollTo().performClick()
        show { GraphContent(GraphConfig(placeHours = mapOf(GraphPlace.NOTIFICATION to 3, GraphPlace.WIDGETS to 6)), actions, {}) }
        compose.onNode(hasText("12 h in the\u00A0app", substring = true)).performScrollTo().performClick()
        assertEquals(listOf(PlaceSetting.Hours, PlaceSetting.Hours), opened)
    }

    @Test
    fun `the sensor time row's switch is the app's, its summary only the places that differ`() {
        val set = mutableListOf<Boolean>()
        val opened = mutableListOf<PlaceSetting>()
        show {
            GraphContent(
                GraphConfig(sensorPlace = mapOf(GraphPlace.NOTIFICATION to false, GraphPlace.WIDGETS to false)),
                GraphActions(sensorTime = { set += it }, openPlaces = { opened += it }),
                {},
            )
        }
        compose.onNodeWithText("Off in the notification and on\u00A0widgets").performScrollTo().performClick()
        assertEquals(listOf(PlaceSetting.SensorTime), opened)
    }

    @Test
    fun `the app's time shown is one tap, Custom types one`() {
        val numbers = mutableListOf<Pair<Setting<Double>, Double>>()
        val actions = GraphActions(number = { s, v -> numbers += s to v })
        show { TimeShownContent(GraphConfig(appHours = 12), actions, {}) }
        compose.onNodeWithText("12 h").assertIsSelected()
        compose.onNodeWithText("6 h").performClick()
        assertEquals(listOf(GraphSettings.Hours to 6.0), numbers)
        // a custom value: Custom… selected, with the value under it
        show { TimeShownContent(GraphConfig(appHours = 18), actions, {}) }
        compose.onNode(hasText("Custom…") and hasText("18 h")).assertIsSelected()
        compose.onNodeWithText("12 h").assertIsNotSelected()
    }

    @Test
    fun `a place's time shown is Same as the app first, with the app's value`() {
        val chosen = mutableListOf<Pair<GraphPlace, Double>>()
        val c = GraphConfig(appHours = 12, placeHours = mapOf(GraphPlace.NOTIFICATION to 3))
        show { TimeShownContent(c, GraphActions(placeHours = { p, h -> chosen += p to h }), {}) }
        compose.onNode(hasText("Notification") and hasText("3 h")).assertExists()
        compose.onNode(hasText("Widgets") and hasText("Same as the app")).assertExists()
        compose.onNodeWithText("Notification").performScrollTo().performClick()
        compose.onNodeWithText("Time shown in the notification").assertExists()
        choice("Same as the app (12 h)").performClick()
        compose.onNodeWithText("Widgets").performScrollTo().performClick()
        compose.onNodeWithText("Time shown on widgets").assertExists()
        choice("24 h").performClick()
        assertEquals(listOf(GraphPlace.NOTIFICATION to 0.0, GraphPlace.WIDGETS to 24.0), chosen)
    }

    @Test
    fun `a follower not in Custom still edits the places`() {
        val chosen = mutableListOf<GraphPlace>()
        show { TimeShownContent(GraphConfig(), GraphActions(placeHours = { p, _ -> chosen += p }), {}, readOnly = true) }
        compose.onNodeWithText("6 h").assertIsNotEnabled()
        compose
            .onNodeWithText("Widgets")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        choice("3 h").performClick()
        assertEquals(listOf(GraphPlace.WIDGETS), chosen)
    }

    @Test
    fun `a place's sensor time is Same as the app, On or Off`() {
        val chosen = mutableListOf<Pair<GraphPlace, Boolean?>>()
        val app = mutableListOf<Boolean>()
        val c = GraphConfig(appSensorTime = false, sensorPlace = mapOf(GraphPlace.WIDGETS to true))
        show { SensorTimeContent(c, GraphActions(sensorTime = { app += it }, sensorPlace = { p, on -> chosen += p to on }), {}) }
        compose.onNodeWithText("Show the sensor time left").performClick()
        compose.onNodeWithText("Widgets").performClick()
        compose.onNodeWithText("Sensor time left on widgets").assertExists()
        choice("Same as the app (Off)").performClick()
        compose.onNodeWithText("Notification").performClick()
        choice("On").performClick()
        assertEquals(listOf(true), app)
        assertEquals(listOf(GraphPlace.WIDGETS to null, GraphPlace.NOTIFICATION to true), chosen)
    }
}
