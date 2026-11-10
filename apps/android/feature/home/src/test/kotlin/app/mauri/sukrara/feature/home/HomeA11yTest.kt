// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.home.Samples.NOW
import app.mauri.sukrara.graph.GraphConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Home with TalkBack (#74): a new reading is said once through a polite live region (value, trend, range: no age, so
 * the minute's tick says nothing), the history can be walked without the drag (the card's custom actions), and
 * *Remove animations* applies while the screen is open.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class HomeA11yTest {
    @get:Rule val compose = createComposeRule()

    private var glucose by mutableStateOf(Samples.wearer())
    private var now by mutableStateOf(NOW)

    private fun showCard() {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                // a day of history: the default window can go back
                val history = glucose.recent.map { it.copy(t = it.t.minus(Duration.ofHours(12))) } + glucose.recent
                PanelCard(glucose, history, emptyList(), now, GraphConfig())
            }
        }
        draw()
    }

    /** The card's overlays (the pill's button, the live region) follow what the renderer drew: draw a frame. */
    private fun draw() {
        compose.onRoot().captureToImage()
        compose.waitForIdle()
    }

    /** What the live region would say (null: silent, or not placed yet because no number was ever drawn). */
    private fun live(): String? =
        compose
            .onAllNodesWithTag("live-value")
            .fetchSemanticsNodes()
            .singleOrNull()
            ?.config
            ?.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString()

    private fun card(): String =
        compose
            .onNodeWithTag("home-card")
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .joinToString()

    private fun actions(): List<String> =
        compose
            .onNodeWithTag("home-card")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.CustomActions)
            .orEmpty()
            .map { it.label }

    @Test fun `a new reading is said once, politely, as it came`() {
        showCard()
        compose.onNodeWithTag("live-value").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertEquals("112 milligrams per decilitre, falling, in range", live())

        // a minute on, no new reading: the card's sentence ages, the live words don't (nothing is announced)
        val before = card()
        now = NOW.plus(Duration.ofMinutes(1))
        draw()
        assertNotEquals(before, card())
        assertEquals("112 milligrams per decilitre, falling, in range", live())

        // the next reading changes the words: TalkBack says it without moving the focus
        glucose = Samples.wearer(last = 64, ageMin = 0)
        draw()
        assertEquals("64 milligrams per decilitre, falling, low", live())

        // 25 min without a reading: no value, nothing to say (the region stays, silent, for the next one)
        now = NOW.plus(Duration.ofMinutes(25))
        draw()
        assertEquals(null, live())
    }

    @Test fun `no value from the start, no live words`() {
        glucose = Samples.wearer(ageMin = 25)
        showCard()
        assertEquals(null, live())
    }

    @Test fun `the history without the drag - earlier, later, zoom and back to now`() {
        showCard()
        assertEquals(listOf("Show earlier", "Zoom out", "Zoom in"), actions())
        val earlier =
            compose
                .onNodeWithTag("home-card")
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .first { it.label == "Show earlier" }
        compose.runOnIdle { earlier.action() }
        draw()
        assertEquals(listOf("Show earlier", "Show later", "Zoom out", "Zoom in", "Back to now"), actions())
    }

    @Test fun `turning Remove animations on applies at once`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) { Text(if (LocalReducedMotion.current) "still" else "moving") }
        }
        compose.onNodeWithText("moving").assertExists()
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        shadowOf(Looper.getMainLooper()).idle()
        compose.onNodeWithText("still").assertExists()
    }
}
