// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Doses
import app.mauri.sukrara.alerts.QuietRule
import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentQuiet
import app.mauri.sukrara.graph.muteRuleOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/**
 * The quiet windows the runtime publishes for the cards' mute lines and last-treatment ring: the rules its engine runs
 * (a follower mirroring the wearer: the wearer's), republished when they change, ending where the engine's quiet does.
 */
class AlertTreatmentQuietTest {
    private val rig = AlertRig()

    @Test fun noEngineNoWindows() =
        runTest {
            rig.runtime().refreshUi()
            assertEquals(TreatmentQuiet(), rig.published.treatmentQuiet)
        }

    @Test fun theWearersRulesAndTheirChanges() =
        runTest {
            val runtime = rig.runtime()
            runtime.onState(rig.wearerState(latest = rig.reading(120)))
            assertEquals(TreatmentQuiet(rapidS = 60 * 60L, carbsS = 15 * 60L), rig.published.treatmentQuiet)
            runtime.onSettings(AlertSettings(afterInsulin = QuietRule(on = false, min = 60), afterCarbs = QuietRule(on = true, min = 30)))
            assertEquals(TreatmentQuiet(rapidS = null, carbsS = 30 * 60L), rig.published.treatmentQuiet)
        }

    @Test fun aMirroringFollowerDrawsTheWearersRules() =
        runTest {
            val runtime = rig.runtime()
            runtime.onState(rig.followerState(latest = rig.reading(120)))
            runtime.onWearerSettings(
                AlertSettings(afterInsulin = QuietRule(on = true, min = 90), afterCarbs = QuietRule(on = false, min = 15)),
            )
            assertEquals(TreatmentQuiet(rapidS = 90 * 60L, carbsS = null), rig.published.treatmentQuiet)
        }

    @Test fun theCardsQuietEndsWhereTheEnginesDoes() =
        runTest {
            val runtime = rig.runtime()
            runtime.onState(rig.wearerState(latest = rig.reading(60)))
            val at = rig.clock.now().epochSecond
            rig.treatments.value = treatmentsOf(Doses(carbs = listOf(at)))
            runtime.onDoses(Doses(carbs = listOf(at)))
            val taken = rig.published.status as CardStatus.Taken
            val carbs = rig.treatments.value.single()
            assertEquals(taken.until, muteRuleOf(rig.published)!!.muteUntil(carbs))
            assertNull(muteRuleOf(rig.published.copy(treatmentQuiet = TreatmentQuiet())))
        }

    /** *Alert me anyway* ends the card's quiet at once: the pill asks for carbs again, the mute line and ring stop (#154). */
    @Test fun alertMeAnywayEndsTheCardsQuiet() =
        runTest {
            val runtime = rig.runtime()
            runtime.onState(rig.wearerState(latest = rig.reading(50)))
            val at = rig.clock.now().epochSecond
            rig.treatments.value = treatmentsOf(Doses(carbs = listOf(at)))
            runtime.onDoses(Doses(carbs = listOf(at)))
            assertEquals(CardStatus.Taken(TreatmentKind.Carbs, at, at + 15 * 60), rig.published.status)
            rig.clock.advance(Duration.ofMinutes(1))
            runtime.unmute("very_low")
            val lifted = at + 60
            assertEquals(CardStatus.Needed(lowSide = true), rig.published.status)
            assertEquals(TreatmentQuiet(rapidS = 60 * 60L, carbsS = 15 * 60L, carbsLiftedAt = lifted), rig.published.treatmentQuiet)
            assertEquals(lifted, muteRuleOf(rig.published)!!.muteUntil(rig.treatments.value.single()))
        }
}
