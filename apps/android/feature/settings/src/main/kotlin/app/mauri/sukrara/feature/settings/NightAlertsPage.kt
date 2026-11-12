// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.KindRule
import app.mauri.sukrara.designsystem.alerts.MuteText
import app.mauri.sukrara.designsystem.alerts.rememberAlertWords
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.MainSwitchRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.designsystem.rows.SplitSwitchRow
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.feature.settings.sensorend.TimeDialog
import app.mauri.sukrara.graph.ClockText
import kotlin.math.roundToInt
import app.mauri.sukrara.core.designsystem.R as DsR

/** The night set's edits (SPEC §1 "Night alerts", #38): the same rule fields as the day's, on `night`. */
object NightEdits {
    fun on(on: Boolean): (AlertSettings) -> AlertSettings = { s -> s.copy(night = s.night.copy(on = on)) }

    fun from(minute: Int): (AlertSettings) -> AlertSettings = { s -> s.copy(night = s.night.copy(fromMin = minute)) }

    fun to(minute: Int): (AlertSettings) -> AlertSettings = { s -> s.copy(night = s.night.copy(toMin = minute)) }

    fun rule(
        kind: AlertKind,
        change: (KindRule) -> KindRule,
    ): (AlertSettings) -> AlertSettings = { s -> s.copy(night = s.night.withRule(kind, change(s.night.rule(kind)))) }

    /** The settings with the night set as their rules: what the night threshold dialog keeps in order. */
    fun asRules(s: AlertSettings): AlertSettings = s.copy(rules = AlertKind.entries.associateWith(s.night::rule))
}

/** "22:00–07:00", "Off", or "On now · until 07:00" (the night alerts' row on the Alerts page). */
@Composable
fun nightSummary(ui: AlertsUi): String {
    val n = ui.settings.night
    if (!n.on) return stringResource(R.string.st_special_off)
    val hours = stringResource(R.string.st_night_hours, ClockText.minuteOfDay(n.fromMin), ClockText.minuteOfDay(n.toMin))
    val until = ui.alerts.nightUntil ?: return hours
    return stringResource(R.string.st_night_now, ClockText.time(until.epochSecond))
}

/**
 * Settings › Alerts › Night alerts: the switch, its hours and every alert's night rule (each opens its night page; very
 * low is always on). While they apply, the page says so with the hours they end ([MuteText.night]).
 */
@Composable
fun NightAlertsContent(
    ui: AlertsUi,
    edit: ((AlertSettings) -> AlertSettings) -> Unit,
    openKind: (AlertKind) -> Unit,
    /** Above the page's groups (a follower's *Settings from* note). */
    header: @Composable () -> Unit = {},
    onBack: () -> Unit,
) {
    val n = ui.settings.night
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val words = rememberAlertWords()
    SettingsPage(stringResource(R.string.st_night_title), onBack) {
        ui.alerts.nightUntil?.let { until ->
            StateStrip(
                DsR.drawable.ds_ic_bedtime,
                MuteText.night(until.epochSecond, words) { ClockText.time(it) },
                listOf(stringResource(R.string.st_night_turn_off) to { edit(NightEdits.on(false)) }),
                headline = true,
            )
        }
        header()
        // what the page is for (TopIntroPreference), then its main switch (MainSwitchPreference)
        SettingsIntro(stringResource(R.string.st_night_use_summary))
        MainSwitchRow(stringResource(R.string.st_night_use), n.on, { on -> edit(NightEdits.on(on)) }, readOnly = ui.mirrored)
        SettingsGroup {
            ValueRow(
                stringResource(R.string.st_night_from),
                ClockText.minuteOfDay(n.fromMin),
                { dialog = "from" },
                icon = DsR.drawable.ds_ic_bedtime,
                enabled = n.on,
                readOnly = ui.mirrored,
            )
            ValueRow(
                stringResource(R.string.st_night_to),
                ClockText.minuteOfDay(n.toMin),
                { dialog = "to" },
                icon = DsR.drawable.ds_ic_sunny,
                enabled = n.on,
                readOnly = ui.mirrored,
            )
        }
        for ((title, kinds) in KindGroups) {
            SettingsGroup(title = stringResource(title)) {
                for (kind in kinds) {
                    val rule = n.rule(kind)
                    val always = kind == AlertKind.VERY_LOW
                    SplitSwitchRow(
                        kindName(kind),
                        rule.on,
                        { on -> edit(NightEdits.rule(kind) { it.copy(on = on) }) },
                        { openKind(kind) },
                        summary =
                            listOfNotNull(
                                stringResource(R.string.st_night_always).takeIf { always },
                                if (rule.on) nightKindSummary(kind, rule) else stringResource(R.string.st_off),
                            ).joinToString(" · "),
                        enabled = n.on,
                        icon = kindIcon(kind),
                        switchEnabled = n.on && !always && !ui.mirrors(kind),
                    )
                }
            }
        }
        FooterText(stringResource(R.string.st_night_footer))
    }
    when (dialog) {
        "from" -> TimeDialog(stringResource(R.string.st_night_from), n.fromMin, { dialog = null }) { edit(NightEdits.from(it)) }
        "to" -> TimeDialog(stringResource(R.string.st_night_to), n.toMin, { dialog = null }) { edit(NightEdits.to(it)) }
    }
}

/** "Below 70 mg/dL · every 5 min · like an alarm": one alert's night rule in words. */
@Composable
private fun nightKindSummary(
    kind: AlertKind,
    rule: KindRule,
): String {
    val repeat =
        if (rule.repeatMin <= 0) {
            numberLabel(SchemaPrefs.RepeatSpec, 0.0)
        } else {
            stringResource(R.string.st_repeat_every, numberLabel(SchemaPrefs.RepeatSpec, rule.repeatMin.toDouble()))
        }
    return listOfNotNull(thresholdSentence(kind, rule.threshold), repeat, stringResource(R.string.st_alarm).takeIf { rule.alarm })
        .joinToString(" · ")
}

/** One alert's night rule: on (never off for very low), when it alerts, how often it reminds, like an alarm. */
@Composable
fun NightKindContent(
    kind: AlertKind,
    ui: AlertsUi,
    edit: ((AlertSettings) -> AlertSettings) -> Unit,
    onBack: () -> Unit,
) {
    val rule = ui.settings.night.rule(kind)
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val label = numberLabeler()
    val readOnly = ui.mirrors(kind)
    SettingsPage(stringResource(R.string.st_night_kind_title, kindName(kind)), onBack) {
        // the page's main switch; very low is always on at night, and the page says so
        MainSwitchRow(
            stringResource(R.string.st_kind_use, kindInSentence(kind)),
            rule.on,
            { on -> edit(NightEdits.rule(kind) { it.copy(on = on) }) },
            readOnly = readOnly || kind == AlertKind.VERY_LOW,
            note = stringResource(R.string.st_night_always).takeIf { kind == AlertKind.VERY_LOW },
        )
        SettingsGroup(title = stringResource(R.string.st_alert_when)) {
            ValueRow(
                stringResource(R.string.st_threshold),
                withSpans(
                    thresholdSentence(kind, rule.threshold),
                    spanWords().text(RangeSpans.ofAlert(kind, NightEdits.asRules(ui.settings))),
                ),
                { dialog = "threshold" },
                enabled = rule.on,
                icon = DsR.drawable.ds_ic_dia_range,
                readOnly = readOnly,
            )
            ValueRow(
                stringResource(R.string.st_repeat),
                numberLabel(SchemaPrefs.RepeatSpec, rule.repeatMin.toDouble()),
                { dialog = "repeat" },
                enabled = rule.on,
                icon = DsR.drawable.ds_ic_repeat,
                readOnly = readOnly,
            )
            SwitchRow(
                stringResource(R.string.st_alarm),
                rule.alarm,
                { on -> edit(NightEdits.rule(kind) { it.copy(alarm = on) }) },
                summary = stringResource(R.string.st_alarm_summary),
                enabled = rule.on,
                icon = DsR.drawable.ds_ic_notifications_active,
            )
        }
        FooterText(stringResource(R.string.st_night_kind_footer))
    }
    when (dialog) {
        "threshold" -> {
            val spec = SchemaPrefs.thresholdSpec(kind)
            val night = NightEdits.asRules(ui.settings)
            val words = spanWords()

            fun spans(v: Double) = RangeSpans.ofAlert(kind, AlertEdits.threshold(kind, v)(night))
            NumberDialog(
                stringResource(R.string.st_threshold),
                thresholdInput(night, kind),
                rule.threshold,
                { label(spec, it) },
                { dialog = null },
                sub = { words.text(spans(it).take(1)) },
                live = { words.text(spans(it)) },
            ) { v -> edit(NightEdits.rule(kind) { it.copy(threshold = spec.coerce(v)) }) }
        }

        "repeat" -> {
            val spec = SchemaPrefs.RepeatSpec
            NumberDialog(stringResource(R.string.st_repeat), NumberInput(spec), rule.repeatMin.toDouble(), { label(spec, it) }, {
                dialog = null
            }) { v -> edit(NightEdits.rule(kind) { it.copy(repeatMin = spec.coerce(v).roundToInt()) }) }
        }
    }
}
