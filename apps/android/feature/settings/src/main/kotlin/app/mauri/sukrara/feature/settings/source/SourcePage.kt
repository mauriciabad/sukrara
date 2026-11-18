// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.source

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.source.SourceIcon
import app.mauri.sukrara.designsystem.source.labelRes
import app.mauri.sukrara.designsystem.source.meansRes
import app.mauri.sukrara.designsystem.source.titleRes
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SeeAlso
import app.mauri.sukrara.feature.settings.SeeAlsoLink
import app.mauri.sukrara.feature.settings.StateStrip
import app.mauri.sukrara.feature.settings.search.Section
import app.mauri.sukrara.feature.settings.sensor.JugglucoGroup
import app.mauri.sukrara.feature.settings.sensor.JugglucoSecretDialog
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsActions
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsState
import app.mauri.sukrara.feature.settings.sensor.SourceLink
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * What Settings › Source shows (docs/44 "Settings"): what the phone runs on ([spec]), whether the person answered
 * ([answered]: an install updated from before the question runs on what it was set up to read until they do), the
 * name of the person a stream follows ([name]) and the newest reading ([latest]).
 */
data class SourcePageState(
    val spec: SourceSpec?,
    val answered: Boolean,
    val name: String? = null,
    val latest: Instant? = null,
)

/** Where Settings › Source leads: setup's two questions in place, and the pages beside it. */
class SourceActions(
    /** *Whose glucose*: setup's person step, then its source step (Back = unchanged). */
    val changePerson: () -> Unit = {},
    /** *Where it comes from*: setup's source step (Back = unchanged). */
    val changeSource: () -> Unit = {},
    val openSensor: () -> Unit = {},
    val openImport: () -> Unit = {},
)

/**
 * Settings › Source (docs/44 "Settings"): *Whose glucose* and *Where it comes from* (each opens setup's step in place:
 * one pattern for the choice, and Back changes nothing), the state now, then the source's own rows: the sensor's page,
 * Juggluco's API secret and *Open Juggluco*, or another Sukrara's follow ([streamRows]: who, the request, the
 * relays, Bluetooth, *Stop following*). An unanswered install says Sukrara asks once more, with *Answer*. The foot says
 * what each change keeps; *See also* the sensor and Import history where they apply.
 */
@Composable
fun SourcePageContent(
    state: SourcePageState,
    sensor: SensorSettingsState?,
    now: Instant,
    actions: SourceActions,
    sensorActions: SensorSettingsActions,
    modifier: Modifier = Modifier,
    streamRows: @Composable () -> Unit = {},
) = Column(modifier) {
    val spec = state.spec
    val context = LocalContext.current
    var editingSecret by rememberSaveable { mutableStateOf(false) }
    if (!state.answered && spec != null) {
        StateStrip(
            DsR.drawable.ds_ic_info,
            stringResource(R.string.st_source_ask),
            listOf(stringResource(R.string.st_source_answer) to actions.changePerson),
        )
    }
    SettingsGroup {
        val whose =
            spec?.let { s ->
                val label = stringResource(s.person.labelRes())
                if (s.person == Person.Theirs && !state.name.isNullOrBlank()) "$label · ${state.name}" else label
            }
        NavigationRow(
            stringResource(R.string.st_source_whose),
            actions.changePerson,
            summary = whose ?: stringResource(R.string.st_source_not_chosen),
            icon = if (spec?.person == Person.Theirs) DsR.drawable.ds_ic_group else DsR.drawable.ds_ic_person,
        )
        NavigationRow(
            stringResource(R.string.st_source_from),
            actions.changeSource,
            summary =
                spec?.let { "${stringResource(it.titleRes())}\n${stringResource(it.meansRes())}" }
                    ?: stringResource(R.string.st_source_not_chosen),
            leading = spec?.let { s -> { SourceIcon(s.kind) } },
            icon = DsR.drawable.ds_ic_water_drop.takeIf { spec == null },
        )
        if (spec != null) {
            val reduced = LocalReducedMotion.current
            val live =
                state.latest?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it.toEpochMilli(),
                        now.toEpochMilli(),
                        DateUtils.MINUTE_IN_MILLIS,
                    )
                }
            // the line changes by itself as readings come: cross-fades, and TalkBack hears it once
            AnimatedContent(live?.toString(), transitionSpec = { Motion.fadeThrough(reduced) }, label = "source-live") { ago ->
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    InfoRow(
                        stringResource(R.string.st_source_status),
                        summary = ago?.let { stringResource(R.string.st_source_live, it) } ?: stringResource(R.string.st_source_waiting),
                        icon = DsR.drawable.ds_ic_schedule,
                    )
                }
            }
        }
    }
    when (spec?.kind) {
        SourceKind.Sensor -> {
            SettingsGroup {
                NavigationRow(
                    stringResource(R.string.st_sensor),
                    actions.openSensor,
                    summary = stringResource(R.string.st_source_sensor_summary),
                    icon = DsR.drawable.ds_ic_tune,
                )
            }
        }

        SourceKind.PhoneApp -> {
            if (sensor?.link == SourceLink.JugglucoUnreachable) {
                StateStrip(
                    DsR.drawable.ds_ic_link_off,
                    stringResource(R.string.st_sensor_juggluco_unreachable_text),
                    emptyList(),
                    warning = true,
                )
            }
            if (sensor?.link == SourceLink.JugglucoRefused) {
                StateStrip(
                    DsR.drawable.ds_ic_key,
                    stringResource(R.string.st_sensor_juggluco_refused_text),
                    listOf(stringResource(R.string.st_sensor_juggluco_secret_change) to { editingSecret = true }),
                )
            }
            val open = context.packageManager.getLaunchIntentForPackage(JUGGLUCO_PACKAGE)
            if (open != null) {
                SettingsGroup {
                    NavigationRow(
                        stringResource(R.string.st_sensor_open_juggluco),
                        { context.startActivity(open) },
                        summary = stringResource(R.string.st_source_open_juggluco_summary),
                        leading = { SourceIcon(SourceKind.PhoneApp) },
                    )
                }
            }
            JugglucoGroup(sensor?.jugglucoSecret == true) { editingSecret = true }
            if (editingSecret) {
                JugglucoSecretDialog(
                    isSet = sensor?.jugglucoSecret == true,
                    onSave = sensorActions.setJugglucoSecret,
                    onClose = { editingSecret = false },
                )
            }
        }

        SourceKind.Sukrara -> {
            streamRows()
        }

        null -> {
            Unit
        }
    }
    FooterText(stringResource(R.string.st_source_footer))
    SeeAlso(
        listOfNotNull(
            SeeAlsoLink(
                stringResource(R.string.st_sensor),
                stringResource(R.string.st_sensor_summary),
                Section.Sensor,
                actions.openSensor,
            ).takeIf { spec?.owner == true && spec.kind != SourceKind.Sensor },
            SeeAlsoLink(
                stringResource(R.string.st_source_import),
                stringResource(R.string.st_source_import_summary),
                Section.ImportExport,
                actions.openImport,
            ).takeIf { spec?.owner == true },
        ),
    )
}

/** Juggluco's package (its launcher opens it so the person can turn its web server on). */
private const val JUGGLUCO_PACKAGE = "tk.glucodata"
