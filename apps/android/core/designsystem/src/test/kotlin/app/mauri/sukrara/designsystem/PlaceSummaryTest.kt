// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.designsystem.rows.PlaceSummary
import app.mauri.sukrara.designsystem.rows.PlaceValue
import app.mauri.sukrara.designsystem.rows.PlaceWords
import app.mauri.sukrara.designsystem.rows.PlacesRow
import app.mauri.sukrara.designsystem.rows.SettingsChrome
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.keepLastWords
import app.mauri.sukrara.designsystem.rows.placeSummary
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #209 (docs/38): what a setting's row says about the places that can differ from the app. Built from the values: only
 * the places whose own value differs are named; one line while one differs and it fits, else one line per value, each
 * saying where.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class PlaceSummaryTest {
    @get:Rule val compose = createComposeRule()

    private val words =
        PlaceWords(
            app = "in the app",
            join = { l -> if (l.size == 1) l[0] else l.dropLast(1).joinToString(", ") + " and " + l.last() },
            valueWhere = { v, w -> "$v $w" },
        )

    private fun hours(
        app: Int,
        notification: Int? = null,
        widgets: Int? = null,
    ) = placeSummary(
        app,
        listOf(PlaceValue("in the notification", notification), PlaceValue("on widgets", widgets)),
        switchRow = false,
        text = { "$it h" },
        words = words,
    )

    private fun sensor(
        app: Boolean,
        notification: Boolean? = null,
        widgets: Boolean? = null,
    ) = placeSummary(
        app,
        listOf(PlaceValue("in the notification", notification), PlaceValue("on widgets", widgets)),
        switchRow = true,
        text = { if (it) "On" else "Off" },
        words = words,
    )

    @Test fun nothingDiffersSaysTheAppsValueAlone() {
        assertEquals(PlaceSummary.Plain("12 h"), hours(12))
    }

    @Test fun onePlaceDiffers() {
        val s = hours(12, notification = 3)
        assertEquals(
            PlaceSummary.Differs("12 h · 3 h in the notification", listOf("12 h in the app and on widgets", "3 h in the notification")),
            s,
        )
    }

    @Test fun twoPlacesDifferEachLineSaysWhere() {
        assertEquals(
            PlaceSummary.Differs(null, listOf("12 h in the app", "3 h in the notification", "6 h on widgets")),
            hours(12, notification = 3, widgets = 6),
        )
    }

    @Test fun placesWithTheSameValueShareALine() {
        assertEquals(
            PlaceSummary.Differs(null, listOf("12 h in the app", "3 h in the notification and on widgets")),
            hours(12, notification = 3, widgets = 3),
        )
    }

    /** A place set to the app's own value isn't listed: nothing differs. */
    @Test fun aPlacePinnedToTheAppsValueIsNotListed() {
        assertEquals(PlaceSummary.Plain("12 h"), hours(12, widgets = 12))
        assertEquals(
            PlaceSummary.Differs("12 h · 3 h in the notification", listOf("12 h in the app and on widgets", "3 h in the notification")),
            hours(12, notification = 3, widgets = 12),
        )
    }

    /** A switch row: the switch shows the app's value, so the summary only names the places that differ. */
    @Test fun aSwitchRow() {
        assertEquals(PlaceSummary.Plain(null), sensor(true))
        assertEquals(PlaceSummary.Plain(null), sensor(true, widgets = true))
        assertEquals(PlaceSummary.Differs("Off on widgets", listOf("Off on widgets")), sensor(true, widgets = false))
        assertEquals(PlaceSummary.Differs(null, listOf("Off in the notification and on widgets")), sensor(true, false, false))
    }

    @Test fun theSpokenFormSeparatesWithACommaAndSaysTheValuesInFull() {
        val spoken =
            placeSummary(
                12,
                listOf(PlaceValue("in the notification", 3), PlaceValue("on widgets", null)),
                switchRow = false,
                text = { "$it hours" },
                words = words.copy(separator = ", "),
            )
        assertEquals("12 hours, 3 hours in the notification", (spoken as PlaceSummary.Differs).oneLine)
    }

    private fun row(fontScale: Float) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SukraraTheme(dark = false, dynamic = false) {
                    SettingsChrome {
                        SettingsGroup {
                            PlacesRow(
                                "Time shown",
                                hours(12, notification = 3),
                                hours(12, notification = 3),
                                {},
                                icon = R.drawable.ds_ic_schedule,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test fun aWrappedLineNeverLeavesItsLastWordAlone() {
        assertEquals("12 h in the app and on\u00A0widgets", keepLastWords("12 h in the app and on widgets"))
        assertEquals("On widgets", keepLastWords("On widgets"))
        assertEquals("12 h", keepLastWords("12 h"))
    }

    @Test fun theOneLineFormWhileItFits() {
        row(1f)
        compose.onNodeWithText("12 h · 3 h in the notification").assertExists()
    }

    /** Large text: "12 h · 3 h in the notification" would break inside an item, so each value gets its own line. */
    @Test fun oneLinePerValueWhenTheOneLineDoesNotFit() {
        row(2f)
        compose.onNodeWithText("12 h · 3 h in the notification").assertDoesNotExist()
        // one paragraph per line; TalkBack reads the lines with a pause between them
        // each line keeps its last two words together, so a wrapped line never leaves one word alone below
        compose.onNodeWithText("12 h in the app and on\u00A0widgets3 h in the\u00A0notification", useUnmergedTree = true).assertExists()
        compose
            .onNodeWithContentDescription("12 h in the app and on widgets, 3 h in the notification", useUnmergedTree = true)
            .assertExists()
    }
}
