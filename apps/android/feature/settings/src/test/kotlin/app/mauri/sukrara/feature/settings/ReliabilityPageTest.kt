// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.designsystem.rows.nameRes
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Alert reliability (#65): every setup item as an SPA row with its state; a missing one opens that item's
 * screen (TalkBack: "double tap to fix"), the failed alert step only while it lasts (a tap copies the report), and the
 * page updating when the readiness changes (back from Android's screen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp")
class ReliabilityPageTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun name(r: AlertRequirement) = context.getString(r.nameRes())

    private var readiness by mutableStateOf(AlertReadiness.Ready)
    private val fixed = mutableListOf<AlertRequirement>()

    private fun show() =
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) { ReliabilityContent(readiness, { fixed += it }) {} }
        }

    /** The rows whose summary starts with [state] ("Missing · …"). */
    private fun count(state: Int) =
        compose.onAllNodesWithText(context.getString(state) + " · ", substring = true).fetchSemanticsNodes().size

    /** What a tap on [r]'s row says it does, for TalkBack. */
    private fun clickLabel(r: AlertRequirement) =
        compose
            .onNode(hasText(name(r)) and hasClickAction())
            .performScrollTo()
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .label

    @Test
    fun `ready - every setup item on, nothing to fix, no failed step`() {
        readiness = AlertReadiness(emptySet(), unlockedSound = UnlockedSound.Heard)
        show()
        compose.onNodeWithText(context.getString(R.string.st_reliability_ready)).assertIsDisplayed()
        for (r in AlertRequirement.entries.filter { it.inSetup }) compose.onNodeWithText(name(r)).performScrollTo()
        compose.onNodeWithText(name(AlertRequirement.AlertDelivery)).assertDoesNotExist()
        assertEquals(0, count(R.string.st_req_missing))
        assertEquals(0, compose.onAllNodes(hasClickLabel(context.getString(R.string.st_fix))).fetchSemanticsNodes().size)
    }

    @Test
    fun `a missing item says so and its row opens its screen, then the page follows the new state`() {
        readiness = AlertReadiness(setOf(AlertRequirement.FullScreenIntent, AlertRequirement.BatteryExemption))
        show()
        compose.onNodeWithText(context.getString(R.string.st_reliability_ready)).assertDoesNotExist()
        assertEquals(2, count(R.string.st_req_missing))
        assertEquals(context.getString(R.string.st_fix), clickLabel(AlertRequirement.BatteryExemption))
        compose.onNode(hasText(name(AlertRequirement.BatteryExemption)) and hasClickAction()).performClick()
        assertEquals(listOf(AlertRequirement.BatteryExemption), fixed)

        // back from Android's screen with it granted: one item left, the granted one no longer opens anything
        readiness = AlertReadiness(setOf(AlertRequirement.FullScreenIntent))
        compose.waitForIdle()
        assertEquals(1, count(R.string.st_req_missing))
        compose.onNode(hasText(name(AlertRequirement.BatteryExemption)) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `a failed alert step is listed while it lasts, its row copies the report instead of fixing`() {
        readiness = AlertReadiness(setOf(AlertRequirement.AlertDelivery), failedSteps = setOf("Alarm"))
        show()
        compose.onNodeWithText(name(AlertRequirement.AlertDelivery)).performScrollTo().assertIsDisplayed()
        assertEquals(1, count(R.string.st_req_failed))
        assertEquals(
            context.getString(app.mauri.sukrara.core.designsystem.R.string.ds_copy_report),
            clickLabel(AlertRequirement.AlertDelivery),
        )
        assertTrue(fixed.isEmpty())
    }

    @Test
    fun `an alert that couldn't be shown says so first, and its row copies the report`() {
        readiness = AlertReadiness(setOf(AlertRequirement.AlertShown), failedSteps = setOf("Show:level NotFoundException"))
        show()
        compose.onNodeWithText(context.getString(R.string.st_reliability_ready)).assertDoesNotExist()
        compose.onNodeWithText("An alert couldn't be shown").performScrollTo().assertIsDisplayed()
        assertEquals(1, count(R.string.st_req_failed))
        assertEquals(
            context.getString(app.mauri.sukrara.core.designsystem.R.string.ds_copy_report),
            clickLabel(AlertRequirement.AlertShown),
        )
        compose.onNode(hasText(name(AlertRequirement.AlertShown)) and hasClickAction()).performClick()
        assertTrue("copies the report, nothing to fix", fixed.isEmpty())
    }
}

private fun hasClickLabel(label: String) =
    SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
