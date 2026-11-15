// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.statusbar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.data.settings.HookPosition
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.core.model.NotificationIcon
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.designsystem.root.AccessStep
import app.mauri.sukrara.designsystem.root.accessStep
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * docs/36 §11, §14: Settings › Status bar & notification is one choice (chip, notification icon, status bar text with
 * root, nothing) and under it only the chosen one's rows, with the one step Android's settings still need. #178 still holds: the page never waits for root it never asked for; choosing
 * the status bar text is the request; each answer has its card and way out; the page asks again on its own only while
 * that choice is on and root isn't granted (back from the root manager).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp")
class GlucoseNotificationPageTest {
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

    /** The page as the app shows it, wired to [vm]. */
    private fun show(
        vm: StatusBarViewModel,
        liveUpdates: Boolean = true,
        shown: Boolean = true,
    ) = compose.setContent { Page(vm, AndroidNotification(liveUpdates, shown)) }

    @Composable
    private fun Page(
        vm: StatusBarViewModel,
        android: AndroidNotification,
    ) = SukraraTheme(dark = false, dynamic = false) {
        val prefs by vm.prefs.collectAsState()
        val access by vm.access.collectAsState()
        val busy by vm.busy.collectAsState()
        val problem by vm.problem.collectAsState()
        prefs?.let {
            StatusBarContent(
                it,
                vm.rootOffered,
                android,
                access,
                busy,
                problem,
                NotificationPageActions(
                    setStyle = vm::setStyle,
                    setIcon = vm::setIcon,
                    setColorize = vm::setColorize,
                    setLockScreen = vm::setLockScreen,
                    setAlertsInside = vm::setAlertsInside,
                    checkRoot = vm::checkRoot,
                ),
                {},
            )
        }
    }

    @Test fun `the step follows root first, then the module, only while the root choice is on`() {
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

    @Test fun `each choice shows only its own rows`() {
        val settings = InMemorySettingsRepository()
        val vm = StatusBarViewModel(settings, FakeAccess(RootAccess.Granted))
        show(vm, liveUpdates = false)
        // the chip (default): Android's Live Updates switch while it's off; no graph, so no icon to pick or background
        compose.onNodeWithText(text(R.string.st_statusbar_allow_live_updates)).assertExists()
        compose.onNodeWithText(text(R.string.st_statusbar_icon_group)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.st_statusbar_colorize)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.st_root_placement)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.st_statusbar_lock_screen)).assertExists()
        compose.onNodeWithText(text(DsR.string.ds_notif_style_icon)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.st_statusbar_allow_live_updates)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.st_statusbar_icon_group)).assertExists()
        compose.onNodeWithText(text(R.string.st_statusbar_icon_app)).assertExists()
        compose.onNodeWithText(text(R.string.st_statusbar_icon_none)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.st_statusbar_colorize)).assertExists()
        compose.onNodeWithText(text(DsR.string.ds_notif_style_root)).performClick()
        compose.waitForIdle()
        // hiding the icon only with the text, tagged Root
        compose.onNodeWithText(text(R.string.st_statusbar_icon_none)).assertExists()
        compose.onNodeWithText(text(R.string.st_root_placement)).assertExists()
        compose.onNodeWithText(text(R.string.st_root_colour)).assertExists()
        compose.onNodeWithText(text(R.string.st_statusbar_glucose_notification)).assertExists()
        compose.onNodeWithText(text(DsR.string.ds_notif_style_off)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.st_statusbar_icon_group)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.st_statusbar_lock_screen)).assertDoesNotExist()
    }

    @Test fun `Nothing asks for the one step in Android's settings until the notification is off`() {
        val settings = InMemorySettingsRepository()
        val vm = StatusBarViewModel(settings, null)
        vm.setStyle(NotificationStyle.Off)
        show(vm, shown = true)
        compose.onNodeWithText(text(R.string.st_statusbar_turn_off)).assertExists()
        compose.onNodeWithText(text(R.string.st_statusbar_no_notification_footer)).assertExists()
    }

    @Test fun `the notification off in Android under Nothing - no step, the alerts' footer`() {
        val vm = StatusBarViewModel(InMemorySettingsRepository(), null)
        vm.setStyle(NotificationStyle.Off)
        show(vm, shown = false)
        compose.onNodeWithText(text(R.string.st_statusbar_turn_off)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.st_statusbar_alerts)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.st_statusbar_no_notification_footer)).assertExists()
    }

    @Test fun `a choice that needs the notification while Android has it off asks to turn it on`() {
        val vm = StatusBarViewModel(InMemorySettingsRepository(), null)
        vm.setStyle(NotificationStyle.Icon)
        show(vm, shown = false)
        compose.onNodeWithText(text(R.string.st_statusbar_turn_on)).assertExists()
        compose.onNodeWithText(text(R.string.st_statusbar_icon_group)).assertDoesNotExist()
    }

    @Test fun `alerts in the notification is the separate alerts setting the other way round`() =
        runTest {
            val settings = InMemorySettingsRepository()
            val vm = StatusBarViewModel(settings, null)
            show(vm)
            compose.onNodeWithText(text(R.string.st_statusbar_alerts_on)).assertExists()
            compose.onNodeWithText(text(R.string.st_statusbar_alerts)).performClick()
            compose.waitForIdle()
            assertTrue(settings.flow(SurfaceSettings.SeparateAlerts).first())
            compose.onNodeWithText(text(R.string.st_statusbar_alerts_off)).assertExists()
        }

    @Test fun `a build without root features offers no root text`() {
        val vm = StatusBarViewModel(InMemorySettingsRepository(), null)
        assertFalse(vm.rootOffered)
        show(vm)
        compose.onNodeWithText(text(DsR.string.ds_notif_style_chip)).assertExists()
        compose.onNodeWithText(text(DsR.string.ds_notif_style_icon)).assertExists()
        compose.onNodeWithText(text(DsR.string.ds_notif_style_root)).assertDoesNotExist()
    }

    @Test fun `never asked - choosing the status bar text asks`() =
        runTest {
            // the owner's phone on 0.12.0: never asked, the switch was disabled behind "Checking root…" for good
            val access = FakeAccess(RootAccess.Missing, seen = true)
            val settings = InMemorySettingsRepository()
            val vm = StatusBarViewModel(settings, access)
            show(vm)
            vm.refresh()
            assertEquals("back on the page with another choice: no su", 0, access.asked)
            assertEquals(1, access.refreshed)
            compose.onNodeWithText(text(DsR.string.ds_notif_style_root)).performClick()
            compose.waitForIdle()
            assertEquals("choosing it is the request", 1, access.asked)
            assertEquals(NotificationStyle.Text.key, settings.flow(SurfaceSettings.StatusShow).first())
            assertEquals(
                "the status bar shows it: the notification's icon goes",
                NotificationIcon.None.key,
                settings.flow(SurfaceSettings.NotifIcon).first(),
            )
            // KernelSU hides su until Sukrara is allowed; LSPosed says root is there: how to allow it
            compose.onNodeWithText(text(DsR.string.ds_access_not_allowed_title)).assertExists()
            compose.onNodeWithText(text(R.string.st_root_unavailable_no_root)).assertExists()
            // allowed in the root manager, then back: the page asks again by itself
            access.answer = RootAccess.Granted
            vm.refresh()
            assertEquals(2, access.asked)
            assertEquals(RootAccess.Granted, access.state.value.root)
            // granted: back on the page asks nothing (Magisk would toast each time)
            vm.refresh()
            assertEquals(2, access.asked)
        }

    @Test fun `a refusal says so and Check again asks again`() =
        runTest {
            val access = FakeAccess(RootAccess.Refused, seen = true)
            access.state.update { it.copy(root = RootAccess.Refused) }
            val settings = InMemorySettingsRepository()
            settings.set(SurfaceSettings.StatusShow, NotificationStyle.Text.key)
            val vm = StatusBarViewModel(settings, access)
            show(vm)
            compose.onNodeWithText(text(DsR.string.ds_access_refused_title)).assertExists()
            compose.onNodeWithText(text(DsR.string.ds_access_root_action)).performClick()
            compose.waitForIdle()
            assertEquals(1, access.asked)
            // asking shows its own card, without a button to press twice
            access.state.update { SystemAccessState(asking = true) }
            compose.waitForIdle()
            compose.onNodeWithText(text(DsR.string.ds_access_asking_title)).assertExists()
            compose.onNodeWithText(text(DsR.string.ds_access_root_action)).assertDoesNotExist()
        }

    @Test fun `choosing the status bar text with root granted asks nothing`() =
        runTest {
            val access = FakeAccess(RootAccess.Granted)
            access.state.update { it.copy(root = RootAccess.Granted) }
            val settings = InMemorySettingsRepository()
            val vm = StatusBarViewModel(settings, access)
            vm.setStyle(NotificationStyle.Text)
            assertEquals(0, access.asked)
            assertEquals(NotificationStyle.Text.key, settings.flow(SurfaceSettings.StatusShow).first())
            assertEquals(HookPosition.DEFAULT, settings.flow(SurfaceSettings.RootPosition).first())
            vm.refresh()
            assertEquals(0, access.asked)
        }
}
