// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The LibreLinkUp Connect step as the person meets it (docs/44 step 2): *Sign in as …* that LibreLinkUp refuses turns
 * into the form with the email filled in and the refusal under the password (it showed nothing but a haptic).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en")
class LluStepTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val facts = SetupFacts(spec = SourceSpec(Person.Yours, SourceKind.LibreLinkUp), ownSensorLive = false, hasIdentity = false)

    private fun ui(llu: LluUi) =
        SetupUi(
            step = SetupStep.Sensor,
            index = 5,
            count = 6,
            facts = facts,
            record = SetupRecord(),
            readiness = AlertReadiness(emptySet()),
            alerts =
                AlertThresholds(
                    app.mauri.sukrara.alerts
                        .AlertSettings(),
                ),
            gate = RestoreGate.evaluate(false),
            share = app.mauri.sukrara.share.owner.OwnerShareState.NoIdentity,
            restore = RestoreUi.Idle,
            busy = false,
            single = false,
            canBack = true,
            llu = llu,
        )

    @Test
    fun `Sign in as refused shows the form with the LibreView email and says why`() {
        var llu by mutableStateOf(LluUi(LluConnection(libreViewEmail = "alex@example.com")))
        var oneTap = 0
        val actions = SetupActions(connectLluLibreView = { oneTap++ })
        compose.setContent { SetupContent(ui(llu), actions) }
        compose.onNodeWithText("alex@example.com", substring = true).performClick()
        assertEquals(1, oneTap)
        llu = llu.copy(result = LluConnectOutcome.BadCredentials)
        compose.waitForIdle()
        compose.onNodeWithText("LibreLinkUp refused this email or password").assertIsDisplayed()
        compose.onNodeWithText("alex@example.com").assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun `Change account from the LibreView account itself is the form, and Keep this account goes back`() {
        val connected =
            LluConnection(
                email = "alex@example.com",
                signedIn = true,
                patient = "p1",
                name = "Alex Example",
                people = listOf(LluChoice("p1", "Alex Example")),
                health = OnlineHealth.Live,
                newest = Instant.now(),
                libreViewEmail = "alex@example.com",
            )
        compose.setContent { SetupContent(ui(LluUi(connected)), SetupActions()) }
        compose.onNodeWithText("Change account").performClick()
        compose.waitForIdle()
        // not *Sign in as alex@example.com* again: the account in use
        compose.onAllNodesWithText("Sign in as alex@example.com").assertCountEquals(0)
        compose.onNodeWithText("Password").assertIsDisplayed()
        compose.onNodeWithText("Keep this account").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Signed in as alex@example.com").assertIsDisplayed()
    }
}
