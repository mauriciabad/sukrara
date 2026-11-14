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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.PenDoseInfo
import app.mauri.sukrara.core.model.PenEntryInfo
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
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * Home's treatment rows (#207): no Delete button and no long-press menu; a swipe towards the start and TalkBack's
 * *Delete* action delete (home's delete, so its *Undo* follows); a row this phone may not change has none of them; the pen
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
            it.label == context.getString(DsR.string.ds_delete)
        }

    @Test fun `TalkBack's Delete action deletes the entry, and there is no Delete button`() {
        show()
        compose.onNodeWithContentDescription("Delete", substring = true).assertDoesNotExist()
        val action = deleteAction("4.5u rapid insulin")!!
        compose.runOnIdle { action.action() }
        assertEquals(listOf(rapid), deleted)
    }

    @Test fun `a swipe towards the start deletes the entry, the other way does nothing`() {
        show()
        row("30g carbs").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue(deleted.isEmpty())
        row("4.5u rapid insulin").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf(rapid), deleted)
    }

    @Test fun `a long press opens no menu`() {
        show()
        row("30g carbs").performTouchInput { longClick() }
        compose.onNodeWithText(context.getString(DsR.string.ds_delete)).assertDoesNotExist()
        assertTrue(deleted.isEmpty())
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

    private fun state(undo: Treatment? = null): HomeUiState {
        val glucose = Samples.wearer()
        val look = homeLook(glucose, NOW, GlucoseUnit.MGDL)
        return HomeUiState(
            glucose = glucose,
            now = NOW,
            look = look,
            reason = NoDataReason.of(glucose, look),
            banner = null,
            firstProblem = null,
            nextSetupStep = null,
            dataProblems = emptyList(),
            treatments = listOf(carbs),
            undo = undo,
        )
    }

    @Test fun `after a delete the snackbar names the entry and Undo brings it back, on home`() {
        var undone = 0
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) { HomeContent(state(undo = rapid), HomeCallbacks(onUndo = { undone++ })) }
        }
        compose.onNodeWithText("Deleted: 4.5u rapid insulin").assertExists()
        compose.onNodeWithText(context.getString(R.string.home_undo)).performClick()
        compose.waitForIdle()
        assertEquals(1, undone)
    }

    @Test fun `after a delete the snackbar names the entry and Undo brings it back, on the Treatments page`() {
        var undone = 0
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                TreatmentHistoryContent(state(undo = rapid), HomeCallbacks(onUndo = { undone++ }), onBack = {})
            }
        }
        compose.onNodeWithText("Deleted: 4.5u rapid insulin").assertExists()
        compose.onNodeWithText(context.getString(R.string.home_undo)).performClick()
        compose.waitForIdle()
        assertEquals(1, undone)
    }

    @Test fun `the undo snackbar stays 10 s, not 4`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) { HomeContent(state(undo = rapid), HomeCallbacks()) }
        }
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithText("Deleted: 4.5u rapid insulin").assertExists()
    }

    @Test fun `a pen entry's note is edited in its sheet`() {
        val pen = rapid
        val entry =
            PenEntryInfo(
                id = pen.id,
                serial = "AB12",
                kind = "rapid",
                units = pen.amount,
                primed = 1.0,
                t = pen.at.epochSecond,
                note = "",
                doses =
                    listOf(
                        PenDoseInfo("0", pen.at.epochSecond - 60, 1.0, primed = true, incomplete = false),
                        PenDoseInfo("1", pen.at.epochSecond, pen.amount, primed = false, incomplete = false),
                    ),
                replaced = null,
            )
        val notes = mutableListOf<String>()
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                HomeContent(
                    state(),
                    HomeCallbacks(pen = PenCallbacks(note = { notes += it })),
                    pen = PenSheet(entry, pen, editable = true, deletable = true),
                )
            }
        }
        compose.onNodeWithTag("pen-note").performTextInput("Pizza")
        compose.onNodeWithText(context.getString(DsR.string.ds_pen_save)).performClick()
        assertEquals(listOf("Pizza"), notes)
    }
}
