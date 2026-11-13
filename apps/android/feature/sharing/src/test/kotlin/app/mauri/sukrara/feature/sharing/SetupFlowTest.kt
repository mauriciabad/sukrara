// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.sharing.setup.SetupRecord
import app.mauri.sukrara.feature.sharing.setup.SetupScreen
import app.mauri.sukrara.feature.sharing.setup.SetupStep
import app.mauri.sukrara.feature.sharing.setup.SetupViewModel
import app.mauri.sukrara.feature.sharing.setup.UpdateChoice
import app.mauri.sukrara.share.owner.OwnerShareState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Setup as the person moves through it (#175): the real [SetupScreen] and view model over a home stand-in, with
 * Back, a rotation ([StateRestorationTester]: the composition is rebuilt from saved state, the view model stays) and
 * home's *Finish setup* banner. The app's navigation does the same: setup on top of home, leaving pops it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en")
class SetupFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val clock = FakeClock()
    private val roles = FakeRoles()
    private val sharing = FakeOwnerSharing()
    private val record = InMemoryStateStore(SetupRecord())

    /** The GitHub version's update check (null [updates] = a build without an updater, as Google Play). */
    private val updateChoice = MutableStateFlow(UpdateChoice.Open)
    private var updates: SetupUpdates? = null

    private val module by lazy {
        SetupModule(
            ApplicationProvider.getApplicationContext<Application>(),
            record,
            roles,
            FakeHub(),
            sharing,
            FakeAlerts(),
            InMemorySettingsRepository(),
            resetAlerts = {},
            startMonitoring = {},
            readiness = FakeReadiness(),
            clock = clock,
            updates = updates,
        )
    }

    private fun recorded() = (record.data as StateFlow<SetupRecord>).value

    /** Home with setup on top while [open]; each opening is a new setup screen (its own view model, as in the app). */
    private fun host(open: Boolean): StateRestorationTester {
        val tester = StateRestorationTester(compose)
        tester.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                var shown by rememberSaveable { mutableStateOf(open) }
                var opened by rememberSaveable { mutableIntStateOf(0) }
                if (shown) {
                    SetupScreen(
                        viewModel = viewModel(key = "setup-$opened", factory = SetupViewModel.factory(module)),
                        onOpenSensorSetup = {},
                        thresholdRows = { _, _ -> },
                        onExportHistory = {},
                        onDone = { shown = false },
                    )
                } else {
                    Column {
                        Text(HOME)
                        Button(onClick = {
                            opened++
                            shown = true
                        }) { Text(OPEN) }
                    }
                }
            }
        }
        return tester
    }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun tap(
        text: String,
        scroll: Boolean = true,
    ) = compose.onNodeWithText(text).apply { if (scroll) performScrollTo() }.performClick()

    private fun shows(text: String) = compose.onNodeWithText(text).assertExists()

    @Test
    fun `Back on the step after the role goes home, and Finish setup resumes the walk exactly there`() {
        val tester = host(open = true)
        tap("Start")
        tap("I wear the sensor")
        tap("Continue")
        tap("Confirm", scroll = false)
        shows("Let alerts reach you")
        back()
        shows(HOME)
        // nothing was answered for the steps not reached: they all wait for the banner
        assertEquals(SetupRecord(completed = setOf(SetupStep.Welcome)), recorded())

        tap(OPEN, scroll = false)
        shows("Step 3 of 7")
        shows("Let alerts reach you")
        tap("Continue")
        shows("Connect your sensor")
        // Back walks to the previous step and a rotation keeps it there
        back()
        shows("Let alerts reach you")
        tester.emulateSavedInstanceStateRestore()
        shows("Let alerts reach you")
        tap("Continue")
        shows("Connect your sensor")
    }

    @Test
    fun `after a rotation Finish setup's Later moves on to the next skipped step, and Back goes home`() {
        roles.role.value = Role.Wearer
        sharing.state.value = OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), 0, 0)
        val skipped = setOf(SetupStep.Sensor, SetupStep.Alerts)
        val finished =
            SetupRecord(
                completed = setOf(SetupStep.Welcome, SetupStep.Permissions, SetupStep.Identity, SetupStep.History),
                skipped = skipped,
            )
        runBlocking { record.update { finished } }
        val tester = host(open = true)
        shows("Check your alerts")
        tester.emulateSavedInstanceStateRestore()
        shows("Check your alerts")
        tap("Later")
        shows("Connect your sensor")
        back()
        shows(HOME)
        // Later and Back keep both on the card
        assertEquals(finished, recorded())
    }

    @Test
    fun `the GitHub version asks about updates after the alert checklist - Not now and Turn on both go on`() {
        updates =
            SetupUpdates(
                updateChoice,
                turnOn = { updateChoice.value = UpdateChoice.On },
                notNow = { updateChoice.value = UpdateChoice.Off },
            )
        host(open = true)
        tap("Start")
        tap("I wear the sensor")
        tap("Continue")
        tap("Confirm", scroll = false)
        shows("Let alerts reach you")
        tap("Continue")
        shows("Step 4 of 8")
        shows("Keep Sukrara up to date")
        shows("Off until you turn it on. You can change it any time in Settings › About.")
        tap("Not now")
        shows("Connect your sensor")
        assertEquals(UpdateChoice.Off, updateChoice.value)
        // Back shows it again, as answered; Turn on goes on to the sensor too
        back()
        shows("Keep Sukrara up to date")
        tap("Turn on")
        shows("Connect your sensor")
        assertEquals(UpdateChoice.On, updateChoice.value)
        back()
        shows("Keep it on")
        shows("Turn off")
    }

    @Test
    fun `Welcome says Sukrara isn't a medical device`() {
        host(open = true)
        compose
            .onNodeWithText(
                "Sukrara isn't a medical device: values can be wrong, late or missing, and alerts may not ring. Check your " +
                    "sensor's app or a meter, and ask your doctor, before you decide on a treatment.",
            ).performScrollTo()
            .assertIsDisplayed()
    }

    private companion object {
        const val HOME = "Home stand-in"
        const val OPEN = "Open setup"
    }
}
