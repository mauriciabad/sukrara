// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.search

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertQuietReason
import app.mauri.sukrara.core.model.AlertQuietSide
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.designsystem.rows.SettingsHighlight
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.AppOutput
import app.mauri.sukrara.feature.settings.Distribution
import app.mauri.sukrara.feature.settings.FakeAlertSettingsStore
import app.mauri.sukrara.feature.settings.FakeReadiness
import app.mauri.sukrara.feature.settings.NoSystemAccess
import app.mauri.sukrara.feature.settings.RecordingControls
import app.mauri.sukrara.feature.settings.SettingsDeps
import app.mauri.sukrara.feature.settings.SettingsRoute
import app.mauri.sukrara.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

/**
 * #181, the owner's crash: search "night", open *Night alerts*, the app crashes (and monitoring with it, in the same
 * process). The pages it opens word the night alerts in force (*Night alerts until 7:00*) and a quiet side (*Low alerts
 * muted after carbs · 25 min left*) with strings found by name (AlertWords), and 0.13–0.14.1's release builds had none
 * of them (resource shrinking can't see a name): the lookup threw on the missing string. Here the pages are opened from
 * the search through the real settings screens, with resources that find nothing by name, as in those builds: it threw
 * `Resources.NotFoundException` before AlertWords found its words by the ids written in code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SearchOpensNightAlertsTest {
    @get:Rule val compose = createComposeRule()

    @After fun clear() = SettingsHighlight.request(null)

    private val now = Instant.parse("2026-11-11T23:00:00Z")

    private val deps =
        object : SettingsDeps {
            override val settings = InMemorySettingsRepository()
            override val alertSettings = FakeAlertSettingsStore(AlertSettings().let { it.copy(night = it.night.copy(on = true)) })
            override val alertControls = RecordingControls()
            override val alertState =
                flowOf(
                    AlertUiState(
                        nightUntil = now.plusSeconds(8 * 3600),
                        quiet = listOf(AlertQuietSide("low", true, AlertQuietReason.AfterCarbs, now.plusSeconds(25 * 60), 0, false)),
                    ),
                )
            override val readiness = FakeReadiness()
            override val appVersion = "0.14.2"
            override val distribution = Distribution.GitHub
            override val updates = null
            override val clock = FakeClock(now, ZoneOffset.UTC)
            override val sensor = flowOf(SensorState.None)
            override val appsShared = flowOf(emptySet<AppOutput>())
            override val systemAccess = NoSystemAccess
        }

    /** The settings page on top of the back stack ([depth] pages), as the app shows it. */
    @Composable
    private fun Page(
        route: SettingsRoute,
        depth: Int,
        navigate: (SettingsRoute) -> Unit,
        onBack: () -> Unit,
    ) {
        key(depth, route) { SettingsScreen(deps, route, navigate, onBack, openSensor = {}, openSharing = {}) }
    }

    @Test
    fun searchNightThenOpenNightAlerts() {
        val stack = mutableStateListOf<SettingsRoute>(SettingsRoute.Overview)
        compose.setContent {
            // a release build of 0.13–0.14.1: nothing is found by name
            CompositionLocalProvider(LocalContext provides NothingByName(LocalContext.current)) {
                SukraraTheme(dark = false, dynamic = false) {
                    Page(stack.last(), stack.size, { stack += it }, { stack.removeAt(stack.lastIndex) })
                }
            }
        }
        compose.onAllNodesWithText("Search settings").onLast().performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("night")
        compose.onNodeWithText("Night alerts").performClick()
        compose.waitForIdle()
        // through the menus' path: Alerts, then Night alerts, which says they apply now
        assertEquals(listOf(SettingsRoute.Overview, SettingsRoute.Alerts, SettingsRoute.NightAlerts), stack.toList())
        compose.onNodeWithText("Night alerts until", substring = true).assertIsDisplayed()
        // Back: Alerts, with the quiet side's line
        stack.removeAt(stack.lastIndex)
        compose.waitForIdle()
        compose.onAllNodesWithText("Low alerts muted after carbs", substring = true)[0].assertExists()
    }
}

/**
 * Resources that find none of the app's own resources by name ([Resources.getIdentifier] = 0; Android's own are still
 * there): what resource shrinking left of the strings found only by name.
 */
private class NothingByName(
    base: Context,
) : ContextWrapper(base) {
    @Suppress("DEPRECATION")
    private val shrunk =
        object : Resources(base.resources.assets, base.resources.displayMetrics, base.resources.configuration) {
            override fun getIdentifier(
                name: String?,
                defType: String?,
                defPackage: String?,
            ): Int = if (defPackage == base.packageName) 0 else super.getIdentifier(name, defType, defPackage)
        }

    override fun getResources(): Resources = shrunk
}
