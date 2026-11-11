// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.monitor.alerts.AlertNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsTest {
    private val ids = Channels.ALL.map { it.id }

    @Test
    fun `every channel id exists exactly once`() {
        val expected =
            listOf("glucose.live", "status", "requests", "sensor", "sensor.countdowns", "pen.results", "pen.check", "treatment.draft") +
                Channels.ALERT_KINDS.map { "alert.$it" } + Channels.ALERT_KINDS.map { "alert_alarm.$it" }
        assertEquals(expected.sorted(), ids.sorted())
        assertEquals(ids.size, ids.toSet().size)
    }

    /** #15: an alarm channel that moves to take Do Not Disturb's override gets the next generation's id. */
    @Test
    fun `alarm channel generations`() {
        assertEquals("alert_alarm.low", Channels.alarm("low"))
        assertEquals("alert_alarm.low.2", Channels.alarm("low", 2))
        assertEquals(1, Channels.alarmGeneration("alert_alarm.low", "low"))
        assertEquals(3, Channels.alarmGeneration("alert_alarm.low.3", "low"))
        assertEquals(null, Channels.alarmGeneration("alert_alarm.very_low", "low"))
        assertEquals(null, Channels.alarmGeneration("alert.low", "low"))
        assertEquals(null, Channels.alarmGeneration("alert_alarm.low.x", "low"))
        assertEquals(null, Channels.alarmGeneration("alert_alarm.low.1", "low"))
        assertEquals("alert_alarm.low", Channels.currentAlarm(listOf("alert.low", "status"), "low"))
        assertEquals(
            "alert_alarm.low.3",
            Channels.currentAlarm(listOf("alert_alarm.low.3", "alert_alarm.low", "alert_alarm.very_low.4"), "low"),
        )
        assertEquals("alert_alarm.low.2", Channels.nextAlarm("alert_alarm.low", "low"))
        assertEquals("alert_alarm.low.4", Channels.nextAlarm("alert_alarm.low.3", "low"))
        assertTrue(Channels.isAlarm("alert_alarm.no_data.7"))
        assertTrue(!Channels.isAlarm("alert.no_data") && !Channels.isAlarm("glucose.live"))
    }

    @Test
    fun `the alert kinds are the contract's`() {
        assertEquals(
            listOf("very_low", "low", "high", "very_high", "extremely_high", "falling_fast", "rising_fast", "no_data"),
            Channels.ALERT_KINDS,
        )
    }

    @Test
    fun `alerts never share a group with other notifications`() {
        Channels.ALL.forEach { spec ->
            when (spec.kind) {
                Channels.Kind.ALERT -> assertEquals(spec.id, Channels.Group.ALERTS, spec.group)
                Channels.Kind.ALARM -> assertEquals(spec.id, Channels.Group.ALERTS_ALARM, spec.group)
                Channels.Kind.INFO -> assertEquals(spec.id, Channels.Group.INFO, spec.group)
            }
        }
        assertEquals(listOf("alerts", "alerts_alarm", "info"), Channels.Group.entries.map { it.id })
    }

    /** #94: the glucose notification shows on the lock screen (not "silent" to the system), yet never sounds. */
    @Test
    fun `alert channels are high importance, the glucose one default importance without sound, the old one deleted`() {
        Channels.ALL.filter { it.kind != Channels.Kind.INFO }.forEach { assertEquals(it.id, 4, it.importance) }
        val glucose = Channels.ALL.single { it.id == Channels.GLUCOSE }
        assertEquals(3, glucose.importance)
        assertTrue(glucose.silent)
        assertTrue("glucose" in Channels.DELETED)
    }

    /** #148: the countdowns show on the lock screen (not "silent" to the system), yet never sound or vibrate. */
    @Test
    fun `the sensor countdowns are default importance without sound or vibration, the old channel is deleted`() {
        val countdown = Channels.ALL.single { it.id == Channels.SENSOR_COUNTDOWN }
        assertEquals(3, countdown.importance)
        assertTrue(countdown.silent)
        assertEquals(
            listOf(Channels.GLUCOSE, Channels.SENSOR_COUNTDOWN, Channels.TREATMENT_DRAFT),
            Channels.ALL.filter { it.silent }.map { it.id },
        )
        assertEquals(listOf("sensor.countdown", "glucose"), Channels.DELETED)
        assertTrue(Channels.DELETED.none { it in ids })
    }

    @Test
    fun `notification ids never collide and alerts start at 1000`() {
        val alertIds = listOf(NotificationIds.ALERTS_SUMMARY) + AlertGroup.entries.map(AlertNotifier::idOf)
        assertEquals((1000..1003).toList(), alertIds)
        assertTrue(NotificationIds.INFO_SUMMARY > alertIds.max())
        val fixed =
            listOf(
                NotificationIds.GLUCOSE,
                NotificationIds.MONITORING_STOPPED,
                NotificationIds.SENSOR,
                NotificationIds.SENSOR_COUNTDOWN,
                NotificationIds.PEN_RESULTS,
                NotificationIds.PEN_CHECK,
                NotificationIds.TREATMENT_DRAFT,
                NotificationIds.REQUESTS,
            )
        assertEquals(fixed.size, fixed.toSet().size)
        assertTrue(fixed.all { it < NotificationIds.ALERTS_BASE })
    }
}
