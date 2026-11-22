// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.KindFacts
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.core.model.PenReadAt
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** The app's Application stand-in: gives the quick dialog its module, like `App` does. */
class TestTreatmentsApp :
    Application(),
    TreatmentsHost {
    val glucose = MutableStateFlow(GlucoseState.Initial)

    /** The alerts' facts of THE kinds' order (the reading, the low threshold, what rings). */
    var facts = KindFacts.NONE

    /** The newest pen read the pens would report. */
    var penRead: PenReadAt? = null
    val hints =
        object : AlertHints {
            override fun kindFacts() = facts

            override val quiet = kotlinx.coroutines.flow.flowOf(QuietRules(afterInsulinMin = 60, afterCarbsMin = 15))

            override fun answered(kind: String) {
                answers += kind
            }
        }

    /** The alerts whose *Treatment* opened the dialog (an answer to each). */
    val answers = mutableListOf<String>()

    /** The pen entry *Already logged by your pen?* names (null = no pen entry near the typed dose). */
    var penNear: app.mauri.sukrara.core.model.PenNearEntry? = null

    /** The insulin [penNear] is ("rapid" / "long"): only a typed dose of that kind finds it, like the pens' log. */
    var penNearKind = "rapid"

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
                    .PenDuplicates { kind, _ -> penNear?.takeIf { kind == penNearKind } },
            newestPenRead = { penRead },
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
        app.glucose.value = GlucoseState.Initial.copy(spec = SourceSpec.YourSensor)
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
        app.glucose.value = GlucoseState.Initial.copy(spec = SourceSpec.YourSensor)
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
            val intent = QuickTreatmentIntents.open(app, source)
            val resolved = app.packageManager.resolveActivity(intent, 0)
            assertEquals(QuickTreatmentActivity::class.java.name, resolved?.activityInfo?.name)
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
        app.glucose.value = GlucoseState.Initial.copy(spec = SourceSpec.YourSensor)
        val activity =
            Robolectric
                .buildActivity(
                    QuickTreatmentActivity::class.java,
                    QuickTreatmentIntents.open(app, QuickSource.Notification),
                ).setup()
                .get()
        assertFalse(activity.isFinishing)
    }

    /** #194: an alert's *Treatment* opens the dialog on Carbs and answers that alert on this phone, once (#39). */
    @Test fun anAlertsLogCarbsAnswersTheAlertOnce() {
        app.glucose.value = GlucoseState.Initial.copy(spec = SourceSpec.YourSensor)
        app.answers.clear()
        // what the monitor's alert notification posts (the package only, the kind's wire name)
        val fromAlert =
            Intent(NavTargets.ACTION_QUICK_TREATMENT)
                .setPackage(app.packageName)
                .putExtra(NavTargets.EXTRA_ALERT_KIND, "very_low")
        assertEquals(
            QuickTreatmentActivity::class.java.name,
            app.packageManager
                .resolveActivity(fromAlert, 0)
                ?.activityInfo
                ?.name,
        )
        val controller =
            Robolectric
                .buildActivity(
                    QuickTreatmentActivity::class.java,
                    fromAlert.setClass(app, QuickTreatmentActivity::class.java),
                ).setup()
        assertFalse(controller.get().isFinishing)
        // the alert it answers counts as ringing (THE kinds' order: carbs first), whatever the reading says
        val answering = QuickTreatmentIntents.alertKind(controller.get().intent)
        assertEquals(TreatmentKind.Carbs, runBlocking { app.treatments.order(answering) }.first())
        assertEquals(listOf("very_low"), app.answers)
        // a rotation doesn't answer again
        controller.recreate()
        assertEquals(listOf("very_low"), app.answers)
        // any other way in answers nothing
        Robolectric.buildActivity(QuickTreatmentActivity::class.java, QuickTreatmentIntents.open(app, QuickSource.Widget)).setup()
        assertEquals(listOf("very_low"), app.answers)
    }

    @Test fun opensTheAppInsteadWhenThisPhoneCannotAdd() {
        app.glucose.value = GlucoseState.Initial.copy(spec = SourceSpec.TheirSukrara)
        val activity =
            Robolectric
                .buildActivity(
                    QuickTreatmentActivity::class.java,
                    QuickTreatmentIntents.open(app, QuickSource.Widget),
                ).setup()
                .get()
        assertTrue(activity.isFinishing)
    }

    /**
     * The owner's 0.14.5 report: `.243` in Rapid on every Add treatment. Typed by a thumb on the number pad while the
     * dialog was over the lock screen, it was kept when the countdown ended; such an amount is no draft, a dose is.
     */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun theLockScreenCountdownKeepsOnlyADoseAsTyped() {
        app.glucose.value = GlucoseState.Initial.copy(spec = SourceSpec.YourSensor)
        assertNull(runOutOnTheLockScreen(".243"))
        val kept = runOutOnTheLockScreen("4.5")
        assertEquals("4.5", kept?.rapid)
        // the next dialog opens with the kept dose, marked as the draft it is
        ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Home)).use {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("treatment-draft").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("4.5").assertExists()
        }
    }

    /** Opens the dialog over the lock screen, types [rapid] and lets the countdown run out; the draft it kept. */
    private fun runOutOnTheLockScreen(rapid: String): TreatmentDraft? {
        val keyguard = Shadows.shadowOf(app.getSystemService(android.app.KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)
        compose.mainClock.autoAdvance = false
        try {
            ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Widget)).use { scenario ->
                repeat(5) { compose.mainClock.advanceTimeByFrame() }
                compose.waitForIdle()
                compose.onNodeWithTag("treatment-rapid").performTextInput(rapid)
                ShadowSystemClock.advanceBy(Duration.ofSeconds(130))
                repeat(5) { compose.mainClock.advanceTimeByFrame() }
                compose.waitForIdle()
                assertTrue("the countdown closed the dialog", scenario.state == Lifecycle.State.DESTROYED || finishing(scenario))
            }
        } finally {
            keyguard.setKeyguardLocked(false)
            compose.mainClock.autoAdvance = true
        }
        return kotlinx.coroutines.runBlocking { app.treatments.draft() }
    }

    private fun finishing(scenario: ActivityScenario<QuickTreatmentActivity>): Boolean {
        var f = false
        scenario.onActivity { f = it.isFinishing }
        return f
    }

    /** THE kinds' order over the module's facts: the alerts' and the newest pen read, at the module's clock. */
    @Test fun theOrderComesFromTheAlertsFactsAndThePens() {
        val usual = listOf(TreatmentKind.RapidInsulin, TreatmentKind.LongInsulin, TreatmentKind.Carbs)
        val now =
            app.treatments.clock
                .now()
                .epochSecond
        try {
            app.facts = KindFacts(120, 70.0, emptySet())
            assertEquals(usual, runBlocking { app.treatments.order() })
            app.facts = KindFacts(62, 70.0, emptySet())
            assertEquals(TreatmentKind.Carbs, runBlocking { app.treatments.order() }.first())
            app.facts = KindFacts(120, 70.0, emptySet())
            assertEquals(TreatmentKind.Carbs, runBlocking { app.treatments.order("falling_fast") }.first())
            assertEquals(usual, runBlocking { app.treatments.order("high") })
            app.penRead = PenReadAt(TreatmentKind.RapidInsulin, now - 60)
            assertEquals(TreatmentKind.LongInsulin, runBlocking { app.treatments.order() }.first())
            assertEquals(app.penRead, runBlocking { app.treatments.kindFacts() }.pen)
        } finally {
            app.facts = KindFacts.NONE
            app.penRead = null
        }
    }

    /** The dialog focuses THE order's first field as it opens: Carbs while low, Long-acting after a rapid pen read. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun theDialogFocusesTheOrdersFirst() {
        app.glucose.value = GlucoseState.Initial.copy(spec = SourceSpec.YourSensor)
        try {
            app.facts = KindFacts(58, 70.0, setOf("low"))
            ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Widget)).use {
                compose.waitUntil(5_000) { compose.onAllNodesWithTag("treatment-carbs").fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                compose.onNodeWithTag("treatment-carbs").assertIsFocused()
            }
            app.facts = KindFacts(140, 70.0, emptySet())
            app.penRead =
                PenReadAt(
                    TreatmentKind.RapidInsulin,
                    app.treatments.clock
                        .now()
                        .epochSecond - 120,
                )
            ActivityScenario.launch<QuickTreatmentActivity>(QuickTreatmentIntents.open(app, QuickSource.Tile)).use {
                compose.waitUntil(5_000) { compose.onAllNodesWithTag("treatment-long").fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                compose.onNodeWithTag("treatment-long").assertIsFocused()
            }
        } finally {
            app.facts = KindFacts.NONE
            app.penRead = null
        }
    }
}
