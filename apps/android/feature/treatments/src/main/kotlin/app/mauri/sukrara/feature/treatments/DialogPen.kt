// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.nfc.Tag
import android.view.View
import app.mauri.sukrara.core.model.PenFill

/**
 * An insulin pen read into the open Add treatment dialog (`AppGraph` adapts `:pen`; none without pens). While the
 * dialog is open it holds the NFC reader, and a pen held to the phone is read like a tap from any app: everything new
 * is logged at once (a read is never confirmed and nothing waits for *Save*, docs/29), and the dialog shows the newest
 * injection in its insulin field and *When*.
 */
interface DialogPen {
    /** Pens can be read into the dialog now: the wearer's phone, pen reading On, NFC on. */
    suspend fun readable(): Boolean

    /** Whether [tag] may be a pen (cheap; a bank card or a passport is left alone). */
    fun mightBePen(tag: Tag): Boolean

    /**
     * Reads [tag] and logs what is new ([view] plays the read's haptics): [reading] once the pen answered (the dialog
     * says *Reading your pen…*), then what the dialog shows; null = not a pen (nothing at all).
     */
    suspend fun read(
        tag: Tag,
        view: View,
        reading: () -> Unit,
    ): PenFill?

    /** *Undo* under the fields: the last read back, while nothing changed since (false = too late). */
    suspend fun undo(): Boolean

    /** *Next time, just tap your pen* is due after rapid insulin typed by hand (a pen known, reading On; once a day, 3 times). */
    suspend fun tapHintDue(): Boolean

    /** The hint was shown (counted, so it stops after 3). */
    fun tapHintShown()
}
