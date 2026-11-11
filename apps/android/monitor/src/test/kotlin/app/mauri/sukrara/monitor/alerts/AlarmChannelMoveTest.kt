// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.monitor.notify.Channels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * #15: once Sukrara has Do Not Disturb access, an alarm channel the person edited (Android won't give it the override
 * in place) moves to its next id with every choice they made, and the old one goes once nothing shows on it.
 * Robolectric keeps an existing channel's override as it was, as Android does for an edited one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlarmChannelMoveTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val veryLow = Channels.alarm("very_low")
    private val ringtone = "content://media/internal/audio/media/42".toUri()
    private val pattern = longArrayOf(0, 300, 200, 300)

    @Before fun setUp() {
        ctx.applicationInfo.icon = android.R.drawable.star_on
    }

    /** Very low's alarm channel as the person left it: their ringtone, their vibration, no pop-up, no override. */
    private fun edited(id: String = veryLow) =
        nm.createNotificationChannel(
            NotificationChannel(id, "Very low", NotificationManager.IMPORTANCE_DEFAULT).apply {
                group = Channels.Group.ALERTS_ALARM.id
                setSound(ringtone, AlertChannels.audioAttributes(alarm = true))
                vibrationPattern = pattern
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(true)
            },
        )

    private fun move() = AlertChannels.moveToDndBypass(ctx) { it != "content://elsewhere/unreadable".toUri() }

    @Test fun anEditedAlarmChannelMovesWithEverythingThePersonChose() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        edited()

        val move = move()

        assertEquals(setOf(AlertKind.VERY_LOW), move.moved)
        val now = nm.getNotificationChannel("alert_alarm.very_low.2")
        assertNotNull(now)
        assertTrue(now.canBypassDnd())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, now.importance)
        assertEquals(ringtone, now.sound)
        assertEquals(AudioAttributes.USAGE_ALARM, now.audioAttributes.usage)
        assertTrue(now.shouldVibrate())
        assertTrue(pattern.contentEquals(now.vibrationPattern))
        assertEquals(Notification.VISIBILITY_PRIVATE, now.lockscreenVisibility)
        assertTrue(now.canShowBadge())
        assertEquals(Channels.Group.ALERTS_ALARM.id, now.group)
        assertTrue("the old channel is gone", shadowOf(nm).isChannelDeleted(veryLow))
        assertEquals("alert_alarm.very_low.2", AlertChannels.id(ctx, AlertKind.VERY_LOW, alarm = true))
        // the alarm channels Sukrara made itself took the override at creation: they stay
        assertEquals(
            AlertKind.entries.size,
            AlertChannels.alarmIds(ctx).values.count {
                nm.getNotificationChannel(it)?.canBypassDnd() ==
                    true
            },
        )
    }

    @Test fun theNewIdStaysAcrossRestartsAndTheOldOneNeverComesBack() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        edited()
        move()

        Channels.create(ctx)

        assertTrue(shadowOf(nm).isChannelDeleted(veryLow))
        assertEquals(1, Channels.existing(ctx).count { Channels.alarmGeneration(it, "very_low") != null })
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, nm.getNotificationChannel("alert_alarm.very_low.2").importance)
    }

    @Test fun silentAndStillAreKeptToo() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.createNotificationChannel(
            NotificationChannel(veryLow, "Very low", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )

        move()

        val now = nm.getNotificationChannel("alert_alarm.very_low.2")
        assertNull(now.sound)
        assertFalse(now.shouldVibrate())
        assertTrue(now.canBypassDnd())
    }

    @Test fun aVibrationEffectMovesAsItIs() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 100, 500), -1)
        nm.createNotificationChannel(
            NotificationChannel(veryLow, "Very low", NotificationManager.IMPORTANCE_HIGH).apply {
                vibrationEffect =
                    effect
            },
        )

        move()

        assertEquals(effect, nm.getNotificationChannel("alert_alarm.very_low.2").vibrationEffect)
    }

    @Test fun withoutDoNotDisturbAccessNothingMoves() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        edited()

        assertEquals(emptySet<AlertKind>(), move().moved)
        assertNotNull(nm.getNotificationChannel(veryLow))
        assertNull(nm.getNotificationChannel("alert_alarm.very_low.2"))
    }

    @Test fun aSoundSukraraCantReadStaysWhereItIs() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.createNotificationChannel(
            NotificationChannel(veryLow, "Very low", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound("content://elsewhere/unreadable".toUri(), AlertChannels.audioAttributes(alarm = true))
            },
        )

        val move = move()

        assertEquals(setOf(AlertKind.VERY_LOW), move.kept)
        assertFalse(nm.getNotificationChannel(veryLow).canBypassDnd())
        assertNull(nm.getNotificationChannel("alert_alarm.very_low.2"))
    }

    @Test fun anAlertOnScreenKeepsItsOldChannelUntilItGoes() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        edited()
        nm.notify(
            1001,
            Notification
                .Builder(ctx, veryLow)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("Very low")
                .build(),
        )

        move()

        assertEquals(
            "new alerts post on the new channel",
            "alert_alarm.very_low.2",
            AlertChannels.id(ctx, AlertKind.VERY_LOW, alarm = true),
        )
        assertNotNull("the alert showing stays", nm.getNotificationChannel(veryLow))
        assertFalse(Channels.dropOldAlarms(ctx))

        nm.cancel(1001)
        assertTrue(Channels.dropOldAlarms(ctx))
        assertTrue(shadowOf(nm).isChannelDeleted(veryLow))
    }

    @Test fun movingAgainTakesTheNextId() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        edited("alert_alarm.very_low.2")

        move()

        assertTrue(nm.getNotificationChannel("alert_alarm.very_low.3").canBypassDnd())
        assertTrue(shadowOf(nm).isChannelDeleted("alert_alarm.very_low.2"))
        assertEquals("alert_alarm.very_low.3", AlertChannels.id(ctx, AlertKind.VERY_LOW, alarm = true))
    }

    @Test fun sukrarasOwnSoundsAreReadableAndAnUnknownProviderIsNot() {
        assertTrue(AlertChannels.soundReadable(ctx, AlertChannels.soundUri(ctx, AlertKind.VERY_LOW)))
        assertFalse(AlertChannels.soundReadable(ctx, "content://nobody.here/sound".toUri()))
    }
}
