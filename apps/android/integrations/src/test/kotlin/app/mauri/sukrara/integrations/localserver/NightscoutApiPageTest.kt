// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.localserver

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The page's own switch is its main switch (stock MainSwitchPreference) and the Wi-Fi option under it stays on the page,
 * greyed with why while the API is off, instead of appearing only once the switch is on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NightscoutApiPageTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-14T14:00:00Z")

    private fun page(ui: NsApiUi) =
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { NightscoutApiContent(ui, now, NsApiActions()) } }

    @Test fun offGreysTheWifiOptionAndSaysWhy() {
        page(NsApiUi(false, NsApiState.Off))
        compose.onNodeWithText("Share with apps on this phone").assertIsOff()
        compose.onNodeWithText("Also on this Wi-Fi").assertIsNotEnabled()
        compose.onNodeWithText("Turn on sharing with apps on this phone first").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Status").fetchSemanticsNodes().size)
    }

    @Test fun onShowsStatusAndTheWifiOption() {
        page(NsApiUi(true, NsApiState.Listening(null)))
        compose.onNodeWithText("Share with apps on this phone").assertIsOn()
        compose.onNodeWithText("Status").assertExists()
        compose.onNodeWithText("Also on this Wi-Fi").assertIsEnabled()
    }

    @Test fun wifiLeftOnCanStillBeTurnedOffWithTheApiOff() {
        page(NsApiUi(false, NsApiState.Off, lanOn = true, lan = LanState.ApiStopped))
        compose.onNodeWithText("Also on this Wi-Fi").assertIsEnabled()
    }
}
