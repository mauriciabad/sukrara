// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.share

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #170 without goldens (every `check` runs it): *Share with someone*, placed as the app places it (its theme, nothing
 * around it), paints its own page in the theme's colours: dark in the dark theme, light in the light one. The goldens
 * ([ShareScreenGoldenTest]) show the text on it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ShareScreenThemeTest {
    @get:Rule val compose = createComposeRule()

    /** The page's colour well below the content (bottom-left corner, clear of every text and control). */
    private fun pageColor(dark: Boolean): Color {
        // the phone's dark theme (the settings pages follow it, as the app's theme does)
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        compose.setContent { SukraraTheme(dark = dark, dynamic = false) { ShareScreen(InviteUi.None, {}, {}, {}) } }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[8, pixels.height - 8]
    }

    @Test
    fun darkThemePaintsADarkPage() {
        val page = pageColor(dark = true)
        assertTrue("page $page should be dark and opaque", page.alpha == 1f && page.luminance() < 0.2f)
    }

    @Test
    fun lightThemePaintsALightPage() {
        val page = pageColor(dark = false)
        assertTrue("page $page should be light and opaque", page.alpha == 1f && page.luminance() > 0.6f)
    }
}
