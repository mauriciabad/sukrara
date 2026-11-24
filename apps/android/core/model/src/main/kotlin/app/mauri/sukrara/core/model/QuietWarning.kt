// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

/**
 * THE rule of the quiet-alerts warning (schema `alerts.quietHidden`; the Mac's `QuietWarning` is the same): Android
 * lets Sukrara's alerts through but an alert channel in use is lowered below sound or blocked
 * ([AlertRequirement.AlertChannel]). The person may hide the warning on purpose (another device next to them rings),
 * for exactly what is down then. It shows again when anything else goes down, and whatever comes back up leaves the
 * hidden list, so turning it down again later (by accident: a long press on a notification's *Silent*, say) warns
 * again.
 */
object QuietWarning {
    /** The warning shows: something is down that the person didn't hide. */
    fun shown(
        down: Set<String>,
        hidden: Set<String>,
    ): Boolean = !hidden.containsAll(down)

    /** What stays hidden: only what is still down (stored again when it shrinks; empty once all is back up). */
    fun kept(
        down: Set<String>,
        hidden: Set<String>,
    ): Set<String> = hidden intersect down

    /** The stored form: the parts, sorted, comma-separated ("" = nothing hidden). */
    fun encode(parts: Set<String>): String = parts.sorted().joinToString(",")

    fun decode(text: String): Set<String> =
        text
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
