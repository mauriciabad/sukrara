// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.sharing.FirstThatFits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The owner, 2026-11-15: home's header said "1 followe". A line in a narrow place is swapped whole for a shorter one,
 * never wrapped inside a word or cut; where not even the shortest fits it is drawn smaller, still whole.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirstThatFitsTest {
    @get:Rule val compose = createComposeRule()

    private fun option(
        tag: String,
        width: Dp,
    ): @Composable () -> Unit = { Box(Modifier.size(width, 10.dp).testTag(tag)) }

    private fun placed(tag: String) =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .layoutInfo.isPlaced

    private fun show(room: Dp) {
        compose.setContent {
            Box(Modifier.width(room)) {
                FirstThatFits(listOf(option("words", 120.dp), option("icons", 60.dp)), Modifier.testTag("line"))
            }
        }
    }

    @Test fun theWordsWhereTheyFit() {
        show(200.dp)
        assertTrue(placed("words"))
        assertFalse(placed("icons"))
        assertEquals(
            120f,
            compose
                .onNodeWithTag("line")
                .fetchSemanticsNode()
                .size.width / compose.density.density,
            0.5f,
        )
    }

    @Test fun theShorterLineWhereTheyDont() {
        show(100.dp)
        assertFalse(placed("words"))
        assertTrue(placed("icons"))
    }

    @Test fun theShortestDrawnSmallerNeverCutWhereNothingFits() {
        show(40.dp)
        assertTrue(placed("icons"))
        val line = compose.onNodeWithTag("line").fetchSemanticsNode().boundsInRoot
        val icons = compose.onNodeWithTag("icons").fetchSemanticsNode().boundsInRoot
        assertTrue("drawn inside its room: $icons in $line", icons.right <= line.right + 0.5f)
    }
}
