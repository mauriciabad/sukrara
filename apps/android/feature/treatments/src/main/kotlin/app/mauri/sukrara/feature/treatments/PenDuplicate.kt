// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.os.Bundle
import app.mauri.sukrara.core.model.PenNearEntry
import app.mauri.sukrara.core.model.TreatmentKind
import java.time.Instant

/**
 * *Already logged by your pen?* while it is open: what *Log anyway* would save ([entries] at [at], null = now) and the
 * pen entry it may double. Kept in the activity's saved state, so a rotation (or Android recreating the dialog) shows
 * the same question again instead of dropping it.
 */
data class PenDuplicate(
    val entries: List<NewEntry>,
    val pen: PenNearEntry,
    val at: Instant?,
) {
    fun save(out: Bundle) {
        out.putStringArray(KINDS, entries.map { it.kind.name }.toTypedArray())
        out.putDoubleArray(AMOUNTS, entries.map { it.amount }.toDoubleArray())
        out.putStringArray(NOTES, entries.map { it.note }.toTypedArray())
        out.putString(PEN_ID, pen.id)
        out.putDouble(PEN_UNITS, pen.units)
        out.putLong(PEN_T, pen.t)
        at?.let { out.putLong(AT, it.toEpochMilli()) }
    }

    companion object {
        private const val KINDS = "pen_dup_kinds"
        private const val AMOUNTS = "pen_dup_amounts"
        private const val NOTES = "pen_dup_notes"
        private const val PEN_ID = "pen_dup_pen"
        private const val PEN_UNITS = "pen_dup_units"
        private const val PEN_T = "pen_dup_t"
        private const val AT = "pen_dup_at"

        /** The question [save] kept, or null when none was open. */
        fun restore(state: Bundle?): PenDuplicate? {
            val s = state ?: return null
            val kinds = s.getStringArray(KINDS) ?: return null
            val amounts = s.getDoubleArray(AMOUNTS) ?: return null
            val notes = s.getStringArray(NOTES) ?: return null
            val id = s.getString(PEN_ID) ?: return null
            if (kinds.size != amounts.size || kinds.size != notes.size) return null
            val entries = kinds.indices.map { i -> NewEntry(TreatmentKind.valueOf(kinds[i]), amounts[i], notes[i]) }
            val at = if (s.containsKey(AT)) Instant.ofEpochMilli(s.getLong(AT)) else null
            return PenDuplicate(entries, PenNearEntry(id, s.getDouble(PEN_UNITS), s.getLong(PEN_T)), at)
        }
    }
}
