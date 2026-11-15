// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.graph.stats.TirRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Every label the Live Update can put over a metric, in every language the app speaks, fits a third of the card
 * ([Slots.fits]): written to fit, never cut by System UI (*Subiendo ráp…* was, round 6 of #36). A new language or a
 * new word that doesn't fit fails here, not on someone's lock screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MetricLabelFitTest {
    private val locales = listOf(Locale.ENGLISH, Locale.forLanguageTag("es"))

    private fun assertFits(
        locale: Locale,
        label: String,
    ) = assertTrue("$locale: \"$label\" is ${TextModel.width(label, 1f)} em, more than ${Slots.LABEL_EM}", Slots.fits(label))

    @Test fun `the trend as a label, every arrow`() {
        for (locale in locales) {
            val w = Scenes.words(locale)
            listOf(null, -3, -2, -1, 0, 1, 2, 3).forEach { assertFits(locale, w.trendLabel(it)) }
        }
        assertEquals("Bajada rápida", Scenes.words(Locale.forLanguageTag("es")).trendLabel(-2))
    }

    @Test fun `every metric, every period`() {
        val windows = listOf(43_200L, 86_400L, 259_200L, 604_800L, 1_209_600L, 2_592_000L, 7_776_000L)
        for (locale in locales) {
            for (window in windows) {
                val specs =
                    listOf(RingSpec(window), RingSpec(window, range = TirRange.TIGHT)) +
                        SlotKind.entries.filter { it != SlotKind.TIR }.map { RingSpec(window, kind = it) }
                for (spec in specs) {
                    GlucoseCard
                        .slotTexts(
                            Scenes.model(Scenes.day(125, swing = 30), Scenes.words(locale)),
                            notificationCardStyle(GraphConfig(slots = listOf(spec))),
                            1,
                        ).forEach { (_, label) -> assertFits(locale, label) }
                }
            }
        }
    }

    @Test fun `the last treatment, large amounts and a note, alone and with carbs`() {
        val rapid = Scenes.treatment("r", TreatmentKind.RapidInsulin, 12.5, 12 * 60)
        val long = Scenes.treatment("l", TreatmentKind.LongInsulin, 28.5, 12 * 60)
        val carbs = Scenes.treatment("c", TreatmentKind.Carbs, 120.0, 10 * 60)
        val note = Scenes.treatment("n", TreatmentKind.Note, 0.0, 10 * 60).copy(note = "Pasta con tomate y queso")
        for (locale in locales) {
            for (trs in listOf(listOf(rapid), listOf(long), listOf(carbs), listOf(note), listOf(rapid, carbs))) {
                GlucoseCard
                    .slotTexts(
                        Scenes.model(Scenes.day(125, swing = 30), Scenes.words(locale), treatments = trs),
                        notificationCardStyle(GraphConfig()),
                        2,
                    ).forEach { (_, label) -> assertFits(locale, label) }
            }
        }
    }
}
