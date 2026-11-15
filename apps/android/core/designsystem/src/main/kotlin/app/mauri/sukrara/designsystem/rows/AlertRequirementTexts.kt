// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.AlertRequirement

/**
 * An alert checklist item's name: the ONE wording first-run setup's permission step and Settings › Alert reliability
 * both show (home's reliability row names the first problem in its own sentence).
 */
@StringRes
fun AlertRequirement.nameRes(): Int =
    when (this) {
        AlertRequirement.AlertShown -> R.string.ds_req_shown
        AlertRequirement.AlertDelivery -> R.string.ds_req_delivery
        AlertRequirement.Notifications -> R.string.ds_req_notifications
        AlertRequirement.FullScreenIntent -> R.string.ds_req_full_screen
        AlertRequirement.ExactAlarm -> R.string.ds_req_exact_alarm
        AlertRequirement.BatteryExemption -> R.string.ds_req_battery
        AlertRequirement.AlertChannel -> R.string.ds_req_channel
        AlertRequirement.DoNotDisturb -> R.string.ds_req_dnd
        AlertRequirement.SoundWhenUnlocked -> R.string.ds_req_unlocked
        AlertRequirement.Bluetooth -> R.string.ds_req_bluetooth
    }

/** What the item does for the person, under its [nameRes]. */
@StringRes
fun AlertRequirement.whyRes(): Int =
    when (this) {
        AlertRequirement.AlertShown -> R.string.ds_req_shown_why
        AlertRequirement.AlertDelivery -> R.string.ds_req_delivery_why
        AlertRequirement.Notifications -> R.string.ds_req_notifications_why
        AlertRequirement.FullScreenIntent -> R.string.ds_req_full_screen_why
        AlertRequirement.ExactAlarm -> R.string.ds_req_exact_alarm_why
        AlertRequirement.BatteryExemption -> R.string.ds_req_battery_why
        AlertRequirement.AlertChannel -> R.string.ds_req_channel_why
        AlertRequirement.DoNotDisturb -> R.string.ds_req_dnd_why
        AlertRequirement.SoundWhenUnlocked -> R.string.ds_req_unlocked_why
        AlertRequirement.Bluetooth -> R.string.ds_req_bluetooth_why
    }

/**
 * The item's own icon: a checklist row tells its items apart by icon (the owner, 2026-11-15: icons are there to tell
 * rows apart), its state is the row's words and colour (a missing item in the error colour), not a check repeated on
 * every row.
 */
@DrawableRes
fun AlertRequirement.iconRes(): Int =
    when (this) {
        AlertRequirement.AlertShown -> R.drawable.ds_ic_notifications_active
        AlertRequirement.AlertDelivery -> R.drawable.ds_ic_send
        AlertRequirement.Notifications -> R.drawable.ds_ic_notifications
        AlertRequirement.FullScreenIntent -> R.drawable.ds_ic_screen_lock_portrait
        AlertRequirement.ExactAlarm -> R.drawable.ds_ic_schedule
        AlertRequirement.BatteryExemption -> R.drawable.ds_ic_battery_full
        AlertRequirement.AlertChannel -> R.drawable.ds_ic_volume_up
        AlertRequirement.DoNotDisturb -> R.drawable.ds_ic_bedtime
        AlertRequirement.SoundWhenUnlocked -> R.drawable.ds_ic_lock_open
        AlertRequirement.Bluetooth -> R.drawable.ds_ic_bluetooth
    }
