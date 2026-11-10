// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.sharing.RoleLoss
import app.mauri.sukrara.share.owner.OwnerShareState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * The role step's confirmation (#125): a change that deletes history says what (counts and dates) and offers *Export
 * CSV first*, *Delete and continue* and *Cancel*; with nothing to delete it's the plain role confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en")
class RoleLossDialogTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions =
        SetupActions(
            reviewRole = { calls += "review:$it" },
            cancelRole = { calls += "cancel" },
            exportHistory = { calls += "export" },
            chooseRole = { calls += "choose:$it" },
        )
    private var zone: TimeZone? = null

    @Before
    fun setUp() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() = TimeZone.setDefault(zone)

    private fun show(change: RoleChange?) =
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                SetupContent(
                    SetupUi(
                        step = SetupStep.Role,
                        index = 2,
                        count = 6,
                        facts = SetupFacts(role = Role.Follower, ownSensorLive = false, hasIdentity = false),
                        record = SetupRecord(),
                        readiness = AlertReadiness(emptySet()),
                        alerts = AlertThresholds(AlertSettings()),
                        gate = RestoreGate.evaluate(false),
                        share = OwnerShareState.NoIdentity,
                        restore = RestoreUi.Idle,
                        busy = false,
                        single = false,
                        canBack = true,
                        roleChange = change,
                    ),
                    actions,
                )
            }
        }

    private val loss = RoleLoss(1204, 12, Instant.parse("2026-10-03T08:00:00Z"), Instant.parse("2026-11-10T07:00:00Z"))

    @Test fun deletingHistorySaysWhatAndWhen() {
        show(RoleChange(Role.Wearer, loss))
        compose.onNodeWithText("Delete the earlier history?").assertExists()
        compose
            .onNodeWithText(
                "You chose “I wear the sensor”. This deletes what this phone kept from before: 1,204 glucose readings and " +
                    "12 treatments, from Oct 3, 2026 to Nov 10, 2026. It can’t be undone. To keep a copy, export a CSV file first.",
            ).assertExists()
    }

    @Test fun exportFirstKeepsTheDialogAndDeletesNothing() {
        show(RoleChange(Role.Wearer, loss))
        compose.onNodeWithText("Export CSV first").performClick()
        assertEquals(listOf("export"), calls)
        compose.onNodeWithText("Delete the earlier history?").assertExists()
    }

    @Test fun cancelChangesNothing() {
        show(RoleChange(Role.Wearer, loss))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test fun deleteAndContinueChoosesTheRole() {
        show(RoleChange(Role.Wearer, loss))
        compose.onNodeWithText("Delete and continue").performClick()
        assertEquals(listOf("choose:Wearer"), calls)
    }

    @Test fun onlyTreatmentsOnOneDay() {
        show(RoleChange(Role.Follower, RoleLoss(0, 1, loss.to, loss.to)))
        compose
            .onNodeWithText("1 treatment, from Nov 10, 2026", substring = true)
            .assertExists()
    }

    @Test fun nothingToDeleteIsThePlainConfirmation() {
        show(RoleChange(Role.Wearer, null))
        compose.onNodeWithText("Delete and continue").assertDoesNotExist()
        compose.onNodeWithText("Export CSV first").assertDoesNotExist()
        compose.onNodeWithText("I wear the sensor?").assertExists()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(listOf("cancel", "choose:Wearer"), calls)
    }

    @Test fun continueAsksForTheConfirmation() {
        show(null)
        compose.onNodeWithText("I follow someone").performClick()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf("review:Follower"), calls)
    }
}
