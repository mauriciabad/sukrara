// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.share.owner.OwnerShareState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The permissions step's *Send a test alert* (#43): Settings' real test alert, from first-run setup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en")
class SetupTestAlertTest {
    @get:Rule val compose = createComposeRule()

    private fun show(send: (() -> Unit)?) =
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                SetupContent(
                    SetupUi(
                        step = SetupStep.Permissions,
                        index = 3,
                        count = 6,
                        facts = SetupFacts(role = Role.Wearer, ownSensorLive = false, hasIdentity = false),
                        record = SetupRecord(),
                        readiness = AlertReadiness(emptySet()),
                        alerts = AlertThresholds(AlertSettings()),
                        gate = RestoreGate.evaluate(false),
                        share = OwnerShareState.NoIdentity,
                        restore = RestoreUi.Idle,
                        busy = false,
                        single = false,
                        canBack = true,
                    ),
                    SetupActions(sendTestAlert = send),
                )
            }
        }

    @Test fun sendsTheRealTestAlertAndSaysWhereToLook() {
        var sent = 0
        show { sent++ }
        compose.onNodeWithText("The real alert, marked as a test. Try it with Do Not Disturb on.").performScrollTo().assertExists()
        compose.onNodeWithTag("setup-test-alert").performScrollTo().performClick()
        assertEquals(1, sent)
        compose.onNodeWithText("Sent: check your notifications. It goes away by itself in 2 min.").assertExists()
        compose.onNodeWithTag("setup-test-alert").performClick()
        assertEquals(2, sent)
    }

    @Test fun noCardWhenNoAlertIsOn() {
        show(null)
        compose.onNodeWithTag("setup-test-alert").assertDoesNotExist()
    }
}
