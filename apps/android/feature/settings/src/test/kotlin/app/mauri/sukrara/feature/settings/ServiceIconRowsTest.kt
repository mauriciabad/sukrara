// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.serviceIconTag
import app.mauri.sukrara.designsystem.rows.ROW_ICON_TAG
import app.mauri.sukrara.designsystem.rows.ROW_TAG
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsActions
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsContent
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsState
import app.mauri.sukrara.feature.settings.sensor.SensorSummary
import app.mauri.sukrara.feature.settings.sensor.SourceChoice
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/**
 * #181 ("in settings we had app icons in some items, bring all of them back"): the settings rows that stand for
 * Juggluco show its own icon (the reading source's radio), as the PoC's did, and the overview's clinic banner shows LibreView's and Tidepool's. Review images:
 * build/outputs/roborazzi/icons-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ServiceIconRowsTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-10-27T14:00:00Z")
    private val zone = ZoneOffset.UTC

    /** The row titled [title] carries the icon tagged [tag]. */
    private fun assertRowIcon(
        title: String,
        tag: String,
    ) {
        val icons =
            compose
                .onAllNodes(
                    hasTestTag(tag) and hasAnyAncestor(hasTestTag(ROW_TAG) and hasAnyDescendant(hasText(title))),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
        assertTrue("$title has no $tag", icons.isNotEmpty())
    }

    /** The icons load off the main thread: let them arrive before the review image. */
    private fun capture(name: String) {
        Thread.sleep(300)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/icons-$name.png")
    }

    @Test
    fun theSourcePageShowsJugglucosOwnIcon() {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                app.mauri.sukrara.feature.settings.source.SourcePageContent(
                    app.mauri.sukrara.feature.settings.source.SourcePageState(
                        app.mauri.sukrara.core.model.SourceSpec(
                            app.mauri.sukrara.core.model.Person.Yours,
                            app.mauri.sukrara.core.model.SourceKind.PhoneApp,
                        ),
                        answered = true,
                    ),
                    null,
                    now,
                    app.mauri.sukrara.feature.settings.source
                        .SourceActions(),
                    SensorSettingsActions(),
                )
            }
        }
        assertRowIcon("Where it comes from", serviceIconTag(Service.Juggluco))
        assertRowIcon("Whose glucose", ROW_ICON_TAG)
        capture("source")
    }

    @Test
    fun overviewClinicBannerShowsTheServices() {
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                SettingsOverviewContent(
                    true,
                    AlertUiState(),
                    AlertReadiness.Ready,
                    FakeClock(now, zone),
                    OverviewActions(),
                    clinicBanner = true,
                ) {}
            }
        }
        compose.onAllNodesWithTag(serviceIconTag(Service.LibreView), useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag(serviceIconTag(Service.Tidepool), useUnmergedTree = true).assertCountEquals(1)
        capture("overview-clinic")
    }
}
