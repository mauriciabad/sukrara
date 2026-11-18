// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.Link
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import app.mauri.sukrara.feature.sharing.HistoryLoss
import app.mauri.sukrara.feature.sharing.LeaveAnswers
import app.mauri.sukrara.feature.sharing.LeaveFacts
import app.mauri.sukrara.feature.sharing.LinkPort
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.follow.FollowStatus
import app.mauri.sukrara.share.follower.FollowState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * The settings side of leaving the link ([FollowerSettingsBridge]): whether *Their settings* is in use and what theirs
 * are ([state]), and the three outcomes link.json `leave` names.
 */
interface LeaveSettings {
    suspend fun state(): Pair<Boolean, Link.Theirs>

    /** *Keep {name}'s settings*: the wearer's mirrored rows copied as this phone's own. */
    suspend fun keepTheirs()

    /** *Sukrara's defaults*: the mirrored rows at their schema defaults. */
    suspend fun defaults()

    /** *Settings from* back to its default (*Their settings*) for a future link. */
    suspend fun forNextLink()
}

/**
 * Leaving the Sukrara link on this phone (one person, one link: contracts/sources/link.json, docs/44 Appendix G): what
 * it asks ([facts], [Link.leave]) and the leave itself ([leave]). The link is left through [leaveLink] alone, the one
 * place that calls the follower engine's leave (PROTOCOL-v2 `leave`, the wearer's phone told).
 */
class AppLink(
    private val follow: StateFlow<FollowState>,
    private val running: Flow<SourceSpec?>,
    private val readings: ReadingsRepository,
    private val treatments: TreatmentsRepository,
    private val settings: LeaveSettings,
    private val setFence: suspend (Instant?) -> Unit,
    /**
     * Leaves the link (the engine's `unfollow`: the record and key go, the wearer is told); [keepHistory] = the
     * readings and treatments stay. False when the key store refused.
     */
    private val leaveLink: suspend (keepHistory: Boolean) -> Boolean,
    /** After the leave: the alert state reset; with no next source, none (setup asks: the way back to following). */
    private val afterLeave: suspend (next: SourceSpec?) -> Unit,
) : LinkPort {
    override suspend fun facts(next: SourceSpec?): LeaveFacts? {
        val spec = running.first()
        val f = follow.value
        if (spec?.stream != true || f == FollowState.NotFollowing || f == FollowState.Loading) return null
        val following = f as? FollowState.Following
        val name =
            following
                ?.wearer
                ?.name
                ?.let(Profile::shownName)
                ?.takeIf { it.isNotBlank() }
        val told = following?.status == FollowStatus.Active || following?.status == FollowStatus.Requested
        val (wearerInUse, theirs) = settings.state()
        val loss = HistoryLoss.of(readings.span(), treatments.span())
        val personChange = next != null && next.person != spec.person
        return LeaveFacts(name, spec.person == Person.Yours, told, Link.leave(wearerInUse, theirs, loss != null, personChange), loss)
    }

    override suspend fun leave(
        answers: LeaveAnswers,
        next: SourceSpec?,
    ): Boolean {
        val facts = facts(next) ?: return true
        // 1. the settings this phone keeps (before the link goes: theirs are the engine's)
        when (facts.plan.result ?: if (answers.keepSettings) Link.Kept.Theirs else Link.Kept.Defaults) {
            Link.Kept.Theirs -> settings.keepTheirs()
            Link.Kept.Defaults -> settings.defaults()
            Link.Kept.Own -> Unit
        }
        settings.forNextLink()
        val questions = facts.plan.questions
        val keep =
            when {
                Link.Question.DeleteHistory in questions -> false
                Link.Question.History in questions -> answers.keepHistory
                else -> true
            }
        // 2. the link left (the wearer told); nothing more arrives through it
        if (!leaveLink(keep)) return false
        // 3. the history: kept (the wearer's entries read-only, the fence at the last reading) or gone
        if (keep) {
            keepHistory(treatments)
            setFence(readings.span().last)
        } else {
            readings.deleteAll()
            treatments.deleteAll()
            setFence(null)
        }
        // 4. no next source: none (setup asks); the caller stores the next one
        afterLeave(next)
        return true
    }

    companion object {
        /**
         * A follow kept behind another source by an earlier version ([stored] isn't the link, yet a follow record is
         * there: one person, one link can't have it) is left with no question: [leave] (the wearer told, the history
         * kept), the wearer's entries kept read-only. True when it left one.
         */
        suspend fun leaveLeftBehind(
            stored: SourceSpec?,
            following: suspend () -> Boolean,
            leave: suspend () -> Boolean,
            treatments: TreatmentsRepository,
        ): Boolean {
            if (stored == null || stored.stream || !following() || !leave()) return false
            keepHistory(treatments)
            return true
        }

        /**
         * The treatment log a left link leaves behind (Appendix G finding 10): entries that came through it are the
         * wearer's log, kept read-only and never sent anywhere ([TreatmentRecord.fromLink]); this phone's own entries
         * that never reached the wearer (*Sending…*, *Not sent*) become its own, so none waits for a link that is gone.
         */
        suspend fun keepHistory(treatments: TreatmentsRepository) {
            for (r in treatments.changedSince(Instant.EPOCH)) {
                val kept =
                    when {
                        r.delivery == TreatmentDelivery.Pending || r.delivery == TreatmentDelivery.Failed -> {
                            r.copy(creator = null, author = null, via = null, delivery = TreatmentDelivery.Local)
                        }

                        r.imported || r.fromSite || r.fromLink -> {
                            null
                        }

                        else -> {
                            r.copy(via = TreatmentRecord.FROM_LINK)
                        }
                    }
                kept?.let { treatments.replace(it) }
            }
        }
    }
}
