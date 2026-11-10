// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.mauri.sukrara.core.model.PenNearEntry
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
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

/** The treatment dialog editing one entry (#75), and *Already logged by your pen?*. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi", application = TestTreatmentsApp::class)
class EditDialogTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-06-15T12:30:00Z")

    @Test fun editingShowsOnlyThatEntrysAmountItsNoteAndItsTime() {
        val t =
            Treatment(
                "a",
                TreatmentKind.Carbs,
                30.0,
                now.minusSeconds(1500),
                "toast",
                now.minusSeconds(1500),
                origin = TreatmentOrigin.Phone,
            )
        val form = TreatmentFormState.editing(t)
        val saved = mutableListOf<Pair<List<NewEntry>, Instant?>>()
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                TreatmentDialogContent(
                    form = form,
                    focusCarbs = false,
                    zone = ZoneOffset.UTC,
                    now = { now },
                    onSave = { e, at -> saved += e to at },
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithText("Edit carbs").assertExists()
        compose.onNodeWithText("Rapid").assertDoesNotExist()
        compose.onNodeWithText("Long").assertDoesNotExist()
        compose.onNodeWithText("30").assertExists()
        compose.onNodeWithText("toast").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/treatment-edit-carbs-light.png")
        compose.onNodeWithTag("treatment-save").performClick()
        assertEquals(listOf(NewEntry(TreatmentKind.Carbs, 30.0, "toast")) to t.at, saved.single())
    }

    @Test fun aNoteHasNoAmount() {
        val t = Treatment("n", TreatmentKind.Note, 0.0, now, "walk", now, origin = TreatmentOrigin.Phone)
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                TreatmentDialogContent(
                    form = TreatmentFormState.editing(t),
                    focusCarbs = false,
                    zone = ZoneOffset.UTC,
                    now = { now },
                    onSave = { _, _ -> },
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithText("Edit note").assertExists()
        compose.onNodeWithText("Carbs").assertDoesNotExist()
        compose.onNodeWithText("walk").assertExists()
    }

    /** A typed dose a pen already logged: *Cancel* goes back to the form, *Log anyway* saves. */
    @Test fun penDuplicateQuestion() {
        val calls = mutableListOf<String>()
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                PenDuplicateDialog(PenNearEntry("p1", 8.5, now.epochSecond), onCancel = { calls += "cancel" }, onLog = { calls += "log" })
            }
        }
        compose.onNodeWithText("Already logged by your pen?").assertExists()
        compose.onNodeWithText("Your pen logged 8.5u", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Log anyway").performClick()
        assertEquals(listOf("cancel", "log"), calls)
    }
}
