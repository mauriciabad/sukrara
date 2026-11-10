// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentQuiet
import app.mauri.sukrara.feature.home.Samples.NOW
import app.mauri.sukrara.graph.CardModel
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.LastTreatment
import app.mauri.sukrara.graph.stats.toPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app panel's card takes the alert runtime's quiet windows, as every other surface: mute lines, ring and pair. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PanelModelTest {
    private val words = CardWords.of(ApplicationProvider.getApplicationContext<android.content.Context>().resources)
    private val dose = Samples.treatment("i1", TreatmentKind.RapidInsulin, 4.0, minutesAgo = 15)
    private val meal = Samples.treatment("c1", TreatmentKind.Carbs, 40.0, minutesAgo = 5)

    private fun model(quiet: TreatmentQuiet): CardModel {
        val glucose = Samples.wearer().copy(alerts = AlertUiState(treatmentQuiet = quiet))
        return panelModel(glucose, glucose.recent.map { it.toPoint() }, listOf(dose, meal), NOW, words)
    }

    private fun CardModel.last() = LastTreatment.of(treatments, muteRule, status, nowS)

    @Test fun rapidInsulinShowsItsQuietAndTheCarbsPair() {
        val m = model(TreatmentQuiet(rapidS = 3600, carbsS = 900))
        assertEquals(dose.at.epochSecond + 3600, m.muteRule!!.muteUntil(dose))
        val last = m.last()!!
        assertEquals("i1", last.tr.id)
        assertEquals(0.75f, last.left(m.nowS), 0.001f)
        assertEquals("c1", last.carbs?.tr?.id)
        assertEquals(10f / 15f, last.carbs!!.left(m.nowS), 0.001f)
    }

    @Test fun rulesOffDrawNoMuteLinesAndAnEmptyRing() {
        val m = model(TreatmentQuiet())
        assertNull(m.muteRule)
        val last = m.last()!!
        // nothing quiets: the newest entry takes the slot on the empty track
        assertEquals("c1", last.tr.id)
        assertEquals(0f, last.left(m.nowS), 0f)
    }
}
