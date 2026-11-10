// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.mauri.sukrara.core.data.db.RoomPenLedgerRepository
import app.mauri.sukrara.core.data.db.RoomTreatmentsRepository
import app.mauri.sukrara.core.data.db.SukraraDatabase
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * The pen ledger in the database (docs/29): a pen change stores the ledger and its entries in one transaction (a
 * failing write leaves neither), the pen reads only insulin and pen entries, and version 1 databases gain the table.
 */
@RunWith(RobolectricTestRunner::class)
class PenLedgerStoreTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SukraraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val pens = RoomPenLedgerRepository(db.penLedger())
    private val treatments = RoomTreatmentsRepository(db.treatments())
    private val t0 = Instant.parse("2026-10-20T13:00:00Z")

    @get:Rule
    val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SukraraDatabase::class.java)

    @After
    fun close() = db.close()

    private fun record(
        id: String,
        kind: TreatmentKind = TreatmentKind.RapidInsulin,
        modifiedS: Long = 0,
        pen: Boolean = true,
    ) = TreatmentRecord(
        Treatment(
            id = id,
            kind = kind,
            amount = 8.5,
            at = t0,
            modifiedAt = t0.plusSeconds(modifiedS),
            origin = TreatmentOrigin.Phone,
        ),
        penJson = if (pen) "{\"pen\":\"AB1\"}" else null,
    )

    @Test fun aChangeStoresTheLedgerAndItsEntriesTogether() =
        runTest {
            assertNull(pens.ledger())
            val stored = pens.commit("{\"v\":1}", listOf(record("a"), record("b")))
            assertEquals(listOf("a", "b"), stored.map { it.treatment.id })
            assertEquals("{\"v\":1}", pens.ledger())
            assertEquals(setOf("a", "b"), pens.insulin().map { it.treatment.id }.toSet())
            // an older version than the stored one isn't written (last writer wins), the ledger still is
            val again = pens.commit("{\"v\":2}", listOf(record("a", modifiedS = -5), record("b", modifiedS = 5)))
            assertEquals(listOf("b"), again.map { it.treatment.id })
            assertEquals("{\"v\":2}", pens.ledger())
            assertEquals(t0.plusSeconds(5), treatments.get("b")!!.treatment.modifiedAt)
        }

    @Test fun aFailedWriteLeavesNeitherTheLedgerNorAnyEntry() =
        runTest {
            pens.commit("{\"v\":1}", emptyList())
            // the second entry's write fails half way through the change (SQLite aborts it)
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER boom BEFORE INSERT ON treatments WHEN NEW.id = 'boom' BEGIN SELECT RAISE(ABORT, 'boom'); END",
            )
            val failed = runCatching { pens.commit("{\"v\":2}", listOf(record("ok"), record("boom"))) }
            assertTrue(failed.isFailure)
            assertEquals("{\"v\":1}", pens.ledger())
            assertNull(treatments.get("ok"))
        }

    @Test fun thePenSeesOnlyInsulinAndPenEntries() =
        runTest {
            treatments.upsertIfNewer(record("rapid", pen = false))
            treatments.upsertIfNewer(record("long", TreatmentKind.LongInsulin, pen = false))
            treatments.upsertIfNewer(record("carbs", TreatmentKind.Carbs, pen = false))
            treatments.upsertIfNewer(record("note", TreatmentKind.Note, pen = false))
            // a tombstone of a pen entry stays in view (its doses stay consumed)
            treatments.upsertIfNewer(record("gone").let { it.copy(treatment = it.treatment.copy(deleted = true)) })
            assertEquals(setOf("rapid", "long", "gone"), pens.insulin().map { it.treatment.id }.toSet())
        }

    @Test fun version1GainsThePenLedger() {
        migrations.createDatabase(MIGRATED, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO treatments (id, kind, amount, at, note, modified_at, deleted, origin, creator, author, via, " +
                    "received_at, delivery, pen_json) VALUES ('t1', 'RapidInsulin', 8.5, 0, '', 1, 0, 'Phone', null, null, null, null, " +
                    "'Local', null)",
            )
        }
        migrations.runMigrationsAndValidate(MIGRATED, 2, true, *SukraraDatabase.MIGRATIONS).use { v2 ->
            v2.query("SELECT COUNT(*) FROM treatments").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
            v2.query("SELECT COUNT(*) FROM pen_ledger").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }

    private companion object {
        const val MIGRATED = "migration-test.db"
    }
}
