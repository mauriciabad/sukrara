// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.MainSwitchRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SplitSwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.core.designsystem.R as DsR

/** A quiet rule's name: the Alerts row's title and its page's title. */
@StringRes
fun quietName(which: QuietKind): Int =
    when (which) {
        QuietKind.AfterInsulin -> R.string.st_quiet_insulin
        QuietKind.AfterCarbs -> R.string.st_quiet_carbs
        QuietKind.FallingHigh -> R.string.st_quiet_falling
        QuietKind.RisingLow -> R.string.st_quiet_rising
    }

/** What a quiet rule does, under its page's rows (SPEC §2): what quiets which alerts, and what still alerts. */
@StringRes
fun quietFooter(which: QuietKind): Int =
    when (which) {
        QuietKind.AfterInsulin -> R.string.st_quiet_insulin_footer
        QuietKind.AfterCarbs -> R.string.st_quiet_carbs_footer
        QuietKind.FallingHigh -> R.string.st_quiet_falling_footer
        QuietKind.RisingLow -> R.string.st_quiet_rising_footer
    }

/** The Alerts row's summary: the rule's length while it is on ("For 1 h", "Back in range within 20 min"), else *Off*. */
@Composable
fun quietSummary(
    s: AlertSettings,
    which: QuietKind,
): String {
    val rule = AlertEdits.quietRule(s, which)
    if (!rule.on) return stringResource(R.string.st_off)
    val length = numberLabel(SchemaPrefs.spec(AlertEdits.quietSpecId(which)), rule.min.toDouble())
    return stringResource(if (which.byTrend) R.string.st_quiet_within_summary else R.string.st_quiet_for, length)
}

/**
 * One quiet rule on the Alerts page, as stock Settings lists a feature with its own options (Display › Night Light,
 * Adaptive brightness; Modes' schedules): SPA's TwoTargetSwitchPreference, the switch turns it on or off, the row opens
 * its page ([QuietRuleContent]). Read-only switch while the wearer's rules apply ([AlertsUi.mirrored]).
 */
@Composable
fun QuietRuleRow(
    ui: AlertsUi,
    which: QuietKind,
    edit: ((AlertSettings) -> AlertSettings) -> Unit,
    onOpen: () -> Unit,
) {
    SplitSwitchRow(
        stringResource(quietName(which)),
        AlertEdits.quietRule(ui.settings, which).on,
        { on -> edit(AlertEdits.quiet(which) { it.copy(on = on) }) },
        onOpen,
        summary = quietSummary(ui.settings, which),
        icon = quietIcon(which),
        switchEnabled = !ui.mirrored,
    )
}

/**
 * Settings › Alerts › one quiet rule: its main switch (SPA MainSwitchPreference, *Use this quiet time*), its length
 * (greyed while off, as stock greys a page's options under its main switch) and what it does. Read-only while the
 * wearer's rules apply; Back leaves.
 */
@Composable
fun QuietRuleContent(
    which: QuietKind,
    ui: AlertsUi,
    edit: ((AlertSettings) -> AlertSettings) -> Unit,
    /** Above the page's rows (a follower's *Settings from* note). */
    header: @Composable () -> Unit = {},
    onBack: () -> Unit,
) {
    val rule = AlertEdits.quietRule(ui.settings, which)
    val spec = SchemaPrefs.spec(AlertEdits.quietSpecId(which))
    val label = numberLabeler()
    var choosing by rememberSaveable { mutableStateOf(false) }
    SettingsPage(stringResource(quietName(which)), onBack) {
        header()
        MainSwitchRow(
            stringResource(R.string.st_quiet_use),
            rule.on,
            { on -> edit(AlertEdits.quiet(which) { it.copy(on = on) }) },
            readOnly = ui.mirrored,
        )
        SettingsGroup {
            ValueRow(
                stringResource(quietLengthTitle(which)),
                numberLabel(spec, rule.min.toDouble()),
                { choosing = true },
                icon = DsR.drawable.ds_ic_timer,
                enabled = rule.on,
                readOnly = ui.mirrored,
            )
        }
        FooterText(stringResource(quietFooter(which)))
    }
    if (choosing) {
        NumberDialog(stringResource(quietLengthTitle(which)), NumberInput(spec), rule.min.toDouble(), { label(spec, it) }, {
            choosing = false
        }) { v -> edit(AlertEdits.quiet(which) { it.copy(min = spec.coerce(v).toInt()) }) }
    }
}
