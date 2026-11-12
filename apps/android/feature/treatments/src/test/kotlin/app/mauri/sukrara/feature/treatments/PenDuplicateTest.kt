// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.app.KeyguardManager
import android.os.Bundle
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.PenNearEntry
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.TreatmentKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * *Already logged by your pen?* lives in the activity's saved state: a rotation asks it again (never drops it) and,
 * over the lock screen, the countdown stands still while it is open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestTreatmentsApp::class)
class PenDuplicateTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestTreatmentsApp>()
    private val title get() = app.getString(R.string.treatment_pen_duplicate_title)

    @Before
    fun setUp() {
        app.glucose.value = GlucoseState.Initial.copy(role = Role.Wearer)
        app.penNear = PenNearEntry("pen-1", 8.5, Instant.parse("2026-01-15T11:58:00Z").epochSecond)
    }

    @After
    fun tearDown() {
        app.penNear = null
        app.penNearKind = "rapid"
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
    }

    private fun typeAndSave(
        field: Int = R.string.treatment_rapid,
        amount: String = "8",
    ) {
        compose.onNodeWithText(app.getString(field)).performTextInput(amount)
        compose.onNodeWithTag("treatment-save").performClick()
        settle()
    }

    /** A few frames (the lock-screen countdown runs every frame: an auto-advancing test clock would run it out). */
    private fun settle() {
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    @Test fun theQuestionSurvivesARotation() {
        ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Home)).use { scenario ->
            typeAndSave()
            compose.onNodeWithText(title).assertExists()
            scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText(title).assertExists()
            // Cancel goes back to the form with what was typed
            compose.onAllNodesWithText(app.getString(R.string.treatment_cancel))[1].performClick()
            compose.waitForIdle()
            compose.onNodeWithText(title).assertDoesNotExist()
            compose.onNodeWithText("8").assertExists()
        }
    }

    @Test fun overTheLockScreenTheCountdownWaitsForTheAnswer() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Notification)).use { scenario ->
            settle()
            scenario.onActivity { assertFalse(it.countdownPaused) }
            typeAndSave()
            scenario.onActivity {
                assertFalse("the dialog closed before the question", it.isFinishing)
                assertTrue(it.countdownPaused)
            }
            scenario.recreate()
            settle()
            scenario.onActivity { assertTrue(it.countdownPaused) }
            compose.onAllNodesWithText(app.getString(R.string.treatment_cancel))[1].performClick()
            settle()
            scenario.onActivity { assertFalse(it.countdownPaused) }
        }
    }

    /**
     * The owner's 0.14.3 report: 5.5u rapid from the smart pen, then 15u long-acting from a plain pen typed at the same
     * time. Only a pen entry of the typed insulin asks; long-acting is never the rapid pen's injection (and back).
     */
    @Test fun onlyAPenEntryOfTheTypedInsulinAsks() =
        runBlocking {
            val m = app.treatments
            val long = NewEntry(TreatmentKind.LongInsulin, 15.0)
            val rapid = NewEntry(TreatmentKind.RapidInsulin, 5.5)
            app.penNearKind = "rapid"
            assertNull(m.penDuplicate(listOf(long), null))
            assertNull(m.penDuplicate(listOf(long, NewEntry(TreatmentKind.Carbs, 30.0)), null))
            assertEquals(rapid, m.penDuplicate(listOf(long, rapid), null)?.first)
            app.penNearKind = "long"
            assertNull(m.penDuplicate(listOf(rapid), null))
            assertEquals(long, m.penDuplicate(listOf(rapid, long), null)?.first)
        }

    @Test fun longActingTypedAfterARapidPenReadIsSavedWithoutAsking() {
        ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Home)).use { scenario ->
            typeAndSave(R.string.treatment_long, "15")
            compose.onNodeWithText(title).assertDoesNotExist()
            val saved = runBlocking { app.repo.changedSince(Instant.EPOCH) }.map { it.treatment }
            assertEquals(listOf(TreatmentKind.LongInsulin to 15.0), saved.map { it.kind to it.amount })
            scenario.onActivity { assertFalse(it.countdownPaused) }
        }
    }

    /** The same over the lock screen: saved at once, the countdown never stood still for a question. */
    @Test fun overTheLockScreenLongActingIsSavedWithoutAsking() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Notification)).use { scenario ->
            settle()
            typeAndSave(R.string.treatment_long, "15")
            compose.onNodeWithText(title).assertDoesNotExist()
            scenario.onActivity { assertFalse(it.countdownPaused) }
            val saved = runBlocking { app.repo.changedSince(Instant.EPOCH) }.map { it.treatment }
            assertEquals(listOf(TreatmentKind.LongInsulin to 15.0), saved.map { it.kind to it.amount })
        }
    }

    @Test fun theQuestionRoundTripsThroughTheSavedState() {
        val d =
            PenDuplicate(
                listOf(NewEntry(TreatmentKind.RapidInsulin, 8.0), NewEntry(TreatmentKind.Note, 0.0, "lunch")),
                PenNearEntry("pen-1", 8.5, 1000),
                Instant.ofEpochMilli(5000),
            )
        val b = Bundle().also(d::save)
        assertEquals(d, PenDuplicate.restore(b))
        assertEquals(d.copy(at = null), PenDuplicate.restore(Bundle().also(d.copy(at = null)::save)))
        assertNull(PenDuplicate.restore(Bundle()))
    }
}
