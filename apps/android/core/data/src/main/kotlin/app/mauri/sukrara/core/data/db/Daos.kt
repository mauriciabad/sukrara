// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.mauri.sukrara.core.model.supersedes
import kotlinx.coroutines.flow.Flow

/** A count with the first and last time in epoch ms (null when the count is 0). */
data class SpanRow(
    val count: Int,
    val oldest: Long?,
    val newest: Long?,
)

@Dao
interface ReadingDao {
    /** The card's window: everything from [from] not from [hidden] sources, oldest first, live. */
    @Query("SELECT * FROM readings WHERE t >= :from AND source NOT IN (:hidden) ORDER BY t")
    fun window(
        from: Long,
        hidden: List<String>,
    ): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings WHERE source NOT IN (:hidden) ORDER BY t DESC LIMIT 1")
    fun latest(hidden: List<String>): Flow<ReadingEntity?>

    /** Backfill / export range [from, to), not from [hidden] sources. */
    @Query("SELECT * FROM readings WHERE t >= :from AND t < :to AND source NOT IN (:hidden) ORDER BY t")
    suspend fun range(
        from: Long,
        to: Long,
        hidden: List<String>,
    ): List<ReadingEntity>

    @Upsert
    suspend fun upsert(readings: List<ReadingEntity>)

    /** Retention. */
    @Query("DELETE FROM readings WHERE t < :cutoff")
    suspend fun deleteBefore(cutoff: Long): Int

    /** What a role change would delete: how many readings from [sources], their first and last time. */
    @Query("SELECT COUNT(*) AS count, MIN(t) AS oldest, MAX(t) AS newest FROM readings WHERE source IN (:sources)")
    suspend fun span(sources: List<String>): SpanRow

    /** A role change: another person's readings go. */
    @Query("DELETE FROM readings WHERE source IN (:sources)")
    suspend fun deleteFrom(sources: List<String>): Int
}

@Dao
interface SeenWrapDao {
    /** Records [id]; returns false when it was already seen (the caller skips the event). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(wrap: SeenWrapEntity): Long

    suspend fun markSeen(
        id: String,
        at: Long,
    ): Boolean = insert(SeenWrapEntity(id, at)) != -1L

    @Query("DELETE FROM seen_wraps WHERE at < :cutoff")
    suspend fun deleteBefore(cutoff: Long): Int
}

@Dao
interface RejectDao {
    @Insert
    suspend fun insert(reject: RejectEntity)

    @Query("SELECT * FROM rejects ORDER BY at DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<RejectEntity>>

    @Query("DELETE FROM rejects WHERE at < :cutoff")
    suspend fun deleteBefore(cutoff: Long): Int
}

@Dao
interface TreatmentDao {
    @Query("SELECT * FROM treatments WHERE deleted = 0 AND at >= :from AND at < :to ORDER BY at")
    fun between(
        from: Long,
        to: Long,
    ): Flow<List<TreatmentEntity>>

    @Query("SELECT * FROM treatments WHERE id = :id")
    suspend fun get(id: String): TreatmentEntity?

    @Query("SELECT * FROM treatments WHERE modified_at > :since ORDER BY modified_at")
    suspend fun changedSince(since: Long): List<TreatmentEntity>

    @Upsert
    suspend fun upsert(treatment: TreatmentEntity)

    @Query("SELECT COUNT(*) AS count, MIN(at) AS oldest, MAX(at) AS newest FROM treatments WHERE deleted = 0")
    suspend fun span(): SpanRow

    @Query("DELETE FROM treatments")
    suspend fun deleteAll()

    /** The contract's merge ([supersedes]): stores [treatment] only if it is newer by `(u, o)` than the stored version. */
    @Transaction
    suspend fun upsertIfNewer(treatment: TreatmentEntity): Boolean {
        val stored = get(treatment.id)
        if (stored != null && !treatment.toRecord().treatment.supersedes(stored.toRecord().treatment)) return false
        upsert(treatment)
        return true
    }
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE sent_at IS NULL ORDER BY created_at")
    fun pending(): Flow<List<OutboxEntity>>

    @Upsert
    suspend fun put(entry: OutboxEntity)

    @Query("UPDATE outbox SET sent_at = :at WHERE id = :id AND version = :version")
    suspend fun markSent(
        id: String,
        version: Long,
        at: Long,
    ): Int

    @Query("DELETE FROM outbox WHERE sent_at IS NOT NULL AND sent_at < :cutoff")
    suspend fun deleteSentBefore(cutoff: Long): Int
}

@Dao
interface PenLedgerDao {
    @Query("SELECT json FROM pen_ledger WHERE id = 0")
    suspend fun ledger(): String?

    @Upsert
    suspend fun put(ledger: PenLedgerEntity)

    /** Insulin and pen entries, tombstones included (the pen engine's view of the log; carbs and notes never matter). */
    @Query("SELECT * FROM treatments WHERE kind IN ('RapidInsulin', 'LongInsulin') OR pen_json IS NOT NULL")
    suspend fun insulin(): List<TreatmentEntity>

    @Query("SELECT modified_at FROM treatments WHERE id = :id")
    suspend fun modifiedAt(id: String): Long?

    @Upsert
    suspend fun upsertTreatment(treatment: TreatmentEntity)

    /**
     * One pen change: the ledger and every one of [treatments] newer than the stored version (last writer wins, as
     * [TreatmentDao.upsertIfNewer]), all or nothing. Returns the ids it stored.
     */
    @Transaction
    suspend fun commit(
        ledger: PenLedgerEntity,
        treatments: List<TreatmentEntity>,
    ): List<String> {
        val stored =
            treatments.filter { t ->
                val had = modifiedAt(t.id)
                (had == null || had < t.modifiedAt).also { newer -> if (newer) upsertTreatment(t) }
            }
        put(ledger)
        return stored.map { it.id }
    }
}
