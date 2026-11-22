// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.graph

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.WatchDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Garmin watch's *Sukrara* settings (the watch as a place, docs/38, the Garmin app's SPEC §11): every value says
 * *Same as the app* until it has its own; each can be set and set back to *Same as the app*; a value's page follows
 * the app under its switch; a watch Garmin Connect no longer knows lets its values go.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class WatchPagesTest {
    @get:Rule val compose = createComposeRule()

    private val app = GraphConfig()
    private var own by mutableStateOf(WatchDisplay())
    private val opened = mutableListOf<WatchPage>()
    private var backs = 0

    private fun page(gone: Boolean = false) =
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                WatchSettingsContent("Venu 3", app, own, { own = it }, { opened += it }, { backs++ }, gone = gone)
            }
        }

    /** A row's click (a row scrolled to the top sits under the app bar, where a touch would land on the bar). */
    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private fun choice(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun `a simple value is set in its dialog and set back to Same as the app`() {
        page()
        compose.onNodeWithText("Sukrara on Venu 3").assertExists()
        compose.onNodeWithText("Same as the app for everything").assertDoesNotExist()
        compose.onNodeWithText("Last treatment").performScrollTo().tap()
        choice("Off").performClick()
        assertEquals(false, own.lastOn)
        compose.onNodeWithText("Same as the app for everything").performScrollTo().assertExists()
        compose.onNodeWithText("Last treatment").performScrollTo().tap()
        choice("Same as the app (On)").performClick()
        assertNull(own.lastOn)
        assertTrue(own.same)
    }

    @Test
    fun `the period the Numbers page opens on, then everything back to the app's`() {
        page()
        compose.onNodeWithText("Opens on").performScrollTo().tap()
        choice("30 days").performClick()
        assertEquals(30, own.reportDays)
        compose.onNodeWithText("Same as the app for everything").performScrollTo().tap()
        assertTrue(own.same)
        assertEquals(0, backs)
    }

    @Test
    fun `a metric's row opens its page`() {
        page()
        compose.onNodeWithText("Second metric").performScrollTo().tap()
        compose.onNodeWithText("Treatments on the graph").performScrollTo().tap()
        assertEquals(listOf(WatchPage.Second, WatchPage.Marks), opened)
    }

    @Test
    fun `a value's page follows the app under its switch, and gets its own from the app's`() {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) { WatchValueContent(WatchPage.Second, app, own, { own = it }, {}) }
        }
        val follow = compose.onNode(hasText("Same as the app") and isToggleable())
        follow.assertIsOn()
        follow.performClick()
        // its own now, starting from the app's
        assertEquals(listOf(null, app.slots[1], null), own.slots)
        follow.assertIsOff()
        follow.performClick()
        assertTrue(own.same)
    }

    @Test
    fun `a watch Garmin Connect no longer knows lets its values go and leaves`() {
        own = WatchDisplay(hours = 3)
        page(gone = true)
        compose.onNodeWithText("Garmin Connect no longer knows Venu 3", substring = true).assertExists()
        compose.onNodeWithText("Forget this watch's settings").performScrollTo().tap()
        assertTrue(own.same)
        assertEquals(1, backs)
    }
}
