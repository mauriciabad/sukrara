// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers of every screen move (docs/10-showcase.md "Motion and back", the PoC's ScreenHost): a page slides like
 * the Settings app's activities, setup fades through, the back gesture previews the page under it.
 */
class ScreenMovesTest {
    private val space = MoveSpace(width = 1080f, height = 2400f, slide = 252f, edge = 21f, dark = false)
    private val eps = 0.01f

    private fun frame(
        move: ScreenMove,
        motion: ScreenMotion,
        t: Float,
        progress: Float = 0f,
        dy: Float = 0f,
        dark: Boolean = false,
    ) = ScreenMoves.frame(move, motion, t, space.copy(dark = dark), progress, dy)

    /** [look] within a pixel / a hundredth of [expected] (a page at rest may sit at -0 px). */
    private fun assertLook(
        expected: PageLook,
        look: PageLook,
    ) {
        assertEquals(expected.translationX, look.translationX, eps)
        assertEquals(expected.translationY, look.translationY, eps)
        assertEquals(expected.scale, look.scale, eps)
        assertEquals(expected.alpha, look.alpha, eps)
        assertEquals(expected.rounded, look.rounded, eps)
        assertEquals(expected.scrim, look.scrim, eps)
    }

    private fun assertRest(look: PageLook) = assertLook(PageLook.Rest, look)

    @Test fun `a page opens like a settings activity`() {
        val start = frame(ScreenMove.Open, ScreenMotion.Page, 0f)
        // the new page starts one slide to the end, invisible; the old one at rest
        assertEquals(252f, start.top.translationX, eps)
        assertEquals(0f, start.top.alpha, eps)
        assertRest(start.bottom)
        // its fade: 83 ms after 50 ms of the 450 ms
        assertEquals(0f, frame(ScreenMove.Open, ScreenMotion.Page, 50f / 450).top.alpha, eps)
        assertEquals(1f, frame(ScreenMove.Open, ScreenMotion.Page, 133f / 450).top.alpha, eps)
        val end = frame(ScreenMove.Open, ScreenMotion.Page, 1f)
        assertEquals(0f, end.top.translationX, eps)
        assertEquals(1f, end.top.alpha, eps)
        // the page under it moves one slide to the start, and stays opaque
        assertEquals(-252f, end.bottom.translationX, eps)
        assertEquals(1f, end.bottom.alpha, eps)
        // fast-out-extra-slow-in: most of the way after a third of the time
        assertTrue(frame(ScreenMove.Open, ScreenMotion.Page, 0.33f).top.translationX < 252f * 0.25f)
    }

    @Test fun `a page closes the reverse way, fading out on top`() {
        val start = frame(ScreenMove.Back, ScreenMotion.Page, 0f)
        assertEquals(-252f, start.bottom.translationX, eps)
        assertRest(start.top)
        // the closing page fades out over 83 ms after 35 ms
        assertEquals(1f, frame(ScreenMove.Back, ScreenMotion.Page, 35f / 450).top.alpha, eps)
        assertEquals(0f, frame(ScreenMove.Back, ScreenMotion.Page, 118f / 450).top.alpha, eps)
        val end = frame(ScreenMove.Back, ScreenMotion.Page, 1f)
        assertEquals(0f, end.bottom.translationX, eps)
        assertEquals(252f, end.top.translationX, eps)
        assertEquals(ScreenMoves.PAGE_MS, ScreenMoves.durationMs(ScreenMove.Open, ScreenMotion.Page))
        assertEquals(ScreenMoves.PAGE_MS, ScreenMoves.durationMs(ScreenMove.Back, ScreenMotion.Page))
    }

    @Test fun `setup fades through both ways, without sliding`() {
        for (move in listOf(ScreenMove.Open, ScreenMove.Back)) {
            assertEquals(300, ScreenMoves.durationMs(move, ScreenMotion.FadeThrough))
            for (t in listOf(0f, 0.2f, 0.5f, 1f)) {
                val f = frame(move, ScreenMotion.FadeThrough, t)
                listOf(f.top, f.bottom).forEach {
                    assertEquals(0f, it.translationX, eps)
                    assertEquals(0f, it.translationY, eps)
                }
            }
            // the leaving screen is gone after 90 ms, before the coming one starts from 92 %
            val out = frame(move, ScreenMotion.FadeThrough, 90f / 300)
            assertEquals(0f, out.bottom.alpha, eps)
            assertEquals(0f, out.top.alpha, eps)
            val coming = { f: MoveFrame -> if (move == ScreenMove.Open) f.top else f.bottom }
            assertEquals(0.92f, coming(out).scale, eps)
            assertRest(coming(frame(move, ScreenMotion.FadeThrough, 1f)))
        }
    }

    @Test fun `the back gesture shrinks the page over the one under it`() {
        val held = frame(ScreenMove.Gesture, ScreenMotion.Page, 0f, progress = 1f)
        assertEquals(0.9f, held.top.scale, eps)
        // the scaled page's end edge sits 8 dp (21 px here) from the screen's
        val endEdge = space.width / 2 + space.width * 0.9f / 2 + held.top.translationX
        assertEquals(space.width - space.edge, endEdge, eps)
        assertEquals(1f, held.top.rounded, eps)
        // the page under it waits one slide to the start at the same scale, under a 20 % scrim (80 % dark)
        assertEquals(-252f, held.bottom.translationX, eps)
        assertEquals(0.9f, held.bottom.scale, eps)
        assertEquals(0.2f, held.bottom.scrim, eps)
        assertEquals(0.8f, frame(ScreenMove.Gesture, ScreenMotion.Page, 0f, progress = 1f, dark = true).bottom.scrim, eps)
        // the finger moving down takes both pages down, never past the margin
        val down = frame(ScreenMove.Gesture, ScreenMotion.Page, 0f, progress = 1f, dy = 5000f)
        assertEquals(space.height * 0.05f - space.edge, down.top.translationY, eps)
        assertEquals(down.top.translationY, down.bottom.translationY, eps)
        // not moved yet: nothing clipped, no scrim
        val still = frame(ScreenMove.Gesture, ScreenMotion.Page, 0f, progress = 0f)
        assertRest(still.top)
        assertEquals(0f, still.bottom.scrim, eps)
    }

    @Test fun `releasing the gesture finishes the back from where the pages are`() {
        val start = frame(ScreenMove.Commit, ScreenMotion.Page, 0f, progress = 0.5f)
        assertEquals(frame(ScreenMove.Gesture, ScreenMotion.Page, 0f, progress = 0.5f), start)
        // the page leaves to the end, gone by 20 % of the way
        val gone = frame(ScreenMove.Commit, ScreenMotion.Page, 0.2f, progress = 0.5f)
        assertEquals(0f, gone.top.alpha, eps)
        val end = frame(ScreenMove.Commit, ScreenMotion.Page, 1f, progress = 0.5f, dy = 300f)
        assertRest(end.bottom)
        assertEquals(ScreenMoves.PAGE_MS, ScreenMoves.durationMs(ScreenMove.Commit, ScreenMotion.FadeThrough))
    }

    @Test fun `the gesture preview is the same for every kind of screen`() {
        for (t in listOf(0f, 0.5f, 1f)) {
            assertEquals(
                frame(ScreenMove.Gesture, ScreenMotion.Page, t, progress = 0.7f, dy = -80f),
                frame(ScreenMove.Gesture, ScreenMotion.FadeThrough, t, progress = 0.7f, dy = -80f),
            )
        }
    }

    @Test fun `every move ends with both pages at rest or gone`() {
        for (motion in ScreenMotion.entries) {
            assertRest(frame(ScreenMove.Open, motion, 1f).top)
            assertRest(frame(ScreenMove.Back, motion, 1f).bottom)
            assertRest(frame(ScreenMove.Open, motion, 0f).bottom)
            assertRest(frame(ScreenMove.Back, motion, 0f).top)
        }
    }

    @Test fun `the gesture curve follows the system's`() {
        assertEquals(0f, ScreenMoves.gestureProgress(0f), eps)
        assertEquals(1f, ScreenMoves.gestureProgress(1f), eps)
        assertTrue(ScreenMoves.gestureProgress(0.3f) > 0.3f)
        assertEquals(1f, ScreenMoves.gestureProgress(1.4f), eps)
    }
}
