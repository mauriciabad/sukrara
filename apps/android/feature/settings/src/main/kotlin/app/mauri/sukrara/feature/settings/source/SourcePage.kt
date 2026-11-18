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
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.rows.DangerRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.source.SourceIcon
import app.mauri.sukrara.designsystem.source.labelRes
import app.mauri.sukrara.designsystem.source.sourceMeans
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
import java.time.Duration
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
    /** What this phone holds for sources not in use ([AlsoSaved]), each with its removal. */
    val saved: AlsoSaved = AlsoSaved(),
    /** A follow this phone left whose wearer isn't told yet (their name, "" = unknown; null = none: PROTOCOL-v2 "Leave"). */
    val untold: String? = null,
)

/**
 * What this phone keeps for a source it doesn't read now (docs/44 Appendix G finding 12): the LibreLinkUp account (its
 * email) and the Nightscout site (its address, with its token). Null = nothing kept.
 */
data class AlsoSaved(
    val libreLinkUp: String? = null,
    val nightscout: String? = null,
) {
    val empty: Boolean get() = libreLinkUp == null && nightscout == null
}

/** Where Settings › Source leads: setup's two questions in place, and the pages beside it. */
class SourceActions(
    /** *Whose glucose*: setup's person step, then its source step (Back = unchanged). */
    val changePerson: () -> Unit = {},
    /** *Where it comes from*: setup's source step (Back = unchanged). */
    val changeSource: () -> Unit = {},
    val openSensor: () -> Unit = {},
    val openImport: () -> Unit = {},
    /** *Sign out of LibreLinkUp* (another source in use): the account is forgotten, nothing reconnects. */
    val signOutLibreLinkUp: () -> Unit = {},
    /** *Forget Nightscout site* (another source in use): the address and its token are forgotten. */
    val forgetNightscout: () -> Unit = {},
)

/**
 * Settings › Source (docs/44 "Settings"): *Whose glucose* and *Where it comes from* (each opens setup's step in place:
 * one pattern for the choice, and Back changes nothing), the state now, then the source's own rows: the sensor's page,
 * Juggluco's API secret and *Open Juggluco*, a Nightscout site's ([siteRows]: the site, its health, treatments both
 * ways), or another Sukrara's follow ([streamRows]: who, the request, the relays, Bluetooth, *Stop following*). An unanswered install says Sukrara asks once more, with *Answer*. The foot says
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
    /** An online source's rows (a Nightscout site, LibreLinkUp: account, health, …), from `:integrations`. */
    siteRows: @Composable () -> Unit = {},
) = Column(modifier) {
    val spec = state.spec
    val context = LocalContext.current
    var editingSecret by rememberSaveable { mutableStateOf(false) }
    // the follow this phone left hasn't reached the wearer yet (offline): it goes by itself once online (the Mac's words)
    state.untold?.let { wearer ->
        StateStrip(
            DsR.drawable.ds_ic_schedule,
            if (wearer.isEmpty()) stringResource(R.string.st_source_untold_unnamed) else stringResource(R.string.st_source_untold, wearer),
            emptyList(),
        )
    }
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
                spec?.let { "${stringResource(it.titleRes())}\n${sourceMeans(it)}" }
                    ?: stringResource(R.string.st_source_not_chosen),
            leading = spec?.let { s -> { SourceIcon(s.kind) } },
            icon = DsR.drawable.ds_ic_water_drop.takeIf { spec == null },
        )
        if (spec != null) {
            val reduced = LocalReducedMotion.current
            val latest = state.latest
            val ago =
                when {
                    latest == null -> {
                        stringResource(R.string.st_source_none_yet)
                    }

                    Duration.between(latest, now) < Duration.ofMinutes(1) -> {
                        stringResource(R.string.st_source_just_now)
                    }

                    else -> {
                        DateUtils
                            .getRelativeTimeSpanString(
                                latest.toEpochMilli(),
                                now.toEpochMilli(),
                                DateUtils.MINUTE_IN_MILLIS,
                            ).toString()
                    }
                }
            // the line changes by itself as readings come and minutes pass: it cross-fades; no live region (TalkBack
            // would speak every minute while the page is open), the row reads it when focused
            AnimatedContent(ago, transitionSpec = { Motion.fadeThrough(reduced) }, label = "source-last") { text ->
                InfoRow(stringResource(R.string.st_source_last), summary = text, icon = DsR.drawable.ds_ic_schedule)
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
            val open = context.packageManager.getLaunchIntentForPackage(JUGGLUCO_PACKAGE)
            // on this page the ways out are right here (Open Juggluco, Where it comes from): the strip only says why
            if (sensor?.link == SourceLink.JugglucoUnreachable) {
                StateStrip(
                    DsR.drawable.ds_ic_link_off,
                    stringResource(if (open == null) R.string.st_source_juggluco_missing else R.string.st_source_juggluco_unreachable),
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

        SourceKind.Nightscout, SourceKind.LibreLinkUp -> {
            siteRows()
        }

        SourceKind.Sukrara -> {
            streamRows()
        }

        null -> {
            Unit
        }
    }
    AlsoSavedGroup(state.saved, actions)
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

/**
 * *Also saved on this phone* (Appendix G finding 12): the accounts this phone keeps for sources it doesn't read now,
 * each removable without reconnecting it (a confirmation says what goes and what stays); hidden when there are none.
 */
@Composable
private fun AlsoSavedGroup(
    saved: AlsoSaved,
    actions: SourceActions,
) {
    if (saved.empty) return
    SettingsGroup(title = stringResource(R.string.st_source_saved_title)) {
        saved.libreLinkUp?.let { email ->
            DangerRow(
                stringResource(R.string.st_source_saved_llu),
                stringResource(R.string.st_source_saved_llu_title),
                stringResource(R.string.st_source_saved_llu_text),
                stringResource(R.string.st_source_saved_llu_action),
                actions.signOutLibreLinkUp,
                summary = email,
                icon = DsR.drawable.ds_ic_logout,
            )
        }
        saved.nightscout?.let { site ->
            DangerRow(
                stringResource(R.string.st_source_saved_ns),
                stringResource(R.string.st_source_saved_ns_title),
                stringResource(R.string.st_source_saved_ns_text),
                stringResource(R.string.st_source_saved_ns_action),
                actions.forgetNightscout,
                summary = site,
                icon = DsR.drawable.ds_ic_link_off,
            )
        }
    }
}

/** Juggluco's package (its launcher opens it so the person can turn its web server on). */
private const val JUGGLUCO_PACKAGE = "tk.glucodata"
