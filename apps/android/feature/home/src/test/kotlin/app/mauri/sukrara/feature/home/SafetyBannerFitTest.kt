// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.ServiceState
import app.mauri.sukrara.core.model.StopReason
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.home.Samples.NOW
import app.mauri.sukrara.feature.home.banners.BannerKind
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A safety banner's action is on screen without scrolling, on a small phone at the largest font (#73): the card gives
 * up height for it (never below its minimum) and takes it back once the banner goes. Images go to
 * build/outputs/roborazzi (scratch, never committed).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class SafetyBannerFitTest {
    @get:Rule val compose = createComposeRule()

    private fun ui(
        glucose: GlucoseState,
        banner: BannerKind?,
    ): HomeUiState {
        val look = homeLook(glucose, NOW)
        return HomeUiState(
            glucose = glucose,
            now = NOW,
            look = look,
            reason = NoDataReason.of(glucose, look),
            banner = banner,
            firstProblem = AlertReadiness(setOf(AlertRequirement.ExactAlarm)).firstProblem,
            nextSetupStep = null,
            dataProblems = emptyList(),
            treatments = emptyList(),
            undo = null,
        )
    }

    private val stopped =
        ui(Samples.wearer(ageMin = 25).copy(service = ServiceState.Stopped(StopReason.StartDenied)), BannerKind.MonitoringStopped)

    private fun show(
        state: HomeUiState,
        fontScale: Float,
    ) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                SukraraTheme(dark = false, dynamic = false) { HomeContent(state, HomeCallbacks()) }
            }
        }
        compose.waitForIdle()
    }

    private fun assertActionOnScreen(label: String) {
        val screen = compose.onRoot().getBoundsInRoot()
        val action = compose.onNode(hasText(label) and hasClickAction()).getBoundsInRoot()
        assertTrue("$label ends at ${action.bottom}, the screen at ${screen.bottom}", action.bottom <= screen.bottom)
    }

    @Test fun `Resume monitoring fits at the largest font`() {
        show(stopped, fontScale = 2f)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/fit-monitoring-stopped-360x640-font2x.png")
        assertActionOnScreen("Resume monitoring")
        val card = compose.onNodeWithTag("home-card").getBoundsInRoot()
        assertTrue("the card keeps its minimum: ${card.height}", card.height >= MIN_CARD_HEIGHT)
    }

    @Test fun `Fix fits at the largest font`() {
        show(ui(Samples.wearer(), BannerKind.AlertsUnreliable), fontScale = 2f)
        assertActionOnScreen("Fix")
    }

    /** Nothing to make room for: the card keeps its full height. */
    @Test fun `without a safety banner the card keeps its height`() {
        show(ui(Samples.wearer(), null), fontScale = 2f)
        assertEquals(CARD_HEIGHT, compose.onNodeWithTag("home-card").getBoundsInRoot().height)
    }
}
