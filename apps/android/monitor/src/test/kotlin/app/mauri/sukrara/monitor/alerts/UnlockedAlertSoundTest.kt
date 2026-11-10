// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.NotificationManager
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.monitor.readiness.NotificationCooldown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * *Vibrate only when unlocked* (#129): Sukrara plays the alert sound itself exactly where Android would have and only
 * vibrates — never a second sound, never through Do Not Disturb.
 */
class UnlockedAlertSoundTest {
    private fun play(
        appPlays: Boolean = true,
        userPresent: Boolean = true,
        channelSounds: Boolean = true,
        channelVibrates: Boolean = true,
        dndLetsThrough: Boolean = true,
        sinceLastMs: Long = 60_000,
    ) = UnlockedAlertSound.shouldPlay(appPlays, userPresent, channelSounds, channelVibrates, dndLetsThrough, sinceLastMs)

    @Test fun `an alert while the phone is unlocked and Android only vibrates gets its sound`() = assertTrue(play())

    @Test fun `nothing extra where Android plays it or wouldn't`() {
        assertFalse("the setting is off (or not answered)", play(appPlays = false))
        assertFalse("locked or screen off: Android plays the sound", play(userPresent = false))
        assertFalse("a channel without vibration still sounds: no second sound", play(channelVibrates = false))
        assertFalse("the person silenced the channel", play(channelSounds = false))
        assertFalse("Do Not Disturb holds it", play(dndLetsThrough = false))
        assertFalse("one sound per second, like Android", play(sinceLastMs = 400))
    }

    @Test fun `Do Not Disturb lets the sound through only where it lets the alert through`() {
        fun through(
            filter: Int?,
            alarm: Boolean,
            bypass: Boolean = false,
            alarmsAllowed: Boolean = false,
        ) = UnlockedAlertSound.dndLetsThrough(
            filter?.let { AlertCategory.Dnd(it, allowsAlarms = alarmsAllowed, allowsReminders = false) },
            alarm,
            bypass,
        )
        assertTrue(through(NotificationManager.INTERRUPTION_FILTER_ALL, alarm = false))
        assertTrue(through(NotificationManager.INTERRUPTION_FILTER_ALARMS, alarm = true))
        assertFalse(through(NotificationManager.INTERRUPTION_FILTER_ALARMS, alarm = false))
        assertTrue(through(NotificationManager.INTERRUPTION_FILTER_PRIORITY, alarm = true, alarmsAllowed = true))
        assertTrue(through(NotificationManager.INTERRUPTION_FILTER_PRIORITY, alarm = true, bypass = true))
        assertFalse(through(NotificationManager.INTERRUPTION_FILTER_PRIORITY, alarm = false, bypass = true))
        assertFalse(through(NotificationManager.INTERRUPTION_FILTER_NONE, alarm = true, bypass = true))
        assertFalse("unknown", through(null, alarm = true, bypass = true))
    }

    @Test fun `Android's answer wins when it gives one, else the person's`() {
        assertTrue(NotificationCooldown.appPlays(vibrateOnly = true, answer = UnlockedSound.Off))
        assertFalse(NotificationCooldown.appPlays(vibrateOnly = false, answer = UnlockedSound.AppPlays))
        assertTrue(NotificationCooldown.appPlays(vibrateOnly = null, answer = UnlockedSound.AppPlays))
        assertFalse(NotificationCooldown.appPlays(vibrateOnly = null, answer = UnlockedSound.Off))
        assertFalse(NotificationCooldown.appPlays(vibrateOnly = null, answer = UnlockedSound.Unchecked))
    }

    @Test fun `stored answers read back, anything else is not answered`() {
        UnlockedSound.entries.forEach { assertEquals(it, UnlockedSound.of(it.wire)) }
        assertEquals(UnlockedSound.Unchecked, UnlockedSound.of("never"))
    }
}
