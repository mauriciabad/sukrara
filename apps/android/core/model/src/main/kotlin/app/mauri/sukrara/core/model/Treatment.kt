// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import java.time.Instant

/**
 * One insulin / carbs / note entry as the app works with it. The wire and storage encodings (field names, `v`) are
 * defined by the treatments contract and live with their codecs (`:core:protocol`, `:core:data`); the merge every
 * store applies is [supersedes].
 */
data class Treatment(
    val id: String,
    val kind: TreatmentKind,
    /** Units of insulin or grams of carbs; 0 for a note. */
    val amount: Double,
    /** When it happened. */
    val at: Instant,
    val note: String = "",
    /** Last edit (the merge version). */
    val modifiedAt: Instant,
    val deleted: Boolean = false,
    /** Which device wrote it. */
    val origin: TreatmentOrigin,
)

enum class TreatmentKind { RapidInsulin, LongInsulin, Carbs, Note }

/**
 * Who created the entry: the wearer's phone, the Mac (legacy link) or a follower allowed to add entries (writer).
 * [wire] = the contract's `o`.
 */
enum class TreatmentOrigin(val wire: String) { Phone("phone"), Mac("mac"), Follower("follower") }

/**
 * The treatments contract's merge (contracts/treatments/SPEC.md §1, `merge.json`): the greater `(u, o)` wins, i.e.
 * the later [Treatment.modifiedAt] and, on equal times, the greater `o` wire string (`phone` > `mac` > `follower`), so
 * every device keeps the same version whatever order two versions arrive in. Equal `(u, o)` is not newer.
 */
fun Treatment.supersedes(other: Treatment): Boolean = compareValuesBy(this, other, { it.modifiedAt }, { it.origin.wire }) > 0
