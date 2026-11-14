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
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.rows.DangerRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * Settings › Alerts › Fine-tuning (docs/35: no global advanced mode; the rarely touched rows in one named subpage at the
 * end of their section): how long a low or high waits before its first alert and the margin a bounce must clear
 * (SPEC §1 #37, mirrored: read-only while [AlertsUi.mirrored]), the notification buttons' lengths (each phone's own),
 * and *Reset fine-tuning*, which asks first. Back leaves; search finds every row here.
 */
@Composable
fun FineTuningContent(
    ui: AlertsUi,
    edit: ((AlertSettings) -> AlertSettings) -> Unit,
    /** Above the page's groups (a follower's *Settings from* note). */
    header: @Composable () -> Unit = {},
    onBack: () -> Unit,
) {
    val s = ui.settings
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsPage(stringResource(R.string.st_finetune_title), onBack) {
        header()
        // #37: a low or high waits before its first alert; bounces inside the margin stay one episode
        SettingsGroup(title = stringResource(R.string.st_wait_title)) {
            for (which in WaitKind.entries) {
                val spec = SchemaPrefs.spec(which.specId)
                ValueRow(
                    stringResource(which.title),
                    numberLabel(spec, which.value(s).toDouble()),
                    { dialog = "wait:${which.name}" },
                    icon = which.icon,
                    readOnly = ui.mirrored,
                )
            }
        }
        FooterText(stringResource(R.string.st_wait_footer))

        SettingsGroup(title = stringResource(R.string.st_actions_title)) {
            for (a in ActionLength.entries) {
                ValueRow(
                    stringResource(a.title),
                    numberLabel(SchemaPrefs.spec(a.specId), a.value(s).toDouble()),
                    { dialog = "action:${a.name}" },
                    icon = a.icon,
                )
            }
        }
        val defaults = AlertSettings()
        val changed = s.actions != defaults.actions || (!ui.mirrored && s.persistence != defaults.persistence)
        if (changed) {
            SettingsGroup {
                DangerRow(
                    stringResource(R.string.st_finetune_reset),
                    stringResource(R.string.st_finetune_reset_title),
                    stringResource(R.string.st_finetune_reset_text),
                    stringResource(R.string.st_reset),
                    {
                        edit {
                            // the waits are mirrored: a follower in Wearer mode keeps the wearer's
                            it.copy(
                                actions = defaults.actions,
                                persistence = if (ui.mirrored) it.persistence else defaults.persistence,
                            )
                        }
                    },
                    icon = DsR.drawable.ds_ic_restart_alt,
                )
            }
        }
        FooterText(stringResource(R.string.st_finetune_footer))
    }
    FineTuningDialogs(dialog, s, edit) { dialog = null }
}

@Composable
private fun FineTuningDialogs(
    dialog: String?,
    s: AlertSettings,
    edit: ((AlertSettings) -> AlertSettings) -> Unit,
    close: () -> Unit,
) {
    val label = numberLabeler()
    when {
        dialog == null -> {
            Unit
        }

        dialog.startsWith("wait:") -> {
            val which = WaitKind.valueOf(dialog.removePrefix("wait:"))
            val spec = SchemaPrefs.spec(which.specId)
            NumberDialog(stringResource(which.title), NumberInput(spec), which.value(s).toDouble(), { label(spec, it) }, close) { v ->
                edit { which.set(it, spec.coerce(v).toInt()) }
            }
        }

        dialog == "action:${ActionLength.Relax.name}" -> {
            val spec = SchemaPrefs.spec(ActionLength.Relax.specId)
            // only intervals slower than every level alert's repeat (SPEC §3 relaxMin)
            val input =
                NumberInput(
                    spec,
                    presets = s.relaxChoices().map { it.toDouble() },
                    customMin = (s.relaxFloor() + 1).toDouble().coerceAtMost(spec.max),
                )
            NumberDialog(stringResource(ActionLength.Relax.title), input, s.actions.relaxMin.toDouble(), { label(spec, it) }, close) { v ->
                edit { ActionLength.Relax.set(it, spec.coerce(v).toInt()) }
            }
        }

        dialog.startsWith("action:") -> {
            val a = ActionLength.valueOf(dialog.removePrefix("action:"))
            val spec = SchemaPrefs.spec(a.specId)
            NumberDialog(stringResource(a.title), NumberInput(spec), a.value(s).toDouble(), { label(spec, it) }, close) { v ->
                edit { a.set(it, spec.coerce(v).toInt()) }
            }
        }
    }
}

/** The notification buttons' lengths (`alerts.actions.*`, each phone's own): Mute, Relax, back by … mg/dL / … min. */
private enum class ActionLength(
    val specId: String,
    val title: Int,
    val icon: Int,
) {
    Mute("alerts.actions.muteMin", R.string.st_action_mute, DsR.drawable.ds_ic_notifications_off),
    Relax("alerts.actions.relaxMin", R.string.st_action_relax, DsR.drawable.ds_ic_snooze),
    BackMgdl("alerts.actions.backMgdl", R.string.st_action_back_mgdl, DsR.drawable.ds_ic_trending_down),
    BackMin("alerts.actions.backMin", R.string.st_action_back_min, DsR.drawable.ds_ic_repeat),
    ;

    fun value(s: AlertSettings): Int =
        when (this) {
            Mute -> s.actions.muteMin
            Relax -> s.actions.relaxMin
            BackMgdl -> s.actions.backMgdl
            BackMin -> s.actions.backMin
        }

    fun set(
        s: AlertSettings,
        v: Int,
    ): AlertSettings =
        s.copy(
            actions =
                when (this) {
                    Mute -> s.actions.copy(muteMin = v)
                    Relax -> s.actions.copy(relaxMin = v)
                    BackMgdl -> s.actions.copy(backMgdl = v)
                    BackMin -> s.actions.copy(backMin = v)
                },
        )
}
