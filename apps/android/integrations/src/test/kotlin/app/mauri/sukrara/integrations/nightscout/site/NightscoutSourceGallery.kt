// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.integrations.repeatedRowIcons
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * Settings › Source's Nightscout rows for the UX review (synthetic state only): each health, the treatments' states,
 * someone else's site, light + dark, plus Spanish. Images go to build/outputs/roborazzi (no goldens).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class NightscoutSourceGallery {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val site =
        NsSourceView(
            root = "https://ns.example.org",
            host = "ns.example.org",
            configured = true,
            health = OnlineHealth.Live,
            newest = now.minusSeconds(120),
        )

    private fun rows(
        view: NsSourceView,
        person: Person = Person.Yours,
    ): @Composable () -> Unit = { NightscoutSourceRows(view, person, now, NightscoutSourceActions()) }

    private val pages: List<Pair<String, @Composable () -> Unit>> =
        listOf(
            "live" to rows(site),
            "late" to rows(site.copy(health = OnlineHealth.Late, newest = now.minusSeconds(540))),
            "silent" to rows(site.copy(health = OnlineHealth.NoNewReadings, newest = now.minusSeconds(2400))),
            "signed-out" to rows(site.copy(health = OnlineHealth.SignedOut)),
            "unreachable" to rows(site.copy(health = OnlineHealth.Unreachable)),
            "offline" to rows(site.copy(health = OnlineHealth.Offline)),
            "read-only" to rows(site.copy(treatments = NsTreatments.ReadOnly)),
            "treatments-off" to rows(site.copy(treatments = NsTreatments.Off)),
            "pulling" to rows(site.copy(pulling = now.minusSeconds(90L * 86400))),
            "theirs" to rows(site, Person.Theirs),
        )

    private fun gallery(
        dark: Boolean,
        prefix: String = "",
    ) {
        var current by mutableStateOf(pages.first().second)
        compose.setContent {
            SukraraTheme(dark = dark, dynamic = false) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).verticalScroll(rememberScrollState()),
                ) {
                    current()
                }
            }
        }
        val theme = if (dark) "dark" else "light"
        val repeats = mutableListOf<String>()
        pages.forEach { (name, page) ->
            current = page
            compose.waitForIdle()
            if (!dark && prefix.isEmpty()) compose.repeatedRowIcons().takeIf { it.isNotEmpty() }?.let { repeats += "$name repeats $it" }
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/nightscout-source-$prefix$name-$theme.png")
        }
        assertTrue(repeats.joinToString("\n"), repeats.isEmpty())
    }

    @Test fun light() = gallery(dark = false)

    @Test fun dark() = gallery(dark = true)

    @Test
    @Config(qualifiers = "es")
    fun spanish() = gallery(dark = false, prefix = "es-")
}
