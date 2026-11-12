// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * #181: Alert reliability with every check on shows SPA's status banner as one card in both themes. Its row (icon and
 * title) is drawn in surfaceBright inside the card; with the card in its default surface, the dark theme showed the row
 * as a lighter rectangle inside a darker card. A line across the card's left edge and the row's must be one colour.
 * The pictures go to the gallery (`build/outputs/roborazzi`) for the UX review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ReliabilityBannerTest {
    @get:Rule val compose = createComposeRule()

    /** The largest difference of one colour channel along the line (0–1). */
    private fun bannerSpread(dark: Boolean): Float {
        // the phone's theme (the settings pages follow it, as the app's theme does)
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        compose.setContent {
            SukraraTheme(dark = dark, dynamic = false) {
                ReliabilityContent(AlertReadiness(emptySet(), unlockedSound = UnlockedSound.Heard), {}) {}
            }
        }
        val banner = compose.onNodeWithTag(READY_BANNER_TAG)
        banner.captureRoboImage("build/outputs/roborazzi/settings-reliability-ready-banner-${if (dark) "dark" else "light"}.png")
        val pixels = banner.captureToImage().toPixelMap()
        val dp =
            RuntimeEnvironment
                .getApplication()
                .resources.displayMetrics.density

        fun px(v: Float) = (v * dp).roundToInt()
        // SPA's layout: the card 16 dp in and 8 + 1 dp down, its row 16 dp further in. 3 dp below the row's top the line
        // crosses the card (x 22–32 dp) and the row (x 32–54 dp), clear of the card's and the row's rounded corners and
        // of the icon's circle (from x 57 dp at that height)
        val y = px(8f + 1f + 16f + 3f)
        val line = (px(22f)..px(54f)).map { pixels[it, y] }
        val channels = listOf(Color::red, Color::green, Color::blue)
        return channels.maxOf { c -> line.maxOf(c) - line.minOf(c) }
    }

    @Test
    fun darkBannerIsOneCard() {
        val spread = bannerSpread(dark = true)
        // the row's colour goes through SPA's highlight animation: a rounding step apart at most (the bug: 0.15)
        assertTrue("the banner's card and row differ in the dark theme by $spread", spread < SAME)
    }

    @Test
    fun lightBannerIsOneCard() {
        val spread = bannerSpread(dark = false)
        assertTrue("the banner's card and row differ in the light theme by $spread", spread < SAME)
    }

    private companion object {
        /** Two colours closer than this per channel look the same (2 steps of 255). */
        const val SAME = 2.5f / 255f
    }
}
