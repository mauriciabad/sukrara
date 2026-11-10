// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The install's one database (`databases/sukrara.db`): glucose history, treatments and the sharing ledger. A phone is
 * either wearer or follower, so tables have no role column.
 *
 * No migrations, ever (the owner, 2026-11-10: "no backwards compatibility for anything, databases included"): a schema
 * change bumps [version] and replaces the exported `core/data/schemas/<version>.json` (only the current one is kept);
 * a phone whose database has any other version gets a new, empty one (`DataModule.database`: destructive replace,
 * upgrade and downgrade, every table dropped). History comes back through Import history (LibreView, Tidepool or a
 * file, deduplicated), never through a migration. `DatabaseSchemaTest` and the `room-schema` check enforce it.
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
    version = 3,
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
    }
}
