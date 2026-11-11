// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.content.Context
import android.text.format.DateFormat
import app.mauri.sukrara.graph.ClockText

/**
 * Every clock time Sukrara shows follows the phone's Settings › System › Date & time › *Use 24-hour format* (#166:
 * nothing installed it, so times followed the language's default). [ClockText] asks here at every time it formats, so a
 * change applies at the next draw: the glucose notification and the widgets redraw at once on Settings'
 * `ACTION_TIME_CHANGED` (`MonitorModule.onClockChanged`), the open screens when the app comes back
 * (`MainActivity`). [App] installs it first thing, also before the first unlock (the locked-boot notice writes a time).
 */
object PhoneClock {
    fun follow(context: Context) {
        val app = context.applicationContext
        ClockText.hour24Source = { DateFormat.is24HourFormat(app) }
    }
}
