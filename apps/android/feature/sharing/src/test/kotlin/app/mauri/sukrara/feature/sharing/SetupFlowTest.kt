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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SourceSpec
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.mauri.sukrara.core.designsystem.R as DsR

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
            settings,
            resetAlerts = {},
            startMonitoring = {},
            readiness = FakeReadiness(),
            clock = clock,
            updates = updates,
            pens = pens,
            garmin = garmin,
            systemAccess = access,
        )
    }

    /** Sukrara's Garmin apps (null [garmin] = no Garmin step, as in a test without the port). */
    private var garmin: SetupGarmin? = null

    private val settings = InMemorySettingsRepository()

    /** Root on this phone (none by default: the root step asks). */
    private var access = FakeSystemAccess()

    /** Pen reading on a phone with NFC (null [pens] = no NFC: no pens step). */
    private val penReading = MutableStateFlow<Boolean?>(false)
    private var pens: SetupPens? = null

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

    private fun shows(
        text: String,
        substring: Boolean = false,
    ) = compose.onNodeWithText(text, substring = substring).assertExists()

    @Test
    fun `Back on the step after the role goes home, and Finish setup resumes the walk exactly there`() {
        val tester = host(open = true)
        tap("Start")
        tap("Yours")
        tap("Continue")
        shows("Where does your glucose come from?")
        tap("Continue")
        shows("Let alerts reach you")
        back()
        shows(HOME)
        // nothing was answered for the steps not reached: they all wait for the banner
        assertEquals(SetupRecord(completed = setOf(SetupStep.Welcome)), recorded())

        tap(OPEN, scroll = false)
        shows("Step 4 of 6")
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
        roles.role.value = SourceSpec.YourSensor
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

    /**
     * The essentials of a wearer's walk, the sensor and the thresholds left for later: Ready never says "done" then, it
     * names what is still to do.
     */
    private fun walkTheEssentials() {
        tap("Start")
        tap("Yours")
        tap("Continue")
        shows("Where does your glucose come from?")
        tap("Continue")
        shows("Let alerts reach you")
        tap("Continue")
        shows("Connect your sensor")
        tap("Later")
        shows("Check your alerts")
        tap("Later")
        shows("Some essentials are left for later")
        compose.onNodeWithText("Still to do:", substring = true).assertExists()
    }

    @Test
    fun `the essentials end on Ready - Later goes home and leaves the optional steps for Settings`() {
        host(open = true)
        walkTheEssentials()
        // what is left, by title
        shows("Your followers")
        shows("Bring your history back")
        tap("Later")
        shows(HOME)
        assertEquals(true, SetupStep.Ready in recorded().skipped)
        assertEquals(
            listOf(SetupStep.Root, SetupStep.Notification, SetupStep.Identity, SetupStep.History),
            runBlocking { module.extrasLeft.first() },
        )
        // Settings' Finish setup: the walk goes on into them
        runBlocking { module.continueExtras() }
        tap(OPEN, scroll = false)
        shows("Optional step 1 of 4")
        shows("Is this phone rooted?", substring = true)
    }

    @Test
    fun `the GitHub version asks about updates first among the optional steps - Not now and Turn on both go on`() {
        updates =
            SetupUpdates(
                updateChoice,
                turnOn = { updateChoice.value = UpdateChoice.On },
                notNow = { updateChoice.value = UpdateChoice.Off },
            )
        host(open = true)
        walkTheEssentials()
        shows("Keep Sukrara up to date")
        tap("Set up the rest")
        shows("Optional step 1 of 5")
        shows("Off until you turn it on. You can change it any time in Settings › About.")
        tap("Not now")
        shows("Optional step 2 of 5")
        assertEquals(UpdateChoice.Off, updateChoice.value)
        // Back shows it again, as answered; Turn on goes on too
        back()
        shows("Keep Sukrara up to date")
        tap("Turn on")
        shows("Optional step 2 of 5")
        assertEquals(UpdateChoice.On, updateChoice.value)
        back()
        shows("Keep it on")
        shows("Turn off")
    }

    @Test
    fun `insulin pens - Read my pens turns reading on, I don't use a smart pen is final`() {
        pens = SetupPens(penReading) { penReading.value = true }
        sharing.state.value = OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), 0, 0)
        host(open = true)
        walkTheEssentials()
        shows("Insulin pens")
        tap("Set up the rest")
        tap("It isn't rooted")
        shows("Status bar & notification")
        tap("Continue")
        shows(
            "With a smart insulin pen such as NovoPen 6 or NovoPen Echo Plus, hold it to the back of the phone after you inject: Sukrara logs the dose.",
        )
        tap("Read my pens")
        shows("Bring your history back")
        assertEquals(true, penReading.value)
        // Back: pens are read, Continue goes on
        back()
        shows("Pens are read. Hold one to the back of the phone, unlocked, from any app.")
        tap("Continue")
        shows("Bring your history back")
        // on a phone where reading is still Ask, I don't use a smart pen answers it for good
        penReading.value = false
        runBlocking { record.update { it.copy(completed = it.completed - SetupStep.Pens) } }
        // the walk is back on the step that is open again
        tap("I don't use a smart pen")
        shows("Bring your history back")
        assertEquals(true, SetupStep.Pens in recorded().completed)
    }

    @Test
    fun `the Garmin step - shown with Garmin Connect, its switch is the Garmin one, no store link before a listing, Skip goes on`() {
        val offered = MutableStateFlow(true)
        val on = MutableStateFlow(true)
        val apps =
            MutableStateFlow(
                listOf(
                    // stand-in names (the real ones are the integrations' strings)
                    SetupWatchApp(
                        "aura",
                        R.string.sh_setup_pens_title,
                        R.string.sh_setup_pens_on,
                        DsR.drawable.ds_ic_watch_face,
                        null,
                        onWatch = true,
                    ),
                    SetupWatchApp(
                        "field",
                        R.string.sh_setup_root_yes,
                        R.string.sh_setup_root_no,
                        DsR.drawable.ds_ic_data_field,
                        null,
                        onWatch = false,
                    ),
                ),
            )
        garmin = SetupGarmin(offered, on, { on.value = it }, apps)
        sharing.state.value = OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), 0, 0)
        host(open = true)
        walkTheEssentials()
        shows("Your Garmin watch")
        tap("Set up the rest")
        tap("It isn't rooted")
        tap("Continue")
        shows("Sukrara can show your glucose on a Garmin watch", substring = true)
        // what is on a watch says so; no listing published: no Get, where they will be instead
        shows("On your watch")
        compose.onNodeWithText("Get").assertDoesNotExist()
        shows("Coming soon to the Connect IQ store.")
        // the switch is THE Garmin switch, stored at once
        tap("Send to Sukrara on your watch")
        compose.waitUntil(2_000) { !on.value }
        // Skip: final, the walk goes on, the switch as it was left
        tap("Skip")
        shows("Bring your history back")
        assertEquals(true, SetupStep.Garmin in recorded().skipped)
        assertEquals(false, on.value)
        assertEquals(false, SetupStep.Garmin in runBlocking { module.extrasLeft.first() })
    }

    @Test
    fun `the Garmin step - not offered without Garmin Connect or a Sukrara app on a watch`() {
        garmin = SetupGarmin(MutableStateFlow(false), MutableStateFlow(true), {}, MutableStateFlow(emptyList()))
        host(open = true)
        walkTheEssentials()
        compose.onNodeWithText("Your Garmin watch").assertDoesNotExist()
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

    @Test
    fun `root - Yes asks, the way to allow it shows, then granted root picks the status bar text`() {
        val port = access
        sharing.state.value = OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), 0, 0)
        host(open = true)
        walkTheEssentials()
        tap("Set up the rest")
        shows("Is this phone rooted?", substring = true)
        assertEquals("nothing asked before Yes", 0, port.asked)
        tap("Yes, it's rooted")
        assertEquals(1, port.asked)
        // no root Sukrara can use yet: what to do, and Check again
        shows("Root needed")
        port.answer = RootAccess.Granted
        tap("Check again")
        // root: the walk goes on, the status bar text already picked
        shows("Status bar & notification")
        compose.onNodeWithText("Status bar text").assertIsSelected()
        assertEquals(NotificationStyle.Text, runBlocking { module.notificationStyle.first() })
        // the person can still choose another one
        tap("Chip")
        compose.onNodeWithText("Chip").assertIsSelected()
        tap("Continue")
        shows("Bring your history back")
        assertEquals(NotificationStyle.Chip, runBlocking { module.notificationStyle.first() })
    }

    @Test
    fun `root - It isn't rooted is final, and the notification step keeps the chip`() {
        sharing.state.value = OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), 0, 0)
        host(open = true)
        walkTheEssentials()
        tap("Set up the rest")
        tap("It isn't rooted")
        shows("Status bar & notification")
        compose.onNodeWithText("Chip").assertIsSelected()
        assertEquals(true, SetupStep.Root in recorded().completed)
    }

    @Test
    fun `root - signs of root skip the question and ask at once`() {
        val port = FakeSystemAccess(RootAccess.Granted, seen = true).also { access = it }
        sharing.state.value = OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), 0, 0)
        host(open = true)
        walkTheEssentials()
        tap("Set up the rest")
        shows("Status bar & notification")
        assertEquals(1, port.asked)
        compose.onNodeWithText("Status bar text").assertIsSelected()
    }

    private companion object {
        const val HOME = "Home stand-in"
        const val OPEN = "Open setup"
    }
}
