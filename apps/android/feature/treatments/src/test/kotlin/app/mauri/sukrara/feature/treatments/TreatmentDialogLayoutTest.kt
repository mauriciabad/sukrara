// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Where the dialog puts things (#181, the PoC's layout): *When* is a chip at the end of the title line, above the
 * fields (not a row of chips under the note); over the lock screen *Unlock* sits at the start of the action row in
 * *Open Sukrara*'s place, with no extra countdown line (the ring on *Cancel* says it).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = TestTreatmentsApp::class)
class TreatmentDialogLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-06-15T12:30:00Z")

    private fun show(
        width: Dp,
        form: TreatmentFormState = TreatmentFormState(),
        lock: LockInfo? = null,
        onOpenApp: (() -> Unit)? = {},
    ) {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                Box(Modifier.width(width)) {
                    TreatmentDialogContent(
                        form = form,
                        focusCarbs = false,
                        zone = ZoneOffset.UTC,
                        now = { now },
                        onSave = { _, _ -> },
                        onCancel = {},
                        onOpenApp = if (lock == null) onOpenApp else null,
                        lock = lock,
                        quiet = QuietRules(afterInsulinMin = 60, afterCarbsMin = 30),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.box(): Rect = fetchSemanticsNode().boundsInRoot

    private fun near(
        a: Float,
        b: Float,
    ) = abs(a - b) <= with(compose.density) { 1.dp.toPx() }

    private fun whenChipOnTheTitleLine(title: String) {
        val chip = compose.onNodeWithTag("treatment-when").box()
        val heading = compose.onNodeWithText(title).box()
        val note = compose.onNodeWithTag("treatment-note").box()
        val rapid = compose.onNodeWithTag("treatment-rapid").box()
        assertTrue("chip $chip on the title's line $heading", chip.center.y in heading.top..heading.bottom)
        assertTrue("chip $chip right of the title $heading", chip.left >= heading.right)
        assertTrue("chip $chip ends where the fields end $note", near(chip.right, note.right))
        assertTrue("chip $chip above the fields $rapid", chip.bottom <= rapid.top)
    }

    @Test fun whenIsAChipAtTheEndOfTheTitleLine() {
        show(412.dp)
        whenChipOnTheTitleLine("Add treatment")
        compose.onNodeWithText("Earlier…").assertDoesNotExist()
        compose.onNodeWithText("Now").assertExists()
        compose.onNodeWithContentDescription("When: Now").assertExists()
        // a tap opens *When?*
        compose.onNodeWithTag("treatment-when").performClick()
        compose.onNodeWithTag("treatment-time").assertExists()
        compose.onNodeWithText("When?").assertExists()
    }

    /** A picked time reads *Today 12:10*, on a narrow phone too. */
    @Test fun aPickedTimeStaysOnTheTitleLine() {
        show(360.dp, TreatmentFormState(at = now.minusSeconds(1200)))
        compose.onNodeWithText("Today 12:10", substring = true).assertExists()
        whenChipOnTheTitleLine("Add treatment")
    }

    @Test fun editingKeepsTheChipOnTheTitleLine() {
        show(
            360.dp,
            TreatmentFormState(
                rapid = "4",
                at = now.minusSeconds(86_400),
                editing = app.mauri.sukrara.core.model.TreatmentKind.RapidInsulin,
            ),
        )
        compose.onNodeWithText("Yesterday 12:30", substring = true).assertExists()
        whenChipOnTheTitleLine("Edit rapid insulin")
    }

    @Test fun openSukraraAtTheStartOfTheActionRow() {
        show(412.dp)
        val open = compose.onNodeWithText("Open Sukrara").box()
        val cancel = compose.onNodeWithText("Cancel").box()
        val save = compose.onNodeWithTag("treatment-save").box()
        val note = compose.onNodeWithTag("treatment-note").box()
        assertTrue("Open Sukrara $open at the start $note", open.left < note.left + with(compose.density) { 16.dp.toPx() })
        assertTrue("Save $save at the end $note", near(save.right, note.right))
        assertTrue("one line: $open $cancel $save", near(open.center.y, save.center.y) && near(cancel.center.y, save.center.y))
    }

    @Test fun overTheLockScreenUnlockTakesOpenSukrarasPlace() {
        show(412.dp, TreatmentFormState(carbs = "15"), lock = LockInfo(12, onUnlock = {}, spokenSeconds = 15, fraction = { 0.6f }))
        compose.onNodeWithText("Open Sukrara").assertDoesNotExist()
        compose.onNodeWithText("Closes unsaved", substring = true).assertDoesNotExist()
        val unlock = compose.onNodeWithTag("treatment-unlock").box()
        val save = compose.onNodeWithTag("treatment-save").box()
        val note = compose.onNodeWithTag("treatment-note").box()
        compose.onNodeWithContentDescription("Unlock to keep editing").assertExists()
        assertTrue("Unlock $unlock at the start $note", unlock.left < note.left + with(compose.density) { 16.dp.toPx() })
        assertTrue("Unlock on Save's line", near(unlock.center.y, save.center.y))
        // the action row comes straight after the last line (no empty countdown row in between)
        val gap = save.top - compose.onNodeWithTag("treatment-quiet").box().bottom
        assertTrue("gap $gap above the actions", gap < with(compose.density) { 24.dp.toPx() })
    }

    /** Spanish on a narrow phone: the row doesn't fit, *Desbloquear* wraps to its own start-aligned line above. */
    @Test
    @Config(qualifiers = "+es")
    fun aRowThatDoesNotFitWrapsTheStartAction() {
        show(320.dp, TreatmentFormState(carbs = "15"), lock = LockInfo(12, onUnlock = {}, spokenSeconds = 15))
        val unlock = compose.onNodeWithTag("treatment-unlock").box()
        val save = compose.onNodeWithTag("treatment-save").box()
        val note = compose.onNodeWithTag("treatment-note").box()
        assertTrue("Desbloquear $unlock above Guardar $save", unlock.bottom <= save.top + 1)
        assertTrue("Desbloquear at the start", unlock.left < note.left + with(compose.density) { 16.dp.toPx() })
        assertTrue("Guardar at the end", near(save.right, note.right))
        compose.onNodeWithText("Desbloquear").assertExists()
    }
}
