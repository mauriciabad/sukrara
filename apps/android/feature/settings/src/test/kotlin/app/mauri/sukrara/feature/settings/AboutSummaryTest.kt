// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.search.BuildFeature
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/**
 * The owner, 2026-11-15: "In about button, we should also have a description, like updates, and links", then "the
 * about description is too long, i woudl like it was only 1 line". The overview's About row says the version and what
 * the page has (the updater only where the build has one), or the update found, on one line. Images go to
 * build/outputs/roborazzi (scratch, never committed).
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
        summary("0.14.5 · updates and links")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-overview-about-github.png")
        build = setOf(BuildFeature.Root)
        summary("0.14.5 · links")
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
        val row = compose.onNodeWithText("About").performScrollTo().fetchSemanticsNode()
        assertEquals(listOf("About"), row.config[SemanticsProperties.Text].map { it.text })
    }

    @Test
    fun `every variant fits one line in English`() = assertEveryVariantFits("Update available")

    @Test
    @Config(qualifiers = "+es")
    fun `every variant fits one line in Spanish`() = assertEveryVariantFits("Actualización disponible")

    /**
     * Each summary (GitHub, Google Play, an update found; a two-digit version) on one line of a 411 dp phone (the
     * owner's Pixel 10 Pro) at the default font size, in at most [ROOM] of the line: the test draws Roboto, a Pixel
     * draws Settings' body text in its own wider font (*Version 0.14.5 · updates, report a problem, source code* took
     * two lines there). [update] = how the update's summary starts.
     */
    private fun assertEveryVariantFits(update: String) {
        show()
        val variants =
            listOf(
                Triple(OverviewAbout(LONG_VERSION), BuildFeature.entries.toSet(), LONG_VERSION),
                Triple(OverviewAbout(LONG_VERSION), setOf(BuildFeature.Root), LONG_VERSION),
                Triple(OverviewAbout(LONG_VERSION, update = "0.14.16"), BuildFeature.entries.toSet(), update),
            )
        for ((a, b, start) in variants) {
            about = a
            build = b
            compose.waitForIdle()
            val node = compose.onNodeWithText(start, substring = true, useUnmergedTree = true)
            node.performScrollTo()
            val layouts = mutableListOf<TextLayoutResult>()
            node
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult]
                .action
                ?.invoke(layouts)
            val layout = layouts.single()
            val text = layout.layoutInput.text.text
            val used = layout.getLineRight(0) / layout.layoutInput.constraints.maxWidth
            assertEquals("\"$text\" wraps", 1, layout.lineCount)
            assertTrue("\"$text\" takes ${(used * 100).toInt()} % of the line, more than ${(ROOM * 100).toInt()} %", used <= ROOM)
        }
    }

    private companion object {
        /** A version as long as they get (0.14.10). */
        const val LONG_VERSION = "0.14.10"

        /** The share of the line a summary may take here (the rest is for a phone's wider body font). */
        const val ROOM = 0.85f
    }
}
