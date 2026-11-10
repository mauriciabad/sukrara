// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.content.res.Resources
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.graph.TreatmentMarks
import java.time.Instant

/**
 * Entries in words, as home's list writes them: *4.5u rapid insulin · 30g carbs · Pasta* (the amounts as the card
 * writes them, `TreatmentMarks.amount`); for the save confirmation and the draft notification.
 */
object EntryWords {
    fun of(
        res: Resources,
        entries: List<NewEntry>,
    ): String = entries.joinToString(" · ") { one(res, it) }

    fun one(
        res: Resources,
        e: NewEntry,
    ): String {
        val amount =
            TreatmentMarks.amount(
                Treatment("", e.kind, e.amount, Instant.EPOCH, modifiedAt = Instant.EPOCH, origin = TreatmentOrigin.Phone),
            )
        val kind =
            when (e.kind) {
                TreatmentKind.RapidInsulin -> res.getString(R.string.treatment_entry_rapid, amount)
                TreatmentKind.LongInsulin -> res.getString(R.string.treatment_entry_long, amount)
                TreatmentKind.Carbs -> res.getString(R.string.treatment_entry_carbs, amount)
                TreatmentKind.Note -> return e.note
            }
        return if (e.note.isBlank()) kind else "$kind · ${e.note}"
    }
}
