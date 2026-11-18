// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.db

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.ReadingRank
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.HistoryImport
import app.mauri.sukrara.core.model.storage.HistoryImportStore
import app.mauri.sukrara.core.model.storage.ImportSource
import app.mauri.sukrara.core.model.storage.PenLedgerRepository
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.StoredSpan
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Readings in Room: the current person's (a person change deletes them all). Every store goes through THE rank rule
 * ([ReadingRank]): one reading per measurement, whichever sources brought it.
 */
class RoomReadingsRepository(
    private val dao: ReadingDao,
) : ReadingsRepository {
    override fun window(from: Instant): Flow<List<Reading>> = dao.window(from.toEpochMilli()).map { rows -> rows.map { it.toReading() } }

    override fun latest(): Flow<Reading?> = dao.latest().map { it?.toReading() }

    override suspend fun range(
        from: Instant,
        to: Instant,
    ): List<Reading> = dao.range(from.toEpochMilli(), to.toEpochMilli()).map { it.toReading() }

    override suspend fun spanOf(sources: Set<SourceId>): StoredSpan =
        if (sources.isEmpty()) StoredSpan.EMPTY else dao.span(sources.map { it.id }).toSpan()

    override suspend fun span(): StoredSpan = dao.spanAll().toSpan().copy(slots = dao.slotsAll())

    override suspend fun deleteAll(): Int = dao.deleteAll()

    override suspend fun upsert(readings: List<Reading>) {
        if (readings.isEmpty()) return
        val window = ReadingRank.WINDOW.toMillis()
        dao.upsertRanked(readings.minOf { it.t.toEpochMilli() } - window, readings.maxOf { it.t.toEpochMilli() } + window) { stored ->
            val plan = ReadingRank.plan(stored.map { it.toReading() }, readings)
            plan.upsert.map { it.toEntity() } to plan.remove.map { it.toEntity() }
        }
    }

    override suspend fun deleteBefore(cutoff: Instant): Int = dao.deleteBefore(cutoff.toEpochMilli())
}

class RoomTreatmentsRepository(private val dao: TreatmentDao) : TreatmentsRepository {
    override fun between(
        from: Instant,
        to: Instant,
    ): Flow<List<Treatment>> = dao.between(from.toEpochMilli(), to.toEpochMilli()).map { rows -> rows.map { it.toRecord().treatment } }

    override fun since(from: Instant): Flow<List<Treatment>> =
        dao.since(from.toEpochMilli()).map { rows ->
            rows.map {
                it.toRecord().treatment
            }
        }

    override suspend fun get(id: String): TreatmentRecord? = dao.get(id)?.toRecord()

    override suspend fun upsertIfNewer(record: TreatmentRecord): Boolean = dao.upsertIfNewer(record.toEntity())

    override suspend fun changedSince(since: Instant): List<TreatmentRecord> = dao.changedSince(since.toEpochMilli()).map { it.toRecord() }

    override suspend fun replace(record: TreatmentRecord) = dao.upsert(record.toEntity())

    override suspend fun span(): StoredSpan = dao.span().toSpan()

    override suspend fun deleteAll() = dao.deleteAll()
}

/**
 * THE import path in Room ([HistoryImportStore]): each batch is compared with everything stored around it (any source;
 * entries also by id, tombstones included) and stored in one transaction, by [HistoryImport]'s rules.
 */
class RoomHistoryImport(
    private val readings: ReadingDao,
    private val treatments: TreatmentDao,
) : HistoryImportStore {
    override suspend fun importReadings(batch: List<Reading>): Int {
        if (batch.isEmpty()) return 0
        val tolerance = HistoryImport.SAME_READING.toMillis()
        val from = batch.minOf { it.t.toEpochMilli() } - tolerance
        val to = batch.maxOf { it.t.toEpochMilli() } + tolerance
        return readings.importAround(from, to) { stored ->
            HistoryImport.readings(stored.map { it.toReading() }, batch).map { it.toEntity() }
        }
    }

    override suspend fun importTreatments(batch: List<TreatmentRecord>): Int {
        if (batch.isEmpty()) return 0
        val tolerance = HistoryImport.SAME_TREATMENT_S * 1000
        val from = batch.minOf { it.treatment.at.toEpochMilli() } - tolerance
        val to = batch.maxOf { it.treatment.at.toEpochMilli() } + tolerance
        return treatments.importAround(from, to, batch.map { it.treatment.id }) { stored ->
            HistoryImport.treatments(stored.map { it.toRecord() }, batch).map { it.toEntity() }
        }
    }
}

internal fun SpanRow.toSpan() = StoredSpan(count, oldest?.let(Instant::ofEpochMilli), newest?.let(Instant::ofEpochMilli))

/** The pen ledger and the pen's view of the log ([PenLedgerDao]): one transaction per pen change. */
class RoomPenLedgerRepository(private val dao: PenLedgerDao) : PenLedgerRepository {
    override suspend fun ledger(): String? = dao.ledger()

    override suspend fun insulin(): List<TreatmentRecord> = dao.insulin().map { it.toRecord() }

    override suspend fun commit(
        ledger: String,
        records: List<TreatmentRecord>,
    ): List<TreatmentRecord> {
        val stored = dao.commit(PenLedgerEntity(json = ledger), records.map { it.toEntity() }).toSet()
        return records.filter { it.treatment.id in stored }
    }
}

internal fun ReadingEntity.toReading() = Reading(Instant.ofEpochMilli(t), mgdl, rate, SourceId(source))

internal fun Reading.toEntity() = ReadingEntity(t.toEpochMilli(), source.id, mgdl, sensorRate)

internal fun TreatmentRecord.toEntity() =
    TreatmentEntity(
        id = treatment.id,
        kind = treatment.kind.name,
        amount = treatment.amount,
        at = treatment.at.toEpochMilli(),
        note = treatment.note,
        modifiedAt = treatment.modifiedAt.toEpochMilli(),
        deleted = treatment.deleted,
        origin = treatment.origin.name,
        creator = creator,
        author = author,
        via = via,
        receivedAt = receivedAt?.toEpochMilli(),
        delivery = delivery.name,
        penJson = penJson,
        importedFrom = importedFrom?.id,
    )

internal fun TreatmentEntity.toRecord() =
    TreatmentRecord(
        treatment =
            Treatment(
                id = id,
                kind = TreatmentKind.valueOf(kind),
                amount = amount,
                at = Instant.ofEpochMilli(at),
                note = note,
                modifiedAt = Instant.ofEpochMilli(modifiedAt),
                deleted = deleted,
                origin = TreatmentOrigin.valueOf(origin),
            ),
        creator = creator,
        author = author,
        via = via,
        receivedAt = receivedAt?.let(Instant::ofEpochMilli),
        delivery = TreatmentDelivery.valueOf(delivery),
        penJson = penJson,
        importedFrom = ImportSource.of(importedFrom),
    )
