// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/**
 * Moving the focus between the treatment dialog's fields changes no field's height and not the dialog's (#181, the
 * owner 2026-11-11: "the label moving animation bug that changes the dialog height happened again"; first fixed in the
 * PoC, 2026-10-21). Checked on every frame while the focus moves, not only where it settles: a label floating into or
 * out of its field grew that field and the dialog by 2 dp for a few frames. Every label now sits small on the outline,
 * so nothing floats. Narrow and wide phones, English and Spanish, empty and with a value typed, larger text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = TestTreatmentsApp::class)
class TreatmentDialogHeightTest {
    @get:Rule val compose = createComposeRule()

    private val fields = listOf("treatment-rapid", "treatment-long", "treatment-carbs", "treatment-note")

    private fun heights(): List<Dp> =
        (fields + "treatment-dialog").map { tag ->
            with(compose.density) {
                compose
                    .onNodeWithTag(tag)
                    .fetchSemanticsNode()
                    .size.height
                    .toDp()
            }
        }

    private fun focusWalk(
        width: Dp,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SukraraTheme(dark = false, dynamic = false) {
                    Box(Modifier.width(width)) {
                        TreatmentDialogContent(
                            form = TreatmentFormState(),
                            focusCarbs = false,
                            zone = ZoneOffset.UTC,
                            now = { Instant.parse("2026-06-15T12:30:00Z") },
                            onSave = { _, _ -> },
                            onCancel = {},
                            onOpenApp = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        var first = heights()

        // every frame of the focus change, not only where it settles: a label floating up animates the height
        fun focus(
            tag: String,
            step: String,
        ) {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag(tag).performClick()
            repeat(FRAMES) {
                compose.mainClock.advanceTimeByFrame()
                assertEquals("heights (rapid, long, carbs, note, dialog) during $step at $width ×$fontScale", first, heights())
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertEquals("heights (rapid, long, carbs, note, dialog) after $step at $width ×$fontScale", first, heights())
        }
        for (t in fields + fields.reversed()) focus(t, "focus $t")
        // a value may add the quiet line (content, not focus); from there on focus changes nothing again
        compose.onNodeWithTag("treatment-rapid").performTextInput("4")
        compose.waitForIdle()
        assertEquals("amount field heights after typing", first.take(3), heights().take(3))
        first = heights()
        for (t in fields) focus(t, "focus $t with rapid filled")
    }

    private companion object {
        /** The frames a label float takes (150 ms) with room to spare. */
        const val FRAMES = 20
    }

    @Test fun narrowPhone() = focusWalk(360.dp)

    @Test fun widePhone() = focusWalk(412.dp)

    @Test
    @Config(qualifiers = "+es")
    fun narrowPhoneSpanish() = focusWalk(360.dp)

    @Test
    @Config(qualifiers = "+es")
    fun widePhoneSpanish() = focusWalk(412.dp)

    /** Larger text, still side by side (the fields stack from 1.3×). */
    @Test
    @Config(qualifiers = "+es")
    fun largerTextSpanish() = focusWalk(360.dp, fontScale = 1.3f)
}
