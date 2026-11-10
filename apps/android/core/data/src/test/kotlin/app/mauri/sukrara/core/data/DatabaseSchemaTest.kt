// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.mauri.sukrara.core.data.db.SukraraDatabase
import app.mauri.sukrara.core.model.DefaultDispatchers
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The database across app updates, with no migrations (the owner, 2026-11-10: a fresh start, no backwards
 * compatibility, databases included). Only the current version's schema is exported to `core/data/schemas/` and
 * committed (CI fails on a schema the build regenerated but nobody committed; the `room-schema` check fails on a
 * changed committed schema and on migration code). A database of the current version opens as it is (Room checks its
 * identity hash, so an entity change without a version bump fails here); a database of any other version, older or
 * newer, is replaced by an empty one of the current version, every table dropped (destructive replace), and
 * [DataModule.databaseReplaced] says so.
 *
 * The corruption path ([QuarantiningOpenHelperFactory][app.mauri.sukrara.core.data.db.QuarantiningOpenHelperFactory]):
 * a damaged file is copied aside and reported before SQLite replaces it, and the app keeps working. The same on a
 * device: docs/QA.md Q15.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SukraraDatabase::class.java)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()

    /** The exported schemas (the Gradle test task runs in the module's folder). */
    private val schemas = File("schemas/${SukraraDatabase::class.java.canonicalName}")

    private fun exported(): List<Int> =
        schemas
            .listFiles()
            .orEmpty()
            .mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
            .sorted()

    /** The version the compiled database has (Room writes it as SQLite's user_version). */
    private fun current(): Int {
        val db = Room.inMemoryDatabaseBuilder(context, SukraraDatabase::class.java).allowMainThreadQueries().build()
        return db.openHelper.writableDatabase.version
            .also { db.close() }
    }

    private fun data() = DataModule(context, "0.12.0-test", DefaultDispatchers, clock, secretBox = SoftwareSecretBox())

    /** A database file as another version left it: [version] as SQLite's user_version, a reading and a table we don't know. */
    private fun foreignDatabase(version: Int) {
        context.deleteDatabase(SukraraDatabase.FILE)
        val file = context.getDatabasePath(SukraraDatabase.FILE)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE readings (t INTEGER NOT NULL, source TEXT NOT NULL, mgdl INTEGER NOT NULL, PRIMARY KEY(t, source))")
            db.execSQL("INSERT INTO readings VALUES (1, 'libre3', 120)")
            db.execSQL("CREATE TABLE old_things (id INTEGER PRIMARY KEY)")
            db.version = version
        }
    }

    private fun tables(db: SukraraDatabase): Set<String> =
        db.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    @Test fun `only the current version has its schema exported`() {
        assertEquals("schemas/ in ${schemas.absoluteFile}", listOf(current()), exported())
    }

    @Test fun `a database of the current version opens as it is`() =
        runTest {
            context.deleteDatabase(SukraraDatabase.FILE)
            helper.createDatabase(SukraraDatabase.FILE, current()).use { db ->
                db.execSQL("INSERT INTO readings (t, source, mgdl, rate) VALUES (1000, 'libre3', 120, NULL)")
            }
            val data = data()
            try {
                assertEquals(current(), data.database.openHelper.writableDatabase.version)
                assertEquals(
                    120,
                    data.readings
                        .latest()
                        .first()
                        ?.mgdl,
                )
                assertFalse(data.databaseReplaced.value)
            } finally {
                data.database.close()
            }
        }

    @Test fun `a database of any other version is replaced by an empty one`() =
        runTest {
            for (version in listOf(1, current() - 1, current() + 1, current() + 40).filter { it >= 1 && it != current() }.distinct()) {
                foreignDatabase(version)
                val data = data()
                try {
                    assertEquals("opened from version $version", current(), data.database.openHelper.writableDatabase.version)
                    assertEquals("from version $version", null, data.readings.latest().first())
                    assertFalse("every table dropped (from version $version)", "old_things" in tables(data.database))
                    assertTrue("reported (from version $version)", data.databaseReplaced.value)
                    // and it works
                    data.readings.upsert(listOf(Reading(clock.now(), 110, null, SourceId.Libre3)))
                    assertEquals(
                        110,
                        data.readings
                            .latest()
                            .first()
                            ?.mgdl,
                    )
                } finally {
                    data.database.close()
                }
            }
        }

    @Test fun `a damaged database is kept aside, reported, and replaced by a working one`() =
        runTest {
            val file = context.getDatabasePath(SukraraDatabase.FILE)
            file.parentFile!!.mkdirs()
            file.writeBytes(ByteArray(8192) { (it * 31 % 251).toByte() })

            val data = data()
            try {
                data.readings.upsert(listOf(Reading(clock.now(), 120, null, SourceId.Libre3)))
                assertEquals(
                    listOf(120),
                    data.readings
                        .latest()
                        .first()
                        ?.let { listOf(it.mgdl) },
                )

                val reported =
                    data.corruption.files.value
                        .single()
                assertEquals(StoreKind.Database, reported.kind)
                assertEquals(SukraraDatabase.FILE, reported.store)
                val kept = reported.quarantinedTo!!
                assertTrue("the damaged bytes are kept: $kept", kept.isFile && kept.length() == 8192L)
            } finally {
                data.database.close()
            }
        }
}
