// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.home.Samples.NOW
import app.mauri.sukrara.feature.home.banners.BannerKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Home's words come from the shared formatters: Mute all's line from `MuteText` (contracts/alerts/copy.json), the
 * treatment rows' amounts as the card writes them (`30g`, `4.5u`) and their age in the card's short durations (`25m`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeTextTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        glucose: GlucoseState,
        widgetBanner: Boolean? = null,
        fromPen: Set<String> = emptySet(),
        callbacks: HomeCallbacks = HomeCallbacks(),
    ) {
        val look = homeLook(glucose, NOW)
        val treatments =
            listOf(Samples.treatment("a", TreatmentKind.Carbs, 30.0, 25), Samples.treatment("b", TreatmentKind.RapidInsulin, 4.5, 3 * 60))
        val state =
            HomeUiState(
                glucose = glucose,
                now = NOW,
                look = look,
                reason = NoDataReason.of(glucose, look),
                banner = widgetBanner?.let { BannerKind.AddWidget },
                firstProblem = AlertReadiness.Ready.firstProblem,
                nextSetupStep = null,
                dataProblems = emptyList(),
                treatments = treatments,
                undo = null,
                widgetPinnable = widgetBanner != false,
                fromPen = fromPen,
            )
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { HomeContent(state, callbacks) } }
    }

    @Test fun widgetBannerAdds() {
        show(Samples.wearer(), widgetBanner = true)
        compose.onNodeWithText("Add widget").assertExists()
    }

    /** The launcher can't place it from the app: the hand-made way, and no button that would do nothing. */
    @Test fun widgetBannerByHand() {
        show(Samples.wearer(), widgetBanner = false)
        compose.onNodeWithText("Touch & hold the home screen, then tap Widgets › Sukrara.").assertExists()
        compose.onNodeWithText("Add widget").assertDoesNotExist()
    }

    private val muted = Samples.wearer().copy(alerts = AlertUiState(mutedUntil = NOW.plus(Duration.ofMinutes(72))))

    @Test fun englishLines() {
        show(muted)
        compose.onNodeWithText("Alerts muted · 1 h 12 min left").assertIsDisplayed()
        compose.onNodeWithText("30g carbs").assertExists()
        compose.onNodeWithText("25m").assertExists()
        compose.onNodeWithText("4.5u rapid insulin").assertExists()
        compose.onNodeWithText("3h").assertExists()
    }

    /** A pen's entry says where it came from and is one button that opens its sheet; typed rows are not buttons. */
    @Test fun penRowOpensItsSheet() {
        val opened = mutableListOf<String>()
        show(Samples.wearer(), fromPen = setOf("b"), callbacks = HomeCallbacks(pen = PenCallbacks(open = { opened += it.id })))
        compose.onNode(hasText("4.5u rapid insulin · from the pen") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("b"), opened)
        compose.onNode(hasText("30g carbs") and hasClickAction()).assertDoesNotExist()
    }

    @Test fun muteAllUntilTurnedOff() {
        show(Samples.wearer().copy(alerts = AlertUiState(mutedUntil = AlertUiState.UNTIL_TURNED_OFF)))
        compose.onNodeWithText("Alerts muted until you turn it off").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "es")
    fun spanishLines() {
        show(muted)
        compose.onNodeWithText("Alertas silenciadas · quedan 1 h 12 min").assertIsDisplayed()
        compose.onNodeWithText("30g de hidratos").assertExists()
    }
}
