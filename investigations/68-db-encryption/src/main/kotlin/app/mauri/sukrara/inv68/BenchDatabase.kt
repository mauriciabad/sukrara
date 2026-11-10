// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.inv68

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver

/** Sukrara's `readings` table as it is in `core/data` (`ReadingEntity`, PK t + source). */
@Entity(tableName = "readings", primaryKeys = ["t", "source"])
data class ReadingEntity(
    val t: Long,
    val source: String,
    val mgdl: Int,
    val rate: Double?,
)

@Dao
interface ReadingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(rows: List<ReadingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(row: ReadingEntity)

    @Query("SELECT * FROM readings WHERE t >= :from ORDER BY t")
    fun since(from: Long): List<ReadingEntity>

    @Query("SELECT COUNT(*) FROM readings")
    fun count(): Int

    @Query("DELETE FROM readings WHERE t < :before")
    fun deleteBefore(before: Long): Int
}

@Database(entities = [ReadingEntity::class], version = 1, exportSchema = false)
abstract class BenchDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
}

/** How the database file is opened: Sukrara today, or SQLCipher through Room's two integration points. */
sealed interface Variant {
    val name: String

    /** Room's default `FrameworkSQLiteOpenHelperFactory`: the platform's SQLite, what Sukrara ships. */
    data object Plain : Variant {
        override val name = "plain"
    }

    /** SQLCipher through `SupportOpenHelperFactory` with a key string: raw `x'…'` (no KDF) or a passphrase (PBKDF2). */
    data class Cipher(
        override val name: String,
        val key: ByteArray,
    ) : Variant

    /** SQLCipher through Room's `SQLiteDriver` API (`setDriver`), the integration Room's KMP path uses. */
    data class CipherDriver(
        override val name: String,
        val key: ByteArray,
    ) : Variant
}

/** Records `onCorruption` the way Sukrara's `QuarantiningOpenHelperFactory` hooks it, then delegates. */
class RecordingFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
) : SupportSQLiteOpenHelper.Factory {
    @Volatile var corruptionCalls = 0

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val callback = configuration.callback
        val guarded =
            object : SupportSQLiteOpenHelper.Callback(callback.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) = callback.onConfigure(db)

                override fun onCreate(db: SupportSQLiteDatabase) = callback.onCreate(db)

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = callback.onUpgrade(db, oldVersion, newVersion)

                override fun onOpen(db: SupportSQLiteDatabase) = callback.onOpen(db)

                override fun onCorruption(db: SupportSQLiteDatabase) {
                    corruptionCalls++
                    callback.onCorruption(db)
                }
            }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration
                .builder(configuration.context)
                .name(configuration.name)
                .callback(guarded)
                .build(),
        )
    }
}

object Bench {
    init {
        System.loadLibrary("sqlcipher")
    }

    fun factoryOf(variant: Variant): SupportSQLiteOpenHelper.Factory? =
        when (variant) {
            Variant.Plain -> FrameworkSQLiteOpenHelperFactory()
            is Variant.Cipher -> SupportOpenHelperFactory(variant.key)
            is Variant.CipherDriver -> null
        }

    /** Room as Sukrara builds it (`DataModule.database`), with the variant's open helper or driver. */
    fun open(
        context: Context,
        file: String,
        variant: Variant,
        recording: RecordingFactory? = null,
    ): BenchDatabase {
        val builder = Room.databaseBuilder(context, BenchDatabase::class.java, file)
        when (variant) {
            is Variant.CipherDriver -> builder.setDriver(SQLCipherDriver(variant.key, null, null))
            else -> builder.openHelperFactory(recording ?: factoryOf(variant)!!)
        }
        return builder.build()
    }

    /** A 256-bit key as SQLCipher's raw-key literal `x'<64 hex>'`: used as is, no PBKDF2 at open. */
    fun rawKey(key: ByteArray): ByteArray = ("x'" + key.joinToString("") { "%02X".format(it) } + "'").toByteArray()
}
