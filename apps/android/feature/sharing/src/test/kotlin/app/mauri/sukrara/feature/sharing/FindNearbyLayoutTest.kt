// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Find phones nearby* with an offer of a newer version (docs/48): the update notice stands on its own above the list
 * card, as on Pair nearby and Settings › Sharing, never inside it (a card in a card).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class FindNearbyLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun updateNoticeSitsAboveTheList() {
        val scene =
            SharingScreenshotTest()
                .also { it.setUp() }
                .scenes()
                .first { it.first == "setup-join-find-nearby-update" }
                .second
        // the search spinner never idles: a paused clock
        compose.mainClock.autoAdvance = false
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { scene() } }
        compose.mainClock.advanceTimeBy(2_000)
        val notice = compose.onNodeWithText("Update Sukrara").getBoundsInRoot()
        val list = compose.onNodeWithText("Find phones nearby").getBoundsInRoot()
        assertTrue("the notice ($notice) ends above the list's title ($list)", notice.bottom < list.top)
    }
}
