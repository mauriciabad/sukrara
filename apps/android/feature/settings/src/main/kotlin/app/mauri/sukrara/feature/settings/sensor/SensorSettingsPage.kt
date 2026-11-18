// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.sensor

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.core.model.SensorAction
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.ServiceIcon
import app.mauri.sukrara.designsystem.rows.DangerRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.RadioRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SplitSwitchRow
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.source.titleRes
import app.mauri.sukrara.feature.settings.BlockedTapsKind
import app.mauri.sukrara.feature.settings.ConfirmDialog
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SchemaPrefs
import app.mauri.sukrara.feature.settings.SeeAlso
import app.mauri.sukrara.feature.settings.SeeAlsoLink
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.feature.settings.StateStrip
import app.mauri.sukrara.feature.settings.TapsBlockedCard
import app.mauri.sukrara.feature.settings.search.Section
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.ClockText
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import app.mauri.sukrara.core.designsystem.R as DsR

/** Where the page's buttons lead: the sensor feature's new-sensor flow and the LibreView account screen. */
class SensorSettingsActions(
    val pairNewSensor: () -> Unit = {},
    /** The LibreView account screen: the sign-in, or who is signed in with *Sign out*. */
    val signIn: () -> Unit = {},
    val setSource: (SourceChoice) -> Unit = {},
    val setOpenOnScan: (Boolean) -> Unit = {},
    val forget: () -> Unit = {},
    val allowTaps: () -> Unit = {},
    val resume: () -> Unit = {},
    val setJugglucoSecret: (String) -> Unit = {},
    /** Settings › Sensor end (docs/35 §7: its own overview row stays, and Sensor links to it). */
    val openSensorEnd: () -> Unit = {},
    /** Settings › Source: where the glucose comes from (the sensor read here, or Juggluco) is chosen there. */
    val openSource: () -> Unit = {},
    /** The source's wear length (minutes). */
    val setWear: (Int) -> Unit = {},
    /** Your sensor's start set by hand; null removes it. */
    val setStart: (Instant?) -> Unit = {},
)

/** Settings › Sensor with its ViewModel; the "ends in" time moves every minute. */
@Composable
fun SensorSettingsScreen(
    viewModel: SensorSettingsViewModel,
    clock: Clock,
    onPairNewSensor: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    onSensorEnd: () -> Unit = {},
    onSource: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // back from Android's app page (where NFC taps are allowed again): read it again
    LifecycleResumeEffect(viewModel) {
        viewModel.recheck()
        onPauseOrDispose {}
    }
    val now by produceState(clock.now()) {
        while (true) {
            delay(60_000)
            value = clock.now()
        }
    }
    SensorSettingsContent(
        state,
        now,
        clock.zone,
        SensorSettingsActions(
            onPairNewSensor,
            onSignIn,
            viewModel::setSource,
            viewModel::setOpenOnScan,
            viewModel::forget,
            viewModel::allowTaps,
            viewModel::resume,
            viewModel::setJugglucoSecret,
            onSensorEnd,
            onSource,
            viewModel::setWear,
            viewModel::setStart,
        ),
        onBack,
    )
}

/**
 * The current sensor and its state, the reading source (with a plain confirmation of what changes), the LibreView
 * account (who is signed in and the receiver ID; the row opens the one account screen) and Pair new sensor. Every
 * problem state shows its way out.
 */
@Composable
fun SensorSettingsContent(
    state: SensorSettingsState?,
    now: Instant,
    zone: ZoneId,
    actions: SensorSettingsActions,
    onBack: () -> Unit,
) {
    SettingsPage(stringResource(R.string.st_sensor), onBack) {
        if (state == null) return@SettingsPage
        var confirming by rememberSaveable { mutableStateOf<SourceChoice?>(null) }
        var editingSecret by rememberSaveable { mutableStateOf(false) }
        var editingStart by rememberSaveable { mutableStateOf(false) }
        var editingWear by rememberSaveable { mutableStateOf(false) }
        val life = state.life
        if (state.online) {
            OnlineNotice(state) { editingStart = true }
        } else {
            StateNotice(state, actions, editSecret = { editingSecret = true }) { confirming = SourceChoice.Juggluco }
        }
        // Android blocks the sensor taps while *Open Sukrara when you scan a sensor* is on: the pens page's card
        if (state.source == SourceChoice.Libre3Direct && state.openOnScan?.let { it.on && it.blocked } == true) {
            TapsBlockedCard(BlockedTapsKind.Sensor, actions.allowTaps)
        }
        SensorGroup(state, now, zone, actions, editStart = { editingStart = true }, editWear = { editingWear = true })
        // a Nightscout site: no sensor is paired or started on this phone, no LibreView account is needed
        val nightscout = state.spec?.kind == SourceKind.Nightscout
        if (state.source == SourceChoice.Libre3Direct && !nightscout) ThisPhoneGroup(state, actions)
        if (!nightscout) AccountGroup(state.account, actions)
        if (life != null && editingStart && life.canSetStart) {
            SensorStartDialog(
                current = life.start.takeIf { life.manual },
                wearMin = life.wearMin,
                now = now,
                zone = zone,
                onClose = { editingStart = false },
                onRemove = { actions.setStart(null) }.takeIf { life.manual },
            ) { actions.setStart(it) }
        }
        if (life != null && editingWear) {
            val spec = SchemaPrefs.spec(life.wearSetting)
            val libre = spec.presets.size == 2 && !spec.custom
            val res = LocalContext.current.resources
            NumberDialog(
                stringResource(R.string.st_sensor_lasts_title),
                NumberInput(spec),
                life.wearMin.toDouble(),
                { days(res, it) },
                { editingWear = false },
                sub = { wearHint(res, it, libre) },
                live = { res.getString(R.string.st_sensor_lasts_live, days(res, it)) },
            ) { actions.setWear(it.toInt()) }
        }
        if (editingSecret) {
            JugglucoSecretDialog(
                isSet = state.jugglucoSecret == true,
                onSave = actions.setJugglucoSecret,
                onClose = { editingSecret = false },
            )
        }
        // what the source doesn't say, said once
        if (life != null) state.spec?.let { FooterText(stringResource(R.string.st_sensor_life_footer, serviceName(it.kind))) }
        if (!nightscout) FooterText(stringResource(R.string.st_sensor_footer))
        SeeAlso(
            listOf(
                SeeAlsoLink(
                    stringResource(R.string.st_source_title),
                    stringResource(R.string.st_source_summary),
                    Section.Source,
                    actions.openSource,
                ),
                SeeAlsoLink(
                    stringResource(R.string.st_sensorend_title),
                    stringResource(R.string.st_sensorend_link_summary),
                    Section.SensorEnd,
                    actions.openSensorEnd,
                ),
            ),
        )
        confirming?.let { target ->
            val toJuggluco = target == SourceChoice.Juggluco
            ConfirmDialog(
                title = stringResource(if (toJuggluco) R.string.st_sensor_to_juggluco_title else R.string.st_sensor_to_direct_title),
                text = stringResource(if (toJuggluco) R.string.st_sensor_to_juggluco_text else R.string.st_sensor_to_direct_text),
                confirm = stringResource(if (toJuggluco) R.string.st_sensor_to_juggluco_confirm else R.string.st_sensor_to_direct_confirm),
                onClose = { confirming = null },
                onConfirm = { actions.setSource(target) },
            )
        }
    }
}

/** A state that needs the person: what's wrong and the button that fixes it ([useJuggluco] asks to switch first). */
@Composable
private fun StateNotice(
    state: SensorSettingsState,
    actions: SensorSettingsActions,
    editSecret: () -> Unit,
    useJuggluco: () -> Unit,
) {
    val context = LocalContext.current
    val pair = stringResource(R.string.st_sensor_pair_new) to actions.pairNewSensor
    if (state.stopped) {
        StateStrip(
            DsR.drawable.ds_ic_pause_circle,
            stringResource(R.string.st_sensor_stopped_text),
            listOf(stringResource(R.string.st_sensor_resume) to actions.resume),
        )
        return
    }
    if (state.link == SourceLink.JugglucoRefused) {
        StateStrip(
            DsR.drawable.ds_ic_key,
            stringResource(R.string.st_sensor_juggluco_refused_text),
            listOf(stringResource(R.string.st_sensor_juggluco_secret_change) to editSecret),
        )
        return
    }
    if (state.link == SourceLink.JugglucoUnreachable) {
        val open = context.packageManager.getLaunchIntentForPackage(JUGGLUCO_PACKAGE)
        val openJuggluco = stringResource(R.string.st_sensor_open_juggluco) to { open?.let(context::startActivity) ?: Unit }
        StateStrip(
            DsR.drawable.ds_ic_link_off,
            stringResource(R.string.st_sensor_juggluco_unreachable_text),
            listOfNotNull(openJuggluco.takeIf { open != null }),
            warning = true,
        )
        return
    }
    val skipped = state.family?.skippedOld ?: 0
    val running = state.state is SensorState.Active || state.state is SensorState.WarmingUp
    if (running && state.link == SourceLink.Searching && skipped > 0) {
        val text = pluralStringResource(R.plurals.st_sensor_skipped_old, skipped, skipped, state.family?.label.orEmpty())
        StateStrip(DsR.drawable.ds_ic_dia_sensor, text, listOf(pair))
        return
    }
    if (running && state.link == SourceLink.NoValueSensorError) {
        StateStrip(DsR.drawable.ds_ic_error, stringResource(R.string.st_sensor_status_no_value_error), listOf(pair), warning = true)
        return
    }
    when (val s = state.state) {
        SensorState.None -> {
            if (state.source ==
                SourceChoice.Libre3Direct
            ) {
                StateStrip(DsR.drawable.ds_ic_dia_sensor, stringResource(R.string.st_sensor_none_text), listOf(pair))
            }
        }

        SensorState.Ended -> {
            StateStrip(DsR.drawable.ds_ic_dia_sensor, stringResource(R.string.st_sensor_ended_text), listOf(pair))
        }

        is SensorState.ActionNeeded -> {
            when (s.action) {
                SensorAction.SignInLibreView -> {
                    StateStrip(
                        DsR.drawable.ds_ic_account_circle,
                        stringResource(R.string.st_sensor_sign_in_needed),
                        listOf(stringResource(R.string.st_sensor_sign_in) to actions.signIn),
                    )
                }

                SensorAction.GrantBluetooth -> {
                    StateStrip(
                        DsR.drawable.ds_ic_bluetooth,
                        stringResource(R.string.st_sensor_bluetooth_permission_text),
                        listOf(pair),
                        warning = true,
                    )
                }

                SensorAction.ScanSensor -> {
                    StateStrip(DsR.drawable.ds_ic_nfc, stringResource(R.string.st_sensor_scan_again_text), listOf(pair), warning = true)
                }

                SensorAction.TakeOver -> {
                    StateStrip(
                        DsR.drawable.ds_ic_mobile_off,
                        stringResource(R.string.st_sensor_taken_over_text),
                        listOf(pair),
                        warning = true,
                    )
                }

                SensorAction.PairAgain -> {
                    StateStrip(
                        DsR.drawable.ds_ic_dia_sensor,
                        stringResource(R.string.st_sensor_pair_again_text),
                        listOf(pair),
                        warning = true,
                    )
                }
            }
        }

        SensorState.BluetoothOff -> {
            StateStrip(
                DsR.drawable.ds_ic_bluetooth,
                stringResource(R.string.st_sensor_bluetooth_off_text),
                listOf(
                    stringResource(R.string.st_sensor_open_bluetooth) to
                        { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                ),
            )
        }

        SensorState.CoreMissing -> {
            if (state.source == SourceChoice.Libre3Direct) {
                StateStrip(
                    DsR.drawable.ds_ic_warning,
                    stringResource(R.string.st_sensor_core_missing_text),
                    listOf(stringResource(R.string.st_sensor_to_juggluco_confirm) to useJuggluco),
                )
            }
        }

        is SensorState.WarmingUp, is SensorState.Active -> {
            Unit
        }
    }
}

/** Juggluco's package (its launcher opens it so the person can turn its web server on). */
private const val JUGGLUCO_PACKAGE = "tk.glucodata"

/**
 * An online source's sensor that needs the person: its start isn't known (*Set start*, where they may set it: a
 * Nightscout site without a *Sensor Start*), or it ended (a new one's start, or where it's started: its app).
 */
@Composable
private fun OnlineNotice(
    state: SensorSettingsState,
    setStart: () -> Unit,
) {
    val life = state.life ?: return
    val name = state.spec?.let { serviceName(it.kind) }.orEmpty()
    val set = stringResource(R.string.st_sensor_start_set) to setStart
    when {
        state.state == SensorState.Ended && life.canSetStart -> {
            StateStrip(DsR.drawable.ds_ic_dia_sensor, stringResource(R.string.st_sensor_ended_manual), listOf(set))
        }

        state.state == SensorState.Ended -> {
            StateStrip(DsR.drawable.ds_ic_dia_sensor, stringResource(R.string.st_sensor_ended_elsewhere, name), emptyList())
        }

        life.start == null && life.canSetStart -> {
            StateStrip(DsR.drawable.ds_ic_calendar_month, stringResource(R.string.st_sensor_start_unknown_text, name), listOf(set))
        }
    }
}

/**
 * The sensor's life where the source doesn't say all of it (docs/44 decision 10): *Started* (from the source, or set
 * by you; a row that opens the start dialog where you may set it), *Lasts* (the source's wear length: THE number
 * input) and *Ends*.
 */
@Composable
private fun LifeRowsGroup(
    state: SensorSettingsState,
    life: LifeRows,
    now: Instant,
    zone: ZoneId,
    editStart: () -> Unit,
    editWear: () -> Unit,
) {
    val res = LocalContext.current.resources
    val name = state.spec?.let { serviceName(it.kind) }.orEmpty()
    val started =
        when {
            life.start == null -> stringResource(R.string.st_sensor_start_none)
            life.manual -> stringResource(R.string.st_sensor_start_by_you, ClockText.dateTime(life.start.epochSecond, zone = zone))
            else -> stringResource(R.string.st_sensor_start_from, ClockText.dateTime(life.start.epochSecond, zone = zone), name)
        }
    if (life.canSetStart) {
        NavigationRow(stringResource(R.string.st_sensor_started), editStart, summary = started, icon = DsR.drawable.ds_ic_calendar_month)
    } else {
        InfoRow(stringResource(R.string.st_sensor_started), summary = started, icon = DsR.drawable.ds_ic_calendar_month)
    }
    NavigationRow(
        stringResource(R.string.st_sensor_lasts),
        editWear,
        summary = days(res, life.wearMin.toDouble()),
        icon = DsR.drawable.ds_ic_timelapse,
    )
    life.end?.let { end ->
        val endsAt = ClockText.dateTime(end.epochSecond, zone = zone)
        val left = Duration.between(now, end).toMinutes()
        val running = state.state is SensorState.Active || state.state is SensorState.WarmingUp
        val words = CardWords.of(res).durations
        InfoRow(
            stringResource(R.string.st_sensor_ends),
            icon = DsR.drawable.ds_ic_hourglass_top,
            summary = if (running && left > 0) stringResource(R.string.st_sensor_ends_in, endsAt, Durations.whole(left, words)) else endsAt,
        )
    }
}

/** A wear length in days (minutes stored): *15 days*. */
internal fun days(
    res: android.content.res.Resources,
    minutes: Double,
): String = (minutes / MIN_PER_DAY).toInt().let { res.getQuantityString(R.plurals.st_sensor_days, it, it) }

/** Which sensors last that long, under an option: helps pick without knowing the number. */
internal fun wearHint(
    res: android.content.res.Resources,
    minutes: Double,
    libre: Boolean,
): String? =
    when ((minutes / MIN_PER_DAY).toInt()) {
        10 -> res.getString(R.string.st_sensor_lasts_10)
        14 -> res.getString(if (libre) R.string.st_sensor_lasts_14_libre else R.string.st_sensor_lasts_14)
        15 -> res.getString(if (libre) R.string.st_sensor_lasts_15_libre else R.string.st_sensor_lasts_15)
        else -> null
    }

private const val MIN_PER_DAY = 1440.0

/** The service a source's sensor life comes from, by its own name (not translated). */
private fun serviceName(kind: SourceKind): String =
    when (kind) {
        SourceKind.Nightscout -> "Nightscout"
        SourceKind.LibreLinkUp -> "LibreLinkUp"
        SourceKind.PhoneApp -> "Juggluco"
        else -> "Sukrara"
    }

@Composable
private fun SensorGroup(
    state: SensorSettingsState,
    now: Instant,
    zone: ZoneId,
    actions: SensorSettingsActions,
    editStart: () -> Unit,
    editWear: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.st_sensor_current)) {
        // the active source, plainly: what Sukrara reads glucose from right now (Settings › Source changes it)
        NavigationRow(
            stringResource(R.string.st_sensor_reading_from),
            actions.openSource,
            summary =
                state.spec?.takeIf { state.online }?.let { stringResource(it.titleRes()) } ?: stringResource(
                    if (state.source ==
                        SourceChoice.Juggluco
                    ) {
                        R.string.st_sensor_reading_from_juggluco
                    } else {
                        R.string.st_sensor_reading_from_direct
                    },
                ),
            // the glucose drop: the sensor icon is the Sensor row's under it (no icon twice on a page)
            icon = DsR.drawable.ds_ic_water_drop,
        )
        state.family?.let { f ->
            InfoRow(
                stringResource(R.string.st_sensor_family),
                summary = if (f.untested) stringResource(R.string.st_sensor_family_untested, f.label) else f.label,
                icon = DsR.drawable.ds_ic_dia_sensor,
            )
        }
        InfoRow(stringResource(R.string.st_sensor_status), summary = statusText(state, zone), icon = DsR.drawable.ds_ic_info)
        val life = state.life
        if (life != null) {
            LifeRowsGroup(state, life, now, zone, editStart, editWear)
        }
        state.sensor?.takeIf { life == null }?.let { sensor ->
            InfoRow(stringResource(R.string.st_sensor_serial), summary = sensor.serial, icon = DsR.drawable.ds_ic_edit_note)
            InfoRow(
                stringResource(R.string.st_sensor_started),
                summary = ClockText.dateTime(sensor.startedAt.epochSecond, zone = zone),
                icon = DsR.drawable.ds_ic_calendar_month,
            )
            val endsAt = ClockText.dateTime(sensor.endsAt.epochSecond, zone = zone)
            // time left only for a running sensor: an ended one (also ended early) has none
            val left = Duration.between(now, sensor.endsAt).toMinutes()
            val running = state.state is SensorState.Active || state.state is SensorState.WarmingUp
            val words = CardWords.of(LocalContext.current.resources).durations
            InfoRow(
                stringResource(R.string.st_sensor_ends),
                icon = DsR.drawable.ds_ic_hourglass_top,
                summary =
                    if (running &&
                        left > 0
                    ) {
                        stringResource(R.string.st_sensor_ends_in, endsAt, Durations.whole(left, words))
                    } else {
                        endsAt
                    },
            )
        }
        // a Nightscout site's sensor is started in its own app (its start: the Started row)
        if (state.spec?.kind != SourceKind.Nightscout) {
            PairRow(state, actions)
        }
    }
}

/** *Pair new sensor* (LibreLinkUp: the takeover, from the Libre app to Sukrara). */
@Composable
private fun PairRow(
    state: SensorSettingsState,
    actions: SensorSettingsActions,
) {
    NavigationRow(
        stringResource(R.string.st_sensor_pair_new),
        actions.pairNewSensor,
        icon = DsR.drawable.ds_ic_add,
        summary =
            stringResource(
                if (state.source ==
                    SourceChoice.Juggluco
                ) {
                    R.string.st_sensor_pair_new_juggluco
                } else {
                    R.string.st_sensor_pair_new_summary
                },
            ),
    )
}

/**
 * What this phone does with the sensor: open Sukrara when a sensor is scanned (NFC; blocked by Android → a warning
 * row that opens Android's screen allowing the taps, its switch still turns it off) and *Forget this sensor* (asks
 * first).
 */
@Composable
private fun ThisPhoneGroup(
    state: SensorSettingsState,
    actions: SensorSettingsActions,
) {
    val scan = state.openOnScan
    if (scan == null && !state.paired) return
    SettingsGroup(title = stringResource(R.string.st_sensor_this_phone)) {
        scan?.let {
            if (it.on && it.blocked) {
                // a warning row: the row opens Android's screen, the switch still turns it off
                SplitSwitchRow(
                    stringResource(R.string.st_sensor_open_on_scan),
                    true,
                    actions.setOpenOnScan,
                    actions.allowTaps,
                    summary = stringResource(R.string.st_sensor_open_on_scan_blocked),
                    icon = DsR.drawable.ds_ic_warning,
                    warning = true,
                )
            } else {
                SwitchRow(
                    stringResource(R.string.st_sensor_open_on_scan),
                    it.on,
                    actions.setOpenOnScan,
                    summary = stringResource(R.string.st_sensor_open_on_scan_summary),
                    icon = DsR.drawable.ds_ic_nfc,
                )
            }
        }
        if (state.paired) {
            DangerRow(
                title = stringResource(R.string.st_sensor_forget),
                confirmTitle = stringResource(R.string.st_sensor_forget_title),
                confirmText = stringResource(R.string.st_sensor_forget_text),
                confirmAction = stringResource(R.string.st_sensor_forget_confirm),
                onConfirm = actions.forget,
                summary = stringResource(R.string.st_sensor_forget_summary),
                icon = DsR.drawable.ds_ic_link_off,
            )
        }
    }
}

/** The sensor's state in words, for the Status row. */
@Composable
private fun statusText(
    state: SensorSettingsState,
    zone: ZoneId,
): String {
    if (state.stopped) return stringResource(R.string.st_sensor_status_stopped)
    if (state.online) {
        // the source's sensor (the hub's state): no link of this phone's own to speak of
        return when (val s = state.state) {
            is SensorState.WarmingUp -> {
                stringResource(R.string.st_sensor_status_warming, ClockText.time(s.readyAt.epochSecond, zone = zone))
            }

            SensorState.Ended -> {
                stringResource(R.string.st_sensor_status_ended)
            }

            is SensorState.Active -> {
                stringResource(
                    if (s.endsAt ==
                        null
                    ) {
                        R.string.st_sensor_status_unknown
                    } else {
                        R.string.st_sensor_status_running
                    },
                )
            }

            else -> {
                stringResource(R.string.st_sensor_status_unknown)
            }
        }
    }
    return when (val s = state.state) {
        SensorState.None -> {
            stringResource(
                if (state.source ==
                    SourceChoice.Juggluco
                ) {
                    R.string.st_sensor_status_waiting_juggluco
                } else {
                    R.string.st_sensor_status_none
                },
            )
        }

        is SensorState.WarmingUp -> {
            stringResource(R.string.st_sensor_status_warming, ClockText.time(s.readyAt.epochSecond, zone = zone))
        }

        is SensorState.Active -> {
            when (state.link) {
                SourceLink.JugglucoUnreachable -> {
                    stringResource(R.string.st_sensor_status_juggluco_unreachable)
                }

                SourceLink.JugglucoRefused -> {
                    stringResource(R.string.st_sensor_status_juggluco_refused)
                }

                SourceLink.Connecting -> {
                    stringResource(
                        if (state.source ==
                            SourceChoice.Juggluco
                        ) {
                            R.string.st_sensor_status_connecting_juggluco
                        } else {
                            R.string.st_sensor_status_connecting
                        },
                    )
                }

                SourceLink.Searching -> {
                    stringResource(R.string.st_sensor_status_searching)
                }

                SourceLink.NoValueCalibration -> {
                    stringResource(R.string.st_sensor_status_no_value_calibration)
                }

                SourceLink.NoValueTemporary -> {
                    stringResource(R.string.st_sensor_status_no_value_temporary)
                }

                SourceLink.NoValueSensorError -> {
                    stringResource(R.string.st_sensor_status_no_value_error)
                }

                SourceLink.NoValueMissing -> {
                    stringResource(R.string.st_sensor_status_no_value_missing)
                }

                SourceLink.Receiving -> {
                    stringResource(
                        if (state.source ==
                            SourceChoice.Juggluco
                        ) {
                            R.string.st_sensor_status_juggluco
                        } else {
                            R.string.st_sensor_status_active
                        },
                    )
                }
            }
        }

        SensorState.Ended -> {
            stringResource(R.string.st_sensor_status_ended)
        }

        SensorState.CoreMissing -> {
            stringResource(R.string.st_sensor_status_core_missing)
        }

        SensorState.BluetoothOff -> {
            stringResource(R.string.st_sensor_status_bluetooth_off)
        }

        is SensorState.ActionNeeded -> {
            stringResource(
                when (s.action) {
                    SensorAction.ScanSensor -> R.string.st_sensor_status_scan_again
                    SensorAction.TakeOver -> R.string.st_sensor_status_taken_over
                    SensorAction.PairAgain -> R.string.st_sensor_status_pair_again
                    SensorAction.SignInLibreView -> R.string.st_sensor_status_sign_in
                    SensorAction.GrantBluetooth -> R.string.st_sensor_status_permission
                },
            )
        }
    }
}

/** Juggluco's web-server API secret: *Set* / *Not set* (never shown), the row opens [JugglucoSecretDialog] (Settings › Source). */
@Composable
internal fun JugglucoGroup(
    isSet: Boolean,
    onEdit: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.st_sensor_juggluco)) {
        NavigationRow(
            stringResource(R.string.st_sensor_juggluco_secret),
            onEdit,
            icon = DsR.drawable.ds_ic_key,
            summary = stringResource(if (isSet) R.string.st_sensor_juggluco_secret_set else R.string.st_sensor_juggluco_secret_none),
        )
    }
    FooterText(stringResource(R.string.st_sensor_juggluco_secret_footer))
}

/**
 * Types Juggluco's API secret (hidden; never kept in saved state, a rotation asks again): *Save* (blank = none),
 * *Remove* while one is saved, *Cancel* changes nothing.
 */
@Composable
internal fun JugglucoSecretDialog(
    isSet: Boolean,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.st_sensor_juggluco_secret)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.st_sensor_juggluco_secret_dialog_text))
                OutlinedTextField(
                    typed,
                    { typed = it },
                    label = { Text(stringResource(R.string.st_sensor_juggluco_secret)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(typed)
                    onClose()
                },
                enabled = typed.isNotBlank(),
            ) { Text(stringResource(R.string.st_sensor_juggluco_secret_save)) }
        },
        dismissButton = {
            Row {
                if (isSet) {
                    TextButton(onClick = {
                        onSave("")
                        onClose()
                    }) { Text(stringResource(R.string.st_sensor_juggluco_secret_remove)) }
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.st_cancel)) }
            }
        },
    )
}

@Composable
private fun AccountGroup(
    account: LibreViewSummary?,
    actions: SensorSettingsActions,
) {
    SettingsGroup(title = stringResource(R.string.st_sensor_libreview)) {
        if (account == null) {
            NavigationRow(
                stringResource(R.string.st_sensor_sign_in),
                actions.signIn,
                summary = stringResource(R.string.st_sensor_signed_out_text),
                icon = DsR.drawable.ds_ic_account_circle,
            )
        } else {
            // THE account screen (shared with the LibreView upload): who is signed in, *Sign out*, or the sign-in again
            NavigationRow(
                stringResource(if (account.signInAgain) R.string.st_sensor_sign_in_again else R.string.st_sensor_signed_in_as),
                actions.signIn,
                summary = account.email.ifEmpty { null },
                icon = DsR.drawable.ds_ic_account_circle,
            )
            account.receiverId?.let {
                InfoRow(
                    stringResource(R.string.st_sensor_receiver_id),
                    summary = it.toString(),
                    icon = DsR.drawable.ds_ic_receipt_long,
                )
            }
        }
    }
}
