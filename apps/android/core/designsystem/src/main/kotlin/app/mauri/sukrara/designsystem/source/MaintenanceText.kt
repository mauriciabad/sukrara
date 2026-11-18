// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.source

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.core.designsystem.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * LibreView's maintenance in a few words (docs/45), the same on home, the glucose notification and Settings › Source:
 * *LibreView maintenance until 23:00*, or without an end when Abbott's status page didn't say one. [until] is the end
 * as a clock time (`ClockText`).
 */
@Composable
fun maintenanceTitle(until: String?): String =
    until?.let { stringResource(R.string.ds_maintenance_until, it) } ?: stringResource(R.string.ds_maintenance)

/**
 * A maintenance window ahead (contracts/sources/librelinkup/status.json `upcoming`): *LibreView maintenance today,
 * 22:00–23:00: LibreLinkUp sends no glucose then*. [startClock] / [endClock] are its start and end as clock times
 * (`ClockText`); the day is *today*, *tomorrow* or its date (*Thu 3 Dec*), in this phone's zone.
 */
@Composable
fun maintenanceAheadText(
    start: Instant,
    now: Instant,
    startClock: String,
    endClock: String,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val locale = LocalConfiguration.current.locales[0]
    val day = start.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val dayText =
        when (day) {
            today -> stringResource(R.string.ds_maintenance_today)
            today.plusDays(1) -> stringResource(R.string.ds_maintenance_tomorrow)
            else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(day)
        }
    return stringResource(R.string.ds_maintenance_ahead, dayText, startClock, endClock)
}
