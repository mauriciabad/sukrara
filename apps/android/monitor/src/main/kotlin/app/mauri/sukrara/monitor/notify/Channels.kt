// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.monitor.R
import app.mauri.sukrara.monitor.alerts.AlertChannels

/**
 * Every notification channel and group of the app, in one table.
 *
 * NEVER rename or reuse an id: Android keeps the person's choices (sound, importance, "override Do Not Disturb") per
 * channel id, so a renamed channel silently resets them to our defaults — for alert channels that can mean a muted
 * alarm the person had made loud. New behaviour gets a new id; a removed channel is deleted explicitly ([DELETED]).
 * The one exception moves the person's choices with it: an alarm channel taking Do Not Disturb's override
 * ([AlertChannels.setDndBypass], `alert_alarm.<kind>.<generation>`).
 */
object Channels {
    enum class Group(
        val id: String,
        @param:StringRes val nameRes: Int,
    ) {
        /** Glucose alerts that notify. Never shared with [INFO]: alerts are never bundled with other notifications. */
        ALERTS("alerts", R.string.monitor_group_alerts),

        /** Glucose alerts at alarm level (alarm volume, full screen). */
        ALERTS_ALARM("alerts_alarm", R.string.monitor_group_alerts_alarm),

        /** Everything that isn't an alert. */
        INFO("info", R.string.monitor_group_info),
    }

    enum class Kind { ALERT, ALARM, INFO }

    data class Spec(
        val id: String,
        @param:StringRes val nameRes: Int,
        val importance: Int,
        val group: Group,
        val kind: Kind,
        /** The alert kind (contract id) of an alert channel. */
        val alertKind: String? = null,
        /** No sound and no vibration, at whatever importance (an info channel that must still show on the lock screen). */
        val silent: Boolean = false,
    )

    const val GLUCOSE = "glucose.live"
    const val STATUS = "status"
    const val REQUESTS = "requests"
    const val SENSOR = "sensor"
    const val SENSOR_COUNTDOWN = "sensor.countdowns"
    const val PEN_RESULTS = "pen.results"
    const val PEN_CHECK = "pen.check"
    const val TREATMENT_DRAFT = "treatment.draft"

    /**
     * Channels that were replaced, deleted by [create]: `sensor.countdown` and `glucose` were low importance, which the
     * phone's "hide silent notifications" lock-screen option hides (#148, #94); a channel's importance can't be raised,
     * so [SENSOR_COUNTDOWN] and [GLUCOSE] are new ids.
     */
    val DELETED = listOf("sensor.countdown", "glucose")

    /** The alert kinds of `contracts/alerts/SPEC.md`, in its order. */
    val ALERT_KINDS =
        listOf("very_low", "low", "high", "very_high", "extremely_high", "falling_fast", "rising_fast", "no_data")

    fun alert(kind: String) = "alert.$kind"

    /**
     * [kind]'s alarm channel: `alert_alarm.<kind>`, then `alert_alarm.<kind>.2`, `.3`… each time it moves to take Do Not
     * Disturb's override that Android won't give it in place ([AlertChannels.setDndBypass], #15).
     */
    fun alarm(
        kind: String,
        generation: Int = 1,
    ) = if (generation <= 1) "alert_alarm.$kind" else "alert_alarm.$kind.$generation"

    /** [id]'s generation as [kind]'s alarm channel ([alarm]), or null when it isn't one of [kind]'s. */
    fun alarmGeneration(
        id: String,
        kind: String,
    ): Int? {
        val first = alarm(kind)
        return when {
            id == first -> 1
            id.startsWith("$first.") -> id.removePrefix("$first.").toIntOrNull()?.takeIf { it >= 2 }
            else -> null
        }
    }

    /** [kind]'s alarm channel among the app's channel [ids]: the newest generation there, else the first id. */
    fun currentAlarm(
        ids: Collection<String>,
        kind: String,
    ): String = ids.mapNotNull { id -> alarmGeneration(id, kind)?.let { it to id } }.maxByOrNull { it.first }?.second ?: alarm(kind)

    /** The id [kind]'s alarm channel [id] moves to. */
    fun nextAlarm(
        id: String,
        kind: String,
    ) = alarm(kind, (alarmGeneration(id, kind) ?: 1) + 1)

    /** An alarm channel of any kind and generation. */
    fun isAlarm(id: String) = ALERT_KINDS.any { alarmGeneration(id, it) != null }

    /** The ids of the app's channels now (Android leaves the deleted ones out). */
    fun existing(context: Context): List<String> =
        context.getSystemService(NotificationManager::class.java).notificationChannels.map { it.id }

    /** [create] and [AlertChannels.setDndBypass] one at a time: an alarm channel is never created twice. */
    val lock = Any()

    val ALL: List<Spec> =
        listOf(
            // the ongoing glucose notification: default importance so it shows on the lock screen (also with "hide
            // silent notifications" on, #94), but never a sound, a vibration or a badge (it changes every minute)
            Spec(GLUCOSE, R.string.monitor_channel_glucose, NotificationManager.IMPORTANCE_DEFAULT, Group.INFO, Kind.INFO, silent = true),
            Spec(STATUS, R.string.monitor_channel_status, NotificationManager.IMPORTANCE_HIGH, Group.INFO, Kind.INFO),
            Spec(REQUESTS, R.string.monitor_channel_requests, NotificationManager.IMPORTANCE_DEFAULT, Group.INFO, Kind.INFO),
            Spec(SENSOR, R.string.monitor_channel_sensor, NotificationManager.IMPORTANCE_HIGH, Group.INFO, Kind.INFO),
            // both sensor countdowns: default importance so they show on the lock screen (also with "hide silent
            // notifications" on), but never a sound or a vibration
            Spec(
                SENSOR_COUNTDOWN,
                R.string.monitor_channel_sensor_countdown,
                NotificationManager.IMPORTANCE_DEFAULT,
                Group.INFO,
                Kind.INFO,
                silent = true,
            ),
            Spec(PEN_RESULTS, R.string.monitor_channel_pen_results, NotificationManager.IMPORTANCE_DEFAULT, Group.INFO, Kind.INFO),
            Spec(PEN_CHECK, R.string.monitor_channel_pen_check, NotificationManager.IMPORTANCE_DEFAULT, Group.INFO, Kind.INFO),
            // *Treatment not saved* (the lock-screen countdown kept a draft): seen after the unlock, never a sound (the
            // person was just looking at the dialog)
            Spec(
                TREATMENT_DRAFT,
                R.string.monitor_channel_treatment_draft,
                NotificationManager.IMPORTANCE_DEFAULT,
                Group.INFO,
                Kind.INFO,
                silent = true,
            ),
        ) +
            ALERT_KINDS.map { Spec(alert(it), alertName(it), NotificationManager.IMPORTANCE_HIGH, Group.ALERTS, Kind.ALERT, it) } +
            ALERT_KINDS.map { Spec(alarm(it), alarmName(it), NotificationManager.IMPORTANCE_HIGH, Group.ALERTS_ALARM, Kind.ALARM, it) }

    /**
     * Creates every group and channel (idempotent: Android keeps the person's edits of an existing channel and only
     * updates its name). Alert channels always get their full setup ([AlertChannels]: the Sukrara sound, the volume's
     * usage, the kind's vibration): Android fixes it at creation, so it is never optional. An alarm channel keeps the Do
     * Not Disturb override it has, unless [alarmBypass] asks for one ([AlertChannels.setDndBypass]); a new one has none.
     * Each alarm channel under its current id ([currentAlarm]: creating an id that was moved away from would bring the
     * old channel back); an older one left behind is deleted once no notification shows on it ([dropOldAlarms]).
     */
    fun create(
        context: Context,
        alarmBypass: Boolean? = null,
    ) {
        synchronized(lock) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ids = existing(context)
            nm.createNotificationChannelGroups(Group.entries.map { NotificationChannelGroup(it.id, context.getString(it.nameRes)) })
            nm.createNotificationChannels(
                current(ids).map { spec ->
                    val bypass =
                        alarmBypass?.takeIf { spec.kind == Kind.ALARM } ?: (nm.getNotificationChannel(spec.id)?.canBypassDnd() == true)
                    channel(context, spec, bypass)
                },
            )
            DELETED.forEach(nm::deleteNotificationChannel)
            dropOldAlarms(context)
        }
    }

    /** [ALL] with each alarm channel under its current id among [ids]. */
    internal fun current(ids: Collection<String>): List<Spec> =
        ALL.map { if (it.kind == Kind.ALARM) it.copy(id = currentAlarm(ids, it.alertKind!!)) else it }

    /**
     * Deletes the alarm channels an alarm moved away from ([AlertChannels.setDndBypass]), each once no notification
     * shows on it (deleting a channel takes its notifications away: never an alert on screen). True when none is left.
     */
    fun dropOldAlarms(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ids = existing(context)
        val now = current(ids).map { it.id }.toSet()
        val old = ids.filter { isAlarm(it) && it !in now }
        if (old.isEmpty()) return true
        val showing = nm.activeNotifications.map { it.notification.channelId }.toSet()
        old.filter { it !in showing }.forEach(nm::deleteNotificationChannel)
        return old.all { it !in showing }
    }

    /**
     * Only the `glucose` and `status` channels (the service's start, before the app is ready). Never the alert
     * channels: Android fixes a channel's sound at creation, so they are created once, with their sounds, by [create].
     */
    fun createCore(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannelGroup(NotificationChannelGroup(Group.INFO.id, context.getString(Group.INFO.nameRes)))
        nm.createNotificationChannels(ALL.filter { it.id == GLUCOSE || it.id == STATUS }.map { channel(context, it) })
    }

    private fun channel(
        context: Context,
        spec: Spec,
        bypassDnd: Boolean = false,
    ) = NotificationChannel(spec.id, context.getString(spec.nameRes), spec.importance).apply {
        group = spec.group.id
        when (spec.kind) {
            Kind.INFO -> {
                setShowBadge(spec.id != GLUCOSE && spec.id != SENSOR_COUNTDOWN)
                if (spec.silent) {
                    setSound(null, null)
                    enableVibration(false)
                }
            }

            Kind.ALERT, Kind.ALARM -> {
                AlertChannels.configure(this, context, AlertKind.fromWire(spec.alertKind!!)!!, spec.kind == Kind.ALARM, bypassDnd)
            }
        }
    }

    @StringRes
    private fun alertName(kind: String): Int =
        when (kind) {
            "very_low" -> R.string.monitor_channel_alert_very_low
            "low" -> R.string.monitor_channel_alert_low
            "high" -> R.string.monitor_channel_alert_high
            "very_high" -> R.string.monitor_channel_alert_very_high
            "extremely_high" -> R.string.monitor_channel_alert_extremely_high
            "falling_fast" -> R.string.monitor_channel_alert_falling_fast
            "rising_fast" -> R.string.monitor_channel_alert_rising_fast
            "no_data" -> R.string.monitor_channel_alert_no_data
            else -> error("unknown alert kind $kind")
        }

    @StringRes
    private fun alarmName(kind: String): Int =
        when (kind) {
            "very_low" -> R.string.monitor_channel_alarm_very_low
            "low" -> R.string.monitor_channel_alarm_low
            "high" -> R.string.monitor_channel_alarm_high
            "very_high" -> R.string.monitor_channel_alarm_very_high
            "extremely_high" -> R.string.monitor_channel_alarm_extremely_high
            "falling_fast" -> R.string.monitor_channel_alarm_falling_fast
            "rising_fast" -> R.string.monitor_channel_alarm_rising_fast
            "no_data" -> R.string.monitor_channel_alarm_no_data
            else -> error("unknown alert kind $kind")
        }
}

/** Every notification id of the app in one table, so two features never post over each other. */
object NotificationIds {
    /** The ongoing glucose notification (also the monitoring service's foreground notification). */
    const val GLUCOSE = 1

    /** "Monitoring stopped — tap to resume". */
    const val MONITORING_STOPPED = 2

    /** Before the first unlock after a restart: "Unlock to resume monitoring", ringing as *No data* (lockedboot). */
    const val LOCKED_BOOT = 3
    const val SENSOR = 10
    const val SENSOR_COUNTDOWN = 11
    const val SENSOR_ENDED = 12
    const val SENSOR_WARMUP = 13
    const val SENSOR_READY = 14
    const val PEN_RESULTS = 20
    const val PEN_CHECK = 21

    /** *Treatment not saved*: what the quick dialog's lock-screen countdown closed on. */
    const val TREATMENT_DRAFT = 22
    const val REQUESTS = 30

    /** What the followed wearer said: invite used, declined, access removed. */
    const val FOLLOWING = 31

    /** Alerts: the `alerts` notification group's summary, then one slot per alert group (level, rate, no data). */
    const val ALERTS_SUMMARY = 1000
    const val ALERTS_BASE = 1001

    /** Settings › an alert › *Send a test alert*: its own slot, so a test never replaces (or is replaced by) a real alert. */
    const val ALERT_TEST = 1010

    /** The `info` notification group's summary. */
    const val INFO_SUMMARY = 1100
}
