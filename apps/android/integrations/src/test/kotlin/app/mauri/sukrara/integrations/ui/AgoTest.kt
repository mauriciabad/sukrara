// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** An upload or a call under a minute ago reads *just now*, never "0 minutes ago" (docs/44 Appendix G finding 18). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgoTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-18T12:00:00Z")

    @Test
    fun `under a minute is just now`() {
        compose.setContent { Text(ago(now.minusSeconds(20), now)) }
        compose.onNodeWithText("just now").assertExists()
    }

    @Test
    fun `from a minute on, minutes ago`() {
        compose.setContent { Text(ago(now.minusSeconds(5 * 60), now)) }
        compose.onNodeWithText("5 minutes ago").assertExists()
    }
}
