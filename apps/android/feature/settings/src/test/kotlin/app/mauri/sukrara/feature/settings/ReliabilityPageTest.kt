// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Settings › Alert reliability (#65): every setup item with its state, *Fix* on each missing one (named for TalkBack)
 * opening that item's screen, the failed alert step only while it lasts (with *Copy report*), and the page updating
 * when the readiness changes (back from Android's screen).
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

    @Test
    fun `ready - every setup item on, no Fix, no failed step`() {
        readiness = AlertReadiness(emptySet(), unlockedSound = UnlockedSound.Off)
        show()
        compose.onNodeWithText(context.getString(R.string.st_reliability_ready)).assertIsDisplayed()
        for (r in AlertRequirement.entries.filter { it.inSetup }) compose.onNodeWithText(name(r)).performScrollTo()
        compose.onNodeWithText(name(AlertRequirement.AlertDelivery)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.st_fix)).assertDoesNotExist()
    }

    @Test
    fun `a missing item says so and Fix opens its screen, then the page follows the new state`() {
        readiness = AlertReadiness(setOf(AlertRequirement.FullScreenIntent, AlertRequirement.BatteryExemption))
        show()
        compose.onNodeWithText(context.getString(R.string.st_reliability_ready)).assertDoesNotExist()
        assertEquals(2, compose.onAllNodesWithText(context.getString(R.string.st_req_missing)).fetchSemanticsNodes().size)
        val battery = name(AlertRequirement.BatteryExemption)
        compose.onNodeWithContentDescription(context.getString(R.string.st_fix_item, battery)).performScrollTo().performClick()
        assertEquals(listOf(AlertRequirement.BatteryExemption), fixed)

        // back from Android's screen with it granted: one item left
        readiness = AlertReadiness(setOf(AlertRequirement.FullScreenIntent))
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText(context.getString(R.string.st_req_missing)).fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription(context.getString(R.string.st_fix_item, battery)).assertDoesNotExist()
    }

    @Test
    fun `a failed alert step is listed while it lasts, with Copy report instead of Fix`() {
        readiness = AlertReadiness(setOf(AlertRequirement.AlertDelivery), failedSteps = setOf("Alarm"))
        show()
        compose.onNodeWithText(name(AlertRequirement.AlertDelivery)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.st_req_failed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(app.mauri.sukrara.core.designsystem.R.string.ds_copy_report)).assertIsDisplayed()
        assertTrue(fixed.isEmpty())
    }
}
