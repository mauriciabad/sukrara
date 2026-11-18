// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.Link
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.designsystem.banner.NoticeCard
import app.mauri.sukrara.designsystem.dialog.DialogRadio
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.source.shortRes
import app.mauri.sukrara.feature.sharing.setup.lossWhat
import app.mauri.sukrara.feature.sharing.setup.lossWhen
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.widget.dialog.SettingsDialog
import com.android.settingslib.spa.widget.ui.SettingsBody
import java.time.ZoneId

/**
 * *Keep following {name}?* (link.json `pick.asks` keepFollowing): another source picked on a phone that follows a
 * Sukrara. It says what the link already brings, from whichever source the wearer's phone uses, and names [picked]
 * only in what it would cost. *Keep following* is the default and the safe answer (it changes nothing; Back and a tap
 * outside are it too); *Stop following* goes on to the leave questions ([LeaveLinkDialog]).
 */
@Composable
internal fun KeepFollowingDialog(
    name: String?,
    yours: Boolean,
    picked: SourceKind,
    onKeep: () -> Unit,
    onStop: () -> Unit,
) {
    val haptics = rememberHaptics()
    val source = if (picked == SourceKind.Sensor) stringResource(R.string.sh_link_source_sensor) else stringResource(picked.shortRes())
    SettingsDialog(
        title =
            when {
                yours -> stringResource(R.string.sh_link_keep_title_yours)
                name != null -> stringResource(R.string.sh_link_keep_title, name)
                else -> stringResource(R.string.sh_link_keep_title_unnamed)
            },
        onDismissRequest = onKeep,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(Modifier.padding(SettingsDimension.dialogItemPadding)) {
                SettingsBody(
                    when {
                        yours -> stringResource(R.string.sh_link_keep_text_yours, source)
                        name != null -> stringResource(R.string.sh_link_keep_text, name, source)
                        else -> stringResource(R.string.sh_link_keep_text_unnamed, source)
                    },
                )
            }
            LinkButtons(
                dismiss = stringResource(R.string.sh_following_stop),
                onDismiss = onStop,
                confirm = stringResource(R.string.sh_link_keep),
                onConfirm = {
                    haptics.confirm()
                    onKeep()
                },
            )
        }
    }
}

/**
 * The leave questions in one place (link.json `leave`): *Stop following {name}?*, what the wearer's phone is told and
 * how to follow again, then only the questions [facts] asks: the settings this phone keeps (*Keep {name}'s settings* /
 * *Sukrara's defaults*), the history the link brought (*Keep it* / *Delete it*, saying what goes), or, when whose glucose
 * changes with it, *Delete the earlier history?* with *Export CSV first*. Each preselected with link.json's defaults.
 * *Cancel* (Back, a tap outside) changes nothing; *Stop following* leaves.
 */
@Composable
internal fun LeaveLinkDialog(
    facts: LeaveFacts,
    onCancel: () -> Unit,
    onLeave: (LeaveAnswers) -> Unit,
    onExport: () -> Unit = {},
) {
    val haptics = rememberHaptics()
    var keepSettings by rememberSaveable { mutableStateOf(LeaveAnswers().keepSettings) }
    var keepHistory by rememberSaveable { mutableStateOf(LeaveAnswers().keepHistory) }
    val name = facts.name
    SettingsDialog(
        title =
            when {
                facts.yours -> stringResource(R.string.sh_link_leave_title_yours)
                name != null -> stringResource(R.string.sh_following_stop_title, name)
                else -> stringResource(R.string.sh_link_leave_title_unnamed)
            },
        onDismissRequest = onCancel,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(Modifier.padding(SettingsDimension.dialogItemPadding)) {
                SettingsBody(
                    when {
                        !facts.told -> stringResource(R.string.sh_link_leave_again)
                        facts.yours -> stringResource(R.string.sh_link_leave_told_yours)
                        name != null -> stringResource(R.string.sh_link_leave_told, name)
                        else -> stringResource(R.string.sh_link_leave_told_unnamed)
                    },
                )
            }
            for (q in facts.plan.questions) {
                when (q) {
                    Link.Question.Settings -> {
                        Question(stringResource(R.string.sh_link_leave_settings)) {
                            DialogRadio(
                                when {
                                    facts.yours -> stringResource(R.string.sh_link_leave_settings_theirs_yours)
                                    name != null -> stringResource(R.string.sh_link_leave_settings_theirs, name)
                                    else -> stringResource(R.string.sh_link_leave_settings_theirs_unnamed)
                                },
                                stringResource(R.string.sh_link_leave_settings_theirs_sub),
                                keepSettings,
                                enabled = true,
                            ) { keepSettings = true }
                            DialogRadio(
                                stringResource(R.string.sh_link_leave_settings_defaults),
                                stringResource(R.string.sh_link_leave_settings_defaults_sub),
                                !keepSettings,
                                enabled = true,
                            ) { keepSettings = false }
                        }
                    }

                    Link.Question.History -> {
                        Question(stringResource(R.string.sh_link_leave_history)) {
                            DialogRadio(
                                stringResource(R.string.sh_link_leave_history_keep),
                                stringResource(R.string.sh_link_leave_history_keep_sub),
                                keepHistory,
                                enabled = true,
                            ) { keepHistory = true }
                            DialogRadio(
                                stringResource(R.string.sh_link_leave_history_delete),
                                facts.history?.let {
                                    stringResource(
                                        R.string.sh_link_leave_history_delete_sub,
                                        lossWhat(it),
                                        lossWhen(it, ZoneId.systemDefault()),
                                    )
                                },
                                !keepHistory,
                                enabled = true,
                            ) { keepHistory = false }
                        }
                    }

                    Link.Question.DeleteHistory -> {
                        val loss = facts.history
                        Column(
                            Modifier.padding(SettingsDimension.dialogItemPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.sh_setup_person_delete_title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() },
                            )
                            if (loss != null) {
                                SettingsBody(
                                    stringResource(
                                        R.string.sh_link_leave_delete_text,
                                        lossWhat(loss),
                                        lossWhen(loss, ZoneId.systemDefault()),
                                    ),
                                )
                            }
                            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.sh_setup_person_delete_export))
                            }
                        }
                    }
                }
            }
            LinkButtons(
                dismiss = stringResource(R.string.sh_setup_cancel),
                onDismiss = onCancel,
                confirm = stringResource(R.string.sh_following_stop),
                danger = true,
                onConfirm = {
                    haptics.confirm()
                    onLeave(LeaveAnswers(keepSettings, keepHistory))
                },
            )
        }
    }
}

/**
 * *Ana hasn't been told yet that you stopped following. Sukrara tells them once this phone is online.* (the Mac's words
 * under *Not following anyone*): a follow this phone left whose `leave` no relay or their phone took yet. It goes by
 * itself once one does; nothing to do but stay online with Sukrara installed.
 */
@Composable
fun UntoldLeaveNotice(
    wearer: String,
    modifier: Modifier = Modifier,
) = NoticeCard(
    if (wearer.isEmpty()) stringResource(R.string.sh_link_untold_unnamed) else stringResource(R.string.sh_link_untold, wearer),
    modifier = modifier,
    icon = app.mauri.sukrara.core.designsystem.R.drawable.ds_ic_schedule,
)

/** One question of the leave: its title, then its choices (one radio group). */
@Composable
private fun Question(
    title: String,
    choices: @Composable () -> Unit,
) = Column {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(
                    start = SettingsDimension.dialogItemPaddingHorizontal,
                    end = SettingsDimension.dialogItemPaddingHorizontal,
                    top = 8.dp,
                ).semantics { heading() },
    )
    Column(Modifier.selectableGroup()) { choices() }
}

/**
 * SPA's dialog buttons at the end (as `DialogButtons`): the outlined [dismiss], then the filled [confirm], in the error
 * colours when it ends something ([danger]: *Stop following*).
 */
@Composable
private fun LinkButtons(
    dismiss: String,
    onDismiss: () -> Unit,
    confirm: String,
    onConfirm: () -> Unit,
    danger: Boolean = false,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(SettingsDimension.dialogItemPadding),
        horizontalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall4, androidx.compose.ui.Alignment.End),
        verticalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall4),
    ) {
        OutlinedButton(onClick = onDismiss) { Text(dismiss) }
        Button(
            onClick = onConfirm,
            colors =
                if (danger) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
        ) { Text(confirm) }
    }
}
