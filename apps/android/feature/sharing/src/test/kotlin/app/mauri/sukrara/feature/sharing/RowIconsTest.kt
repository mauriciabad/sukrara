// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.designsystem.rows.RowIconRes
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

/**
 * No row icon twice on one page (the owner, 2026-11-15: "icons are to make items easy to tell apart and read faster"):
 * every setup step, Settings › Sharing and its pages, the invite, the approve dialog and the follower sheet, the
 * scenes [SharingScreenshotTest] renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class RowIconsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun noPageRepeatsARowIcon() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        var current: @Composable () -> Unit by mutableStateOf({})
        // spinners and countdowns never go idle: the clock moves by hand
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SukraraTheme(dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { current() }
            }
        }
        val repeats = mutableListOf<String>()
        // the leaked-link dialog's code field blinks its cursor for ever (never idle); its rows are the plain dialog's
        for ((name, scene) in SharingScreenshotTest().scenes().filter { it.first != "approve-leaked" }) {
            current = scene
            compose.mainClock.advanceTimeBy(SETTLE_MS)
            val twice =
                compose
                    .onAllNodes(SemanticsMatcher.keyIsDefined(RowIconRes), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .map { it.config[RowIconRes] }
                    .groupingBy { it }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
                    .map { resources.getResourceEntryName(it) }
            if (twice.isNotEmpty()) repeats += "$name repeats $twice"
        }
        assertTrue(repeats.joinToString("\n"), repeats.isEmpty())
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
