// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import app.mauri.sukrara.core.model.Link
import app.mauri.sukrara.core.model.SourceSpec

/**
 * What leaving the Sukrara link asks on this phone now (contracts/sources/link.json `leave`, [Link.leave]): whom it
 * follows ([name], the wearer's as the stream gives it; [yours] = *Sukrara on your other phone*), whether the wearer's
 * phone is told ([told]: it knows this phone, approved or waiting), the questions ([plan]) and the history the link
 * brought ([history]: what *Delete it*, or a change of whose glucose, deletes).
 */
data class LeaveFacts(
    val name: String?,
    val yours: Boolean,
    val told: Boolean,
    val plan: Link.Leave,
    val history: HistoryLoss?,
)

/** The leave questions' answers; the defaults are link.json's (nothing the person sees shifts, nothing is lost). */
data class LeaveAnswers(
    /** *Keep {name}'s settings* (true) or *Sukrara's defaults*; read only when [Link.Question.Settings] is asked. */
    val keepSettings: Boolean = Link.DEFAULT_SETTINGS == Link.Kept.Theirs,
    /** *Keep it* (true) or *Delete it*; read only when [Link.Question.History] is asked. */
    val keepHistory: Boolean = Link.DEFAULT_KEEP_HISTORY,
)

/**
 * Leaving the Sukrara link (one person, one link: docs/44 Appendix G): what it asks ([facts]) and the leave itself
 * ([leave]), the one way out of a link on this phone (*Stop following*, a source picked instead, setup's source step).
 */
interface LinkPort {
    /** What leaving asks now with [next] picked instead (null = *Stop following* alone); null = no link to leave. */
    suspend fun facts(next: SourceSpec?): LeaveFacts?

    /**
     * Leaves the link with [answers], in this order: the settings kept (theirs copied as this phone's own, or the
     * defaults; *Settings from* back to its default for a future link), the history kept (the fence at its last
     * reading, the wearer's entries read-only) or deleted, the link left (the wearer's phone told), and with no [next]
     * source the phone has none (setup asks). False when the key store refused (nothing left: try again).
     */
    suspend fun leave(
        answers: LeaveAnswers,
        next: SourceSpec?,
    ): Boolean

    companion object {
        /** No link (screenshot tests, a build without sharing). */
        val None: LinkPort =
            object : LinkPort {
                override suspend fun facts(next: SourceSpec?): LeaveFacts? = null

                override suspend fun leave(
                    answers: LeaveAnswers,
                    next: SourceSpec?,
                ) = true
            }
    }
}
