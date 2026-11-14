// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The slots as words (`GlucoseCard.slotTexts`, the glucose notification's Live Update metrics): each label stands alone
 * in sentence case, in the words' language (round 3 of #36: "some places start with uppercase and some are all
 * lowercase"), and they follow the card's own slots, whatever they are set to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SlotTextTest {
    private fun texts(
        cfg: GraphConfig,
        locale: Locale = Locale.ENGLISH,
    ) = GlucoseCard.slotTexts(Scenes.model(Scenes.day(125, swing = 30), Scenes.words(locale)), notificationCardStyle(cfg), 3)

    @Test fun `every label starts with a capital, the rest as the words say`() {
        val en = texts(GraphConfig())
        assertTrue(en.isNotEmpty())
        en.forEach { (_, label) -> assertTrue(label, label.first().isUpperCase()) }
        assertEquals("In range 1d", en.first().second)
        val es = texts(GraphConfig(), Locale.forLanguageTag("es"))
        es.forEach { (_, label) -> assertTrue(label, label.first().isUpperCase()) }
    }

    @Test fun `the words follow the card's slots as set`() {
        val avg = GraphConfig(slots = listOf(RingSpec(86_400L, kind = SlotKind.AVERAGE), RingSpec(86_400L), RingSpec(86_400L)))
        val first = texts(avg).first().second
        assertTrue(first, !first.startsWith("In range"))
    }
}
