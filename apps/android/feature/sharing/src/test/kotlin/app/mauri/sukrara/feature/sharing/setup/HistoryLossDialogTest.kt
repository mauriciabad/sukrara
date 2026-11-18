// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.sharing.HistoryLoss
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
class HistoryLossDialogTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions =
        SetupActions(
            reviewSource = { calls += "review:${it.person.id}:${it.kind.id}" },
            cancelSource = { calls += "cancel" },
            exportHistory = { calls += "export" },
            chooseSource = { calls += "choose:${it.person.id}:${it.kind.id}" },
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

    private fun show(change: SourceChange?) =
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                SetupContent(
                    SetupUi(
                        step = SetupStep.Source,
                        index = 2,
                        count = 6,
                        facts = SetupFacts(spec = SourceSpec.TheirSukrara, ownSensorLive = false, hasIdentity = false),
                        record = SetupRecord(),
                        readiness = AlertReadiness(emptySet()),
                        alerts = AlertThresholds(AlertSettings()),
                        gate = RestoreGate.evaluate(false),
                        share = OwnerShareState.NoIdentity,
                        restore = RestoreUi.Idle,
                        busy = false,
                        single = false,
                        canBack = true,
                        sourceChange = change,
                    ),
                    actions,
                )
            }
        }

    private val loss = HistoryLoss(4 * 1440, 12, Instant.parse("2026-10-03T08:00:00Z"), Instant.parse("2026-11-10T07:00:00Z"))

    @Test fun deletingHistorySaysWhatAndWhen() {
        show(SourceChange(SourceSpec.YourSensor, loss, SourceSpec.TheirSukrara))
        compose.onNodeWithText("Delete the earlier history?").assertExists()
        compose
            .onNodeWithText(
                "You chose “Yours”. This deletes what this phone kept from before: 4 days of glucose and " +
                    "12 treatments, from Oct 3, 2026 to Nov 10, 2026. It can’t be undone.",
            ).assertExists()
    }

    @Test fun exportFirstKeepsTheDialogAndDeletesNothing() {
        show(SourceChange(SourceSpec.YourSensor, loss, SourceSpec.TheirSukrara))
        compose.onNodeWithText("Export CSV first").performClick()
        assertEquals(listOf("export"), calls)
        compose.onNodeWithText("Delete the earlier history?").assertExists()
    }

    @Test fun cancelChangesNothing() {
        show(SourceChange(SourceSpec.YourSensor, loss, SourceSpec.TheirSukrara))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test fun deleteAndContinueChoosesTheSource() {
        show(SourceChange(SourceSpec.YourSensor, loss, SourceSpec.TheirSukrara))
        compose.onNodeWithText("Delete and continue").performClick()
        assertEquals(listOf("choose:yours:sensor"), calls)
    }

    @Test fun onlyTreatmentsOnOneDay() {
        show(SourceChange(SourceSpec.TheirSukrara, HistoryLoss(0, 1, loss.to, loss.to), SourceSpec.YourSensor))
        compose
            .onNodeWithText("1 treatment, from Nov 10, 2026", substring = true)
            .assertExists()
    }

    @Test fun nothingToDeleteIsThePlainConfirmation() {
        show(SourceChange(SourceSpec(Person.Yours, SourceKind.PhoneApp), null, SourceSpec.YourSensor))
        compose.onNodeWithText("Delete and continue").assertDoesNotExist()
        compose.onNodeWithText("Export CSV first").assertDoesNotExist()
        compose.onNodeWithText("Switch to “Juggluco on this phone”?").assertExists()
        compose.onNodeWithText("Switch").performClick()
        assertEquals(listOf("cancel", "choose:yours:phoneApp"), calls)
    }

    @Test fun continueAsksForTheConfirmation() {
        show(null)
        compose.onNodeWithText("Juggluco on this phone").performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        assertEquals(listOf("review:theirs:phoneApp"), calls)
    }
}
