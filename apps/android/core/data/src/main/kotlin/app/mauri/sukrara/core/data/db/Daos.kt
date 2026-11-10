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

    /**
     * Stores [readings]; those that aren't [imported] then remove every imported reading within [tolerance] ms of them
     * (the sensor's own reading wins over an imported copy, HistoryImport), in one transaction.
     */
    @Transaction
    suspend fun upsertOwnFirst(
        readings: List<ReadingEntity>,
        imported: List<String>,
        tolerance: Long,
    ) {
        upsert(readings)
        val own = readings.filter { it.source !in imported }
        if (own.isNotEmpty()) dropImportedNear(own.minOf { it.t } - tolerance, own.maxOf { it.t } + tolerance, imported, tolerance)
    }

    /** Imported readings in [from, to] with a reading from another source within [tolerance] ms. */
    @Query(
        "DELETE FROM readings WHERE source IN (:imported) AND t >= :from AND t <= :to AND EXISTS (SELECT 1 FROM readings AS own " +
            "WHERE own.source NOT IN (:imported) AND own.t >= readings.t - :tolerance AND own.t <= readings.t + :tolerance)",
    )
    suspend fun dropImportedNear(
        from: Long,
        to: Long,
        imported: List<String>,
        tolerance: Long,
    ): Int

    /** Every reading in [from, to], any source (the import path compares with all of them). */
    @Query("SELECT * FROM readings WHERE t >= :from AND t <= :to ORDER BY t")
    suspend fun around(
        from: Long,
        to: Long,
    ): List<ReadingEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(readings: List<ReadingEntity>)

    /** The import path: [plan] chooses, from what is stored in [from, to], what of the batch to add; one transaction. */
    @Transaction
    suspend fun importAround(
        from: Long,
        to: Long,
        plan: (stored: List<ReadingEntity>) -> List<ReadingEntity>,
    ): Int {
        val add = plan(around(from, to))
        if (add.isNotEmpty()) insertNew(add)
        return add.size
    }

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

    /** Every entry with `at` in [from, to], tombstones included (the import path compares with all of them). */
    @Query("SELECT * FROM treatments WHERE at >= :from AND at <= :to")
    suspend fun around(
        from: Long,
        to: Long,
    ): List<TreatmentEntity>

    @Query("SELECT * FROM treatments WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<TreatmentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(treatments: List<TreatmentEntity>)

    /**
     * The import path: [plan] chooses, from what is stored in [from, to] and under [ids], what of the batch to add; one
     * transaction.
     */
    @Transaction
    suspend fun importAround(
        from: Long,
        to: Long,
        ids: List<String>,
        plan: (stored: List<TreatmentEntity>) -> List<TreatmentEntity>,
    ): Int {
        val stored = (around(from, to) + ids.distinct().chunked(SQL_VARIABLES).flatMap { byIds(it) }).distinctBy { it.id }
        val add = plan(stored)
        if (add.isNotEmpty()) insertNew(add)
        return add.size
    }

    /** Last writer wins: stores [treatment] only if nothing with the same or a later `modifiedAt` is stored. */
    @Transaction
    suspend fun upsertIfNewer(treatment: TreatmentEntity): Boolean {
        val stored = get(treatment.id)
        if (stored != null && stored.modifiedAt >= treatment.modifiedAt) return false
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

/** Bound parameters per query, under SQLite's 999. */
internal const val SQL_VARIABLES = 900
