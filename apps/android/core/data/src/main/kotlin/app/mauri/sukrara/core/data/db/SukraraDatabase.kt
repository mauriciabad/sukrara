// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The install's one database (`databases/sukrara.db`): glucose history, treatments and the sharing ledger. A phone is
 * either wearer or follower, so tables have no role column. Every schema change bumps [version], exports the schema to
 * `core/data/schemas/` and adds a migration with a `MigrationTestHelper` test (no destructive fallback).
 *
 * 2: the pen ledger (`pen_ledger`), written with the treatments of a pen change in one transaction.
 */
@Database(
    entities = [
        ReadingEntity::class,
        SeenWrapEntity::class,
        RejectEntity::class,
        TreatmentEntity::class,
        OutboxEntity::class,
        PenLedgerEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SukraraDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao

    abstract fun seenWraps(): SeenWrapDao

    abstract fun rejects(): RejectDao

    abstract fun treatments(): TreatmentDao

    abstract fun outbox(): OutboxDao

    abstract fun penLedger(): PenLedgerDao

    companion object {
        const val FILE = "sukrara.db"

        /**
         * 1 → 2: the pen ledger moves into the database (the old `files/state/pens.json` isn't read: a known pen's
         * next tap rebuilds its record from the pen entries in the log, docs/29).
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `pen_ledger` (`id` INTEGER NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`id`))")
                }
            }

        /** Every migration, in order (`DataModule` and the migration tests use this list). */
        val MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
