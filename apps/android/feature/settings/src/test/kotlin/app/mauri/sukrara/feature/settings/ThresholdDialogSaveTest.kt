// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The number dialog saves what the person chose (the owner, 2026-11-12: low set to 69, then back to 70 by typing it,
 * and OK did nothing: its click went to the unused third button). A typed value saves on OK and on the keyboard's
 * Done, a preset at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThresholdDialogSaveTest {
    @get:Rule val compose = createComposeRule()

    private var settings =
        AlertSettings().let { it.withRule(AlertKind.LOW, it.rule(AlertKind.LOW).copy(threshold = 69.0)) }

    private fun openThreshold() {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                AlertKindContent(
                    AlertKind.LOW,
                    AlertsUi(settings, AlertUiState()),
                    FakeClock(Instant.parse("2026-11-12T14:00:00Z")),
                    AlertKindActions(edit = { settings = it(settings) }),
                ) {}
            }
        }
        compose.onNodeWithText("Below 69 mg/dL", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test fun aTypedValueSavesOnOk() {
        openThreshold()
        compose.onNode(hasSetTextAction()).performTextReplacement("72")
        compose.onNodeWithText("OK").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(72.0, settings.rule(AlertKind.LOW).threshold, 0.0)
    }

    @Test fun aTypedValueSavesOnTheKeyboardsDone() {
        openThreshold()
        compose.onNode(hasSetTextAction()).performTextReplacement("71")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        assertEquals(71.0, settings.rule(AlertKind.LOW).threshold, 0.0)
    }

    @Test fun aPresetSavesAtOnce() {
        openThreshold()
        compose.onNodeWithText("70 mg/dL", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(70.0, settings.rule(AlertKind.LOW).threshold, 0.0)
    }
}
