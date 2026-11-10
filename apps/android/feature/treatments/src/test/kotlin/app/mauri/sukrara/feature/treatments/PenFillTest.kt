// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.os.Bundle
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.core.model.PenFill
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.TreatmentKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * A pen read into the open Add treatment dialog: the newest injection fills its field and *When*, it is in the log
 * already (the read logged it), so *Save* never logs it again; changed by hand it is a typed dose again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestTreatmentsApp::class)
class PenFillTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestTreatmentsApp>()

    // FakeClock's now is 2026-01-15 12:00 UTC: the pen's injection was 10 min earlier
    private val injected = Instant.parse("2026-01-15T11:50:00Z").epochSecond
    private val fill = PenFill.Logged("rapid", 8.5, 1.5, injected, also = 0)

    @Before
    fun setUp() {
        app.glucose.value = GlucoseState.Initial.copy(role = Role.Wearer)
    }

    private fun saved() = runBlocking { app.repo.changedSince(Instant.EPOCH) }.map { it.treatment }

    @Test fun theReadFillsRapidAndWhenAndSaveKeepsOnlyWhatWasTyped() {
        ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Home)).use { scenario ->
            scenario.onActivity { it.penRead(fill) }
            compose.waitForIdle()
            compose.onNodeWithText("8.5").assertExists()
            compose.onNodeWithText("Logged from your pen · 1.5u primed").assertExists()
            compose.onNodeWithText(app.getString(R.string.treatment_carbs)).performTextInput("40")
            compose.waitForIdle()
            compose.onNodeWithText("40").assertExists()
            compose.onNodeWithText("Logged from your pen · 1.5u primed").assertExists()
            compose.onNodeWithTag("treatment-save").performScrollTo().performClick()
            compose.waitForIdle()
            val list = saved()
            assertEquals(listOf(TreatmentKind.Carbs), list.map { it.kind })
            // the carbs go in at the injection's time
            assertEquals(injected, list.single().at.epochSecond)
        }
    }

    @Test fun onlyThePensDoseFilledSaveJustCloses() {
        ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Home)).use { scenario ->
            scenario.onActivity { it.penRead(fill) }
            compose.waitForIdle()
            compose.onNodeWithTag("treatment-save").performScrollTo().performClick()
            compose.waitForIdle()
            assertTrue(saved().isEmpty())
        }
    }

    @Test fun aFilledDoseChangedByHandIsTypedAgain() {
        val form = TreatmentFormState(rapid = "8.5")
        val line = PenLine.Filled(fill, "8.5")
        val typed = listOf(NewEntry(TreatmentKind.RapidInsulin, 8.5), NewEntry(TreatmentKind.Carbs, 40.0))
        assertEquals(listOf(TreatmentKind.Carbs), PenFills.withoutFilled(typed, line, form).map { it.kind })
        form.rapid = "9"
        assertEquals(typed, PenFills.withoutFilled(typed, line, form))
        assertEquals(typed, PenFills.withoutFilled(typed, PenLine.Ready, form))
        // a long pen's dose fills Long-acting
        val long = PenLine.Filled(fill.copy(kind = "long"), "8.5")
        assertEquals("", PenFills.fieldText(long, form))
    }

    @Test fun aFillIsKeptThroughARotationWithoutItsUndo() {
        val b = Bundle().also { PenFills.save(PenLine.Filled(fill, "8.5"), it) }
        assertEquals(PenLine.Filled(fill, "8.5", undo = false), PenFills.restore(b))
        assertEquals(null, PenFills.restore(Bundle()))
        assertEquals("8", PenFills.number(8.0))
        assertEquals("0.5", PenFills.number(0.5))
    }

    @Test fun addTreatmentAfterAReadStartsAtItsTimeWithLongFocused() {
        val i =
            QuickTreatmentIntents
                .open(app, QuickSource.Notification)
                .putExtra(NavTargets.EXTRA_AT, injected * 1000)
                .putExtra(NavTargets.EXTRA_FOCUS_LONG, true)
        assertEquals(Instant.ofEpochSecond(injected), QuickTreatmentIntents.at(i))
        assertTrue(QuickTreatmentIntents.focusLong(i))
        ActivityScenario.launch<QuickTreatmentActivity>(i).use {
            compose.waitForIdle()
            compose.onNodeWithText(app.getString(R.string.treatment_long)).performTextReplacement("10")
            compose.onNodeWithTag("treatment-save").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(injected, saved().single().at.epochSecond)
        }
    }
}
