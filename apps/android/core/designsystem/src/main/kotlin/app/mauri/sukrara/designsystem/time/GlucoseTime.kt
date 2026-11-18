// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.time

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import app.mauri.sukrara.core.designsystem.R
import java.text.NumberFormat
import kotlin.math.roundToLong

/**
 * How much glucose there is, the one wording every screen uses (*18 hours of glucose*, *3 days of glucose*): the
 * delete-history dialog and the export count the same thing (`StoredSpan.glucoseMinutes`, `glucoseMinutes`), where
 * "1,204 readings" counted a minute's readings on one and the file's 5-minute rows on the other.
 */
object GlucoseTime {
    /** Which unit and how many for [minutes]: minutes under an hour, hours under two days, else days (rounded). */
    fun of(minutes: Long): Pair<Unit, Long> =
        when {
            minutes < 60 -> Unit.Minutes to minutes
            minutes < 48 * 60 -> Unit.Hours to (minutes / 60.0).roundToLong()
            else -> Unit.Days to (minutes / 1440.0).roundToLong()
        }

    enum class Unit { Minutes, Hours, Days }
}

/** *18 hours of glucose* for [minutes] of glucose time ([GlucoseTime]). */
@Composable
fun glucoseTime(minutes: Long): String {
    val (unit, n) = GlucoseTime.of(minutes)
    val number = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(n)
    val res =
        when (unit) {
            GlucoseTime.Unit.Minutes -> R.plurals.ds_glucose_minutes
            GlucoseTime.Unit.Hours -> R.plurals.ds_glucose_hours
            GlucoseTime.Unit.Days -> R.plurals.ds_glucose_days
        }
    return pluralStringResource(res, n.toInt(), number)
}
