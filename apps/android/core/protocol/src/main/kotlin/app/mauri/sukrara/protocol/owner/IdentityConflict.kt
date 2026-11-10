// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol.owner

import app.mauri.sukrara.protocol.KeyPair
import app.mauri.sukrara.protocol.NostrCrypto
import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.event.BackupCodec
import app.mauri.sukrara.protocol.event.RosterCodec

/**
 * What the wearer's phone must persist to tell its own roster / backup from another app publishing as the same
 * identity (e.g. the old phone still running after a restore): its device key, the read keys it holds by `gk`
 * (epoch number → public key), and the `created_at` of the last roster and backup it published.
 */
data class OwnerMarks(
    val identity: KeyPair,
    val device: String,
    val sid: String,
    val epochsByGk: Map<String, Pair<Int, KeyPair>>,
    val lastRosterAt: Long,
    val lastBackupAt: Long,
)

/** The verdict on one roster or backup signed by our identity. */
sealed interface IdentityCheck {
    /** Ours (an echo), older, or not about our stream: nothing to do. */
    data object Fine : IdentityCheck

    /**
     * Another device publishes as this identity. The engine stops republishing roster / backup and shows a blocking
     * state ("Sukrara is also running somewhere else with this identity") instead of fighting it.
     */
    data class Conflict(val reason: Reason, val otherDevices: List<String> = emptyList()) : IdentityCheck

    enum class Reason { RosterFromOtherDevice, RosterUnknownEpoch, BackupFromElsewhere }
}

/** Detects "another app is publishing as this identity" from the owner's own roster and backup events. */
object IdentityConflict {
    fun check(crypto: NostrCrypto, marks: OwnerMarks, ev: SignedEvent): IdentityCheck {
        if (ev.pubkey != marks.identity.public.hex || ev.kind != Wire.KIND_DATA || !crypto.verify(ev)) return IdentityCheck.Fine
        return when (ev.tag("d")) {
            Wire.rosterD(marks.sid) -> roster(crypto, marks, ev)
            Wire.backupD(marks.identity.public) -> backup(crypto, marks, ev)
            else -> IdentityCheck.Fine
        }
    }

    private fun roster(crypto: NostrCrypto, marks: OwnerMarks, ev: SignedEvent): IdentityCheck {
        if (ev.createdAt <= marks.lastRosterAt) return IdentityCheck.Fine
        val (_, readKey) = marks.epochsByGk[ev.tag("gk")] ?: return IdentityCheck.Conflict(IdentityCheck.Reason.RosterUnknownEpoch)
        val plain = crypto.decrypt(readKey.secret, marks.identity.public, ev.content) ?: return IdentityCheck.Fine
        val roster = runCatching { RosterCodec.decode(plain) }.getOrNull() ?: return IdentityCheck.Fine
        return if (marks.device in roster.devices) {
            IdentityCheck.Fine
        } else {
            IdentityCheck.Conflict(IdentityCheck.Reason.RosterFromOtherDevice, roster.devices)
        }
    }

    /** A newer backup that names an epoch we don't hold, or lacks our newest one, was written by someone else. */
    private fun backup(crypto: NostrCrypto, marks: OwnerMarks, ev: SignedEvent): IdentityCheck {
        if (ev.createdAt <= marks.lastBackupAt) return IdentityCheck.Fine
        val plain = crypto.decrypt(marks.identity.secret, marks.identity.public, ev.content) ?: return IdentityCheck.Fine
        val backup = runCatching { BackupCodec.decode(plain) }.getOrNull() ?: return IdentityCheck.Fine
        val ours =
            marks.epochsByGk.values
                .map { it.first }
                .toSet()
        val theirs = backup.epochs.map { it.n }.toSet()
        val newest = ours.maxOrNull()
        return if (backup.sid != marks.sid || !ours.containsAll(theirs) || (newest != null && newest !in theirs)) {
            IdentityCheck.Conflict(IdentityCheck.Reason.BackupFromElsewhere)
        } else {
            IdentityCheck.Fine
        }
    }

    /**
     * The epoch "I turned it off — retry" must rotate to, or null when republishing roster and backup is enough
     * (PROTOCOL-v2 "Two apps as one identity"). [found] = what the relays hold signed by the identity (its newest roster
     * and backup); [unknownEpochSeen] = a roster under an epoch we don't hold was seen (the recorded conflict).
     *
     * Followers rank rosters and keys by epoch number, so a rotation that doesn't go above the other app's newest epoch
     * would be ignored. Its backup (encrypted to the identity, which we hold) lists its epochs: rotate past the newest of
     * ours and theirs. When the other app holds an epoch no readable backup names (its backup isn't on the relays, or
     * none answered), its number is unknown: jump [EPOCH_JUMP] past the newest known.
     */
    fun retryEpoch(crypto: NostrCrypto, marks: OwnerMarks, found: List<SignedEvent>, unknownEpochSeen: Boolean): Int? {
        val ours = marks.epochsByGk.values.maxOfOrNull { it.first } ?: 0
        val mine = found.filter { it.pubkey == marks.identity.public.hex && it.kind == Wire.KIND_DATA && crypto.verify(it) }
        val theirs =
            mine
                .filter { it.tag("d") == Wire.backupD(marks.identity.public) }
                .mapNotNull { ev ->
                    crypto
                        .decrypt(marks.identity.secret, marks.identity.public, ev.content)
                        ?.let { runCatching { BackupCodec.decode(it) }.getOrNull() }
                        ?.takeIf { it.sid == marks.sid }
                }.flatMap { b ->
                    b.epochs.mapNotNull { e -> crypto.parseNsec(e.nsec)?.let(crypto::keyPair)?.let { Wire.gk(it.public) to e.n } }
                }.toMap()
        val unknownRosters =
            mine
                .filter { it.tag("d") == Wire.rosterD(marks.sid) && it.createdAt > marks.lastRosterAt }
                .mapNotNull { it.tag("gk") }
                .filter { it !in marks.epochsByGk }
                .toSet()
        val newest = maxOf(ours, theirs.values.maxOrNull() ?: 0)
        // an unknown epoch that no backup we read names: its number may be anything
        val unread = (unknownRosters - theirs.keys).isNotEmpty() || (unknownEpochSeen && unknownRosters.isEmpty() && newest == ours)
        return when {
            unread -> newest + 1 + EPOCH_JUMP
            newest > ours || unknownRosters.isNotEmpty() || unknownEpochSeen -> newest + 1
            else -> null
        }
    }

    /** How far a retry jumps past the newest known epoch when the other app's newest epoch can't be read. */
    const val EPOCH_JUMP = 100
}
