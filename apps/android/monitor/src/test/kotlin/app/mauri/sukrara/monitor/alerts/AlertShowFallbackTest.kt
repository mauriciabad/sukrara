// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An alert whose own notification can't be built or posted is never silent (0.14.1: the release build lost strings
 * the alert looks up by name, `show` threw, and no alert notification was ever posted): it is shown plain on its own
 * channel, else as a problem notice on *Monitoring problems*; the failure (step + exception class, never a value) is
 * listed until an alert of that group shows normally again, and it never stops the next alert.
 */
class AlertShowFallbackTest {
    private val rig = AlertRig()
    private val logged = mutableListOf<String>()

    /** A shade where the full alert, the plain one and the problem notice can each be made to fail, per group. */
    private class BrokenPresenter(private val inner: FakePresenter) : AlertPresenter by inner {
        val showFails = mutableSetOf<AlertGroup>()
        val plainFails = mutableSetOf<AlertGroup>()
        val problemFails = mutableSetOf<AlertGroup>()

        override fun show(firing: Firing, settings: AlertSettings, silent: Boolean, who: String?, relaxedMin: Int) {
            if (firing.kind.group in showFails) throw IllegalStateException("no such string")
            inner.show(firing, settings, silent, who, relaxedMin)
        }

        override fun showPlain(firing: Firing, settings: AlertSettings, silent: Boolean, who: String?) {
            if (firing.kind.group in plainFails) throw SecurityException("refused")
            inner.showPlain(firing, settings, silent, who)
        }

        override fun showProblem(group: AlertGroup, silent: Boolean) {
            if (group in problemFails) throw IllegalArgumentException("no channel")
            inner.showProblem(group, silent)
        }
    }

    private val presenter = BrokenPresenter(rig.presenter)

    private fun runtime() =
        AlertRuntime(
            rig.store,
            rig.treatments,
            presenter,
            rig.alarm,
            { },
            { rig.published = it },
            rig.clock,
            log = { logged += it },
        )

    private fun later(min: Long = 1) {
        rig.clock.instant = rig.clock.now().plusSeconds(min * 60)
    }

    private val level = AlertGroup.LEVEL

    @Test fun aRealAlertThatCantBeShownIsShownPlainWithItsSound() =
        runTest {
            presenter.showFails += level
            runtime().onState(rig.wearerState(latest = rig.reading(60)))
            assertEquals("plain low", rig.presenter.slots[level])
            assertEquals("loud, as the alert was meant to be", listOf("plain low"), rig.presenter.log)
            assertTrue(rig.published.failedSteps.toString(), "Show:level IllegalStateException" in rig.published.failedSteps)
            assertTrue(logged.toString(), "show failed: IllegalStateException" in logged)
        }

    @Test fun thePlainAlertFailingTooShowsTheProblemNotice() =
        runTest {
            presenter.showFails += level
            presenter.plainFails += level
            runtime().onState(rig.wearerState(latest = rig.reading(60)))
            assertEquals("problem", rig.presenter.slots[level])
            assertEquals(listOf("problem level"), rig.presenter.log)
            assertTrue("Show:level IllegalStateException" in rig.published.failedSteps)
            assertTrue("Plain:level SecurityException" in rig.published.failedSteps)
        }

    @Test fun nothingShownAtAllIsTriedAgainLoudOnTheNextReading() =
        runTest {
            presenter.showFails += level
            presenter.plainFails += level
            presenter.problemFails += level
            val runtime = runtime()
            runtime.onState(rig.wearerState(latest = rig.reading(60)))
            assertEquals(null, rig.presenter.slots[level])
            assertTrue("Problem:level IllegalArgumentException" in rig.published.failedSteps)
            presenter.showFails.clear()
            later()
            runtime.onState(rig.wearerState(latest = rig.reading(59)))
            assertEquals("never heard: it rings now", listOf("show low"), rig.presenter.log)
            assertTrue("every step worked again", rig.published.failedSteps.isEmpty())
        }

    @Test fun afterThePlainAlertRangTheFullOneReplacesItSilently() =
        runTest {
            presenter.showFails += level
            val runtime = runtime()
            runtime.onState(rig.wearerState(latest = rig.reading(60)))
            presenter.showFails.clear()
            later()
            runtime.onState(rig.wearerState(latest = rig.reading(59)))
            assertEquals("the plain one already rang", listOf("plain low", "resume low"), rig.presenter.log)
            assertEquals("low", rig.presenter.slots[level])
            assertTrue(rig.published.failedSteps.isEmpty())
        }

    @Test fun aFullAlertThatKeepsFailingStaysPlainWithoutRingingEveryReading() =
        runTest {
            presenter.showFails += level
            val runtime = runtime()
            runtime.onState(rig.wearerState(latest = rig.reading(60)))
            later()
            runtime.onState(rig.wearerState(latest = rig.reading(59)))
            val log = rig.presenter.log
            assertEquals("plain low", log.first())
            assertTrue("then only silent re-posts: $log", log.size > 1 && log.drop(1).all { it == "plain resume low" })
        }

    @Test fun aBrokenAlertNeverStopsTheNextOne() =
        runTest {
            rig.store.setSettings(
                AlertSettings()
                    .let { it.withRule(AlertKind.LOW, it.rule(AlertKind.LOW).copy(on = true)) }
                    .let { it.withRule(AlertKind.FALLING_FAST, it.rule(AlertKind.FALLING_FAST).copy(on = true)) },
            )
            presenter.showFails += level
            presenter.plainFails += level
            presenter.problemFails += level
            runtime().onState(rig.wearerState(latest = Reading(rig.clock.now(), 62, -3.0, SourceId.Libre3)))
            assertEquals("falling_fast", rig.presenter.slots[AlertGroup.RATE])
            assertNotNull("the exact alarm is armed", rig.alarm.at)
            assertNotNull(
                "the state is saved",
                rig.record.data
                    .first()
                    .machine,
            )
        }

    @Test fun theFailureIsListedAfterTheAlertEndsUntilAnAlertOfItsGroupShowsAgain() =
        runTest {
            presenter.showFails += level
            val runtime = runtime()
            runtime.onState(rig.wearerState(latest = rig.reading(60)))
            later(30)
            runtime.onState(rig.wearerState(latest = rig.reading(120)))
            assertEquals("the alert ended", null, rig.presenter.slots[level])
            assertTrue("still listed: no alert has shown since", "Show:level IllegalStateException" in rig.published.failedSteps)
            presenter.showFails.clear()
            later(60)
            runtime.onState(rig.wearerState(latest = rig.reading(60)))
            assertEquals("low", rig.presenter.slots[level])
            assertTrue(rig.published.failedSteps.isEmpty())
        }

    @Test fun aReshowGoesThroughTheSameFallback() =
        runTest {
            val runtime = runtime()
            runtime.onState(rig.wearerState(latest = rig.reading(60)))
            presenter.showFails += level
            runtime.reshow(AlertKind.LOW.wire)
            assertEquals(listOf("show low", "plain resume low"), rig.presenter.log)
            assertTrue("Show:level IllegalStateException" in rig.published.failedSteps)
        }

    @Test fun failureLinesCarryNoGlucoseValues() =
        runTest {
            presenter.showFails += level
            presenter.plainFails += level
            presenter.problemFails += level
            runtime().onState(rig.wearerState(latest = rig.reading(60)))
            assertTrue(logged.isNotEmpty())
            (logged + rig.published.failedSteps).forEach { assertFalse("no digits in '$it'", it.any(Char::isDigit)) }
        }
}
