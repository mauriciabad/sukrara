// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.share

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.sharing.Invite
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #181 (complete flows): every step of *Share with someone* (the QR shown, someone asked, the outcomes) has an
 * on-screen Back in its top bar, not only the system back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ShareScreenBackTest {
    @get:Rule val compose = createComposeRule()

    private val ready = InviteUi.Ready(Invite("https://sukrara.mauri.app/join#c3ludGhldGlj", "Anna", 1_761_600_000))

    @Test
    fun theQrPageHasAnOnScreenBack() {
        var back = 0
        compose.setContent { SukraraTheme(dynamic = false) { ShareScreen(ready, {}, {}, onBack = { back++ }) } }
        compose.onNodeWithContentDescription("Navigate up").performClick()
        assertEquals(1, back)
    }

    @Test
    fun anOutcomeHasAnOnScreenBack() {
        var back = 0
        compose.setContent {
            SukraraTheme(dynamic = false) { ShareScreen(ready, {}, {}, onBack = { back++ }, progress = InviteProgress.Expired) }
        }
        compose.onNodeWithContentDescription("Navigate up").performClick()
        assertEquals(1, back)
    }
}
