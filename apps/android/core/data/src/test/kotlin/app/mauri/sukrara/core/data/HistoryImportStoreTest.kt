// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.data.db.RoomHistoryImport
import app.mauri.sukrara.core.data.db.RoomReadingsRepository
import app.mauri.sukrara.core.data.db.RoomTreatmentsRepository
import app.mauri.sukrara.core.data.db.SukraraDatabase
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.HistoryImport
import app.mauri.sukrara.core.model.storage.ImportSource
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * THE import path in Room ([RoomHistoryImport]) and the sensor-first rule of [RoomReadingsRepository.upsert]: an import
 * run twice adds nothing, the sensor's own reading replaces an imported copy whenever it arrives, a deleted entry is
 * never brought back, and imported entries keep their origin through an edit. Synthetic data only.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryImportStoreTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SukraraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val readings = RoomReadingsRepository(db.readings())
    private val treatments = RoomTreatmentsRepository(db.treatments())
    private val store = RoomHistoryImport(db.readings(), db.treatments())
    private val t0 = Instant.parse("2026-10-01T08:00:00Z")
    private val lv = ImportSource.LibreView.readings

    @After
    fun close() = db.close()

    private fun r(
        sec: Long,
        mgdl: Int,
        source: SourceId,
    ) = Reading(t0.plusSeconds(sec), mgdl, null, source)

    private suspend fun all() = readings.range(t0.minusSeconds(86_400), t0.plusSeconds(86_400))

    @Test fun anImportRunTwiceAddsNothing() =
        runTest {
            val batch = (0 until 12).map { r(it * 300L, 100 + it, lv) } + r(65, 104, lv)
            assertEquals(12, store.importReadings(batch))
            assertEquals(0, store.importReadings(batch))
            assertEquals(0, store.importReadings(batch.map { it.copy(source = ImportSource.Tidepool.readings) }))
            assertEquals(12, all().size)
        }

    @Test fun theSensorsOwnReadingWinsWhicheverCameFirst() =
        runTest {
            // the sensor first: the import skips what it has
            readings.upsert(listOf(r(0, 120, SourceId.Libre3)))
            assertEquals(1, store.importReadings(listOf(r(60, 121, lv), r(600, 130, lv))))
            // the sensor later (a backfill): the imported copy within two minutes goes, one further away stays
            store.importReadings(listOf(r(1200, 140, lv), r(1500, 150, lv)))
            readings.upsert(listOf(r(1260, 141, SourceId.Libre3)))
            assertEquals(
                listOf(0L to SourceId.Libre3, 600L to lv, 1260L to SourceId.Libre3, 1500L to lv),
                all().map { (it.t.epochSecond - t0.epochSecond) to it.source },
            )
            // a 1-minute stream keeps every reading of its own
            readings.upsert((0 until 5).map { r(3000 + it * 60L, 100, SourceId.Libre3) })
            assertEquals(5, all().count { it.t >= t0.plusSeconds(3000) })
        }

    private fun entry(
        id: String,
        amount: Double,
        sec: Long,
        from: ImportSource? = null,
        deleted: Boolean = false,
    ) = TreatmentRecord(
        Treatment(id, TreatmentKind.RapidInsulin, amount, t0.plusSeconds(sec), "", t0.plusSeconds(sec), deleted, TreatmentOrigin.Phone),
        importedFrom = from,
    )

    @Test fun entriesMatchByIdOrByTimeKindAndAmountAndADeletedOneStaysDeleted() =
        runTest {
            treatments.upsertIfNewer(entry("6f1c2b9a-0d3e-4c5f-8a7b-0123456789ab", 2.25, 0))
            treatments.upsertIfNewer(entry("gone", 4.0, 3600, deleted = true))
            val tp = ImportSource.Tidepool
            val batch =
                listOf(
                    // the same entry read back from Tidepool by its origin id, then from a file without one
                    entry("6f1c2b9a-0d3e-4c5f-8a7b-0123456789ab", 2.25, 0, tp),
                    entry(HistoryImport.treatmentId(tp, "x"), 2.3, 60, tp),
                    // deleted here: not brought back
                    entry(HistoryImport.treatmentId(tp, "y"), 4.0, 3630, tp),
                    // new
                    entry(HistoryImport.treatmentId(tp, "z"), 6.0, 7200, tp),
                )
            assertEquals(1, store.importTreatments(batch))
            assertEquals(0, store.importTreatments(batch))
            val added = treatments.get(HistoryImport.treatmentId(tp, "z"))!!
            assertEquals(ImportSource.Tidepool, added.importedFrom)
            assertNull(treatments.get(HistoryImport.treatmentId(tp, "y")))
            assertNull(treatments.get("6f1c2b9a-0d3e-4c5f-8a7b-0123456789ab")!!.importedFrom)
            // an edit (the treatment log copies the stored record) keeps the origin
            val edited = added.copy(treatment = added.treatment.copy(amount = 6.5, modifiedAt = t0.plusSeconds(9000)))
            treatments.upsertIfNewer(edited)
            assertEquals(ImportSource.Tidepool, treatments.get(edited.treatment.id)!!.importedFrom)
            assertFalse(treatments.get("6f1c2b9a-0d3e-4c5f-8a7b-0123456789ab")!!.imported)
        }
}
