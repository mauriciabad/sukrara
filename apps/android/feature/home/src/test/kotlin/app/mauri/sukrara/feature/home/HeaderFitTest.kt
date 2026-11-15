// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.home.Samples.NOW
import app.mauri.sukrara.protocol.event.Avatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The owner, 2026-11-15: the header said "1 followe" on their phone. The followers line is one whole line at every font
 * size, in Spanish, on a small phone and with a long name: never wrapped (inside a word), never under the top bar's
 * buttons, and TalkBack reads it in words.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi")
class HeaderFitTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun state(profile: HomeProfile): HomeUiState {
        val look = homeLook(Samples.wearer(), NOW, GlucoseUnit.MGDL)
        return HomeUiState(
            glucose = Samples.wearer(),
            now = NOW,
            look = look,
            reason = null,
            banner = null,
            firstProblem = null,
            nextSetupStep = null,
            dataProblems = emptyList(),
            treatments = emptyList(),
            undo = null,
            profile = profile,
        )
    }

    private val profiles =
        listOf(
            HomeProfile("Mauri", Avatar.v1(0, "drop", 45, 3), followers = 1),
            HomeProfile("Mauri", Avatar.v1(0, "drop", 45, 3), followers = 2, nearby = 1),
            HomeProfile("Maurici Abad Fernández de la Vega", null, followers = 12, nearby = 10),
        )

    private fun everyCase() {
        var scale by mutableFloatStateOf(1f)
        var profile by mutableStateOf(profiles.first())
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                SukraraTheme(dark = false, dynamic = false) { HomeContent(state(profile), HomeCallbacks()) }
            }
        }
        val share = context.getString(R.string.home_share)
        for (s in listOf(1f, 1.3f, 2f)) {
            for (p in profiles) {
                scale = s
                profile = p
                compose.waitForIdle()
                val line = compose.onNodeWithTag(FOLLOWERS_LINE_TAG, useUnmergedTree = true).fetchSemanticsNode()
                val button = compose.onNodeWithContentDescription(share).fetchSemanticsNode().boundsInRoot
                val bounds = line.boundsInRoot
                val case = "${p.followers}/${p.nearby} at ${s}x: $bounds, Share at $button"
                assertTrue("ends before the buttons, $case", bounds.right <= button.left)
                // one line: bodySmall's 16 sp line (the marks are 1.25 em) and no more
                val oneLine = 16 * s * compose.density.density * 1.3f
                assertTrue("one line, $case", bounds.height <= oneLine)
                val words = context.resources.getQuantityString(R.plurals.home_followers, p.followers, p.followers)
                val near = context.resources.getQuantityString(R.plurals.home_followers_nearby, p.nearby, p.nearby)
                assertEquals(
                    if (p.nearby >
                        0
                    ) {
                        "$words, $near"
                    } else {
                        words
                    },
                    line.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single(),
                )
            }
        }
    }

    @Test fun pixel() = everyCase()

    @Test
    @Config(qualifiers = "es")
    fun pixelSpanish() = everyCase()

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun smallPhone() = everyCase()

    @Test
    @Config(qualifiers = "es-w320dp-h640dp-xhdpi")
    fun smallPhoneSpanish() = everyCase()
}
