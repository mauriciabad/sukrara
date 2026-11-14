// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import android.graphics.Bitmap
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.designsystem.rows.SettingsChrome
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
 * One placed widget's own settings (#209, docs/38): every row is its own value or *Same as in Settings*; each dialog
 * offers *Same as in Settings (…)* first, with the value the Widgets place has in Settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val inSettings =
        GraphConfig(appHours = 12, placeHours = mapOf(GraphPlace.WIDGETS to 6), sensorPlace = mapOf(GraphPlace.WIDGETS to true))

    private fun choice(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test fun eachRowIsItsOwnValueOrSameAsInSettings() {
        var saved: WidgetOverrides? = null
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                SettingsChrome {
                    WidgetSettingsScreen(
                        WidgetKind.COMBINED,
                        WidgetOverrides(hours = 3),
                        inSettings,
                        preview = { Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888) },
                        describe = { "" },
                        onCancel = {},
                        onSave = { saved = it },
                    )
                }
            }
        }
        compose.onNodeWithText("3 h").assertExists()
        assertEquals(4, compose.onAllNodesWithText("Same as in Settings").fetchSemanticsNodes().size)
        // the time shown: what Settings gives the widgets first, then the presets
        compose.onNodeWithText("Time shown").performClick()
        choice("Same as in Settings (6 h)").performClick()
        compose.onNodeWithText("Sensor time left").performScrollTo().performClick()
        choice("Same as in Settings (On)").assertExists()
        choice("Off").performClick()
        compose.onNodeWithText("Rings").performScrollTo().performClick()
        choice("Same as in Settings (2)").assertExists()
        choice("3").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(WidgetOverrides(rings = 3, sensorTime = false), saved)
    }
}
