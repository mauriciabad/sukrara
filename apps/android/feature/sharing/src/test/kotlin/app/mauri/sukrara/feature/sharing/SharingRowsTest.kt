// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import app.mauri.sukrara.designsystem.rows.ROW_ICON_TAG
import app.mauri.sukrara.designsystem.rows.ROW_TAG
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.sharing.backup.BackupScreen
import app.mauri.sukrara.feature.sharing.backup.BackupUi
import app.mauri.sukrara.feature.sharing.followers.FollowersActions
import app.mauri.sukrara.feature.sharing.followers.FollowersPage
import app.mauri.sukrara.share.ble.NearbyStatus
import app.mauri.sukrara.share.owner.OwnerShareState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #181: Sharing's and Backup's rows carry their icons, as every settings row does (and the PoC's did). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharingRowsTest {
    @get:Rule val compose = createComposeRule()

    private val sharing = OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), backupPublishedAt = 0, recoveryExportedAt = 0)

    /** [content] in a scrolling column, unless it [scrolls] itself. */
    private fun show(
        scrolls: Boolean = false,
        content: @Composable () -> Unit,
    ) = compose.setContent {
        SukraraTheme(dark = false, dynamic = false) {
            if (scrolls) content() else Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        }
    }

    private fun assertIcon(title: String) {
        val icons =
            compose
                .onAllNodes(
                    hasTestTag(ROW_ICON_TAG) and hasAnyAncestor(hasTestTag(ROW_TAG) and hasAnyDescendant(hasText(title))),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
        assertTrue("$title has no icon", icons.isNotEmpty())
    }

    @Test
    fun followersRowsHaveIcons() {
        show(scrolls = true) {
            FollowersPage(
                sharing,
                FollowersActions(),
                nearby = NearbyUi(on = true, status = NearbyStatus.Searching, pairingUntil = null),
            )
        }
        for (t in listOf("Backup", "Share with someone", "Pair nearby")) assertIcon(t)
    }

    @Test
    fun backupRowsHaveIcons() {
        show { BackupScreen(sharing, BackupUi.Idle, onSave = {}, recoveryCode = { null }, onRecoveryKeep = {}) }
        for (t in listOf("Save to password manager", "Recovery code", "Replace this identity")) assertIcon(t)
    }
}
