// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readiness

import android.app.NotificationManager
import app.mauri.sukrara.core.model.DndStatus
import app.mauri.sukrara.monitor.alerts.AlertCategory

/**
 * Where Sukrara stands with Do Not Disturb (#184), as plain rules with JVM tests: whether it is an exception
 * ([through]) and what a mode on now holds back ([held]).
 *
 * By default Sukrara respects every mode as Android applies it: no override, no sound of its own around a mode. The
 * alerts that ring like an alarm still ring where the mode lets alarms through (the person's choice in the mode); the
 * others pass where it lets reminders through. Letting Sukrara through is the person's choice, through Android's own
 * exception: the alarm channels' *Override Do Not Disturb* (a channel that bypasses is one of the mode's apps that
 * can interrupt; Sukrara can set it only with Do Not Disturb access). Even then a mode without alarms mutes the alarm
 * volume (AudioService), so such an alert shows and vibrates without a sound (#15).
 */
object DndHold {
    /** One alert channel in use, as Do Not Disturb sees it. */
    data class Channel(
        /** `alert_alarm.*`: `USAGE_ALARM`, `CATEGORY_ALARM`. */
        val alarm: Boolean,
        /** Its *Override Do Not Disturb*. */
        val bypass: Boolean,
    )

    /** Sukrara is an exception: every alarm channel in use (or, with none in use, every alarm channel) overrides. */
    fun through(alarmChannelsBypass: List<Boolean>): Boolean = alarmChannelsBypass.isNotEmpty() && alarmChannelsBypass.all { it }

    /**
     * Whether an alert on [channel] makes its sound under [dnd] (null = Android couldn't say: no claim, it sounds).
     * Alarms only: only the alarm-level ones. Priority: an alarm-level one where the mode lets alarms through (its
     * override alone gets it shown, not heard); a notification-volume one where reminders pass (it posts as a reminder
     * there, [AlertCategory]) or its channel overrides Do Not Disturb.
     */
    fun sounds(
        channel: Channel,
        dnd: AlertCategory.Dnd?,
    ): Boolean =
        when (dnd?.filter) {
            null, NotificationManager.INTERRUPTION_FILTER_ALL -> {
                true
            }

            NotificationManager.INTERRUPTION_FILTER_ALARMS -> {
                channel.alarm
            }

            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> {
                if (channel.alarm) {
                    dnd.allowsAlarms
                } else {
                    dnd.allowsReminders || channel.bypass
                }
            }

            else -> {
                false
            }
        }

    /** What a mode on now holds back among the alerts in use ([channels]). */
    fun held(
        channels: List<Channel>,
        dnd: AlertCategory.Dnd?,
    ): DndStatus.Held {
        val silent = channels.count { !sounds(it, dnd) }
        return when {
            silent == 0 -> DndStatus.Held.None
            silent == channels.size -> DndStatus.Held.All
            else -> DndStatus.Held.Some
        }
    }
}
