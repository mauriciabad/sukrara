// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.sign

/**
 * How a screen comes and goes, by the kind of screen it is (the PoC's motion, docs/10-showcase.md "Motion and back").
 * [ScreenHost] draws every move from [ScreenMoves.frame]; the app says which kind each of its routes is.
 */
enum class ScreenMotion {
    /**
     * A page opened from another one, Back returns to it (home's details, every settings page, sharing, the new-sensor
     * flow): what the Settings app does on Android 16/17, whose pages are activities, so the system's activity open /
     * close and its cross-activity predictive back.
     */
    Page,

    /**
     * A screen with no parent/child relation to the one under it (setup over the app): M3 fade through, both ways.
     * The back gesture still previews the page under it like any page (the system does the same for every activity).
     */
    FadeThrough,
}

/** One move between two screens. */
enum class ScreenMove {
    /** A screen opens over the one shown (new on top). */
    Open,

    /** The top screen closes (Back, the page's own back arrow, a flow that ends): the one under it returns. */
    Back,

    /** The predictive back gesture is held: the top screen follows the finger over the one under it. */
    Gesture,

    /** The back gesture was released past the point of no return: [Back] from where the finger left the pages. */
    Commit,
}

/**
 * One page's look in a frame of a move. [rounded] is the share of the window's corner radius the page is clipped to
 * (0 = not clipped); [scrim] the black drawn over the page (and the screen around it).
 */
data class PageLook(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val rounded: Float = 0f,
    val scrim: Float = 0f,
) {
    companion object {
        val Rest = PageLook()
    }
}

/** The two pages of a move in one frame, in drawing order: on [ScreenMove.Open] the new page is the [top] one. */
data class MoveFrame(
    val bottom: PageLook,
    val top: PageLook,
)

/** The screen a move happens on, in pixels: its size, the slide distance, the edge margin, and dark or light. */
data class MoveSpace(
    val width: Float,
    val height: Float,
    val slide: Float,
    val edge: Float,
    val dark: Boolean,
)

/**
 * Every screen move's numbers, in one place. [ScreenMotion.Page] copies what the Settings app shows on Android 16/17
 * (AOSP core/res/res/anim/activity_{open,close}_*.xml and WM Shell back/CrossActivityBackAnimation.kt; SettingsLib's
 * own transition helper is empty):
 *  - open: both pages slide [SLIDE_DP] to the start, [PAGE_MS] fast-out-extra-slow-in; the new page fades in over
 *    83 ms after 50 ms;
 *  - back: the reverse; the closing page fades out over 83 ms after 35 ms;
 *  - back gesture: the page shrinks to 90 % with the window's corner radius, its end edge [EDGE_DP] from the screen's,
 *    and follows the finger up and down; the page below waits [SLIDE_DP] to the start at the same scale, a black scrim
 *    (20 % light, 80 % dark) between them. Releasing finishes in [PAGE_MS] (the page leaves [SLIDE_DP] to the end, gone
 *    by 20 %), cancelling springs back.
 * [ScreenMotion.FadeThrough] is [Motion.fadeThrough]'s: the old screen fades out over 90 ms, then the new one fades in
 * and grows from 92 % over 210 ms, emphasized decelerate.
 * With reduced motion (*Remove animations*) nothing moves: [ScreenHost] swaps the pages at once and the back gesture
 * shows no preview.
 */
object ScreenMoves {
    const val PAGE_MS = 450
    const val SLIDE_DP = 96
    const val EDGE_DP = 8
    private const val OPEN_FADE_DELAY_MS = 50f
    private const val CLOSE_FADE_DELAY_MS = 35f
    private const val PAGE_FADE_MS = 83f
    private const val GESTURE_SCALE = 0.9f
    private const val SCRIM_LIGHT = 0.2f
    private const val SCRIM_DARK = 0.8f

    /** How long a move runs (the gesture itself follows the finger; a commit is a [ScreenMove.Back] from there). */
    fun durationMs(
        move: ScreenMove,
        motion: ScreenMotion,
    ): Int =
        when {
            move == ScreenMove.Gesture || move == ScreenMove.Commit -> PAGE_MS
            motion == ScreenMotion.FadeThrough -> Motion.FADE_THROUGH_MS
            else -> PAGE_MS
        }

    /**
     * The two pages at [t] (0..1, linear time of the move) of [move] for a screen of kind [motion]. During
     * [ScreenMove.Gesture] / [ScreenMove.Commit], [progress] is the gesture's eased progress (0..1, [gestureProgress])
     * and [dy] how far the finger moved up or down since it started.
     */
    fun frame(
        move: ScreenMove,
        motion: ScreenMotion,
        t: Float,
        space: MoveSpace,
        progress: Float = 0f,
        dy: Float = 0f,
    ): MoveFrame =
        when {
            move == ScreenMove.Gesture || move == ScreenMove.Commit -> gesture(move == ScreenMove.Commit, t, progress, dy, space)
            motion == ScreenMotion.FadeThrough -> fadeThrough(move == ScreenMove.Open, t)
            move == ScreenMove.Open -> open(t, space)
            else -> back(t, space)
        }

    /** The system's back-gesture progress curve over the raw progress the system reports. */
    fun gestureProgress(raw: Float): Float = BackGesture.transform(raw.coerceIn(0f, 1f))

    private fun open(
        t: Float,
        space: MoveSpace,
    ): MoveFrame {
        val e = FastOutExtraSlowIn.transform(t)
        return MoveFrame(
            bottom = PageLook(translationX = -space.slide * e),
            top = PageLook(translationX = space.slide * (1 - e), alpha = fade(t, OPEN_FADE_DELAY_MS)),
        )
    }

    private fun back(
        t: Float,
        space: MoveSpace,
    ): MoveFrame {
        val e = FastOutExtraSlowIn.transform(t)
        return MoveFrame(
            bottom = PageLook(translationX = -space.slide * (1 - e)),
            top = PageLook(translationX = space.slide * e, alpha = 1f - fade(t, CLOSE_FADE_DELAY_MS)),
        )
    }

    /** A page fade's progress: [PAGE_FADE_MS] long, after [delayMs] of the [PAGE_MS] move. */
    private fun fade(
        t: Float,
        delayMs: Float,
    ): Float = ((t * PAGE_MS - delayMs) / PAGE_FADE_MS).coerceIn(0f, 1f)

    /**
     * Open and back alike: the leaving screen fades out first, then the coming one fades and grows in (on open the
     * coming screen is the top one, on back the bottom one; the two never show at once).
     */
    private fun fadeThrough(
        open: Boolean,
        t: Float,
    ): MoveFrame {
        val ms = t * Motion.FADE_THROUGH_MS
        val out = (ms / Motion.FADE_OUT_MS).coerceIn(0f, 1f)
        val inn = Motion.EmphasizedDecelerate.transform(((ms - Motion.FADE_OUT_MS) / Motion.FADE_IN_MS).coerceIn(0f, 1f))
        val coming = PageLook(alpha = inn, scale = Motion.FADE_IN_SCALE + (1f - Motion.FADE_IN_SCALE) * inn)
        val leaving = PageLook(alpha = 1f - out)
        return if (open) MoveFrame(bottom = leaving, top = coming) else MoveFrame(bottom = coming, top = leaving)
    }

    private fun gesture(
        commit: Boolean,
        t: Float,
        p: Float,
        dy: Float,
        space: MoveSpace,
    ): MoveFrame {
        val c = if (commit) FastOutExtraSlowIn.transform(t) else 0f
        val scale = 1f - (1f - GESTURE_SCALE) * p
        // at full progress the page's end edge sits EDGE_DP from the screen's
        val toEnd = p * (space.width * (1f - GESTURE_SCALE) / 2f - space.edge).coerceAtLeast(0f)
        val maxY = (space.height * (1f - scale) / 2f - space.edge).coerceAtLeast(0f)
        val yn = (abs(dy) / (space.height / 2f)).coerceIn(0f, 1f)
        val y = sign(dy) * (1f - (1f - yn) * (1f - yn)) * maxY
        val below = scale + (1f - scale) * c
        val scrimMax = if (space.dark) SCRIM_DARK else SCRIM_LIGHT
        val shown = p > 0f
        return MoveFrame(
            bottom =
                PageLook(
                    translationX = -space.slide * (1f - c),
                    translationY = y * (1f - c),
                    scale = below,
                    rounded = if (shown) 1f - c else 0f,
                    scrim = if (shown) scrimMax * (1f - if (commit) t else 0f) else 0f,
                ),
            top =
                PageLook(
                    translationX = toEnd + space.slide * c,
                    translationY = y,
                    scale = scale,
                    alpha = (1f - c * 5f).coerceAtLeast(0f),
                    rounded = if (shown) 1f else 0f,
                ),
        )
    }

    // AOSP fast_out_extra_slow_in: the path M0,0 C0.05,0 0.133333,0.06 0.166666,0.4 C0.208333,0.82 0.25,1 1,1, as its two
    // cubic segments (PathEasing needs androidx.graphics' native path iterator, missing in JVM tests)
    private val FoesiStart = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val FoesiEnd = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val FastOutExtraSlowIn =
        Easing { x ->
            if (x < 0.166666f) {
                0.4f * FoesiStart.transform(x / 0.166666f)
            } else {
                0.4f + 0.6f * FoesiEnd.transform((x - 0.166666f) / 0.833334f)
            }
        }
    private val BackGesture = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
}
