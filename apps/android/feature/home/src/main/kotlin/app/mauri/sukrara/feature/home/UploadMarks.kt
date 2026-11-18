// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import app.mauri.sukrara.core.model.Treatment
import java.time.Instant

/**
 * Where a listed entry stands with the clinic upload (LibreView): its current version is there, an older version is
 * (the correction is on its way), it is still to go up, it waits because the upload stopped (sign in again), or it waits
 * while the Libre app uploads (LibreLinkUp is the source: it goes up once Sukrara reads the sensor; nothing is wrong).
 */
enum class UploadMark { Uploaded, Updating, Pending, Stopped, WaitsForLibreApp }

/** The clinic upload's view of the entries, by its own rules (the app adapts the uploader's ledger). */
interface UploadMarks {
    /** Null = the upload has nothing of [t] and never will (no mark: it would say "not uploaded" forever). */
    fun mark(
        t: Treatment,
        now: Instant,
    ): UploadMark?

    /** Deleting [t] takes it out of the clinic upload too (it has it, and the upload runs). */
    fun removesOnDelete(t: Treatment): Boolean
}

/**
 * The mark a row shows (#207, docs/37: Google Photos' backup marks): nothing once the entry is in LibreView, the check
 * only for [JUST_UPLOADED_MS] right after it got there ([justUploaded]); every state that isn't there yet, always.
 */
fun UploadMark?.shown(justUploaded: Boolean): UploadMark? =
    when (this) {
        null -> null
        UploadMark.Uploaded -> takeIf { justUploaded }
        else -> this
    }

/** How long a row shows the check after its entry got to LibreView (Google Photos: 3 s). */
const val JUST_UPLOADED_MS = 3_000L
