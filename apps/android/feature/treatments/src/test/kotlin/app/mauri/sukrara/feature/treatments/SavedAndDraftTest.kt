// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/** The snackbar after a quick Save with *Undo*, and a kept draft in the form with *Discard* (#100). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi", application = TestTreatmentsApp::class)
class SavedAndDraftTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-06-15T12:30:00Z")

    @Test fun theEntriesInWords() {
        val res = ApplicationProvider.getApplicationContext<TestTreatmentsApp>().resources
        val entries = TreatmentForm.entries("4.5", "", "30", "Pasta")!!
        assertEquals("4.5u rapid insulin · 30g carbs · Pasta", EntryWords.of(res, entries))
        assertEquals("12u long insulin · after dinner", EntryWords.one(res, NewEntry(TreatmentKind.LongInsulin, 12.0, "after dinner")))
    }

    @Test fun undoOnTheSnackbarTakesTheSaveBack() {
        var undone = 0
        var c by mutableStateOf(Confirmation("Saved: 4.5u rapid insulin · 30g carbs", Saved { undone++ }))
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                SavedSnackbar(c, onUndo = {
                    c.undo?.undo()
                    c = Confirmation("Undone", null)
                })
            }
        }
        compose.onNodeWithText("Saved: 4.5u rapid insulin · 30g carbs").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/treatment-saved-snackbar-light.png")
        compose.onNodeWithText("Undo").performClick()
        assertEquals(1, undone)
        compose.waitForIdle()
        compose.onNodeWithText("Undone").assertExists()
        // undone once: no second Undo
        compose.onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test fun aKeptDraftSaysWhenAndCanBeDiscarded() {
        val form = TreatmentDraft("4.5", "", "30", "", atMs = now.minusSeconds(600).toEpochMilli(), keptMs = now.toEpochMilli()).form()
        var draft by mutableStateOf<DraftLine?>(null)
        draft =
            DraftLine(now) {
                form.fill(TreatmentFormState())
                draft = null
            }
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                TreatmentDialogContent(
                    form = form,
                    focusCarbs = false,
                    zone = ZoneOffset.UTC,
                    now = { now },
                    onSave = { _, _ -> },
                    onCancel = {},
                    draft = draft,
                )
            }
        }
        compose.onNodeWithText("Kept at 12:30", substring = true).assertExists()
        // the kept time, not now
        compose.onNodeWithTag("treatment-when").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/treatment-draft-light.png")
        compose.onNodeWithText("Discard").performClick()
        compose.waitForIdle()
        assertEquals("", form.rapid)
        assertEquals(null, form.at)
        compose.onNodeWithTag("treatment-draft").assertDoesNotExist()
    }
}
