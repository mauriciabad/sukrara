// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.protocol.KeyPair
import app.mauri.sukrara.protocol.NostrCrypto
import app.mauri.sukrara.protocol.SecretKey
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.owner.IdentityCheck
import app.mauri.sukrara.protocol.owner.OwnerMarks
import kotlinx.serialization.Serializable

/** One read key G_n. [secretHex] is a secret: never logged. */
@Serializable
data class EpochKey(val n: Int, val secretHex: String) {
    override fun toString() = "EpochKey($n)"
}

@Serializable
data class FollowerRecord(
    val key: String,
    val name: String,
    val since: Long,
    val revoked: Long? = null,
    /** The follower's generated picture as JSON (PROTOCOL-v2 "Profiles"), null if it never sent one. */
    val avatarJson: String? = null,
    /** When the follower wrote the profile update applied last (its rumor's created_at, epoch s): an older one is ignored. */
    val profileAt: Long = 0,
) {
    val active: Boolean get() = revoked == null
}

/** A single-use invite: [claimedBy] = first requester; [done] = approved or denied (kept until expiry for `invite-used`). */
@Serializable
data class InviteRecord(
    val token: String,
    val expiry: Long,
    val label: String = "",
    val sentAt: Long = 0,
    val claimedBy: String? = null,
    val done: Boolean = false,
    /** Offered over Bluetooth by *Pair nearby* (10 min): anyone in range can ask, so both phones compare a code. */
    val nearby: Boolean = false,
    /** *Pair nearby*: the wearer's pairing nonce `nb` (the offer carries its commitment; the claimer gets it). */
    val pairNonce: String? = null,
) {
    override fun toString() = "InviteRecord(nearby=$nearby, claimed=${claimedBy != null}, done=$done)"
}

/** An access request waiting for the wearer. [others] = later phones that tried the same invite (a leaked link). */
@Serializable
data class PendingRequest(
    val follower: String,
    val name: String,
    val token: String,
    val code: String,
    val at: Long,
    val avatarJson: String? = null,
    val label: String = "",
    val others: Int = 0,
    /** When the invite this request used was made (the approve dialog's "sent … · asked …"); 0 = unknown. */
    val inviteSentAt: Long = 0,
    /**
     * Asked with the *Pair nearby* invite: [code] is the 6-digit pairing code both phones show (PROTOCOL-v2 §Nearby
     * pairing); the wearer allows only if the follower's phone shows the same.
     */
    val nearby: Boolean = false,
) {
    /** Allow only with the match code typed from the follower's phone: other phones tried the same link. */
    val needsTypedCode: Boolean get() = others > 0 && !nearby

    /** Allow straight from the notification: an ordinary link nobody else tried (nearby needs the code compared). */
    val allowFromNotification: Boolean get() = !needsTypedCode && !nearby
}

/** Another app publishes roster / backup as this identity; persisted so the block survives a restart. */
@Serializable
data class ConflictRecord(val reason: IdentityCheck.Reason, val seenAt: Long, val eventCreatedAt: Long)

/**
 * A control event (key delivery, notice, roster, backup) saved before it is published and dropped once a relay
 * accepted it, so a rotation interrupted by a crash still reaches every follower. [key] dedupes (newest roster /
 * backup only); [follower] lets a removed follower's deliveries be dropped.
 */
@Serializable
data class PendingControl(val key: String, val eventId: String, val eventJson: String, val follower: String? = null)

/**
 * Backup › Replace this identity is telling [told] (follower keys) that this identity stopped sharing, with notices
 * made at [at] (PROTOCOL-v2 "Replace the identity"). Saved before the notices go out: an identity that still has it
 * when sharing starts was never replaced (the app was closed meanwhile), so those followers get the key again.
 */
@Serializable
data class ReplaceRecord(val at: Long, val told: List<String>)

/**
 * Everything the wearer's phone knows about sharing (a restore from the recovery code brings the identity over
 * from another phone). Encrypted at rest by [OwnerIdentityStore]; holds I, D and
 * every G_n, so [toString] is redacted. The owner backup carries the subset in `BackupPayload` (never D).
 */
@Serializable
data class OwnerState(
    val identityHex: String,
    val deviceHex: String,
    val sid: String,
    val epochs: List<EpochKey>,
    val followers: List<FollowerRecord> = emptyList(),
    val invites: List<InviteRecord> = emptyList(),
    val pending: List<PendingRequest> = emptyList(),
    val writers: Set<String> = emptySet(),
    /** The wearer's profile JSON (name + picture), sent only to followers. */
    val profileJson: String? = null,
    val rosterPublishedAt: Long = 0,
    val backupPublishedAt: Long = 0,
    val recoveryExportedAt: Long = 0,
    /** When the identity was handed to the phone's password manager (Credential Manager); 0 = never. */
    val passwordManagerSavedAt: Long = 0,
    /** When this phone restored the identity from a code or the password manager; 0 = created here. */
    val restoredAt: Long = 0,
    val conflict: ConflictRecord? = null,
    val pendingControl: List<PendingControl> = emptyList(),
    /** When the whole treatment log (last 30 days) went out last (republished at least weekly: 30-day expiry). */
    val treatmentsPublishedAt: Long = 0,
    /** The mirrored settings last published (PROTOCOL-v2 §Settings): their content, its `u` and the event's `created_at`. */
    val settingsJson: String? = null,
    val settingsU: Long = 0,
    val settingsPublishedAt: Long = 0,
    /**
     * Followers approved from a *Pair nearby* request: Nearby devices lists them also while out of range. This phone
     * only (never in the owner backup); Remove / Forget clears it.
     */
    val nearbyPaired: Set<String> = emptySet(),
    /** Set only while Backup › Replace this identity tells the followers (see [ReplaceRecord]). */
    val replacing: ReplaceRecord? = null,
) {
    val current: EpochKey get() = epochs.maxBy { it.n }

    /**
     * Always the current default set (not stored, not taken from a backup), so the relays the roster,
     * key deliveries and invites announce are the ones the pool talks to.
     */
    val relays: List<String> get() = Wire.DEFAULT_RELAYS

    fun activeFollowers(): List<FollowerRecord> = followers.filter { it.active }

    /** Writers that are still approved followers (the roster never names a removed one). */
    fun activeWriters(): List<String> = writers.filter { w -> followers.any { it.key == w && it.active } }

    fun identity(crypto: NostrCrypto): KeyPair = pair(crypto, identityHex)

    fun device(crypto: NostrCrypto): KeyPair = pair(crypto, deviceHex)

    fun readKey(crypto: NostrCrypto, e: EpochKey = current): KeyPair = pair(crypto, e.secretHex)

    /** What [app.mauri.sukrara.protocol.owner.IdentityConflict] needs to tell our own roster / backup from another app's. */
    fun marks(crypto: NostrCrypto): OwnerMarks =
        OwnerMarks(
            identity = identity(crypto),
            device = device(crypto).public.hex,
            sid = sid,
            epochsByGk = epochs.associate { e -> readKey(crypto, e).let { Wire.gk(it.public) to (e.n to it) } },
            lastRosterAt = rosterPublishedAt,
            lastBackupAt = backupPublishedAt,
        )

    override fun toString() =
        "OwnerState(epoch=${current.n}, followers=${activeFollowers().size}, pending=${pending.size}, conflict=${conflict?.reason})"

    private fun pair(crypto: NostrCrypto, hex: String): KeyPair = crypto.keyPair(SecretKey.ofHex(hex)!!)!!
}
