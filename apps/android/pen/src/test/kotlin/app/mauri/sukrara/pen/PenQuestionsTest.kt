// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.pen

import app.mauri.sukrara.core.model.PenCandidate
import app.mauri.sukrara.core.model.PenTypedBy
import app.mauri.sukrara.pen.engine.Ledger
import app.mauri.sukrara.pen.engine.Pen
import app.mauri.sukrara.pen.engine.PenEngine
import app.mauri.sukrara.pen.engine.PenEntry
import app.mauri.sukrara.pen.engine.Question
import app.mauri.sukrara.pen.engine.Seen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The open questions as home's card and *Doses read* show them: what answering logs, and the live typed entries a pen
 * injection may be (nearest first, where each was typed); *Next time, just tap your pen*'s limits.
 */
class PenQuestionsTest {
    private val t = 1_792_497_600L

    private fun ledger(vararg q: Question) =
        Ledger(
            mapOf(
                "P1" to
                    Pen(
                        "P1",
                        since = 0,
                        first = 0,
                        doses =
                            mapOf(
                                "a" to Seen(t, 1.0, PenEngine.QUESTION, q = "d", p = true),
                                "b" to Seen(t + 60, 1.5, PenEngine.QUESTION, q = "d"),
                                "c" to Seen(t + 3600, 8.5, PenEngine.QUESTION, q = "m"),
                            ),
                    ),
            ),
            q.toList(),
        )

    @Test fun primingOrInjectedLogsTheSuggestion() {
        val q = Question("d", "P1", PenEngine.DOSES, listOf("a", "b"), at = t + 1800)
        val info = questionInfo(q, ledger(q), emptyList())!!
        assertFalse(info.same)
        assertEquals(1.5, info.units, 0.0)
        assertEquals(t + 60, info.t)
        assertEquals(listOf(true, false), info.doses.map { it.primed })
        assertEquals(listOf("a", "b"), info.keys)
    }

    @Test fun sameInjectionListsTheLiveTypedEntriesNearestFirst() {
        val q = Question("m", "P1", PenEngine.MATCH_Q, listOf("c"), listOf("far", "near", "gone"), at = t + 3700)
        val log =
            listOf(
                PenEntry("far", "rapid", 9.0, t + 3600 - 1800, u = 1, o = "mac"),
                PenEntry("near", "rapid", 8.0, t + 3600 + 300, u = 1, o = "follower"),
                PenEntry("gone", "rapid", 8.5, t + 3600, u = 1, del = true),
            )
        val info = questionInfo(q, ledger(q), log)!!
        assertTrue(info.same)
        assertEquals(8.5, info.units, 0.0)
        assertEquals(
            listOf(PenCandidate("near", 8.0, t + 3900, PenTypedBy.Follower), PenCandidate("far", 9.0, t + 1800, PenTypedBy.Mac)),
            info.candidates,
        )
    }

    @Test fun aQuestionWhoseDosesAreGoneIsNotShown() {
        val q = Question("x", "P1", PenEngine.DOSES, listOf("nope"), at = t)
        assertNull(questionInfo(q, ledger(q), emptyList()))
    }

    @Test fun theTapHintShowsOnceADayThreeTimesInAll() {
        var h = PenTapHint()
        assertTrue(h.due(100))
        h = h.shownOn(100)
        assertFalse(h.due(100))
        assertTrue(h.due(101))
        h = h.shownOn(101).shownOn(102)
        assertFalse(h.due(103))
    }
}
