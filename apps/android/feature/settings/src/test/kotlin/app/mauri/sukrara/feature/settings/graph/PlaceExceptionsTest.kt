// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPlace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #209 (docs/38): a setting with a default and per-place exceptions. Its page shows the default as the setting's row
 * and one *Exceptions* row naming only the places that differ; the Exceptions page lists every place, *Default (…)* or
 * its own, and each place's choice offers *Default* first. The places are each phone's own: a follower edits them in
 * every *Settings from* mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PlaceExceptionsTest {
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

    @Test
    fun `Graph names only the places that differ, and the per-place rows are on the Exceptions page`() {
        val opened = mutableListOf<PlaceSetting>()
        val actions = GraphActions(openExceptions = { opened += it })
        show { GraphContent(GraphConfig(), actions, {}) }
        compose.onAllNodesWithText("None").assertCountEquals(2)
        compose.onNodeWithText("Notification").assertDoesNotExist()
        show { GraphContent(GraphConfig(placeHours = mapOf(GraphPlace.NOTIFICATION to 3)), actions, {}) }
        compose.onNodeWithText("Notification: 3 h").performScrollTo().performClick()
        assertEquals(listOf(PlaceSetting.Hours), opened)
    }

    @Test
    fun `a follower not in Custom still edits the places`() {
        var opened = 0
        show { GraphContent(GraphConfig(), GraphActions(openExceptions = { opened++ }), {}, readOnly = true) }
        compose
            .onAllNodesWithText("None")
            .onFirst()
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `a place's time shown is Default first, with the default's value`() {
        val chosen = mutableListOf<Pair<GraphPlace, Double>>()
        val c = GraphConfig(defaultHours = 12, placeHours = mapOf(GraphPlace.NOTIFICATION to 3))
        show { PlaceExceptionsContent(PlaceSetting.Hours, c, GraphActions(placeHours = { p, h -> chosen += p to h }), {}) }
        compose.onNodeWithText("3 h").assertExists()
        compose.onNodeWithText("App").performClick()
        compose.onNodeWithText("6 h").performClick()
        compose.onNodeWithText("Notification").performClick()
        compose.onAllNodesWithText("Default (12 h)").onLast().performClick()
        assertEquals(listOf(GraphPlace.APP to 6.0, GraphPlace.NOTIFICATION to 0.0), chosen)
    }

    @Test
    fun `a place's sensor time is Default, On or Off`() {
        val chosen = mutableListOf<Pair<GraphPlace, Boolean?>>()
        val c = GraphConfig(sensorTimeDefault = false, sensorPlace = mapOf(GraphPlace.WIDGETS to true))
        show { PlaceExceptionsContent(PlaceSetting.SensorTime, c, GraphActions(sensorPlace = { p, on -> chosen += p to on }), {}) }
        compose.onNodeWithText("Widgets").performClick()
        compose.onAllNodesWithText("Default (Off)").onLast().performClick()
        compose.onNodeWithText("App").performClick()
        compose.onAllNodesWithText("On").onLast().performClick()
        assertEquals(listOf(GraphPlace.WIDGETS to null, GraphPlace.APP to true), chosen)
    }
}
