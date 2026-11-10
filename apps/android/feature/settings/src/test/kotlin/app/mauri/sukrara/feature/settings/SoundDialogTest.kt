// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * An alert's sound dialog (#131): with a sound chosen in the channel's Android settings every ▶ plays that sound, so the
 * dialog says why; otherwise it says nothing more.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoundDialogTest {
    @get:Rule val compose = createComposeRule()

    private val line = "Android plays the sound chosen in its settings"

    private fun openSoundDialog(chosenInAndroid: Boolean) {
        val playback =
            object : AlertPlayback by AlertPlayback.None {
                override fun soundChosenInAndroid(
                    kind: AlertKind,
                    alarm: Boolean,
                ) = chosenInAndroid
            }
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                AlertKindContent(
                    AlertKind.LOW,
                    AlertsUi(AlertSettings(), AlertUiState()),
                    FakeClock(Instant.parse("2026-10-27T14:00:00Z")),
                    AlertKindActions(playback = playback),
                ) {}
            }
        }
        compose.onNodeWithText("Same as the pack", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test fun aSoundChosenInAndroidIsSaidInTheDialog() {
        openSoundDialog(chosenInAndroid = true)
        compose.onNodeWithText(line).assertExists()
    }

    @Test fun thePacksOwnSoundsNeedNoLine() {
        openSoundDialog(chosenInAndroid = false)
        assertEquals(0, compose.onAllNodesWithText(line).fetchSemanticsNodes().size)
    }
}
