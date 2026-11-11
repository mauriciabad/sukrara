// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.share

import app.mauri.sukrara.share.owner.FollowerRecord
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.share.owner.PendingRequest

/**
 * What happened to the invite the share screen shows, read from the engine's state as it changes (PROTOCOL-v2 "Invite
 * link / QR": single use, the first request claims it, the wearer allows or denies), so the screen moves on by itself.
 */
sealed interface InviteProgress {
    /** Nobody asked with it yet: the QR and the link stay. */
    data object Open : InviteProgress

    /** Someone asked with it (nobody else can now): the approve dialog shows over the screen. */
    data class Asked(val request: PendingRequest) : InviteProgress

    /** Allowed: [follower] follows now (also listed in Settings › Sharing). */
    data class Following(val follower: FollowerRecord) : InviteProgress

    /** Denied: the invite is used up. [name] = who asked, when this screen saw the request (null otherwise). */
    data class Denied(val name: String?) : InviteProgress

    /** Nobody asked with it before it expired (or an Allow came too late). */
    data object Expired : InviteProgress
}

/**
 * Follows one invite ([token], expiring at [expiresAt], epoch s) through the engine's states. It remembers the request
 * it saw: a denied request leaves only the claim behind, and the screen still names who it was.
 */
class InviteWatch(
    private val token: String,
    private val expiresAt: Long,
) {
    private var asked: PendingRequest? = null

    /** The invite's progress in [state] at [now] (epoch s). Loading, a conflict or no identity keep it [InviteProgress.Open]. */
    fun progress(
        state: OwnerShareState,
        now: Long,
    ): InviteProgress {
        val s = state as? OwnerShareState.Sharing ?: return InviteProgress.Open
        s.pending.firstOrNull { it.token == token }?.let {
            asked = it
            return InviteProgress.Asked(it)
        }
        val record = s.invites.firstOrNull { it.token == token }
        val claimer = record?.claimedBy ?: asked?.follower
        if (claimer != null) {
            s.followers.lastOrNull { it.key == claimer && it.active }?.let { return InviteProgress.Following(it) }
            // answered without a new follower: denied (an Allow refused because the invite expired isn't `done`)
            if (record?.done == true) return InviteProgress.Denied(asked?.name)
        }
        return if (now >= expiresAt || (record == null && asked != null)) InviteProgress.Expired else InviteProgress.Open
    }
}
