// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceGroup
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.designsystem.banner.NoticeCard
import app.mauri.sukrara.designsystem.rows.RadioRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.source.PersonChoiceCard
import app.mauri.sukrara.designsystem.source.SourceIcon
import app.mauri.sukrara.designsystem.source.labelRes
import app.mauri.sukrara.designsystem.source.sourceMeans
import app.mauri.sukrara.designsystem.source.titleRes
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * *Whose glucose will Sukrara show?* (docs/44 "Setup"): two big illustrated choices, yours or someone else's, and the
 * line that says it can be changed later (it once said "reinstall"). Preselected with what the phone runs on (an
 * update from before the question; Settings › Source), never with a guess on a new install. *Continue* goes to the
 * source step; nothing is stored until the source is.
 */
@Composable
internal fun ColumnScope.PersonStep(
    u: SetupUi,
    actions: SetupActions,
) {
    var selected by rememberSaveable { mutableStateOf((u.person ?: u.facts.running?.person)?.id) }
    StepHeader(stringResource(R.string.sh_setup_person_title), null)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (person in Person.entries) {
            PersonChoiceCard(
                person = person,
                title = stringResource(person.labelRes()),
                means =
                    stringResource(
                        if (person ==
                            Person.Yours
                        ) {
                            R.string.sh_setup_person_yours_means
                        } else {
                            R.string.sh_setup_person_theirs_means
                        },
                    ),
                selected = selected == person.id,
                onSelect = { selected = person.id },
            )
        }
    }
    // opened from Settings › Source it is no news that it can be changed there
    if (!u.visit) {
        Text(
            stringResource(R.string.sh_setup_person_later),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    StepButtons(
        stringResource(R.string.sh_setup_continue),
        onPrimary = { Person.of(selected)?.let(actions.choosePerson) },
        enabled = selected != null && !u.busy,
    )
}

/**
 * *Where does your (their) glucose come from?*: the sources for the person picked, grouped by what the person feels
 * (on this phone, offline / from another Sukrara), each with what it brings, the most direct one marked
 * *Recommended* (its line starts with it: a label at the row's end squeezed the title; SPA's radio rows, as every choice in
 * Settings). *Continue* stores it: the first time at once; a
 * change says what changes first, and a change of whose glucose with history on the phone asks to delete it (*Export
 * CSV first*).
 */
@Composable
internal fun ColumnScope.SourceStep(
    u: SetupUi,
    actions: SetupActions,
) {
    val running = u.facts.spec ?: u.facts.running
    val person = u.person ?: running?.person ?: Person.Yours
    var selected by rememberSaveable(person) { mutableStateOf(SetupMachine.preselected(running, person).id) }
    StepHeader(
        stringResource(if (person == Person.Yours) R.string.sh_setup_source_title else R.string.sh_setup_source_title_theirs),
        null,
    )
    val kinds = SourceKind.forPerson(person)
    for (group in SourceGroup.entries) {
        val inGroup = kinds.filter { it.group == group }
        if (inGroup.isEmpty()) continue
        SettingsGroup(
            title =
                stringResource(
                    when (group) {
                        SourceGroup.Sukrara -> R.string.sh_setup_source_group_sukrara
                        SourceGroup.Online -> R.string.sh_setup_source_group_online
                        SourceGroup.Offline, SourceGroup.Network -> R.string.sh_setup_source_group_offline
                    },
                ),
            modifier = Modifier.selectableGroup(),
        ) {
            for (kind in inGroup) {
                val spec = SourceSpec(person, kind)
                RadioRow(
                    title = stringResource(spec.titleRes()),
                    selected = selected == kind.id,
                    onSelect = { selected = kind.id },
                    summary =
                        sourceMeans(spec, grouped = true).let { means ->
                            if (spec.readsSensor) stringResource(R.string.sh_setup_source_recommended, means) else means
                        },
                    leading = { SourceIcon(kind) },
                )
            }
        }
    }
    StepButtons(
        stringResource(R.string.sh_setup_continue),
        onPrimary = { SourceSpec.of(person, SourceKind.of(selected))?.let(actions.reviewSource) },
        enabled = SourceSpec.of(person, SourceKind.of(selected)) != null && !u.busy,
    )
    val change = u.sourceChange ?: return
    val loss = change.loss
    if (loss != null) {
        HistoryLossDialog(
            personName = stringResource(change.spec.person.labelRes()),
            loss = loss,
            onExport = actions.exportHistory,
            onDelete = { actions.chooseSource(change.spec) },
            onCancel = actions.cancelSource,
        )
    } else {
        ConfirmDialog(
            title = stringResource(R.string.sh_setup_source_change_title, stringResource(change.spec.titleRes())),
            text =
                stringResource(
                    when (change.spec.kind) {
                        SourceKind.Sensor -> {
                            R.string.sh_setup_source_change_sensor
                        }

                        // only a sensor Sukrara read has a connection to stop and a pairing that stays
                        SourceKind.PhoneApp -> {
                            if (change.replaces?.kind == SourceKind.Sensor) {
                                R.string.sh_setup_source_change_phone_app_from_sensor
                            } else {
                                R.string.sh_setup_source_change_phone_app
                            }
                        }

                        SourceKind.Nightscout -> {
                            R.string.sh_setup_source_change_nightscout
                        }

                        SourceKind.Sukrara -> {
                            R.string.sh_setup_source_change_sukrara
                        }
                    },
                ),
            confirm = stringResource(R.string.sh_setup_source_change_confirm),
            onConfirm = { actions.chooseSource(change.spec) },
            onDismiss = actions.cancelSource,
        )
    }
}

/**
 * The connect step for Juggluco on this phone: what to turn on in Juggluco, *Open Juggluco* (or, Juggluco not
 * installed, that it is missing), then *Reading received* once the first reading arrives (the same notice as the
 * sensor's). *Later* keeps it on home's *Finish setup*.
 */
@Composable
internal fun ColumnScope.JugglucoStep(
    u: SetupUi,
    actions: SetupActions,
) {
    val context = LocalContext.current
    StepHeader(stringResource(R.string.sh_setup_juggluco_title), stringResource(R.string.sh_setup_juggluco_body))
    if (u.facts.ownSensorLive) {
        NoticeCard(stringResource(R.string.sh_setup_sensor_live), icon = DsR.drawable.ds_ic_check_circle)
        StepButtons(stringResource(R.string.sh_setup_continue), actions.completeSensor)
    } else {
        val open = context.packageManager.getLaunchIntentForPackage(JUGGLUCO_PACKAGE)
        // not installed: waiting would never end, so say what is missing
        NoticeCard(stringResource(if (open == null) R.string.sh_setup_juggluco_missing else R.string.sh_setup_juggluco_waiting))
        StepButtons(
            stringResource(R.string.sh_setup_juggluco_open).takeIf { open != null },
            { open?.let { context.startSafely(it) } },
            secondary = listOf(stringResource(R.string.sh_setup_later) to { actions.skip(SetupStep.Sensor) }),
        )
    }
}

/** Juggluco's package (its launcher opens it so the person can turn its web server on). */
private const val JUGGLUCO_PACKAGE = "tk.glucodata"
