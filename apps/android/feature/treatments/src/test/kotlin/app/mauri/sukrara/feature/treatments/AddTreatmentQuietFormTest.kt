// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

/**
 * #181 (2026-11-14): the form says only what it needs. No example in the empty note, no pen reminder under the fields
 * (setup is where a pen is added), and *Minutes ago* is a wheel of lengths, minutes and hours in one scroll, with the
 * one-tap lengths as compact chips ("5m" … "1h") that TalkBack reads in full words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = TestTreatmentsApp::class)
class AddTreatmentQuietFormTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-06-15T12:30:00Z")

    private fun show(form: TreatmentFormState = TreatmentFormState()) {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                TreatmentDialogContent(
                    form = form,
                    focus = TreatmentKind.RapidInsulin,
                    zone = ZoneOffset.UTC,
                    now = { now },
                    onSave = { _, _ -> },
                    onCancel = {},
                    penLine = { PenFillLine(PenLine.Ready, onUndo = {}) },
                )
            }
        }
    }

    @Test fun theEmptyNoteHasNoExample() {
        show()
        compose.onNodeWithText("Pizza, exercise, sick…").assertDoesNotExist()
    }

    @Test fun noPenReminderUnderTheFields() {
        show()
        compose.onNodeWithText("Injected? Hold your pen to the phone.").assertDoesNotExist()
    }

    @Test fun minutesAgoIsAWheelWithCompactChips() {
        val form = TreatmentFormState(rapid = "2")
        show(form)
        compose.onNodeWithTag("treatment-when").performClick()
        compose.onNodeWithTag("when-ago").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("when-minutes").assertDoesNotExist()
        compose.onNodeWithTag("length-wheel").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        for ((shown, spoken) in listOf("5m" to "5 minutes", "15m" to "15 minutes", "30m" to "30 minutes", "1h" to "1 hour")) {
            compose.onNodeWithText(shown).assertExists()
            compose.onNodeWithContentDescription(spoken).assertExists()
        }
        compose.onNodeWithText("1h").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose
            .onNodeWithTag("length-wheel")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 hour"))
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertEquals(60, form.ago)
    }

    @Test fun talkBackMovesTheWheelOneLengthAtATime() {
        val form = TreatmentFormState(rapid = "2", ago = 16)
        show(form)
        compose.onNodeWithTag("treatment-when").performClick()
        compose.waitForIdle()
        compose
            .onNodeWithTag("length-wheel")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "16 minutes"))
        // the next length: 17 min
        compose.onNodeWithTag("length-wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(16f) }
        compose.waitForIdle()
        compose
            .onNodeWithTag("length-wheel")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "17 minutes"))
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertEquals(17, form.ago)
    }

    @Test fun theWheelReachesHoursInTheSameScroll() {
        val wheel = TreatmentForm.AGO_WHEEL
        assertEquals(1, wheel.first())
        assertEquals((1..60).toList(), wheel.take(60))
        assertEquals(true, 90 in wheel && 180 in wheel && 720 in wheel)
        assertEquals(true, wheel.last() < 24 * 60)
        assertEquals(wheel.sorted(), wheel)
    }
}

/** The same in Spanish: "min" stays (RAE: "m" is the metre), TalkBack hears "minutos" / "hora". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "es-w411dp-h891dp", application = TestTreatmentsApp::class)
class AddTreatmentQuietFormSpanishTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chipsSayMinAndReadInFullWords() {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                TreatmentDialogContent(
                    form = TreatmentFormState(ago = 5),
                    focus = TreatmentKind.RapidInsulin,
                    zone = ZoneOffset.UTC,
                    now = { Instant.parse("2026-06-15T12:30:00Z") },
                    onSave = { _, _ -> },
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithTag("treatment-when").performClick()
        compose.waitForIdle()
        for ((shown, spoken) in listOf("5 min" to "5 minutos", "15 min" to "15 minutos", "1 h" to "1 hora")) {
            compose.onNodeWithText(shown).assertExists()
            compose.onNodeWithContentDescription(spoken).assertExists()
        }
        compose.onNodeWithText("Pizza, ejercicio, enfermo…").assertDoesNotExist()
    }
}
