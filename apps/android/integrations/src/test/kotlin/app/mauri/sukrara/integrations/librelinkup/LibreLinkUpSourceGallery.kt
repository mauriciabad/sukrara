// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.librelinkup

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
import app.mauri.sukrara.core.data.libreview.LluPerson
import app.mauri.sukrara.core.model.MaintenanceWindow
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.integrations.nightscout.site.NsSensor
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
 * Settings › Source's LibreLinkUp rows for the UX review (synthetic state only): each health (LibreLinkUp's own too),
 * several people, someone else's glucose, light + dark, plus Spanish. Images go to build/outputs/roborazzi (no goldens).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class LibreLinkUpSourceGallery {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val yours =
        LluSourceView(
            signedIn = true,
            email = "alex@example.com",
            patient = "p1",
            name = "Alex Example",
            people = listOf(LluPerson("p1", "Alex Example")),
            health = OnlineHealth.Live,
            newest = now.minusSeconds(70),
            sensor = NsSensor("0D00ABC123", now.minusSeconds(3 * 86400)),
        )
    private val theirs =
        yours.copy(
            email = "parent@example.com",
            patient = "p2",
            name = "Ana Example",
            people = listOf(LluPerson("p2", "Ana Example"), LluPerson("p3", "Leo")),
        )

    private fun rows(
        view: LluSourceView,
        person: Person = Person.Yours,
    ): @Composable () -> Unit = { LibreLinkUpSourceRows(view, person, now, LibreLinkUpSourceActions()) }

    private val pages: List<Pair<String, @Composable () -> Unit>> =
        listOf(
            "live" to rows(yours),
            "silent" to rows(yours.copy(health = OnlineHealth.NoNewReadings, newest = now.minusSeconds(2400))),
            // a new sensor warming up, the newest reading the old sensor's (the owner's test, 2026-11-18)
            "warming-up" to
                rows(
                    yours.copy(
                        health = OnlineHealth.WarmingUp,
                        newest = now.minusSeconds(4 * 3600),
                        sensor = NsSensor("0D00NEW456", now.minusSeconds(20 * 60)),
                    ),
                ),
            "signed-out" to rows(yours.copy(health = OnlineHealth.SignedOut)),
            "terms" to rows(yours.copy(health = OnlineHealth.ActionNeeded)),
            "not-shared" to rows(theirs.copy(health = OnlineHealth.NotShared), Person.Theirs),
            "stopped" to rows(yours.copy(health = OnlineHealth.Stopped)),
            "rate-limited" to rows(yours.copy(health = OnlineHealth.RateLimited)),
            "theirs" to rows(theirs, Person.Theirs),
            // LibreView's maintenance (docs/45): a wait with its end, then none known; a window ahead
            "maintenance" to
                rows(yours.copy(health = OnlineHealth.Maintenance, newest = now.minusSeconds(1500), until = now.plusSeconds(1800))),
            "maintenance-open" to rows(yours.copy(health = OnlineHealth.Maintenance, newest = now.minusSeconds(1500))),
            "maintenance-ahead" to
                rows(yours.copy(ahead = MaintenanceWindow(now.plusSeconds(10 * 3600), now.plusSeconds(11 * 3600)))),
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
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/librelinkup-source-$prefix$name-$theme.png")
        }
        assertTrue(repeats.joinToString("\n"), repeats.isEmpty())
    }

    @Test fun light() = gallery(dark = false)

    @Test fun dark() = gallery(dark = true)

    @Test
    @Config(qualifiers = "es")
    fun spanish() = gallery(dark = false, prefix = "es-")
}
