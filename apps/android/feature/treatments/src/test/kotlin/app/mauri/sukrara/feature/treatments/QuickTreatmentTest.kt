// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app's Application stand-in: gives the quick dialog its module, like `App` does. */
class TestTreatmentsApp :
    Application(),
    TreatmentsHost {
    val glucose = MutableStateFlow(GlucoseState.Initial)
    var low = false
    val hints =
        object : AlertHints {
            override fun lowNow() = low

            override val quiet = kotlinx.coroutines.flow.flowOf(QuietRules(afterInsulinMin = 60, afterCarbsMin = 15))
        }

    /** The pen entry *Already logged by your pen?* names (null = no pen entry near the typed dose). */
    var penNear: app.mauri.sukrara.core.model.PenNearEntry? = null

    /** The log the dialog saves into. */
    val repo = InMemoryTreatmentsRepository()

    override val treatments by lazy {
        TreatmentsModule(
            repo,
            LocalTreatmentSync(),
            FakeClock(),
            glucose,
            InMemorySettingsRepository(),
            CoroutineScope(Dispatchers.Unconfined),
            hints,
            penDuplicates =
                app.mauri.sukrara.core.model
                    .PenDuplicates { _, _ -> penNear },
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestTreatmentsApp::class)
class QuickTreatmentTest {
    private val app = ApplicationProvider.getApplicationContext<TestTreatmentsApp>()

    @get:Rule val compose = createEmptyComposeRule()

    /** #100: the dialog opens with what the lock-screen countdown kept; Save stores it and offers Undo. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun aKeptDraftFillsTheDialogAndSaveOffersUndo() {
        app.glucose.value = GlucoseState.Initial.copy(role = Role.Wearer)
        val now = app.treatments.clock.now()
        app.treatments.keepDraft(
            TreatmentDraft("4.5", "", "", "", atMs = now.minusSeconds(300).toEpochMilli(), keptMs = now.toEpochMilli()),
            "",
        )
        ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Draft)).use {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("treatment-draft").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("4.5").assertExists()
            // the snackbar goes by itself after a few seconds: time moves by hand from here
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("treatment-save").performScrollTo().performClick()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            compose.onNodeWithTag("treatment-saved").assertExists()
            compose.onNodeWithText("Saved: 4.5u rapid insulin").assertExists()
            compose.onNodeWithText("Undo").assertExists()
        }
        // saved, so no longer offered
        kotlinx.coroutines.runBlocking { assertNull(app.treatments.draft()) }
    }

    /** #181: *Open Sukrara* only where the app isn't already behind the dialog (a notification, a widget, the tile). */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun openSukraraOnlyFromOutsideTheApp() {
        app.glucose.value = GlucoseState.Initial.copy(role = Role.Wearer)
        ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Notification)).use {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("treatment-save").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Open Sukrara").assertExists()
        }
        val fromHome = QuickTreatmentIntents.open(app, QuickSource.Home).putExtra(NavTargets.EXTRA_FROM_APP, true)
        ActivityScenario.launch<QuickTreatmentActivity>(fromHome).use {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("treatment-save").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Open Sukrara").assertDoesNotExist()
        }
    }

    @Test fun widgetAndNotificationIntentsResolveToTheQuickDialog() {
        for (source in QuickSource.entries) {
            val intent = QuickTreatmentIntents.open(app, source, focusCarbs = true)
            val resolved = app.packageManager.resolveActivity(intent, 0)
            assertEquals(QuickTreatmentActivity::class.java.name, resolved?.activityInfo?.name)
            assertTrue(QuickTreatmentIntents.focusCarbs(intent))
        }
        // the monitor's notification posts the action with the package only
        val notification = Intent(NavTargets.ACTION_QUICK_TREATMENT).setPackage(app.packageName)
        assertEquals(
            QuickTreatmentActivity::class.java.name,
            app.packageManager
                .resolveActivity(notification, 0)
                ?.activityInfo
                ?.name,
        )
    }

    @Test fun opensTheDialogWhenThisPhoneCanAdd() {
        app.glucose.value = GlucoseState.Initial.copy(role = Role.Wearer)
        val activity =
            Robolectric
                .buildActivity(
                    QuickTreatmentActivity::class.java,
                    QuickTreatmentIntents.open(app, QuickSource.Notification),
                ).setup()
                .get()
        assertFalse(activity.isFinishing)
    }

    @Test fun opensTheAppInsteadWhenThisPhoneCannotAdd() {
        app.glucose.value = GlucoseState.Initial.copy(role = Role.Follower)
        val activity =
            Robolectric
                .buildActivity(
                    QuickTreatmentActivity::class.java,
                    QuickTreatmentIntents.open(app, QuickSource.Widget),
                ).setup()
                .get()
        assertTrue(activity.isFinishing)
    }

    @Test fun carbsFirstWhileTheLatestReadingIsLow() {
        app.low = true
        assertTrue(app.treatments.lowNow())
        app.low = false
        assertFalse(app.treatments.lowNow())
        assertFalse(QuickTreatmentIntents.focusCarbs(Intent()))
    }
}
