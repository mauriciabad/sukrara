// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * M3 Expressive motion springs (androidx material3 `ExpressiveMotionTokens` values): spatial springs (position, size,
 * rotation) have a hint of bounce, effects springs (alpha, colour) don't. All settle in well under 300 ms.
 * Every spec snaps when [reduced] is true, so callers pass `LocalReducedMotion.current` and need no branch.
 */
object Motion {
    fun <T> defaultSpatial(reduced: Boolean = false): FiniteAnimationSpec<T> = orSnap(reduced) { spring(0.8f, 380f) }

    fun <T> fastSpatial(reduced: Boolean = false): FiniteAnimationSpec<T> = orSnap(reduced) { spring(0.6f, 800f) }

    fun <T> defaultEffects(reduced: Boolean = false): FiniteAnimationSpec<T> = orSnap(reduced) { spring(1f, 1600f) }

    fun <T> fastEffects(reduced: Boolean = false): FiniteAnimationSpec<T> = orSnap(reduced) { spring(1f, 3800f) }

    private inline fun <T> orSnap(
        reduced: Boolean,
        spec: () -> FiniteAnimationSpec<T>,
    ): FiniteAnimationSpec<T> = if (reduced) snap() else spec()

    /**
     * M3 fade through: for contents without a parent/child relation (setup's steps, a question that replaces another).
     * Whole screens fade through in [ScreenHost] ([ScreenMotion.FadeThrough]) with the same numbers.
     */
    fun fadeThrough(reduced: Boolean = false): ContentTransform {
        if (reduced) return fadeIn(snap()) togetherWith fadeOut(snap())
        val enter = tween<Float>(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = EmphasizedDecelerate)
        return (fadeIn(enter) + scaleIn(enter, initialScale = FADE_IN_SCALE)) togetherWith
            fadeOut(tween(FADE_OUT_MS, easing = LinearEasing))
    }

    /** M3 emphasized decelerate, the fade-through's way in. */
    internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    internal const val FADE_OUT_MS = 90
    internal const val FADE_IN_MS = 210
    internal const val FADE_THROUGH_MS = FADE_OUT_MS + FADE_IN_MS
    internal const val FADE_IN_SCALE = 0.92f
}

/**
 * True when the person turned animations off ("Remove animations" sets the animator duration scale to 0). Compose
 * already snaps its frame-clock animations then; this flag is for the things we choose not to do at all (counting
 * numbers, bouncing arrows) and is provided by `SukraraTheme`.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Whether an animator duration scale means "no animations". */
fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/**
 * The system animator duration scale as "reduced motion", observed: turning *Remove animations* on or off (Quick
 * Settings, Accessibility) while a screen is open applies at once, not only on the next screen.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) { mutableStateOf(readReducedMotion(resolver)) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = readReducedMotion(resolver)
                }
            }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        // a change between the first read and the registration
        reduced = readReducedMotion(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun readReducedMotion(resolver: ContentResolver): Boolean =
    isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
