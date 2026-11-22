// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

/** The newest pen read on this phone: its pen's insulin ([kind], rapid or long-acting), read at [at] (epoch s). */
data class PenReadAt(
    val kind: TreatmentKind,
    val at: Long,
)

/**
 * What THE order of the treatment kinds is decided from, gathered in one place (`AppGraph`: the alert hints and the
 * pens): [reading] = the newest reading in mg/dL while fresh (the alerts' 15 min; null = none, or older), [lowBelow] =
 * the low alert's threshold in force now (whether or not the alert is on), [ringing] = the alert kinds ringing (wire
 * names), [pen] = the newest pen read (null = none, or a phone that reads no pens).
 */
data class KindFacts(
    val reading: Int?,
    val lowBelow: Double,
    val ringing: Set<String>,
    val pen: PenReadAt? = null,
) {
    companion object {
        /** Nothing known: the usual order. */
        val NONE = KindFacts(null, 70.0, emptySet())
    }
}

/**
 * THE order of the treatment kinds wherever Sukrara offers to log one (contracts/treatments/kind-order.json, the Mac's
 * `KindOrder` runs the same cases): the focused kind first, then the others in [USUAL]. Every Add treatment focuses the
 * first (the dialog's fields stay in [USUAL]); the watch app's log list takes the whole order (the payload's
 * `log.order`). Carbs while low (the reading under the low alert's threshold, or an alert carbs answer rings); else
 * long-acting for [PEN_S] after a rapid pen was read (the other insulin); else rapid.
 */
object KindOrder {
    /** The dialog's fields, left to right. */
    val USUAL = listOf(TreatmentKind.RapidInsulin, TreatmentKind.LongInsulin, TreatmentKind.Carbs)

    /** The alerts carbs answer (contracts/alerts SPEC: very low, low, falling fast). */
    val CARBS_ALERTS = setOf("very_low", "low", "falling_fast")

    /** How long a rapid pen's read puts long-acting first: as long as the read's notification and its *Add treatment* last. */
    const val PEN_S = 2 * 3600L

    /** Glucose is low now: carbs is what's needed. */
    fun low(f: KindFacts): Boolean = (f.reading != null && f.reading < f.lowBelow) || f.ringing.any { it in CARBS_ALERTS }

    /** The kind focused at [now] (epoch s): the order's first. */
    fun focus(
        f: KindFacts,
        now: Long,
    ): TreatmentKind =
        when {
            low(f) -> TreatmentKind.Carbs
            f.pen?.kind == TreatmentKind.RapidInsulin && now - f.pen.at in 0 until PEN_S -> TreatmentKind.LongInsulin
            else -> TreatmentKind.RapidInsulin
        }

    /** The kinds in order at [now] (epoch s): [focus], then the rest of [USUAL]. */
    fun of(
        f: KindFacts,
        now: Long,
    ): List<TreatmentKind> = focus(f, now).let { first -> listOf(first) + (USUAL - first) }
}
