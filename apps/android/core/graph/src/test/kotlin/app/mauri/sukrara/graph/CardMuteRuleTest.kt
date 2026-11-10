// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentQuiet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The card model every surface outside the app panel builds ([cardModelOf]: widgets, the notification, the lock screen)
 * takes the alert runtime's quiet windows: the mute lines and the last-treatment ring end where the engine's quiet does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CardMuteRuleTest {
    private val now = Instant.ofEpochSecond(Scenes.NOW)
    private val defaults = TreatmentQuiet(rapidS = 60 * 60L, carbsS = 15 * 60L)

    private fun model(
        quiet: TreatmentQuiet,
        vararg treatments: Treatment,
    ): CardModel {
        val state = GlucoseState.Initial.copy(alerts = AlertUiState(treatmentQuiet = quiet))
        return cardModelOf(state, treatments.toList(), now, Scenes.words())
    }

    @Test fun rapidInsulinQuietsForTheEnginesWindow() {
        val dose = Scenes.treatment("i1", TreatmentKind.RapidInsulin, 4.0, agoS = 15 * 60)
        val m = model(defaults, dose)
        assertEquals(dose.at.epochSecond + 3600, m.muteRule!!.muteUntil(dose))
        val last = LastTreatment.of(m.treatments, m.muteRule, m.status, m.nowS)!!
        assertEquals("i1", last.tr.id)
        assertEquals(dose.at.epochSecond + 3600, last.until)
        // 45 of 60 minutes left: the ring three quarters full
        assertEquals(0.75f, last.left(m.nowS), 0.001f)
    }

    @Test fun carbsAfterRunningInsulinRideAlongAsThePair() {
        val dose = Scenes.treatment("i1", TreatmentKind.RapidInsulin, 4.0, agoS = 20 * 60)
        val meal = Scenes.treatment("c1", TreatmentKind.Carbs, 40.0, agoS = 5 * 60)
        val m = model(defaults, dose, meal)
        val last = LastTreatment.of(m.treatments, m.muteRule, m.status, m.nowS)!!
        // running insulin keeps the slot; the carbs logged after it are its inner ring
        assertEquals("i1", last.tr.id)
        assertEquals("c1", last.carbs?.tr?.id)
        assertEquals(meal.at.epochSecond + 15 * 60, last.carbs?.until)
        assertEquals(10f / 15f, last.carbs!!.left(m.nowS), 0.001f)
    }

    @Test fun aRuleTurnedOffQuietsNothing() {
        val dose = Scenes.treatment("i1", TreatmentKind.RapidInsulin, 4.0, agoS = 15 * 60)
        val meal = Scenes.treatment("c1", TreatmentKind.Carbs, 40.0, agoS = 5 * 60)
        val m = model(defaults.copy(rapidS = null), dose, meal)
        assertNull(m.muteRule!!.muteUntil(dose))
        assertEquals(meal.at.epochSecond + 15 * 60, m.muteRule!!.muteUntil(meal))
        // no running insulin: the newest entry takes the slot, without a pair
        val last = LastTreatment.of(m.treatments, m.muteRule, m.status, m.nowS)!!
        assertEquals("c1", last.tr.id)
        assertNull(last.carbs)
    }

    /** *Alert me anyway* on the low side: the carbs logged up to then stop quieting at that moment, later ones don't. */
    @Test fun aLiftedQuietEndsAtTheLift() {
        val meal = Scenes.treatment("c1", TreatmentKind.Carbs, 15.0, agoS = 5 * 60)
        val more = Scenes.treatment("c2", TreatmentKind.Carbs, 15.0, agoS = 60)
        val dose = Scenes.treatment("i1", TreatmentKind.RapidInsulin, 4.0, agoS = 10 * 60)
        val lifted = Scenes.NOW - 2 * 60
        val m = model(defaults.copy(carbsLiftedAt = lifted), meal, more, dose)
        assertEquals(lifted, m.muteRule!!.muteUntil(meal))
        assertEquals(more.at.epochSecond + 15 * 60, m.muteRule!!.muteUntil(more))
        // the high side wasn't lifted
        assertEquals(dose.at.epochSecond + 3600, m.muteRule!!.muteUntil(dose))
        // the lifted carbs' ring is empty: its quiet is over
        val ring = LastTreatment.of(listOf(meal), m.muteRule, m.status, m.nowS)!!
        assertEquals(0f, ring.left(m.nowS), 0f)
    }

    @Test fun noEngineOrBothRulesOffMeansNoRule() {
        val dose = Scenes.treatment("i1", TreatmentKind.RapidInsulin, 4.0, agoS = 15 * 60)
        assertNull(model(TreatmentQuiet(), dose).muteRule)
        val last = model(TreatmentQuiet(), dose).let { LastTreatment.of(it.treatments, it.muteRule, it.status, it.nowS)!! }
        // the slot still shows the dose, on the empty track
        assertNull(last.until)
        assertEquals(0f, last.left(Scenes.NOW), 0f)
    }

    @Test fun longActingAndNotesQuietNothing() {
        val long = Scenes.treatment("l1", TreatmentKind.LongInsulin, 20.0, agoS = 60)
        val note = Scenes.treatment("n1", TreatmentKind.Note, 0.0, agoS = 60)
        val rule = model(defaults).muteRule!!
        assertNull(rule.muteUntil(long))
        assertNull(rule.muteUntil(note))
    }

    @Test fun equalWindowsMakeEqualModels() {
        val dose = Scenes.treatment("i1", TreatmentKind.RapidInsulin, 4.0, agoS = 60)
        assertEquals(model(defaults, dose).muteRule, model(defaults.copy(), dose).muteRule)
    }
}
