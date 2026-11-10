// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.NotificationManager
import android.content.Context
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.monitor.notify.SoundGap
import app.mauri.sukrara.monitor.readiness.NotificationCooldown

/**
 * The alert's sound while the phone is unlocked and Android's *Vibrate only when unlocked* is on ([NotificationCooldown]):
 * Android then only vibrates, so Sukrara plays the channel's own sound itself, once, on the volume the alert rings on
 * (`USAGE_ALARM` / `USAGE_NOTIFICATION_EVENT`: the ringer mode and the stream volume apply as they would to the
 * notification). Only where Android would have played it: a ringing post (not a silent re-post), a channel that sounds
 * and vibrates (one that doesn't vibrate still sounds: Android plays it), and Do Not Disturb letting it through
 * ([dndLetsThrough]; unknown = no). Like Android, one sound per second: a second alert within it stays quiet (the ring
 * order put the one to hear first). Clearing or muting the alert stops it.
 */
class UnlockedAlertSound(
    private val ctx: Context,
    /** The person's answer, or Android's when it gives one ([NotificationCooldown.appPlays]). */
    private val appPlays: () -> Boolean,
) {
    private var player: MediaPlayer? = null
    private var playing: AlertGroup? = null
    private var startedAt = Long.MIN_VALUE / 2

    @Synchronized
    fun ringing(
        group: AlertGroup,
        channelId: String,
        alarm: Boolean,
    ) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(channelId) ?: return
        val now = SystemClock.elapsedRealtime()
        val play =
            shouldPlay(
                appPlays = runCatching(appPlays).getOrDefault(false),
                userPresent = NotificationCooldown.userPresent(ctx),
                channelSounds = channel.sound != null && channel.importance >= NotificationManager.IMPORTANCE_DEFAULT,
                channelVibrates = channel.shouldVibrate(),
                dndLetsThrough = dndLetsThrough(AlertCategory.Dnd.of(nm), alarm, channel.canBypassDnd()),
                sinceLastMs = now - startedAt,
            )
        if (!play) return
        stopLocked()
        val p = MediaPlayer()
        player = p
        playing = group
        startedAt = now
        runCatching {
            p.setAudioAttributes(AlertChannels.audioAttributes(alarm))
            p.setDataSource(ctx, channel.sound)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { done(it) }
            p.setOnErrorListener { mp, _, _ -> done(mp).let { true } }
            p.prepareAsync()
            SoundGap.shared.alertRang()
        }.onFailure {
            Log.w(TAG, "unlocked sound: ${it.javaClass.simpleName}")
            done(p)
        }
    }

    /** [group]'s alert went (cleared, muted, replaced by its muted card): its sound stops. */
    @Synchronized
    fun stop(group: AlertGroup) {
        if (playing == group) stopLocked()
    }

    private fun stopLocked() {
        player?.let { runCatching { it.release() } }
        player = null
        playing = null
    }

    @Synchronized
    private fun done(p: MediaPlayer) {
        runCatching { p.release() }
        if (player === p) {
            player = null
            playing = null
        }
    }

    companion object {
        /** The rule (JVM-tested): see the class. */
        fun shouldPlay(
            appPlays: Boolean,
            userPresent: Boolean,
            channelSounds: Boolean,
            channelVibrates: Boolean,
            dndLetsThrough: Boolean,
            sinceLastMs: Long,
        ): Boolean = appPlays && userPresent && channelSounds && channelVibrates && dndLetsThrough && sinceLastMs >= 1_000

        /**
         * Whether Do Not Disturb lets this alert's sound through, conservatively: off lets everything; an alarm-level alert
         * passes *Alarms only*, and Priority when it lets alarms through or the channel overrides it; anything else, or
         * Do Not Disturb unknown, no (the app never sounds where Android wouldn't).
         */
        fun dndLetsThrough(
            dnd: AlertCategory.Dnd?,
            alarm: Boolean,
            channelBypassesDnd: Boolean,
        ): Boolean =
            when (dnd?.filter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> true
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> alarm
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> alarm && (dnd.allowsAlarms || channelBypassesDnd)
                else -> false
            }

        private const val TAG = "Sukrara.alerts"
    }
}
