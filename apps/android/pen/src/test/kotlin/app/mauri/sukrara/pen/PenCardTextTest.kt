// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.pen

import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.pen.engine.PenEntry
import app.mauri.sukrara.pen.engine.Report
import app.mauri.sukrara.pen.engine.UndoRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/** The read card's words for a big read (*Review*) and for a read that brings earlier days' injections (en + es). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PenCardTextTest {
    // 2026-10-20 12:00 UTC
    private val now = 1_792_497_600L

    private fun text() = PenCardText(ApplicationProvider.getApplicationContext())

    private fun entry(
        id: String,
        daysAgo: Int,
        a: Double,
    ) = PenEntry(id, "rapid", a, now - daysAgo * 86400L, u = 1, pen = "P1")

    private fun state(
        report: Report,
        entries: List<PenEntry>,
    ) = PenReadState(PenReadState.Stage.Done, "P1", report, UndoRecord("P1", emptyList(), emptyMap()), entries = entries)

    @Before fun utc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test fun aBigReadSaysHowManyAndSinceWhenAndOffersReviewFirst() {
        val es = listOf(entry("a", 4, 6.0), entry("b", 2, 5.0), entry("c", 0, 4.0))
        val r = Report("P1", created = listOf("a", "c"), merged = listOf("b"), bigSince = es[0].t)
        val card = text().card(state(r, es))
        assertEquals("3 injections logged", card.title)
        assertTrue(card.lines[0], card.lines[0].startsWith("Since 16 Oct"))
        assertEquals("1 replaced your entry", card.lines[1])
        assertEquals(PenAction.Review("P1"), card.actions.first())
        assertTrue(PenAction.Undo in card.actions)
    }

    @Test fun aBigReadOfOneInjectionStillSaysItsDay() {
        val e = entry("a", 4, 6.0)
        val card = text().card(state(Report("P1", created = listOf("a"), bigSince = e.t), listOf(e)))
        assertEquals("1 injection logged", card.title)
        assertTrue(card.lines[0], card.lines[0].startsWith("Since 16 Oct"))
        assertEquals(PenAction.Review("P1"), card.actions.first())
    }

    @Test fun anEverydayReadWithYesterdaysInjectionNamesTheDay() {
        val es = listOf(entry("a", 1, 6.0), entry("b", 0, 4.0))
        val card = text().card(state(Report("P1", created = listOf("a", "b")), es))
        assertEquals("2 injections logged", card.title)
        assertTrue(card.lines[0], card.lines[0].startsWith("10u since 19 Oct"))
        assertTrue(card.actions.none { it is PenAction.Review })
    }

    @Test fun oneLoggedInjectionCountsUpToItsAmountUnderACheck() {
        val e = entry("a", 0, 8.5)
        val card = text().card(state(Report("P1", created = listOf("a")), listOf(e)))
        assertEquals("8.5u logged", card.title)
        assertEquals(8.5, card.count!!, 0.0)
        assertEquals(PenCard.Glyph.Check, card.glyph)
        assertEquals("4.5u logged", text().logged(4.5))
    }

    @Test fun aFailedReadShowsTheErrorMarkAndNothingCounts() {
        val card = text().card(PenReadState(PenReadState.Stage.Failed))
        assertEquals(PenCard.Glyph.Error, card.glyph)
        assertEquals(null, card.count)
    }

    @Test fun anInjectionNearSeveralTypedEntriesOffersChooseFirst() {
        val l =
            app.mauri.sukrara.pen.engine.Ledger(
                mapOf(
                    "P1" to
                        app.mauri.sukrara.pen.engine.Pen(
                            "P1",
                            since = 0,
                            first = 0,
                            doses =
                                mapOf(
                                    "k1" to
                                        app.mauri.sukrara.pen.engine
                                            .Seen(now, 8.5, "question", q = "q1"),
                                ),
                        ),
                ),
                listOf(
                    app.mauri.sukrara.pen.engine
                        .Question("q1", "P1", "match", listOf("k1"), listOf("t1", "t2"), now),
                ),
            )
        val typed = listOf(PenEntry("t1", "rapid", 8.0, now - 300, u = 1), PenEntry("t2", "rapid", 9.0, now + 600, u = 1))
        val actions = text().questionActions(l.questions[0], l, typed)
        assertEquals(PenAction.Choose("q1"), actions.first())
        assertEquals(PenAction.Same("q1", listOf("k1"), null), actions[1])
    }

    @Test
    @Config(qualifiers = "es")
    fun theBigReadCardInSpanish() {
        val es = listOf(entry("a", 4, 6.0), entry("b", 0, 4.0))
        val card = text().card(state(Report("P1", created = listOf("a", "b"), bigSince = es[0].t), es))
        assertEquals("2 inyecciones registradas", card.title)
        assertTrue(card.lines[0], card.lines[0].startsWith("Desde el 16 oct"))
    }
}
