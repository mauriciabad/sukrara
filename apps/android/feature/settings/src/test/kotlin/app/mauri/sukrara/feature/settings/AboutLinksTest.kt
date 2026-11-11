// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * About's links (#179): *Sukrara on other devices* opens each app's page on the website (never a download), whatever
 * the update check says and on the Google Play build (no check) too; the other rows open their pages. #142: the
 * version row says which version this is and *About the versions* opens the website's explanation, on both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp")
class AboutLinksTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private var update by mutableStateOf<UpdateState?>(UpdateState.UpToDate)
    private var distribution by mutableStateOf(Distribution.GitHub)

    private fun show() =
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { AboutContent("0.12.0", distribution, update, {}) {} } }

    /** Taps the row titled [title] and returns the URL it opened in the browser. */
    private fun open(title: Int): String {
        compose.onNodeWithText(app.getString(title)).performScrollTo().performClick()
        val intent = checkNotNull(shadowOf(app).nextStartedActivity) { "nothing opened" }
        assertEquals(Intent.ACTION_VIEW, intent.action)
        return intent.dataString.orEmpty()
    }

    @Test
    fun `other devices open the website's page per app`() {
        assertEquals(listOf("https://sukrara.mauri.app/mac/"), OTHER_DEVICES.map { it.url })
        show()
        for (state in listOf(UpdateState.UpToDate, UpdateState.Checking, UpdateState.Offline, null)) {
            update = state
            assertEquals("$state", "https://sukrara.mauri.app/mac/", open(R.string.st_device_mac))
        }
    }

    @Test
    fun `the version says which one it is and About the versions opens the website's section`() {
        assertEquals("https://sukrara.mauri.app/android/#versions", VERSIONS_URL)
        show()
        compose.onNodeWithText("0.12.0 · GitHub version").assertExists()
        assertEquals(VERSIONS_URL, open(R.string.st_about_versions))
        // the Google Play build: no update check, the same link, nothing disabled or "get it elsewhere"
        distribution = Distribution.GooglePlay
        update = null
        compose.onNodeWithText("0.12.0 · Google Play version").assertExists()
        compose.onNodeWithText(app.getString(R.string.st_update_check_again)).assertDoesNotExist()
        assertEquals(VERSIONS_URL, open(R.string.st_about_versions))
    }

    @Test
    fun `the other rows open the issues, the source and the privacy policy`() {
        show()
        assertEquals(UpdateCheck.ISSUES_URL, open(R.string.st_report_problem))
        assertEquals(UpdateCheck.REPO_URL, open(R.string.st_source_code))
        assertEquals("https://sukrara.mauri.app/privacy", open(R.string.st_privacy))
    }

    @Test
    fun `About says Sukrara isn't a medical device, on both versions`() {
        show()
        val reminder = app.getString(R.string.st_not_medical)
        assertTrue(reminder.startsWith("Sukrara isn't a medical device"))
        compose.onNodeWithText(reminder).performScrollTo().assertIsDisplayed()
        distribution = Distribution.GooglePlay
        update = null
        compose.onNodeWithText(reminder).performScrollTo().assertIsDisplayed()
    }
}
