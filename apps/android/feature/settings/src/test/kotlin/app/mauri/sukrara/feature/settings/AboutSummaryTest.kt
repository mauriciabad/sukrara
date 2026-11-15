// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.search.BuildFeature
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

/**
 * The owner, 2026-11-15: "In about button, we should also have a description, like updates, and links". The overview's
 * About row says the version and what the page has (the updater only where the build has one), or the update found.
 * Images go to build/outputs/roborazzi (scratch, never committed).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AboutSummaryTest {
    @get:Rule val compose = createComposeRule()

    private var about by mutableStateOf(OverviewAbout("0.14.5"))
    private var build by mutableStateOf(BuildFeature.entries.toSet())

    private fun show() =
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                SettingsOverviewContent(
                    true,
                    AlertUiState(),
                    AlertReadiness.Ready,
                    FakeClock(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC),
                    OverviewActions(build = build),
                    about = about,
                ) {}
            }
        }

    private fun summary(text: String) {
        compose.onNodeWithText(text).performScrollTo()
        assertEquals(1, compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size)
    }

    @Test
    fun `the GitHub version names its updates, the Google Play version its links`() {
        show()
        summary("Version 0.14.5 · updates, report a problem, source code")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-overview-about-github.png")
        build = setOf(BuildFeature.Root)
        summary("Version 0.14.5 · report a problem, source code, privacy")
    }

    @Test
    fun `an update found is what the row says`() {
        about = OverviewAbout("0.14.5", update = "0.14.6")
        show()
        summary("Update available: 0.14.6")
    }

    @Test
    fun `an unknown version says nothing`() {
        about = OverviewAbout()
        show()
        compose.onNodeWithText("About").performScrollTo()
        assertEquals(0, compose.onAllNodes(hasText("Version", substring = true)).fetchSemanticsNodes().size)
    }
}
