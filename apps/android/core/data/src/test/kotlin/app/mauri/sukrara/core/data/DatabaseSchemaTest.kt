// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import android.content.Context
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The database across app updates (#82). Every schema version is exported to `core/data/schemas/` and committed (CI
 * fails on a schema the build regenerated but nobody committed). For every exported version, a database created
 * exactly as that version left it ([MigrationTestHelper], from its JSON) must open through the app's own builder
 * ([DataModule.database]): an older one through the migrations it registers, validated by Room against the current
 * schema; the current one by its identity hash. So a schema bump without a migration fails here (it would crash the
 * app on open), and so does an entity change without a version bump. A migration's own data checks go in a test
 * beside these (insert with the old schema's SQL, open, read through the DAO).
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

    @Test fun `every version up to the current one has its schema exported`() {
        assertEquals("schemas/ in ${schemas.absoluteFile}", (1..current()).toList(), exported())
    }

    @Test fun `a database from every exported version opens through the app's builder`() {
        for (version in exported()) {
            context.deleteDatabase(SukraraDatabase.FILE)
            helper.createDatabase(SukraraDatabase.FILE, version).close()
            val data = data()
            try {
                // migrations (none yet) and Room's schema validation run here; a missing migration throws
                assertEquals("opened from version $version", current(), data.database.openHelper.writableDatabase.version)
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
