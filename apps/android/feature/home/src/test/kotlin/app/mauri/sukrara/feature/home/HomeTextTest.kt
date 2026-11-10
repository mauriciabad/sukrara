// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
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
        readOnly: Set<String> = emptySet(),
        delivery: Map<String, TreatmentDelivery> = emptyMap(),
    ) {
        val look = homeLook(glucose, NOW, GlucoseUnit.MGDL)
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
                readOnly = readOnly,
                delivery = delivery,
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

    /** A pen's entry says where it came from and is one button that opens its sheet; a read-only row is no button. */
    @Test fun penRowOpensItsSheet() {
        val opened = mutableListOf<String>()
        show(
            Samples.wearer(),
            fromPen = setOf("b"),
            callbacks = HomeCallbacks(pen = PenCallbacks(open = { opened += it.id })),
            readOnly = setOf("a"),
        )
        compose.onNode(hasText("4.5u rapid insulin · from the pen") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("b"), opened)
        compose.onNode(hasText("30g carbs") and hasClickAction()).assertDoesNotExist()
    }

    /** #75: a row this phone may change opens the treatment dialog on that entry. */
    @Test fun aRowOpensItsEdit() {
        val asked = mutableListOf<HomeAction>()
        show(Samples.wearer(), callbacks = HomeCallbacks(onAction = { asked += it }))
        compose.onNode(hasText("30g carbs") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf<HomeAction>(HomeAction.EditTreatment("a")), asked)
    }

    /** #75: a writer's entry nothing took says *Not sent* with *Retry*; one still on its way says *Sending…*. */
    @Test fun notSentOffersRetry() {
        val retried = mutableListOf<String>()
        show(
            Samples.wearer(),
            callbacks = HomeCallbacks(onRetry = { retried += it.id }),
            delivery = mapOf("a" to TreatmentDelivery.Failed, "b" to TreatmentDelivery.Pending),
        )
        compose.onNodeWithText("Not sent").performScrollTo().assertExists()
        compose.onNodeWithText("Sending…").assertExists()
        // its action (a scrolled-to button can sit under the floating Add treatment button)
        compose.onNodeWithContentDescription("Retry sending 30g carbs").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("a"), retried)
    }

    @Test fun muteAllUntilTurnedOff() {
        show(Samples.wearer().copy(alerts = AlertUiState(mutedUntil = AlertUiState.UNTIL_TURNED_OFF)))
        compose.onNodeWithText("Alerts muted until you turn it off").assertIsDisplayed()
    }

    /** The sensor's time left in the card's spoken words (#104): lower-case "in", whole days, partial minutes up. */
    @Test fun sensorLine() {
        show(Samples.wearer())
        compose.onNodeWithText("Sensor ends in 9 days").assertExists()
    }

    @Test fun sensorLineLastDay() {
        show(Samples.wearer(sensor = SensorState.Active(NOW.plus(Duration.ofMinutes(4 * 60 + 58).plusSeconds(30)))))
        compose.onNodeWithText("Sensor ends in 4 h 59 min").assertExists()
    }

    /** A new sensor's first 12 h (#46): the note says until when and what to do; later it's gone. */
    @Test fun earlyReadingsNote() {
        show(Samples.wearer(sensor = SensorState.Active(NOW.plus(Duration.ofDays(15)).minus(Duration.ofHours(3)))))
        compose
            .onNodeWithText("New sensor: readings can be less accurate until", substring = true)
            .assertExists()
        compose.onNodeWithText("check with a meter", substring = true).assertExists()
    }

    @Test fun noEarlyNoteOnAnOlderSensor() {
        show(Samples.wearer())
        compose.onNodeWithTag("home-sensor-early").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "es")
    fun spanishLines() {
        show(muted)
        compose.onNodeWithText("Alertas silenciadas · quedan 1 h 12 min").assertIsDisplayed()
        compose.onNodeWithText("30g de hidratos").assertExists()
        compose.onNodeWithText("El sensor termina en 9 días").assertExists()
    }
}
