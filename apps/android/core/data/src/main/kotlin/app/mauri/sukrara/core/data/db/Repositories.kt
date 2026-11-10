// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.db

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.PenLedgerRepository
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.model.storage.TreatmentsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Readings in Room. Every read leaves out the [hidden] sources: `DataModule.readingsFor` passes the other role's
 * ([SourceId.notOf]), so the history, the report, the rings, the export and every upload show only the current
 * person's readings, even one of the previous role still on its way when the role changed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomReadingsRepository(
    private val dao: ReadingDao,
    private val hidden: Flow<Set<SourceId>> = flowOf(emptySet()),
) : ReadingsRepository {
    private val hiddenIds = hidden.map { set -> set.map { it.id } }.distinctUntilChanged()

    override fun window(from: Instant): Flow<List<Reading>> =
        hiddenIds.flatMapLatest { ids -> dao.window(from.toEpochMilli(), ids) }.map { rows -> rows.map { it.toReading() } }

    override fun latest(): Flow<Reading?> = hiddenIds.flatMapLatest { ids -> dao.latest(ids) }.map { it?.toReading() }

    override suspend fun range(
        from: Instant,
        to: Instant,
    ): List<Reading> = dao.range(from.toEpochMilli(), to.toEpochMilli(), hiddenIds.first()).map { it.toReading() }

    override suspend fun deleteFrom(sources: Set<SourceId>): Int = if (sources.isEmpty()) 0 else dao.deleteFrom(sources.map { it.id })

    override suspend fun upsert(readings: List<Reading>) {
        if (readings.isNotEmpty()) dao.upsert(readings.map { it.toEntity() })
    }

    override suspend fun deleteBefore(cutoff: Instant): Int = dao.deleteBefore(cutoff.toEpochMilli())
}

class RoomTreatmentsRepository(private val dao: TreatmentDao) : TreatmentsRepository {
    override fun between(
        from: Instant,
        to: Instant,
    ): Flow<List<Treatment>> = dao.between(from.toEpochMilli(), to.toEpochMilli()).map { rows -> rows.map { it.toRecord().treatment } }

    override suspend fun get(id: String): TreatmentRecord? = dao.get(id)?.toRecord()

    override suspend fun upsertIfNewer(record: TreatmentRecord): Boolean = dao.upsertIfNewer(record.toEntity())

    override suspend fun changedSince(since: Instant): List<TreatmentRecord> = dao.changedSince(since.toEpochMilli()).map { it.toRecord() }

    override suspend fun replace(record: TreatmentRecord) = dao.upsert(record.toEntity())

    override suspend fun deleteAll() = dao.deleteAll()
}

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

internal fun Reading.toEntity() = ReadingEntity(t.toEpochMilli(), source.id, mgdl, rateMgdlPerMin)

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
    )
