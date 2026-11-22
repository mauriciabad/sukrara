// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import app.mauri.sukrara.core.model.KindFacts
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

/**
 * What the treatment dialog needs to know about this phone's alerts (`AppGraph` answers from the alert settings and
 * the glucose state; the dialog never reads the alert engine).
 */
interface AlertHints {
    /**
     * The alerts' part of THE kinds' order ([app.mauri.sukrara.core.model.KindOrder]) now: the newest fresh reading, the
     * low alert's threshold in force and the alerts ringing ([KindFacts.pen] stays null: the pens add it).
     */
    fun kindFacts(): KindFacts

    /** How long a treatment quiets this phone's alerts, as the settings change ([QuietRules.NONE] until known). */
    val quiet: Flow<QuietRules>

    /**
     * The dialog was opened from the alert [kind]'s *Log carbs* (its wire name): an answer to it on this phone, like
     * the alert's other actions (#39: the followers don't get a very low escalated).
     */
    fun answered(kind: String) {}
}

/**
 * Minutes a treatment quiets the alerts it answers: after rapid insulin the highs, after carbs the lows; null = that
 * rule is off, alerts are off, or this phone mirrors the wearer's alerts (the wearer's own rules decide).
 */
data class QuietRules(
    val afterInsulinMin: Int?,
    val afterCarbsMin: Int?,
) {
    companion object {
        val NONE = QuietRules(null, null)
    }
}

/**
 * The dialog's quiet line: which side stays quiet for how long once these entries are saved ("Highs quiet for 45 min").
 * Minutes are what is left at [now] for entries at [at]; a side whose quiet is already over says nothing.
 */
data class QuietNote(
    val highsMin: Long?,
    val lowsMin: Long?,
) {
    companion object {
        fun of(
            rules: QuietRules,
            rapid: Boolean,
            carbs: Boolean,
            at: Instant,
            now: Instant,
        ): QuietNote? {
            fun left(
                on: Boolean,
                min: Int?,
            ): Long? {
                if (!on || min == null) return null
                return Duration.between(now, at.plus(Duration.ofMinutes(min.toLong()))).toMinutes().takeIf { it > 0 }
            }
            val note = QuietNote(left(rapid, rules.afterInsulinMin), left(carbs, rules.afterCarbsMin))
            return note.takeIf { it.highsMin != null || it.lowsMin != null }
        }
    }
}
