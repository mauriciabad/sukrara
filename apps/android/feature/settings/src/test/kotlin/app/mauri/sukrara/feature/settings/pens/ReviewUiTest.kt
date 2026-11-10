// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.pens

import app.mauri.sukrara.core.model.PenDoseInfo
import app.mauri.sukrara.core.model.PenReview
import app.mauri.sukrara.core.model.PenSessionInfo
import app.mauri.sukrara.core.model.PenSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** *Review* of a big read lists only that read's sessions, and keeps listing them after *Undo this read*. */
class ReviewUiTest {
    private fun session(
        key: String,
        status: PenSessionStatus,
    ) = PenSessionInfo(
        serial = "P1",
        t = 1000,
        units = 6.0,
        primed = 0.0,
        status = status,
        entry = if (status == PenSessionStatus.NotLogged) null else "e-$key",
        question = null,
        sameQuestion = false,
        mergedFrom = null,
        incomplete = false,
        doses = listOf(PenDoseInfo(key, 1000, 6.0, primed = false, incomplete = false)),
    )

    private val review = PenReview(at = 2000, since = 1000, entries = 2, merged = 0, keys = setOf("a", "b"))

    @Test fun onlyTheReadsSessionsAreListed() {
        val all =
            listOf(session("a", PenSessionStatus.Logged), session("b", PenSessionStatus.Merged), session("c", PenSessionStatus.Logged))
        val ui = ReviewUi.next(null, review, all)
        assertEquals(listOf("a", "b"), ui.sessions.map { it.doses.single().key })
        assertEquals(review, ui.review)
    }

    @Test fun afterUndoThisReadItsSessionsStayListedAsNotLogged() {
        val before = ReviewUi.next(null, review, listOf(session("a", PenSessionStatus.Logged), session("b", PenSessionStatus.Logged)))
        val after = ReviewUi.next(before, null, listOf(session("a", PenSessionStatus.NotLogged), session("b", PenSessionStatus.NotLogged)))
        assertNull(after.review)
        assertEquals(listOf(PenSessionStatus.NotLogged, PenSessionStatus.NotLogged), after.sessions.map { it.status })
    }

    @Test fun nothingToReviewListsNothing() {
        val ui = ReviewUi.next(null, null, listOf(session("a", PenSessionStatus.Logged)))
        assertEquals(emptyList<PenSessionInfo>(), ui.sessions)
    }
}
