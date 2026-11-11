// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One glucose value; times are epoch milliseconds. A value per time and source (re-sends replace it). */
@Entity(tableName = "readings", primaryKeys = ["t", "source"])
data class ReadingEntity(
    val t: Long,
    val source: String,
    val mgdl: Int,
    /** Trend rate in mg/dL/min when the source sends one. */
    val rate: Double?,
)

/** A Nostr gift wrap the wearer's inbox already handled (dedupe across relays and restarts); [at]: epoch seconds. */
@Entity(tableName = "seen_wraps")
data class SeenWrapEntity(
    @PrimaryKey val id: String,
    val at: Long,
)

/** A relay event this phone refused, and why (shown in the follower's diagnostics). */
@Entity(tableName = "rejects", indices = [Index("at")])
data class RejectEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val at: Long,
    val why: String,
    val author: String?,
    val via: String?,
)

/** A treatment version (last writer wins on [modifiedAt]); deleted ones stay as tombstones. */
@Entity(tableName = "treatments", indices = [Index("at"), Index("modified_at")])
data class TreatmentEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val amount: Double,
    val at: Long,
    val note: String,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    val deleted: Boolean,
    val origin: String,
    val creator: String?,
    val author: String?,
    val via: String?,
    @ColumnInfo(name = "received_at") val receivedAt: Long?,
    val delivery: String,
    @ColumnInfo(name = "pen_json") val penJson: String?,
    /** `ImportSource.id` for an entry Import history brought back, else null. */
    @ColumnInfo(name = "imported_from") val importedFrom: String?,
)

/** A signed event waiting to be published (follower writes, wearer re-publishes); [sentAt] null until a relay took it. */
@Entity(tableName = "outbox", indices = [Index("sent_at")])
data class OutboxEntity(
    @PrimaryKey val id: String,
    /** The version (`modifiedAt` of what it carries), so a newer edit replaces an unsent older one. */
    val version: Long,
    val event: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "sent_at") val sentAt: Long?,
)

/**
 * The pen ledger (`:pen`'s JSON document; one row, id 0): in this database so a pen change stores it and the
 * treatments it writes in one transaction ([PenLedgerDao.commit]).
 */
@Entity(tableName = "pen_ledger")
data class PenLedgerEntity(
    @PrimaryKey val id: Int = 0,
    val json: String,
)
