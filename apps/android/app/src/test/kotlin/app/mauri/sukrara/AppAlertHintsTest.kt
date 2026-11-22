// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.AlertSource
import app.mauri.sukrara.alerts.KindRule
import app.mauri.sukrara.alerts.NightProfile
import app.mauri.sukrara.alerts.QuietRule
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.KindFacts
import app.mauri.sukrara.core.model.KindOrder
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.feature.treatments.QuietRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** The kinds' order's alert facts: the latest fresh reading against the low threshold in force, and what rings. */
class AppAlertHintsTest {
    private val clock = FakeClock()
    private val settings = MutableStateFlow<AlertSettings?>(AlertSettings())
    private val glucose = MutableStateFlow(GlucoseState.Initial.copy(spec = SourceSpec.YourSensor))
    private val hints = AppAlertHints(settings, glucose, clock)

    private fun latest(
        mgdl: Int,
        age: Duration = Duration.ofMinutes(1),
    ) {
        val r = Reading(clock.now() - age, mgdl, null, SourceId.Libre3)
        glucose.value = glucose.value.copy(latest = r, recent = listOf(r))
    }

    private fun lowNow() = KindOrder.low(hints.kindFacts())

    @Test fun lowIsTheLatestFreshReadingUnderTheLowRule() {
        latest(60)
        assertTrue(lowNow())
        latest(45)
        assertTrue(lowNow()) // very low is the low side too
        latest(110)
        assertFalse(lowNow())
        // low is below the low threshold (70 by default): 69 is low, 70 is in range
        latest(69)
        assertTrue(lowNow())
        latest(70)
        assertFalse(lowNow())
        latest(60, age = Duration.ofMinutes(30))
        assertFalse(lowNow()) // stale: nothing is known now
        assertNull(hints.kindFacts().reading)
        // a low is a low whether or not its alert is on
        settings.value = AlertSettings(on = false)
        latest(60)
        assertTrue(lowNow())
        // settings not read yet: the default threshold
        settings.value = null
        assertTrue(lowNow())
    }

    @Test fun theFactsAreTheThresholdInForceAndWhatRings() {
        latest(110)
        glucose.value = glucose.value.copy(alerts = AlertUiState(firingKinds = setOf("falling_fast")))
        assertEquals(KindFacts(110, 70.0, setOf("falling_fast")), hints.kindFacts())
        assertTrue(lowNow()) // falling fast: carbs answer it
        // the night threshold inside its window
        val minute = clock.now().atZone(clock.zone).let { it.hour * 60 + it.minute }
        val night = AlertSettings.DEFAULT_RULES + (AlertKind.LOW to KindRule(on = true, threshold = 80.0, repeatMin = 5))
        settings.value =
            AlertSettings(night = NightProfile(on = true, fromMin = (minute + 1380) % 1440, toMin = (minute + 60) % 1440, rules = night))
        assertEquals(80.0, hints.kindFacts().lowBelow, 0.0)
    }

    @Test fun quietFollowsTheRulesOnThisPhone() =
        runTest {
            assertEquals(QuietRules(60, 15), hints.quiet.first())
            settings.value = AlertSettings(afterCarbs = QuietRule(on = false, min = 15))
            assertEquals(QuietRules(60, null), hints.quiet.first())
            glucose.value = glucose.value.copy(spec = SourceSpec.TheirSukrara)
            settings.value = AlertSettings(source = AlertSource.WEARER)
            assertEquals(QuietRules.NONE, hints.quiet.first())
            settings.value = AlertSettings(source = AlertSource.OWN)
            assertEquals(QuietRules(60, 15), hints.quiet.first())
            settings.value = null
            assertEquals(QuietRules.NONE, hints.quiet.first())
        }
}
