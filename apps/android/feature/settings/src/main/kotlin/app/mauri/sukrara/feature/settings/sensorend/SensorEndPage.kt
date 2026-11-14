// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.sensorend

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.data.settings.SurfaceSettings
import app.mauri.sukrara.core.model.SensorEndConfig
import app.mauri.sukrara.core.model.SensorReminder
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.designsystem.dialog.DialogButtons
import app.mauri.sukrara.designsystem.dialog.DialogRadio
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.SectionButton
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.feature.settings.AlertSettingsStore
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SchemaPrefs
import app.mauri.sukrara.feature.settings.SeeAlso
import app.mauri.sukrara.feature.settings.SeeAlsoLink
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.feature.settings.graph.SensorTimeRows
import app.mauri.sukrara.feature.settings.search.Section
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.graph.GraphPlace
import app.mauri.sukrara.graph.GraphSettings
import app.mauri.sukrara.settings.SettingsSpec
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.widget.dialog.SettingsDialog
import com.android.settingslib.spa.widget.ui.SettingsBody
import com.android.settingslib.spa.widget.ui.SettingsDialogItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * Everything Settings › Sensor end shows. [sensor] = the wearer's sensor as the hub knows it; [config]'s night is THE
 * night (the night alerts' window, docs/35 §7); [sensorTime] = whether a card shows the sensor's time left
 * (`graph.sensorTime`, the default) and [sensorPlace] the places with their own (the same rows as Graph's, docs/38).
 */
data class SensorEndPrefs(
    val config: SensorEndConfig,
    val countdownHours: Int,
    val sensor: SensorState,
    val sensorTime: Boolean = true,
    val sensorPlace: Map<GraphPlace, Boolean> = emptyMap(),
)

class SensorEndViewModel(
    private val settings: SettingsRepository,
    sensor: Flow<SensorState>,
    private val alerts: AlertSettingsStore,
) : ViewModel() {
    private val sensorTime: Flow<Pair<Boolean, Map<GraphPlace, Boolean>>> =
        GraphSettings.config(settings).map { it.sensorTimeDefault to it.sensorPlace }.distinctUntilChanged()

    val prefs: StateFlow<SensorEndPrefs?> =
        combine(
            SurfaceSettings.sensorEnd(settings),
            alerts.settings.map { it.night.fromMin to it.night.toMin },
            settings.flow(SurfaceSettings.CountdownHours),
            sensor,
            sensorTime,
        ) { c, night, h, s, time ->
            SensorEndPrefs(c.withNight(night.first, night.second), h.toInt(), s, time.first, time.second)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** THE night's start or end (the night alerts' `night.from` / `to`): one time range, edited here and in Night alerts. */
    fun setNight(
        from: Int? = null,
        to: Int? = null,
    ) {
        viewModelScope.launch {
            alerts.update { s -> s.copy(night = s.night.copy(fromMin = from ?: s.night.fromMin, toMin = to ?: s.night.toMin)) }
        }
    }

    /** The sensor's time left: the default every place follows (its exceptions are on their own page). */
    fun setSensorTime(on: Boolean) {
        viewModelScope.launch { settings.set(GraphSettings.SensorTime, on) }
    }

    /** Applies [transform] to the stored settings (never to a stale copy). */
    fun change(transform: (SensorEndConfig) -> SensorEndConfig) {
        viewModelScope.launch {
            val now = SurfaceSettings.sensorEnd(settings).first()
            SurfaceSettings.setSensorEnd(settings, transform(now).normalized())
        }
    }

    fun setCountdownHours(hours: Double) {
        viewModelScope.launch { settings.set(SurfaceSettings.CountdownHours, hours) }
    }
}

/** The page's actions. */
class SensorEndActions(
    val change: ((SensorEndConfig) -> SensorEndConfig) -> Unit = {},
    val setCountdownHours: (Double) -> Unit = {},
    /** THE night's start ([from]) or end ([to]): the night alerts' window. */
    val setNight: (from: Int?, to: Int?) -> Unit = { _, _ -> },
    val setSensorTime: (Boolean) -> Unit = {},
    /** The sensor time's Exceptions page (docs/38). */
    val openSensorPlaces: () -> Unit = {},
    /** *See also*: Settings › Sensor and Alerts › Night alerts. */
    val openSensor: () -> Unit = {},
    val openNight: () -> Unit = {},
)

/** "On the end day" / "1 day before". */
@Composable
fun reminderDays(d: Int): String =
    if (d == 0) stringResource(R.string.st_sensorend_end_day) else pluralStringResource(R.plurals.st_sensorend_days_before, d, d)

/**
 * Settings › Sensor end (the wearer): this sensor's end, the reminders (rows; a row opens its dialog with Remove,
 * *Add reminder* below the list up to six), first-unlock delivery, the night (evening reminder, bedtime, wake-up:
 * Android doesn't share its Bedtime schedule), the live countdowns (end countdown and its hours, warm-up) and the
 * sensor's end in the calendar ([calendar]: null = not offered), the sensor's time left per place (its home; Graph
 * has the same switches) and *See also* Sensor and Night alerts. The night is THE night (docs/35 §7): the same time
 * range as Night alerts, edited from either page. [countdownReadOnly] = a follower not in Custom: the countdown's hours
 * and the night are mirrored (*Settings from*), shown in use under [countdownNote] (why, and the way to change it).
 */
@Composable
fun SensorEndContent(
    prefs: SensorEndPrefs,
    now: Instant,
    actions: SensorEndActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    calendar: Pair<SensorCalendarState, SensorCalendarPort>? = null,
    countdownReadOnly: Boolean = false,
    countdownNote: @Composable () -> Unit = {},
) {
    val c = prefs.config
    // null = closed; -1 = a new reminder
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var choosingHours by rememberSaveable { mutableStateOf(false) }
    var choosingAgain by rememberSaveable { mutableStateOf(false) }
    // the night's time picker: true = bedtime, false = wake-up, null = closed
    var pickingNight by rememberSaveable { mutableStateOf<Boolean?>(null) }
    SettingsPage(stringResource(R.string.st_sensorend_title), onBack, modifier) {
        SettingsGroup {
            InfoRow(
                stringResource(R.string.st_sensorend_current),
                summary = currentSensor(prefs.sensor, now),
                icon = DsR.drawable.ds_ic_dia_sensor,
            )
        }
        SettingsGroup(title = stringResource(R.string.st_sensorend_reminders)) {
            if (c.reminders.isEmpty()) InfoRow(stringResource(R.string.st_sensorend_no_reminders), icon = DsR.drawable.ds_ic_calendar_month)
            c.reminders.forEachIndexed { i, r ->
                ValueRow(
                    reminderDays(r.daysBefore),
                    ClockText.minuteOfDay(r.minuteOfDay),
                    { editing = i },
                    icon = DsR.drawable.ds_ic_calendar_month,
                )
            }
        }
        // adding is a button under the list (SettingsLib's SectionButtonPreference), not a row of it
        if (c.reminders.size < SensorEndConfig.MAX_REMINDERS) {
            SectionButton(DsR.drawable.ds_ic_add, stringResource(R.string.st_sensorend_add), { editing = -1 })
        }
        SettingsGroup {
            SwitchRow(
                stringResource(R.string.st_sensorend_unlock),
                c.unlock,
                { v -> actions.change { it.copy(unlock = v) } },
                summary = stringResource(R.string.st_sensorend_unlock_summary),
                icon = DsR.drawable.ds_ic_lock_open,
            )
        }
        // the night and the countdown's hours are mirrored: why they can't be changed, and the way to change that
        if (countdownReadOnly) countdownNote()
        SettingsGroup(title = stringResource(R.string.st_sensorend_night)) {
            val evening = (c.nightStart - EVENING_BEFORE_MIN + SensorReminder.MINUTES_PER_DAY) % SensorReminder.MINUTES_PER_DAY
            SwitchRow(
                stringResource(R.string.st_sensorend_evening),
                c.evening,
                { v -> actions.change { it.copy(evening = v) } },
                summary = stringResource(R.string.st_sensorend_evening_summary, ClockText.minuteOfDay(evening)),
                icon = DsR.drawable.ds_ic_bedtime,
            )
            ValueRow(
                stringResource(R.string.st_sensorend_bedtime),
                ClockText.minuteOfDay(c.nightStart),
                { pickingNight = true },
                enabled = c.evening || c.endedRepeatHours > 0,
                icon = DsR.drawable.ds_ic_bedtime,
                readOnly = countdownReadOnly,
            )
            ValueRow(
                stringResource(R.string.st_sensorend_wakeup),
                ClockText.minuteOfDay(c.nightEnd),
                { pickingNight = false },
                enabled = c.evening || c.endedRepeatHours > 0,
                icon = DsR.drawable.ds_ic_sunny,
                readOnly = countdownReadOnly,
            )
        }
        FooterText(stringResource(R.string.st_sensorend_night_footer))
        SettingsGroup(title = stringResource(R.string.st_sensorend_countdowns)) {
            SwitchRow(
                stringResource(R.string.st_sensorend_countdown),
                c.countdown,
                { v -> actions.change { it.copy(countdown = v) } },
                summary = stringResource(R.string.st_sensorend_countdown_summary),
                icon = DsR.drawable.ds_ic_timelapse,
            )
            ValueRow(
                stringResource(R.string.st_sensorend_countdown_hours),
                pluralStringResource(R.plurals.st_sensorend_hours_before, prefs.countdownHours, prefs.countdownHours),
                { choosingHours = true },
                enabled = c.countdown,
                icon = DsR.drawable.ds_ic_hourglass_top,
                readOnly = countdownReadOnly,
            )

            SwitchRow(
                stringResource(R.string.st_sensorend_warmup),
                c.warmup,
                { v -> actions.change { it.copy(warmup = v) } },
                summary = stringResource(R.string.st_sensorend_warmup_summary),
                icon = DsR.drawable.ds_ic_hourglass_top,
            )
        }
        SettingsGroup(title = stringResource(R.string.st_sensorend_after)) {
            ValueRow(
                stringResource(R.string.st_sensorend_again),
                againText(c.endedRepeatHours),
                { choosingAgain = true },
                icon = DsR.drawable.ds_ic_dia_sensor,
            )
        }
        // the sensor's time left on the card: its home is here, Graph shows the same rows (docs/38)
        SettingsGroup(title = stringResource(R.string.st_graph_sensor)) {
            SensorTimeRows(prefs.sensorTime, prefs.sensorPlace, actions.setSensorTime, actions.openSensorPlaces)
        }
        calendar?.let { (state, port) -> SensorCalendarGroup(state, port) }
        FooterText(stringResource(R.string.st_sensorend_footer))
        SeeAlso(
            listOf(
                SeeAlsoLink(
                    stringResource(R.string.st_sensor),
                    stringResource(R.string.st_sensor_summary),
                    Section.Sensor,
                    actions.openSensor,
                ),
                SeeAlsoLink(stringResource(R.string.st_night_title), null, Section.Alerts, actions.openNight),
            ),
        )
    }
    editing?.let { i ->
        ReminderDialog(
            c.reminders.getOrNull(i),
            onClose = { editing = null },
            onRemove = { actions.change { cfg -> cfg.copy(reminders = cfg.reminders.filterIndexed { j, _ -> j != i }) } },
        ) { r ->
            actions.change { cfg ->
                cfg.copy(reminders = if (i < 0) cfg.reminders + r else cfg.reminders.mapIndexed { j, o -> if (j == i) r else o })
            }
        }
    }
    if (choosingHours) {
        val spec = SchemaPrefs.spec("sensor.countdownHours")
        val res = LocalContext.current.resources
        NumberDialog(
            stringResource(R.string.st_sensorend_countdown_hours),
            NumberInput(spec),
            prefs.countdownHours.toDouble(),
            { res.getQuantityString(R.plurals.st_sensorend_hours_before, it.toInt(), it.toInt()) },
            { choosingHours = false },
        ) { actions.setCountdownHours(it) }
    }
    if (choosingAgain) {
        val res = LocalContext.current.resources
        NumberDialog(
            stringResource(R.string.st_sensorend_again),
            NumberInput(SettingsSpec.ENDED_REPEAT_HOURS),
            c.endedRepeatHours.toDouble(),
            { againText(res, it.toInt()) },
            { choosingAgain = false },
        ) { v -> actions.change { it.copy(endedRepeatHours = v.toInt()) } }
    }
    pickingNight?.let { bed ->
        TimeDialog(
            stringResource(if (bed) R.string.st_sensorend_bedtime else R.string.st_sensorend_wakeup),
            if (bed) c.nightStart else c.nightEnd,
            onClose = { pickingNight = null },
        ) { m ->
            // THE night (the night alerts' window): equal times would be no night, so the other end's time is ignored
            when {
                bed && m != c.nightEnd -> actions.setNight(m, null)
                !bed && m != c.nightStart -> actions.setNight(null, m)
            }
        }
    }
}

/** *Sensor ended* again: "Every 2 hours" / "Off". */
@Composable
private fun againText(hours: Int): String = againText(LocalContext.current.resources, hours)

private fun againText(
    res: android.content.res.Resources,
    hours: Int,
): String =
    if (hours <=
        0
    ) {
        res.getString(R.string.st_sensorend_again_off)
    } else {
        res.getQuantityString(R.plurals.st_sensorend_again_every, hours, hours)
    }

/** The evening reminder comes this long before bedtime (the planner's rule). */
private const val EVENING_BEFORE_MIN = 2 * 60

/** "Ends 17 Jan 2026, 14:30" / "Ended …" / "Warming up, readings at 19:45" / "No sensor yet". */
@Composable
private fun currentSensor(
    sensor: SensorState,
    now: Instant,
): String =
    when (sensor) {
        is SensorState.Active -> {
            sensor.endsAt?.let { end ->
                val at = ClockText.dateTime(end.epochSecond)
                if (end <=
                    now
                ) {
                    stringResource(R.string.st_sensorend_current_ended, at)
                } else {
                    stringResource(R.string.st_sensorend_current_ends, at)
                }
            } ?: stringResource(R.string.st_sensorend_current_unknown)
        }

        is SensorState.WarmingUp -> {
            stringResource(R.string.st_sensorend_current_warming, ClockText.time(sensor.readyAt.epochSecond))
        }

        SensorState.Ended -> {
            stringResource(R.string.st_sensorend_current_ended_now)
        }

        SensorState.None -> {
            stringResource(R.string.st_sensorend_current_none)
        }

        else -> {
            stringResource(R.string.st_sensorend_current_unknown)
        }
    }

/** The M3 time picker in a dialog (a reminder's time, bedtime, wake-up, the night alerts' hours). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(
    title: String,
    minute: Int,
    onClose: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val state = rememberTimePickerState(minute / 60, minute % 60, is24Hour = ClockText.hour24)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                onPick(state.hour * 60 + state.minute)
                onClose()
            }) { Text(stringResource(R.string.st_sensorend_ok)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.st_sensorend_cancel)) } },
    )
}

/**
 * A reminder's dialog, SPA's SettingsDialog: days before (ListPreference's radio rows), its time (a row that opens the
 * time picker), then *Remove* (an existing reminder, in the error colour), *Cancel* and *Save*.
 */
@Composable
private fun ReminderDialog(
    r: SensorReminder?,
    onClose: () -> Unit,
    onRemove: () -> Unit,
    onSave: (SensorReminder) -> Unit,
) {
    var days by rememberSaveable { mutableStateOf(r?.daysBefore ?: 1) }
    var minute by rememberSaveable { mutableStateOf(r?.minuteOfDay ?: (9 * 60)) }
    var picking by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    SettingsDialog(
        title = stringResource(if (r == null) R.string.st_sensorend_reminder_new else R.string.st_sensorend_reminder_edit),
        onDismissRequest = onClose,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.selectableGroup()) {
                for (d in 0..SensorReminder.MAX_DAYS) {
                    DialogRadio(reminderDays(d), null, d == days, enabled = true) {
                        if (d != days) haptics.tick()
                        days = d
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = SettingsDimension.paddingSmall))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { picking = true }
                    .padding(SettingsDimension.dialogItemPadding),
            ) {
                SettingsDialogItem(stringResource(R.string.st_sensorend_time))
                SettingsBody(ClockText.minuteOfDay(minute))
            }
            DialogButtons(
                dismiss = stringResource(R.string.st_sensorend_cancel),
                onDismiss = onClose,
                confirm = stringResource(R.string.st_sensorend_save),
                onConfirm = {
                    haptics.confirm()
                    onSave(SensorReminder(days, minute))
                    onClose()
                },
                other = r?.let { stringResource(R.string.st_sensorend_remove) },
                otherDanger = true,
                onOther = {
                    onRemove()
                    onClose()
                },
            )
        }
    }
    if (picking) TimeDialog(stringResource(R.string.st_sensorend_time), minute, onClose = { picking = false }) { minute = it }
}
