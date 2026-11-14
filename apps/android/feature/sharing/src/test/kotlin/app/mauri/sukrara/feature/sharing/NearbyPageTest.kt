// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.sharing.followers.FollowersActions
import app.mauri.sukrara.feature.sharing.followers.FollowersPage
import app.mauri.sukrara.feature.sharing.followers.NearbyPage
import app.mauri.sukrara.share.ble.NearbyStatus
import app.mauri.sukrara.share.owner.OwnerShareState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bluetooth sharing follows stock Settings' pattern for a feature with its own options (Connected devices › Bluetooth):
 * on Sharing a two-target row, never rows that appear under a switch; its page has the main switch and *Pair nearby*,
 * greyed while off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NearbyPageTest {
    @get:Rule val compose = createComposeRule()

    private val sharing = OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), 0, 0)

    @Test fun sharingShowsOneRowThatOpensThePage() {
        var opened = false
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                FollowersPage(sharing, FollowersActions(openNearby = { opened = true }), nearby = NearbyUi(status = NearbyStatus.Searching))
            }
        }
        assertEquals(0, compose.onAllNodesWithText("Pair nearby").fetchSemanticsNodes().size)
        compose.onNodeWithText("Over Bluetooth nearby").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test fun pageHasTheMainSwitchAndPairNearby() {
        var on: Boolean? = null
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                NearbyPage(sharing, NearbyUi(status = NearbyStatus.Searching), FollowersActions(setNearby = { on = it }))
            }
        }
        compose.onNodeWithText("Share over Bluetooth nearby").assertIsOn()
        compose.onNodeWithText("Pair nearby").assertIsEnabled()
        compose.onNodeWithText("Share over Bluetooth nearby").performClick()
        assertEquals(false, on)
    }

    @Test fun offGreysPairNearbyInsteadOfHidingIt() {
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { NearbyPage(sharing, NearbyUi(on = false), FollowersActions()) } }
        compose.onNodeWithText("Share over Bluetooth nearby").assertIsOff()
        compose.onNodeWithText("Pair nearby").assertIsNotEnabled()
    }

    @Test fun anOpenPairingKeepsItsSwitch() {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                NearbyPage(sharing, NearbyUi(on = false, pairingUntil = 2_000_000_000), FollowersActions(), now = { 1_999_999_000 })
            }
        }
        compose.onNodeWithText("Pair nearby").assertIsEnabled()
    }
}
