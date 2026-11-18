// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.designsystem.source.onlineHealthText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * An online source's health in words (setup's Connect step, Settings › Source): a reading under a minute old is *just
 * now*; the QA review (2026-11-18) saw *Live · last reading 0 minutes ago* on the LibreLinkUp Connect step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en")
class OnlineHealthTextTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-18T15:19:20Z")

    @Test
    fun aReadingUnderAMinuteOldIsJustNow() {
        compose.setContent {
            Text(onlineHealthText(OnlineHealth.Live, now.minusSeconds(50), now, "LibreLinkUp", SourceKind.LibreLinkUp))
        }
        compose.onNodeWithText("Live · last reading just now").assertExists()
    }

    @Test
    fun anOlderOneSaysItsMinutes() {
        compose.setContent {
            Text(onlineHealthText(OnlineHealth.Live, now.minusSeconds(130), now, "LibreLinkUp", SourceKind.LibreLinkUp))
        }
        compose.onNodeWithText("Live · last reading 2 minutes ago").assertExists()
    }
}
