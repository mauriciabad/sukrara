// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import app.mauri.sukrara.core.model.PenNearEntry
import app.mauri.sukrara.core.model.Treatment

/**
 * What [TreatmentsModule.fromWatch] did with an entry from Sukrara on the watch (contracts/watchface/payload README
 * "Entries from the watch": `stored`, `known`, `pen`, `amount`, `when`, `off`).
 */
sealed interface FromWatch {
    data class Stored(
        val treatment: Treatment,
    ) : FromWatch

    /** The id is in the log already: never stored twice; [treatment] null = deleted on the phone. */
    data class Known(
        val treatment: Treatment?,
    ) : FromWatch

    /** *Already logged by your pen?*: nothing stored until the person says *Log anyway* on the watch. */
    data class Pen(
        val entry: PenNearEntry,
    ) : FromWatch

    /** Not an amount the phone's Add treatment takes (its limits, its decimals). */
    data object Amount : FromWatch

    /** Further back than the phone's *minutes ago* reaches, or ahead of the phone. */
    data object When : FromWatch

    /** This phone takes no entries now (its Add treatment isn't offered). */
    data object Off : FromWatch
}
