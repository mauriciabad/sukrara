// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.AlertSource
import app.mauri.sukrara.alerts.Rules
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.KindFacts
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.feature.treatments.AlertHints
import app.mauri.sukrara.feature.treatments.QuietRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The treatment dialog's view of the alert rules: the alerts' facts of THE kinds' order (the newest fresh reading, the
 * low threshold, what rings), and the after-treatment quiet. [settings] = the stored alert settings, null until they're
 * read (no quiet line is claimed). [answer] = the alert runtime's answer on this phone (an alert's *Treatment* opened
 * the dialog).
 */
class AppAlertHints(
    private val settings: StateFlow<AlertSettings?>,
    private val glucose: StateFlow<GlucoseState>,
    private val clock: Clock,
    private val answer: (String) -> Unit = {},
) : AlertHints {
    override fun answered(kind: String) = answer(kind)

    /**
     * The newest reading while fresh (the alerts' rule), the low alert's threshold in force now (the night one inside its
     * window), whether or not the low alert is on (eating is what a low needs either way; before the settings are read,
     * the default), and the alerts ringing.
     */
    override fun kindFacts(): KindFacts {
        val now = clock.now().epochSecond
        val g = glucose.value
        val fresh =
            g.latest?.takeIf { r ->
                Rules.fresh(AlertReading(r.t.epochSecond, r.mgdl, r.rate, r.trend?.held, r.trend?.band), now)
            }
        val s = (settings.value ?: AlertSettings()).at(now, clock.zone)
        return KindFacts(fresh?.mgdl, s.rule(AlertKind.LOW).threshold, g.alerts.firingKinds)
    }

    override val quiet: Flow<QuietRules> =
        combine(settings, glucose) { s, g -> rules(s, g.spec) }.distinctUntilChanged()

    private fun rules(
        s: AlertSettings?,
        role: SourceSpec?,
    ): QuietRules {
        // not read yet, alerts off, or a follower that mirrors the wearer's alerts: nothing this phone decides is quiet
        if (s == null || !s.on || (role?.stream == true && s.source == AlertSource.WEARER)) return QuietRules.NONE
        return QuietRules(
            afterInsulinMin = s.afterInsulin.min.takeIf { s.afterInsulin.on },
            afterCarbsMin = s.afterCarbs.min.takeIf { s.afterCarbs.on },
        )
    }
}
