// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The quiet rules follow stock Settings' pattern for a feature with its own options (Display › Night Light): on Alerts
 * a two-target row (switch + summary, the row opens the rule's page), never a length row that appears under a switch;
 * the page has the main switch and the length, greyed while off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuietRulePageTest {
    @get:Rule val compose = createComposeRule()

    private val clock = FakeClock(Instant.parse("2026-11-14T14:00:00Z"))

    private fun alerts(
        s: AlertSettings,
        open: (QuietKind) -> Unit = {},
    ) = compose.setContent {
        SukraraTheme(dark = false, dynamic = false) {
            AlertsSettingsContent(AlertsUi(s, AlertUiState()), null, clock, AlertsActions(openQuiet = open)) {}
        }
    }

    @Test fun alertsPageShowsNoLengthRowsAndSaysTheLengthOnce() {
        alerts(AlertSettings())
        // on by default: the summary says the length; no "How long" / "Back in range within" row under the switches
        assertEquals(0, compose.onAllNodesWithText("How long").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Back in range within").fetchSemanticsNodes().size)
        compose.onNodeWithText("For 1 h").performScrollTo()
        compose.onNodeWithText("For 15 min").performScrollTo()
    }

    @Test fun anOffRuleSaysOff() {
        val s = AlertEdits.quiet(QuietKind.AfterCarbs) { it.copy(on = false) }(AlertSettings())
        alerts(s)
        assertEquals(0, compose.onAllNodesWithText("For 15 min").fetchSemanticsNodes().size)
    }

    @Test fun theRowOpensTheRulesPage() {
        var opened: QuietKind? = null
        alerts(AlertSettings()) { opened = it }
        compose.onNodeWithText("Quiet lows after carbs").performScrollTo().performClick()
        assertEquals(QuietKind.AfterCarbs, opened)
    }

    private fun page(
        s: AlertSettings,
        which: QuietKind,
        mirrored: Boolean = false,
        edit: ((AlertSettings) -> AlertSettings) -> Unit = {},
    ) = compose.setContent {
        SukraraTheme(dark = false, dynamic = false) { QuietRuleContent(which, AlertsUi(s, AlertUiState(), mirrored = mirrored), edit) {} }
    }

    @Test fun pageHasItsMainSwitchAndLength() {
        var s = AlertSettings()
        page(s, QuietKind.AfterInsulin, edit = { s = it(s) })
        compose.onNodeWithText("Use this quiet time").assertIsOn()
        compose.onNodeWithText("How long").assertIsEnabled()
        compose.onNodeWithText("Use this quiet time").performClick()
        assertEquals(false, s.afterInsulin.on)
    }

    @Test fun anOffRulesLengthIsGreyedNotHidden() {
        page(AlertEdits.quiet(QuietKind.FallingHigh) { it.copy(on = false) }(AlertSettings()), QuietKind.FallingHigh)
        compose.onNodeWithText("Use this quiet time").assertIsOff()
        compose.onNodeWithText("Back in range within").assertIsNotEnabled()
    }

    @Test fun mirroredRuleIsReadOnly() {
        page(AlertSettings(), QuietKind.RisingLow, mirrored = true)
        compose.onNodeWithText("Use this quiet time").assertIsNotEnabled()
        compose.onNodeWithText("Back in range within").assertIsNotEnabled()
    }
}
