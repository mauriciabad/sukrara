// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.rows.ActionRow
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.ROW_TAG
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsHighlight
import app.mauri.sukrara.designsystem.rows.SettingsScaffoldPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The settings rows around SPA: a search result's row highlighted on arrival, click labels, groups' first frame. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SettingsRowsTest {
    @get:Rule val compose = createComposeRule()

    @After fun clear() = SettingsHighlight.request(null)

    /** A page long enough that its last rows start below the screen; [n] makes it a new page. */
    private fun page(n: () -> Int = { 1 }) =
        compose.setContent {
            key(n()) {
                SettingsScaffoldPage("Page ${n()}", onBack = {}) {
                    SettingsGroup(title = "Group") {
                        for (i in 1..30) NavigationRow("Row $i", onClick = {})
                    }
                }
            }
        }

    /** The row a search result asked for scrolls into view on the page that opens next (SPA's HighlightBox). */
    @Test
    fun searchResultRowIsBroughtIntoView() {
        SettingsHighlight.request("Row 28")
        page()
        compose.onNodeWithText("Row 28").assertIsDisplayed()
    }

    @Test
    fun withoutARequestThePageOpensAtTheTop() {
        page()
        compose.onNodeWithText("Row 1").assertIsDisplayed()
        compose.onNodeWithText("Row 28").assertIsNotDisplayed()
    }

    /** One request, one page: the page that opens takes it, a later page opens at its top. */
    @Test
    fun aRequestIsTakenOnce() {
        SettingsHighlight.request("Row 28")
        var opened by mutableIntStateOf(1)
        page { opened }
        compose.onNodeWithText("Row 28").assertIsDisplayed()
        opened = 2
        compose.onAllNodesWithText("Page 2")[0].assertExists()
        compose.onNodeWithText("Row 28").assertIsNotDisplayed()
    }

    /** An action row says what a tap does when its title doesn't ("double tap to copy", not "to activate"). */
    @Test
    fun actionRowsCarryTheirClickLabel() {
        compose.setContent {
            SettingsScaffoldPage("Page", onBack = {}) {
                SettingsGroup {
                    ActionRow("secret", onClick = {}, onClickLabel = "copy")
                    ActionRow("Show closed tips", onClick = {})
                }
            }
        }
        assertEquals(
            "copy",
            compose
                .onNodeWithText("secret")
                .fetchSemanticsNode()
                .config[SemanticsActions.OnClick]
                .label,
        )
        assertNull(
            compose
                .onNodeWithText("Show closed tips")
                .fetchSemanticsNode()
                .config[SemanticsActions.OnClick]
                .label,
        )
    }

    /**
     * A group has SPA's 16 dp side margins and its title from the first frame on (they came a frame late: the page
     * flashed its groups full width).
     */
    @Test
    fun groupsHaveTheirMarginsInTheFirstFrame() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SettingsScaffoldPage("Page", onBack = {}) {
                SettingsGroup(title = "Group") { InfoRow("Row") }
            }
        }
        compose.onNodeWithTag(ROW_TAG).assertLeftPositionInRootIsEqualTo(16.dp)
        compose.onNodeWithText("Group").assertIsDisplayed()
    }
}
