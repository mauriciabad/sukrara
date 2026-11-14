// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.home.Samples.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's treatment rows (#207): no Delete button; a swipe either way, the long-press menu and TalkBack's *Delete*
 * action delete (home's delete, so its *Undo* follows); a row this phone may not change has none of them; the pen
 * icon and the glucose then are spoken; *See all* opens the Treatments page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h2000dp")
class TreatmentRowTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val rapid = Samples.treatment("r", TreatmentKind.RapidInsulin, 4.5, 40)
    private val carbs = Samples.treatment("c", TreatmentKind.Carbs, 30.0, 45)
    private val deleted = mutableListOf<Treatment>()
    private val asked = mutableListOf<HomeAction>()

    private fun show(
        readOnly: Set<String> = emptySet(),
        fromPen: Set<String> = emptySet(),
    ) {
        val glucose = Samples.wearer()
        val look = homeLook(glucose, NOW, GlucoseUnit.MGDL)
        val state =
            HomeUiState(
                glucose = glucose,
                now = NOW,
                look = look,
                reason = NoDataReason.of(glucose, look),
                banner = null,
                firstProblem = null,
                nextSetupStep = null,
                dataProblems = emptyList(),
                treatments = listOf(rapid, carbs),
                undo = null,
                readOnly = readOnly,
                fromPen = fromPen,
            )
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                HomeContent(state, HomeCallbacks(onAction = { asked += it }, onDelete = { deleted += it }))
            }
        }
    }

    private fun row(label: String): SemanticsNodeInteraction = compose.onNodeWithText(label).performScrollTo()

    private fun deleteAction(label: String) =
        row(label).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull {
            it.label == context.getString(R.string.home_treatment_action_delete)
        }

    @Test fun `TalkBack's Delete action deletes the entry, and there is no Delete button`() {
        show()
        compose.onNodeWithContentDescription("Delete", substring = true).assertDoesNotExist()
        val action = deleteAction("4.5u rapid insulin")!!
        compose.runOnIdle { action.action() }
        assertEquals(listOf(rapid), deleted)
    }

    @Test fun `a swipe either way deletes the entry`() {
        show()
        row("4.5u rapid insulin").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        row("30g carbs").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf(rapid, carbs), deleted)
    }

    @Test fun `the long press menu edits or deletes`() {
        show()
        row("30g carbs").performTouchInput { longClick() }
        compose.onNodeWithText(context.getString(R.string.home_treatment_menu_edit)).performClick()
        assertEquals(listOf<HomeAction>(HomeAction.EditTreatment("c")), asked)
        row("30g carbs").performTouchInput { longClick() }
        compose.onNodeWithText(context.getString(R.string.home_treatment_action_delete)).performClick()
        assertEquals(listOf(carbs), deleted)
    }

    @Test fun `a row this phone may not change has no delete at all`() {
        show(readOnly = setOf("r", "c"))
        assertNull(deleteAction("4.5u rapid insulin"))
        row("4.5u rapid insulin").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue(deleted.isEmpty())
    }

    @Test fun `the pen and the glucose then are spoken, See all opens the Treatments page`() {
        show(fromPen = setOf("r"))
        compose.onNodeWithContentDescription(context.getString(R.string.home_treatment_pen_source), useUnmergedTree = true).assertExists()
        compose.onAllNodesWithContentDescription("glucose then", substring = true, useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText(context.getString(R.string.home_treatments_see_all)).performScrollTo().performClick()
        assertEquals(listOf<HomeAction>(HomeAction.OpenTreatments), asked)
    }
}
