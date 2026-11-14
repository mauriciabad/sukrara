// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Review alert thresholds (and setup's alert step, the same rows): each row is the alert's name and only what sets it
 * off, the comparison the engine makes (low below, very low at or below, highs at or above); an off alert says Off.
 * Nothing repeats the name, no span ("In range" is the Ranges page's word, not an alert's), no unit on the levels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertThresholdRowsTest {
    @get:Rule val compose = createComposeRule()

    private val veryHighOff =
        AlertSettings().let { it.withRule(AlertKind.VERY_HIGH, it.rule(AlertKind.VERY_HIGH).copy(on = false)) }

    private fun page() =
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) { AlertThresholdReviewContent(veryHighOff, { _, _ -> }, {}, {}) }
        }

    private fun gone(text: String) = assertEquals(text, 0, compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size)

    @Test fun eachRowSaysOnlyWhatSetsItOff() {
        page()
        compose.onNodeWithText("Nothing is saved until you confirm.").assertExists()
        for (line in listOf("At or below 55", "Below 70", "At or above 180", "Off", "At or above 300", "After 20 min")) {
            compose.onNodeWithText(line).performScrollTo().assertExists()
        }
        compose.onNodeWithText("Confirm thresholds").performScrollTo().assertExists()
        listOf("In range", "mg/dL", "Low:", "Very low:", "No reading", "Off (", "Use these").forEach(::gone)
    }

    @Test
    @Config(qualifiers = "es")
    fun spanish() {
        page()
        compose.onNodeWithText("No se guarda nada hasta que confirmes.").assertExists()
        for (line in listOf("Hasta 55", "Por debajo de 70", "A partir de 180", "Desactivada", "A partir de 300", "Tras 20 min")) {
            compose.onNodeWithText(line).performScrollTo().assertExists()
        }
        compose.onNodeWithText("Confirmar umbrales").performScrollTo().assertExists()
        listOf("En rango", "mg/dL", "Bajo:", "Sin lecturas", "Usar estos").forEach(::gone)
    }
}
