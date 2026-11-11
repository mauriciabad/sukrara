// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.statusbar

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.data.settings.HookPosition
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
 * #178: the root section never waits for root it never asked for. The switch is always there to turn on (that is the
 * request for root); each answer has its card and way out; the page asks again on its own only while the value is on
 * and root isn't granted (back from the root manager), never for someone who never turned it on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp")
class RootAccessPageTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun text(id: Int) = context.getString(id)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** A port whose `su` answers [answer]; counts the requests. */
    private class FakeAccess(
        var answer: RootAccess,
        seen: Boolean = false,
    ) : SystemAccessPort {
        override val state = MutableStateFlow(SystemAccessState(rootSeen = seen, module = ModuleState.Restart))
        var asked = 0
        var refreshed = 0

        override suspend fun checkRoot() {
            asked++
            state.update { it.copy(root = answer) }
        }

        override fun refresh() {
            refreshed++
        }

        override suspend fun restartSystemUi(): String? = null

        override suspend fun requestScope(): String? = null
    }

    @Test fun `the step follows root first, then the module, only while the value is on`() {
        val granted = SystemAccessState(root = RootAccess.Granted)
        assertNull(accessStep(SystemAccessState(root = RootAccess.Missing), valueOn = false))
        assertNull("not asked yet: the request is on its way", accessStep(SystemAccessState(), valueOn = true))
        assertEquals(AccessStep.Asking, accessStep(SystemAccessState(asking = true), valueOn = true))
        assertEquals(AccessStep.NoRoot, accessStep(SystemAccessState(root = RootAccess.Missing), valueOn = true))
        assertEquals(AccessStep.NotAllowed, accessStep(SystemAccessState(root = RootAccess.Missing, rootSeen = true), valueOn = true))
        assertEquals(AccessStep.Refused, accessStep(SystemAccessState(root = RootAccess.Refused, rootSeen = true), valueOn = true))
        assertEquals(AccessStep.NoAnswer, accessStep(SystemAccessState(root = RootAccess.NoAnswer), valueOn = true))
        assertEquals(AccessStep.ModuleOff, accessStep(granted.copy(module = ModuleState.Missing), valueOn = true))
        assertEquals(AccessStep.NoScope, accessStep(granted.copy(module = ModuleState.NoScope), valueOn = true))
        assertEquals(AccessStep.Restart, accessStep(granted.copy(module = ModuleState.Restart), valueOn = true))
        assertEquals(AccessStep.Update, accessStep(granted.copy(module = ModuleState.Update), valueOn = true))
        assertNull(accessStep(granted.copy(module = ModuleState.Active), valueOn = true))
        // granted root re-checked: the module's step stays, no "asking" card over a working status bar
        assertEquals(AccessStep.Restart, accessStep(granted.copy(module = ModuleState.Restart, asking = true), valueOn = true))
    }

    @Test fun `never asked - the switch is there to turn on, and turning it on asks`() {
        // the owner's phone on 0.12.0: never asked, the switch was disabled behind "Checking root…" for good
        val access = FakeAccess(RootAccess.Missing, seen = true)
        val vm = StatusBarViewModel(InMemorySettingsRepository(), access)
        var on by mutableStateOf(false)
        var state by mutableStateOf(access.state.value)
        val actions =
            RootActions(
                setOn = {
                    on = it
                    vm.actions.setOn(it)
                    state = access.state.value
                },
                setPosition = {},
                setColor = {},
                setHideIcon = {},
                checkRoot = {
                    vm.actions.checkRoot()
                    state = access.state.value
                },
                requestScope = {},
                restart = {},
            )
        compose.setContent {
            Page {
                RootStatusBarSection(
                    RootSettings(on, HookPosition.DEFAULT, color = true, hideIcon = true),
                    state,
                    false,
                    null,
                    false,
                    {},
                    actions,
                )
            }
        }
        vm.refresh()
        assertEquals("back on the page with the value off: no su", 0, access.asked)
        assertEquals(1, access.refreshed)
        compose.onNodeWithText(text(R.string.st_root_value_off_needs_root)).assertExists()
        compose
            .onNodeWithText(text(R.string.st_root_value))
            .assertIsEnabled()
            .performClick()
        compose.waitForIdle()
        assertEquals("turning it on is the request", 1, access.asked)
        // KernelSU hides su until Sukrara is allowed; LSPosed says root is there: how to allow it
        compose.onNodeWithText(text(R.string.st_access_not_allowed_title)).assertExists()
        compose.onNodeWithText(text(R.string.st_root_value_waiting)).assertExists()
        // colour, placement and the notification icon say what they wait for (not "while the value is off")
        compose.onAllNodesWithText(text(R.string.st_root_unavailable_no_root)).assertCountEquals(3)
        // allowed in the root manager, then back: the page asks again by itself
        access.answer = RootAccess.Granted
        vm.refresh()
        assertEquals(2, access.asked)
        assertEquals(RootAccess.Granted, access.state.value.root)
        // granted: back on the page asks nothing (Magisk would toast each time)
        vm.refresh()
        assertEquals(2, access.asked)
    }

    @Test fun `a refusal says so and Check again asks again`() {
        val access = FakeAccess(RootAccess.Refused)
        val vm = StatusBarViewModel(InMemorySettingsRepository(), access)
        var state by mutableStateOf(SystemAccessState(root = RootAccess.Refused, rootSeen = true))
        val actions =
            RootActions({}, {}, {}, {}, {
                vm.actions.checkRoot()
                state = access.state.value
            }, {}, {})
        compose.setContent {
            Page {
                RootStatusBarSection(
                    RootSettings(true, HookPosition.DEFAULT, color = true, hideIcon = true),
                    state,
                    false,
                    null,
                    false,
                    {},
                    actions,
                )
            }
        }
        compose.onNodeWithText(text(R.string.st_access_refused_title)).assertExists()
        compose.onNodeWithText(text(R.string.st_access_root_action)).performClick()
        compose.waitForIdle()
        assertEquals(1, access.asked)
        // asking shows its own card, without a button to press twice
        state = SystemAccessState(asking = true)
        compose.onNodeWithText(text(R.string.st_access_asking_title)).assertExists()
        compose.onNodeWithText(text(R.string.st_root_value_asking)).assertExists()
        compose.onNodeWithText(text(R.string.st_access_root_action)).assertDoesNotExist()
    }

    @Test fun `turning the value on with root granted asks nothing`() =
        runTest {
            val access = FakeAccess(RootAccess.Granted)
            access.state.update { it.copy(root = RootAccess.Granted) }
            val settings = InMemorySettingsRepository()
            val vm = StatusBarViewModel(settings, access)
            vm.actions.setOn(true)
            assertEquals(0, access.asked)
            settings.set(SurfaceSettings.RootValue, true)
            vm.refresh()
            assertEquals(0, access.asked)
        }
}

/** The section as the page lays it out: one column. */
@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) = SukraraTheme(dark = false, dynamic = false) { Column(content = content) }
