// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertQuietSide
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.designsystem.alerts.MuteText
import app.mauri.sukrara.designsystem.alerts.QuietExit
import app.mauri.sukrara.designsystem.alerts.QuietExits
import app.mauri.sukrara.designsystem.alerts.QuietRow
import app.mauri.sukrara.designsystem.alerts.rememberAlertWords
import app.mauri.sukrara.designsystem.alerts.rememberCountdownNow
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.ChoiceDialog
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.MainSwitchRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.designsystem.rows.SplitSwitchRow
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.feature.settings.search.Section
import app.mauri.sukrara.settings.SettingsSpec
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/** What the Alerts page can do (the ViewModel's actions; no-ops in screenshots). */
class AlertsActions(
    val edit: ((AlertSettings) -> AlertSettings) -> Unit = {},
    val muteAll: (Double) -> Unit = {},
    val unmuteAll: () -> Unit = {},
    val openKind: (AlertKind) -> Unit = {},
    val openReview: () -> Unit = {},
    val playback: AlertPlayback = AlertPlayback.None,
    /** A quiet side's way out under Active now (Unmute, Alert me anyway, Remind as usual). */
    val quietExit: (AlertQuietSide, QuietExit) -> Unit = { _, _ -> },
    /** A muted side's *Change to* length: kind (wire name), minutes. */
    val muteSide: (String, Int) -> Unit = { _, _ -> },
    /** Settings › Alerts › Night alerts (#38). */
    val openNight: () -> Unit = {},
    /** Settings › Alerts › Fine-tuning (the rarely touched rows). */
    val openFineTuning: () -> Unit = {},
    /** *See also*: Alert reliability and Readings (the ranges, separate from the alert levels). */
    val openReliability: () -> Unit = {},
    val openReadings: () -> Unit = {},
    /** Settings › Alerts › one quiet rule. */
    val openQuiet: (QuietKind) -> Unit = {},
    /** *Separate alert notifications* (docs/36). */
    val setSeparate: (Boolean) -> Unit = {},
)

/** The alert rows' groups: lows, highs, rates, signal. */
val KindGroups =
    listOf(
        R.string.st_group_lows to listOf(AlertKind.VERY_LOW, AlertKind.LOW),
        R.string.st_group_highs to listOf(AlertKind.HIGH, AlertKind.VERY_HIGH, AlertKind.EXTREMELY_HIGH),
        R.string.st_group_rates to listOf(AlertKind.FALLING_FAST, AlertKind.RISING_FAST),
        R.string.st_group_signal to listOf(AlertKind.NO_DATA),
    )

/** "Below 70 mg/dL · every 5 min · Quest": when it alerts, how often it reminds, which sound. */
@Composable
fun kindSummary(
    s: AlertSettings,
    kind: AlertKind,
): String {
    val rule = s.rule(kind)
    val repeat =
        if (rule.repeatMin <= 0) {
            numberLabel(SchemaPrefs.RepeatSpec, 0.0)
        } else {
            stringResource(R.string.st_repeat_every, numberLabel(SchemaPrefs.RepeatSpec, rule.repeatMin.toDouble()))
        }
    return listOf(thresholdSentence(kind, rule.threshold), repeat, packName(s.sounds.soundOf(kind))).joinToString(" · ")
}

/**
 * Active now: every quiet side on this phone (muted, quiet after a treatment or while falling back, reminding less)
 * with its ways out, counting down; nothing while none is.
 */
@Composable
private fun ActiveNow(
    alerts: AlertUiState,
    clock: Clock,
    actions: AlertsActions,
) {
    val rows = QuietExits.active(alerts)
    if (rows.isEmpty()) return
    SettingsGroup(title = stringResource(R.string.st_active_now), modifier = Modifier.testTag("active-now")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            for (q in rows) {
                val now = rememberCountdownNow(q.until, clock::now)
                QuietRow(q, now, onExit = { actions.quietExit(q, it) }, onChangeTo = { actions.muteSide(q.kind, it) })
            }
        }
    }
}

/** Mute all's "Alerts muted · 1 h 12 min left · Unmute · Change time" strip ([MuteText], counting down), or nothing. */
@Composable
fun MuteAllStrip(
    alerts: AlertUiState,
    clock: Clock,
    onUnmute: () -> Unit,
    onChangeTime: () -> Unit,
) {
    val words = rememberAlertWords()
    val now = rememberCountdownNow(alerts.mutedUntil, clock::now)
    val text = MuteText.muteAll(alerts, now, words) ?: return
    StateStrip(
        DsR.drawable.ds_ic_notifications_off,
        text,
        listOf(stringResource(R.string.st_unmute) to onUnmute, stringResource(R.string.st_change_time) to onChangeTime),
        headline = true,
    )
}

/** The Alerts page: main switch, Mute all, each alert, quiet rules, notification actions, sounds, threshold review. */
@Composable
fun AlertsSettingsContent(
    ui: AlertsUi,
    confirmedAt: Instant?,
    clock: Clock,
    actions: AlertsActions,
    /** Above the page's groups (a follower's *Settings from* note). */
    header: @Composable () -> Unit = {},
    onBack: () -> Unit,
) {
    val s = ui.settings
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsPage(stringResource(R.string.st_alerts_title), onBack) {
        MuteAllStrip(ui.alerts, clock, actions.unmuteAll) { dialog = "muteAll" }
        header()
        ActiveNow(ui.alerts, clock, actions)
        // the page's main switch (SPA MainSwitchPreference); while it is off, the page says what that means
        MainSwitchRow(
            stringResource(R.string.st_alerts_main),
            s.on,
            { on -> if (on) actions.edit { it.copy(on = true) } else dialog = "alertsOff" },
            note = if (s.on) null else stringResource(R.string.st_alerts_main_off),
        )
        SettingsGroup {
            ValueRow(
                stringResource(R.string.st_mute_all),
                ui.alerts.mutedUntil?.let {
                    if (ui.alerts.mutedUntilTurnedOff) {
                        stringResource(
                            R.string.st_special_until_turned_off,
                        )
                    } else {
                        stringResource(R.string.st_until, clockTime(it, clock.zone))
                    }
                } ?: stringResource(R.string.st_not_muted),
                { dialog = "muteAll" },
                icon = DsR.drawable.ds_ic_notifications_off,
            )
        }
        AlertsLately(ui.alerts.lately, clock)
        // each alert: the row opens its page, the switch turns it on or off (very low / no data off asks first)
        for ((title, kinds) in KindGroups) {
            SettingsGroup(title = stringResource(title)) {
                for (kind in kinds) {
                    val rule = s.rule(kind)
                    SplitSwitchRow(
                        kindName(kind),
                        rule.on,
                        { on ->
                            if (!on &&
                                kind in ConfirmOff
                            ) {
                                dialog = "kindOff:${kind.wire}"
                            } else {
                                actions.edit(AlertEdits.kindOn(kind, on))
                            }
                        },
                        { actions.openKind(kind) },
                        summary = if (rule.on) kindSummary(s, kind) else stringResource(R.string.st_off),
                        enabled = s.on,
                        icon = kindIcon(kind),
                        switchEnabled = s.on && !ui.mirrors(kind),
                    )
                }
            }
        }
        // #38: a second set of the rules for the night, with its hours; says when it is on now
        SettingsGroup {
            NavigationRow(
                stringResource(R.string.st_night_title),
                actions.openNight,
                summary = nightSummary(ui),
                icon = DsR.drawable.ds_ic_bedtime,
            )
        }
        // #39: a very low nobody answers: the wearer tells the followers; a follower takes those escalations
        SettingsGroup(title = stringResource(R.string.st_escalation_title)) {
            if (ui.follower) {
                SwitchRow(
                    stringResource(R.string.st_escalation_follow),
                    s.escalation.follow,
                    { on -> actions.edit { it.copy(escalation = it.escalation.copy(follow = on)) } },
                    summary = stringResource(R.string.st_escalation_follow_summary),
                    icon = DsR.drawable.ds_ic_group,
                )
            } else {
                SwitchRow(
                    stringResource(R.string.st_escalation_on),
                    s.escalation.on,
                    { on -> actions.edit { it.copy(escalation = it.escalation.copy(on = on)) } },
                    summary = stringResource(R.string.st_escalation_on_summary),
                    icon = DsR.drawable.ds_ic_group,
                )
                ValueRow(
                    stringResource(R.string.st_escalation_min),
                    numberLabel(SchemaPrefs.spec("alerts.escalation.min"), s.escalation.min.toDouble()),
                    { dialog = "escalateMin" },
                    icon = DsR.drawable.ds_ic_timer,
                    enabled = s.escalation.on,
                )
            }
        }
        FooterText(stringResource(R.string.st_escalation_footer))
        // the review edits the thresholds: only where this phone's own are in use
        if (!ui.mirrored) {
            SettingsGroup {
                NavigationRow(
                    stringResource(R.string.st_review_title),
                    actions.openReview,
                    summary =
                        confirmedAt?.let { stringResource(R.string.st_review_confirmed) }
                            ?: stringResource(R.string.st_review_not_confirmed),
                    icon = DsR.drawable.ds_ic_rule,
                )
            }
        }
        // each quiet rule: the switch turns it on or off, the row opens its page (its length, what it does)
        SettingsGroup(title = stringResource(R.string.st_quiet_title)) {
            for (which in QuietKind.entries) QuietRuleRow(ui, which, actions.edit) { actions.openQuiet(which) }
        }

        SettingsGroup(title = stringResource(R.string.st_sounds_title)) {
            ValueRow(
                stringResource(R.string.st_sound_pack),
                packName(s.sounds.pack),
                { dialog = "pack" },
                icon = DsR.drawable.ds_ic_volume_up,
            )
            SwitchRow(
                stringResource(R.string.st_shuffle),
                s.sounds.shuffle,
                { on -> actions.edit { it.copy(sounds = it.sounds.copy(shuffle = on)) } },
                summary = stringResource(R.string.st_shuffle_summary),
                icon = DsR.drawable.ds_ic_shuffle,
            )
            // docs/36: Wear OS never gets the ongoing glucose notification that carries the alerts; a watch app on the
            // phone is the moment to suggest it
            val watchApp = remember { context.hasWatchApp() }
            SwitchRow(
                stringResource(R.string.st_alerts_separate),
                ui.separate,
                actions.setSeparate,
                summary =
                    stringResource(
                        when {
                            ui.separate -> R.string.st_alerts_separate_on
                            watchApp -> R.string.st_alerts_separate_suggested
                            else -> R.string.st_alerts_separate_off
                        },
                    ),
                icon = DsR.drawable.ds_ic_watch,
            )
            NavigationRow(
                stringResource(R.string.st_notification_settings),
                { context.openNotificationSettings() },
                summary = stringResource(R.string.st_notification_settings_summary),
                icon = DsR.drawable.ds_ic_tune,
            )
        }
        // the rarely touched rows: one named subpage at the end (docs/35: no global advanced mode)
        SettingsGroup {
            NavigationRow(
                stringResource(R.string.st_finetune_title),
                actions.openFineTuning,
                summary = stringResource(R.string.st_finetune_summary),
                icon = DsR.drawable.ds_ic_tune,
            )
        }
        FooterText(stringResource(R.string.st_alerts_footer))
        SeeAlso(
            listOf(
                SeeAlsoLink(stringResource(R.string.st_reliability_title), null, Section.Reliability, actions.openReliability),
                SeeAlsoLink(
                    stringResource(R.string.st_readings_title),
                    stringResource(R.string.st_overview_readings),
                    Section.Readings,
                    actions.openReadings,
                ),
            ),
        )
    }
    AlertsDialogs(dialog, ui, actions) { dialog = null }
}

@Composable
private fun AlertsDialogs(
    dialog: String?,
    ui: AlertsUi,
    actions: AlertsActions,
    close: () -> Unit,
) {
    val s = ui.settings
    val label = numberLabeler()
    when {
        dialog == null -> {
            Unit
        }

        dialog.startsWith("kindOff:") -> {
            val kind = AlertKind.fromWire(dialog.removePrefix("kindOff:")) ?: return close()
            ConfirmDialog(
                stringResource(R.string.st_kind_off_title, kindInSentence(kind)),
                stringResource(if (kind == AlertKind.NO_DATA) R.string.st_kind_off_text_no_data else R.string.st_kind_off_text_very_low),
                stringResource(R.string.st_turn_off),
                close,
            ) { actions.edit(AlertEdits.kindOn(kind, false)) }
        }

        dialog == "alertsOff" -> {
            ConfirmDialog(
                stringResource(R.string.st_alerts_off_title),
                stringResource(R.string.st_alerts_off_text),
                stringResource(R.string.st_turn_off),
                close,
            ) { actions.edit { it.copy(on = false) } }
        }

        dialog == "muteAll" -> {
            val spec = SettingsSpec.MUTE_ALL
            NumberDialog(
                stringResource(R.string.st_mute_all),
                NumberInput(spec),
                spec.default,
                { label(spec, it) },
                close,
            ) { actions.muteAll(it) }
        }

        dialog == "escalateMin" -> {
            val spec = SchemaPrefs.spec("alerts.escalation.min")
            NumberDialog(
                stringResource(R.string.st_escalation_min),
                NumberInput(spec),
                s.escalation.min.toDouble(),
                { label(spec, it) },
                close,
            ) { v ->
                actions.edit { it.copy(escalation = it.escalation.copy(min = spec.coerce(v).toInt())) }
            }
        }

        dialog == "pack" -> {
            val packs = SettingsSpec.OPTIONS.getValue("soundPack")
            // a pack is heard as the low alert rings: the alert most people hear most
            val playback = actions.playback
            DisposableEffect(playback) { onDispose { playback.stop() } }
            val alarm = s.rule(AlertKind.LOW).alarm
            val options = packs.map { Choice(packName(it), it, trailing = soundPreview(AlertKind.LOW, it, alarm, playback)) }
            ChoiceDialog(stringResource(R.string.st_sound_pack), options, s.sounds.pack, close) { pack ->
                actions.edit { it.copy(sounds = it.sounds.copy(pack = pack)) }
            }
        }
    }
}

/** A confirmation for turning off something that protects the person (all alerts, the very low alarm). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onClose()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.st_cancel)) } },
    )
}

/** The rows of *Near a threshold* (SPEC §1 #37): the low and high waits and the margin, each one schema number. */
enum class WaitKind(
    val specId: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
) {
    Low("alerts.persistence.lowMin", R.string.st_wait_low, DsR.drawable.ds_ic_timer),
    High("alerts.persistence.highMin", R.string.st_wait_high, DsR.drawable.ds_ic_timer),
    Margin("alerts.persistence.marginMgdl", R.string.st_wait_margin, DsR.drawable.ds_ic_trending_down),
    ;

    fun value(s: AlertSettings): Int =
        when (this) {
            Low -> s.persistence.lowMin
            High -> s.persistence.highMin
            Margin -> s.persistence.marginMgdl
        }

    fun set(s: AlertSettings, v: Int): AlertSettings =
        s.copy(
            persistence =
                when (this) {
                    Low -> s.persistence.copy(lowMin = v)
                    High -> s.persistence.copy(highMin = v)
                    Margin -> s.persistence.copy(marginMgdl = v)
                },
        )
}

/**
 * The phone has a watch's companion app (Wear OS, Pixel Watch, Galaxy Wearable): *Separate alert notifications* is suggested.
 * Declared in the manifest's `<queries>`, so Android lets Sukrara see exactly these packages.
 */
internal fun Context.hasWatchApp(): Boolean =
    WATCH_APPS.any { pkg ->
        runCatching {
            packageManager.getPackageInfo(
                pkg,
                android.content.pm.PackageManager.PackageInfoFlags
                    .of(0),
            )
        }.isSuccess
    }

/** The watch companion apps that bridge phone notifications to a watch. */
internal val WATCH_APPS =
    listOf(
        "com.google.android.wearable.app",
        "com.google.android.apps.wear.companion",
        "com.samsung.android.app.watchmanager",
    )
