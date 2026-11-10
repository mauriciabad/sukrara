// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model.storage

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/*
 * Storage ports. They live in this JVM module so pure engines and the in-memory fakes (`:core:testing`) can use
 * them; `:core:data` implements them on Android (DataStore, Keystore, Room).
 */

/** One setting of THE schema (`contracts/settings/settings.json`); the generated `SettingsSpec` lists them all. */
interface SettingSpec<T> {
    /** The schema key, also the Preferences DataStore key. */
    val key: String
    val default: T
}

/** Settings in Preferences DataStore, keyed by the schema. Unset settings read as their default. */
interface SettingsRepository {
    fun <T> flow(spec: SettingSpec<T>): Flow<T>

    suspend fun <T> set(
        spec: SettingSpec<T>,
        value: T,
    )

    /** Applies [transform] to the stored value in one atomic edit (two quick edits from different screens both land). */
    suspend fun <T> update(
        spec: SettingSpec<T>,
        transform: (T) -> T,
    )
}

/** One typed state file (typed DataStore with kotlinx.serialization). Updates are atomic. */
interface StateStore<T> {
    val data: Flow<T>

    suspend fun update(transform: (T) -> T)
}

/**
 * Small secrets (identity key, sensor pairing secrets, account passwords / tokens), AES-GCM encrypted with an Android
 * Keystore key (alias `sukrara.<name>`) in `files/secure/<name>.bin`. Never in backups, never logged.
 */
interface SecureStore {
    /**
     * The secret, or null when none was ever written (or it was deleted). A secret that exists but can't be read
     * throws [SecretUnreadableException]: callers must never treat that as "no secret" (e.g. "no identity").
     */
    suspend fun read(name: String): ByteArray?

    suspend fun write(
        name: String,
        bytes: ByteArray,
    )

    suspend fun delete(name: String)
}

/** A stored secret exists but can't be decrypted now. The file stays in place (a damaged one is also copied aside). */
class SecretUnreadableException(
    val name: String,
    /** True when retrying later may work (the Keystore was busy); false when the file is damaged. */
    val transient: Boolean,
    cause: Throwable?,
) : Exception("secret '$name' unreadable (transient=$transient)", cause)

/** How many stored entries there are and when the oldest and newest are (null when [count] is 0). */
data class StoredSpan(
    val count: Int,
    val first: Instant?,
    val last: Instant?,
) {
    companion object {
        val EMPTY = StoredSpan(0, null, null)
    }
}

/** Glucose history (Room). Upserting the same timestamp and source replaces the value. */
interface ReadingsRepository {
    /** Readings at or after [from], oldest first, updated as new ones arrive. */
    fun window(from: Instant): Flow<List<Reading>>

    /** The newest reading, updated as new ones arrive. */
    fun latest(): Flow<Reading?>

    /** Readings in [from, to), oldest first (backfill, export). */
    suspend fun range(
        from: Instant,
        to: Instant,
    ): List<Reading>

    suspend fun upsert(readings: List<Reading>)

    /** Retention: deletes readings older than [cutoff]; returns how many. */
    suspend fun deleteBefore(cutoff: Instant): Int

    /** What [deleteFrom] would delete: how many readings from [sources] and their first and last time. */
    suspend fun spanOf(sources: Set<SourceId>): StoredSpan

    /** A role change: deletes every reading from [sources] (another person's); returns how many. */
    suspend fun deleteFrom(sources: Set<SourceId>): Int
}

/** How a treatment reached this phone and whether the wearer has it (followers write through an outbox). */
enum class TreatmentDelivery { Local, Pending, Delivered }

/**
 * A stored treatment with what sharing needs: [creator] (the key that created it; writers may change only their
 * own), [author] (the key that signed this version), [via] (relay host / source), and the pen-read keys as opaque
 * JSON owned by the pens code.
 */
data class TreatmentRecord(
    val treatment: Treatment,
    val creator: String? = null,
    val author: String? = null,
    val via: String? = null,
    val receivedAt: Instant? = null,
    val delivery: TreatmentDelivery = TreatmentDelivery.Local,
    val penJson: String? = null,
)

/** Treatments (Room), last-writer-wins on `Treatment.modifiedAt`. Deleted ones stay as tombstones for sync. */
interface TreatmentsRepository {
    /** Not-deleted treatments with `at` in [from, to), oldest first, updated live. */
    fun between(
        from: Instant,
        to: Instant,
    ): Flow<List<Treatment>>

    suspend fun get(id: String): TreatmentRecord?

    /** Stores [record] unless a version with a later (or equal) `modifiedAt` is stored; returns true when stored. */
    suspend fun upsertIfNewer(record: TreatmentRecord): Boolean

    /** Every record changed after [since], tombstones included (sync). */
    suspend fun changedSince(since: Instant): List<TreatmentRecord>

    /** Stores [record] whatever is stored (the wearer's own entry over a writer's copy, a delivery mark). */
    suspend fun replace(record: TreatmentRecord)

    /** The not-deleted entries [deleteAll] would remove: how many and their first and last `at`. */
    suspend fun span(): StoredSpan

    /** Removes every entry (a follower stopped following: the wearer's log goes with it). */
    suspend fun deleteAll()
}

/**
 * The pen ledger (one JSON document owned by `:pen`; the wearer's phone only, never synced) stored next to the
 * treatments a pen change writes: [commit] stores both in one transaction (docs/29), so a crash can never leave a dose
 * in the ledger pointing at an entry that isn't in the log, or an entry the ledger doesn't know it wrote.
 */
interface PenLedgerRepository {
    /** The stored ledger, or null before the first pen. */
    suspend fun ledger(): String?

    /** Insulin entries and pen entries, tombstones included: all the pen engine compares a read with. */
    suspend fun insulin(): List<TreatmentRecord>

    /**
     * Stores [ledger] and each of [records] that is newer (`modifiedAt`) than the stored version, in one transaction;
     * returns the records it stored (sharing then gets them).
     */
    suspend fun commit(
        ledger: String,
        records: List<TreatmentRecord>,
    ): List<TreatmentRecord>
}

/**
 * Until when the uploads to a service (LibreView, Nightscout, Tidepool) hold an entry back, epoch s: a pen entry
 * while its session can still grow (until 10 min after its last dose, docs/29), so a top-up isn't sent as a
 * change right after the first version. Null = send it now.
 */
fun interface UploadHold {
    fun until(record: TreatmentRecord): Long?

    companion object {
        val NONE = UploadHold { null }
    }
}
