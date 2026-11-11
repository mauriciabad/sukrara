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
import app.mauri.sukrara.core.model.AlertHold
import app.mauri.sukrara.core.model.UpdateFailure
import app.mauri.sukrara.core.model.UpdateRelease
import app.mauri.sukrara.core.model.UpdateState
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

    /** What About's update rows asked the updater for. */
    private val asked = mutableListOf<String>()
    private val updates =
        AboutUpdates(
            check = { asked += "check" },
            update = { asked += "update" },
            cancel = { asked += "cancel" },
            confirm = { asked += "confirm" },
            automaticChecks = true,
            automaticInstall = false,
            setAutomaticChecks = { asked += "checks $it" },
            setAutomaticInstall = { asked += "install $it" },
        )

    private fun show() =
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                AboutContent("0.12.0", distribution, update, updates.takeIf { distribution == Distribution.GitHub }) {}
            }
        }

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
        compose.onNodeWithText(app.getString(R.string.st_update_auto_checks)).assertDoesNotExist()
        assertEquals(VERSIONS_URL, open(R.string.st_about_versions))
    }

    /** #188: each update state's row does its next step or its way out; the release notes open the release's page. */
    @Test
    fun `every update state has its way on and its way out`() {
        val release =
            UpdateRelease("0.13.0", "https://github.com/mauriciabad/sukrara/releases/tag/v0.13.0", prerelease = true, installable = true)
        show()

        fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

        tap(app.getString(R.string.st_update_none))
        update = UpdateState.Available(release)
        tap(app.getString(R.string.st_update_available, "0.13.0"))
        assertEquals(release.notesUrl, open(R.string.st_update_notes))
        update = UpdateState.Downloading(release, 0.5f)
        tap(app.getString(R.string.st_update_downloading, "0.13.0"))
        update = UpdateState.Waiting(release, AlertHold(setOf("low")))
        tap(app.getString(R.string.st_update_ready, "0.13.0"))
        update = UpdateState.Confirm(release)
        tap(app.getString(R.string.st_update_ready, "0.13.0"))
        tap(app.getString(R.string.st_update_cancel))
        update = UpdateState.Failed(release, UpdateFailure.Download)
        tap(app.getString(R.string.st_update_failed, "0.13.0"))
        compose.onNodeWithText(app.getString(R.string.st_update_failed_download, "0.12.0")).assertExists()
        update = UpdateState.Offline
        tap(app.getString(R.string.st_update_offline))
        assertEquals(ProjectLinks.RELEASES_URL, open(R.string.st_update_offline_summary))
        update = UpdateState.Installing(release)
        compose.onNodeWithText(app.getString(R.string.st_update_installing, "0.13.0")).assertExists()
        assertEquals(listOf("check", "update", "cancel", "cancel", "confirm", "cancel", "update", "check"), asked)
    }

    @Test
    fun `the switches turn the daily check and automatic installs on and off`() {
        show()
        compose.onNodeWithText(app.getString(R.string.st_update_auto_checks)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.st_update_auto_install)).performScrollTo().performClick()
        assertEquals(listOf("checks false", "install true"), asked)
    }

    @Test
    fun `a release without an APK says to open its page`() {
        update =
            UpdateState.Available(
                UpdateRelease("0.13.0", "https://github.com/mauriciabad/sukrara/releases/tag/v0.13.0", false, installable = false),
            )
        show()
        compose.onNodeWithText(app.getString(R.string.st_update_open)).assertExists()
    }

    @Test
    fun `the other rows open the issues, the source and the privacy policy`() {
        show()
        assertEquals(ProjectLinks.ISSUES_URL, open(R.string.st_report_problem))
        assertEquals(ProjectLinks.REPO_URL, open(R.string.st_source_code))
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
