// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.sensor

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.mauri.sukrara.designsystem.dialog.DialogButtons
import app.mauri.sukrara.designsystem.dialog.DialogRadio
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.sensorend.TimeDialog
import app.mauri.sukrara.graph.ClockText
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.widget.dialog.SettingsDialog
import com.android.settingslib.spa.widget.ui.SettingsBody
import com.android.settingslib.spa.widget.ui.SettingsDialogItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * When a sensor started, as the start dialog picks it (docs/44 decision 10): a day and a time of day. Only days on
 * which a sensor started then could still be running are offered ([days]); [check] says whether the picked moment is
 * later than now, gives a sensor that has already ended, or is a start (with its end).
 */
object StartPick {
    sealed interface Check {
        data object Future : Check

        data class Ended(val end: Instant) : Check

        data class Ok(val start: Instant, val end: Instant) : Check
    }

    /** Today back to the day a sensor of [wearMin] minutes started at the earliest and still runs at [now], newest first. */
    fun days(
        now: Instant,
        zone: ZoneId,
        wearMin: Int,
    ): List<LocalDate> {
        val today = now.atZone(zone).toLocalDate()
        val first = now.minusSeconds(wearMin * 60L).atZone(zone).toLocalDate()
        return generateSequence(today) { it.minusDays(1) }.takeWhile { !it.isBefore(first) }.toList()
    }

    fun check(
        day: LocalDate,
        minuteOfDay: Int,
        now: Instant,
        zone: ZoneId,
        wearMin: Int,
    ): Check {
        val start = day.atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)).atZone(zone).toInstant()
        val end = start.plusSeconds(wearMin * 60L)
        return when {
            start.isAfter(now) -> Check.Future
            !end.isAfter(now) -> Check.Ended(end)
            else -> Check.Ok(start, end)
        }
    }
}

/**
 * *When did this sensor start?* (the reminder dialog's pattern, SPA's SettingsDialog): the day as radio rows (*Today*,
 * *Yesterday*, then dates, back to the first day a sensor of this length could still be running), the time as a row
 * that opens THE time picker, then what it means, live: *Ends Thu 3 Dec, 09:12*, or why it can't be saved (*Later than
 * now*, *This sensor would have ended on …*: a start that ends it would keep no data quiet). *Remove* (a start the
 * person set), *Cancel*, *Save*. Opens on the set start, else now.
 */
@Composable
internal fun SensorStartDialog(
    current: Instant?,
    wearMin: Int,
    now: Instant,
    zone: ZoneId,
    onClose: () -> Unit,
    onRemove: (() -> Unit)?,
    onSave: (Instant) -> Unit,
) {
    val initial = (current ?: now).atZone(zone)
    val days = remember(now, wearMin) { StartPick.days(now, zone, wearMin) }
    var day by rememberSaveable { mutableLongStateOf(initial.toLocalDate().toEpochDay()) }
    var minute by rememberSaveable { mutableIntStateOf(initial.hour * 60 + initial.minute) }
    var picking by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    val check = StartPick.check(LocalDate.ofEpochDay(day), minute, now, zone, wearMin)
    SettingsDialog(title = stringResource(R.string.st_sensor_start_title), onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.selectableGroup()) {
                for (d in days) {
                    DialogRadio(dayLabel(d, now, zone), null, d.toEpochDay() == day, enabled = true) {
                        if (d.toEpochDay() != day) haptics.tick()
                        day = d.toEpochDay()
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
                SettingsDialogItem(stringResource(R.string.st_sensor_start_time))
                SettingsBody(ClockText.minuteOfDay(minute))
            }
            // what the pick means, said once when it changes (TalkBack), cross-fading
            AnimatedContent(check, transitionSpec = { Motion.fadeThrough(reduced) }, label = "start-check") { c ->
                val (text, error) =
                    when (c) {
                        StartPick.Check.Future -> {
                            stringResource(R.string.st_sensor_start_future) to true
                        }

                        is StartPick.Check.Ended -> {
                            stringResource(R.string.st_sensor_start_ended, dateText(c.end, zone, LocalConfiguration.current.locales[0])) to
                                true
                        }

                        is StartPick.Check.Ok -> {
                            stringResource(R.string.st_sensor_start_ends, ClockText.dateTime(c.end.epochSecond, zone = zone)) to
                                false
                        }
                    }
                Text(
                    text,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(SettingsDimension.dialogItemPadding)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            DialogButtons(
                dismiss = stringResource(R.string.st_cancel),
                onDismiss = onClose,
                confirm = stringResource(R.string.st_sensor_start_save),
                confirmEnabled = check is StartPick.Check.Ok,
                other = onRemove?.let { stringResource(R.string.st_sensor_start_remove) },
                otherDanger = true,
                onOther = {
                    onRemove?.invoke()
                    onClose()
                },
                onConfirm = {
                    (check as? StartPick.Check.Ok)?.let {
                        haptics.confirm()
                        onSave(it.start)
                        onClose()
                    }
                },
            )
        }
    }
    if (picking) {
        TimeDialog(stringResource(R.string.st_sensor_start_time), minute, onClose = { picking = false }) {
            haptics.tick()
            minute = it
        }
    }
}

/** *Today*, *Yesterday*, else the weekday and date in the phone's order (*Mon 16 Nov*). */
@Composable
private fun dayLabel(
    d: LocalDate,
    now: Instant,
    zone: ZoneId,
): String {
    val today = now.atZone(zone).toLocalDate()
    val locale = LocalConfiguration.current.locales[0]
    return when (d) {
        today -> stringResource(R.string.st_sensor_start_today)
        today.minusDays(1) -> stringResource(R.string.st_sensor_start_yesterday)
        else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(d)
    }
}

/** A date in the phone's order (*3 Nov*). */
private fun dateText(
    t: Instant,
    zone: ZoneId,
    locale: Locale,
): String = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale).format(t.atZone(zone))
