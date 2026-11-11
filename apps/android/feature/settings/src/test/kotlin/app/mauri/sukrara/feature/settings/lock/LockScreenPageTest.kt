// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.lock

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.LockAppearance
import app.mauri.sukrara.core.model.LockBackground
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.statusbar.AccessStep
import app.mauri.sukrara.feature.settings.statusbar.lockAccessStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Lock screen's widget (#66): on by default and visible as such, its look chosen in dialogs, the module's
 * missing step as a card only where the page can fix it, and nothing for a phone without root or LSPosed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp")
class LockScreenPageTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun text(id: Int) = context.getString(id)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeAccess(
        module: ModuleState,
    ) : SystemAccessPort {
        override val state = MutableStateFlow(SystemAccessState(module = module))
        var refreshed = 0
        var restarts = 0
        var asked = 0

        override suspend fun checkRoot() {
            asked++
        }

        override fun refresh() {
            refreshed++
        }

        override suspend fun restartSystemUi(): String? {
            restarts++
            return null
        }

        override suspend fun requestScope(): String? = null
    }

    @Test fun `the card shows only the module's steps the page can fix, while the widget is on`() {
        assertNull(lockAccessStep(SystemAccessState(module = ModuleState.Restart), on = false))
        assertNull("no LSPosed: most phones, no card", lockAccessStep(SystemAccessState(module = ModuleState.Missing), on = true))
        assertNull(lockAccessStep(SystemAccessState(module = ModuleState.Checking), on = true))
        assertNull(lockAccessStep(SystemAccessState(module = ModuleState.Active), on = true))
        assertEquals(AccessStep.NoScope, lockAccessStep(SystemAccessState(module = ModuleState.NoScope), on = true))
        assertEquals(AccessStep.Restart, lockAccessStep(SystemAccessState(module = ModuleState.Restart), on = true))
        assertEquals(AccessStep.Update, lockAccessStep(SystemAccessState(module = ModuleState.Update), on = true))
        // it needs no root of its own: root's answers never show here
        assertNull(lockAccessStep(SystemAccessState(root = RootAccess.Refused, module = ModuleState.Missing), on = true))
    }

    @Test fun `the switch says what it does, what it waits for, and that it is off`() {
        assertEquals(
            R.string.st_lock_widget_on,
            lockWidgetSummary(LockWidgetUi(true, LockLook(), SystemAccessState(module = ModuleState.Active))),
        )
        assertEquals(
            R.string.st_lock_widget_on,
            lockWidgetSummary(LockWidgetUi(true, LockLook(), SystemAccessState(module = ModuleState.Update))),
        )
        assertEquals(
            R.string.st_lock_widget_needs_module,
            lockWidgetSummary(LockWidgetUi(true, LockLook(), SystemAccessState(module = ModuleState.Missing))),
        )
        assertEquals(
            R.string.st_lock_widget_off,
            lockWidgetSummary(LockWidgetUi(false, LockLook(), SystemAccessState(module = ModuleState.Active))),
        )
    }

    @Test fun `on by default with frosted glass, chosen in dialogs, turned off with the switch`() =
        runTest {
            val settings = InMemorySettingsRepository()
            val access = FakeAccess(ModuleState.Restart)
            val vm = LockScreenViewModel(settings, access)
            compose.setContent {
                SukraraTheme {
                    val ui by vm.widget.collectAsState()
                    ui?.let { LockScreenContent(20.0, {}, {}, it, vm.actions) }
                }
            }
            compose.onNodeWithText(text(R.string.st_lock_widget_needs_module)).assertExists()
            // the module waits for a restart: the card with its button, which restarts System UI
            compose.onNodeWithText(text(R.string.st_access_restart_text)).assertExists()
            // the card's title and its button
            compose.onAllNodesWithText(text(R.string.st_access_restart_title)).assertCountEquals(2)
            compose.onNodeWithText(text(R.string.st_lock_bg_frosted)).assertExists()
            compose.onNodeWithText(text(R.string.st_lock_look_auto)).assertExists()
            // Background › Shadow
            compose.onNodeWithText(text(R.string.st_lock_background)).assertIsEnabled().performClick()
            compose.onNodeWithText(text(R.string.st_lock_bg_shadow)).performClick()
            compose.waitForIdle()
            assertEquals(LockBackground.SHADOW.key, settings.flow(SurfaceSettings.LockWidgetBackground).first())
            // Appearance › Dark
            compose.onNodeWithText(text(R.string.st_lock_appearance)).performClick()
            compose.onNodeWithText(text(R.string.st_lock_look_dark)).performClick()
            compose.waitForIdle()
            assertEquals(LockAppearance.DARK.key, settings.flow(SurfaceSettings.LockWidgetAppearance).first())
            // off: its rows say why they're off, the card goes
            compose.onNodeWithText(text(R.string.st_lock_widget)).performClick()
            compose.waitForIdle()
            assertEquals(false, settings.flow(SurfaceSettings.LockWidget).first())
            compose.onNodeWithText(text(R.string.st_lock_widget_off)).assertExists()
            compose.onNodeWithText(text(R.string.st_access_restart_text)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.st_lock_background)).assertIsNotEnabled()
            vm.refresh()
            assertEquals("back on the page: LSPosed is asked again", 1, access.refreshed)
            assertEquals("never su", 0, access.asked)
        }
}
