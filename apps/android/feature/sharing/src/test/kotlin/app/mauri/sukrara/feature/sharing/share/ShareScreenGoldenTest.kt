// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.share

import android.content.res.Configuration
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.sharing.Invite
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import com.github.takahirom.roborazzi.uiMode
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

/**
 * Goldens of *Share with someone* (#170: in the dark theme it drew black text on the dark window, because nothing under
 * it set a background or a content colour). The screen is drawn exactly as the app places it, in the app's theme and
 * nothing else (no Surface around it, unlike the setup gallery), so it has to bring its own page: light and dark, before
 * and after an invite (synthetic link). Recorded on CI by .github/workflows/goldens.yml (only this class: the module's
 * other galleries are for review, never goldens), compared by checks.yml with `-Proborazzi.test.verify=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ShareScreenGoldenTest {
    private val savedZone = TimeZone.getDefault()
    private val savedLocale = Locale.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    private val ready =
        InviteUi.Ready(Invite("https://sukrara.mauri.app/join#c3ludGhldGljLWludml0ZS1mb3ItdGVzdHM", "Anna", 1_761_600_000))

    private fun shot(
        name: String,
        dark: Boolean,
        invite: InviteUi,
    ) {
        val options =
            RoborazziComposeOptions {
                size(widthDp = 411, heightDp = 891)
                uiMode(if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            }
        captureRoboImage("src/test/snapshots/$name.png", roborazziComposeOptions = options) {
            // what MainActivity puts around the navigation: the app's theme only
            SukraraTheme(dark = dark, dynamic = false) { ShareScreen(invite, {}, {}, {}) }
        }
    }

    @Test
    fun newInviteDark() = shot("share-screen-new-dark", dark = true, InviteUi.None)

    @Test
    fun newInviteLight() = shot("share-screen-new-light", dark = false, InviteUi.None)

    @Test
    fun readyInviteDark() = shot("share-screen-ready-dark", dark = true, ready)
}
