// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Keeps the app's other sounding notifications out of the alerts' way.
 *
 * Android makes one sound per app per second: a second sounding notification within 1 s of the previous one is muted,
 * whatever its category or channel (AOSP `NotificationUsageStats.AlertRateLimiter`, `MUTE_REASON_RATE_LIMIT` in
 * `NotificationAttentionHelper.shouldMuteNotificationLocked`, frameworks/base main). A sensor or status notice posted
 * right before an alert of the same reading (the sensor lost, then No data) muted the alert. So an info notification
 * that would sound waits [GAP_MS] before it posts (an alert decided at the same moment rings first), and posts only
 * [GAP_MS] after the last alert sound ([alertRang]); a newer post of the same id replaces the waiting one, a cancel
 * drops it. One that can't wait ([postNow]: *Monitoring stopped*, posted while Android stops the service) posts at
 * once. Either way an alert that comes less than [GAP_MS] after an info sound waits out the rest ([alertWaitMs], at
 * most [GAP_MS]): a short delay instead of a silent alert. Among themselves alerts never wait: the runtime's ring order
 * decides (AlertRingOrder).
 *
 * [clock] is monotonic (`SystemClock.elapsedRealtime`); [later] runs a post after a delay.
 */
class SoundGap(
    private val clock: () -> Long,
    private val later: (delayMs: Long, run: () -> Unit) -> Unit,
) {
    private var lastAlertAt = Long.MIN_VALUE / 2

    /** When an info notification last made a sound. */
    private var lastInfoAt = Long.MIN_VALUE / 2

    /** The waiting post of each notification id (a newer one replaces it: only the latest runs). */
    private val waiting = HashMap<Int, Any>()

    /** An alert just made a sound (posted ringing, or played by the app itself). */
    @Synchronized
    fun alertRang() {
        lastAlertAt = clock()
    }

    /** Runs [post] now when it makes no sound, else after the gap (see the class). */
    @Synchronized
    fun submit(
        id: Int,
        sounds: Boolean,
        post: () -> Unit,
    ) {
        if (!sounds) {
            waiting.remove(id)
            post()
            return
        }
        val token = Any()
        waiting[id] = token
        schedule(id, token, GAP_MS, post)
    }

    /** Runs [post] now, for a notification that can't wait; one that [sounds] holds the next alert back ([alertWaitMs]). */
    @Synchronized
    fun postNow(
        id: Int,
        sounds: Boolean,
        post: () -> Unit,
    ) {
        waiting.remove(id)
        post()
        if (sounds) lastInfoAt = clock()
    }

    /** How long an alert about to ring waits so the info sound just before it can't mute it (0 = none). */
    @Synchronized
    fun alertWaitMs(): Long = (lastInfoAt + GAP_MS - clock()).coerceIn(0, GAP_MS)

    /** The notification [id] was cancelled: a post still waiting for it never lands. */
    @Synchronized
    fun cancel(id: Int) {
        waiting.remove(id)
    }

    private fun schedule(
        id: Int,
        token: Any,
        delayMs: Long,
        post: () -> Unit,
    ) = later(delayMs) { flush(id, token, post) }

    @Synchronized
    private fun flush(
        id: Int,
        token: Any,
        post: () -> Unit,
    ) {
        if (waiting[id] !== token) return
        val wait = lastAlertAt + GAP_MS - clock()
        if (wait > 0) {
            schedule(id, token, wait, post)
            return
        }
        waiting.remove(id)
        post()
        lastInfoAt = clock()
    }

    companion object {
        /** Android's 1 s rate limit, plus a margin for the post's own way through the system. */
        const val GAP_MS = 1_100L

        /** The process's one gap: every alert and info post of the app goes through it. */
        val shared: SoundGap by lazy {
            val main = Handler(Looper.getMainLooper())
            SoundGap(SystemClock::elapsedRealtime) { delay, run -> main.postDelayed(run, delay) }
        }

        /** Whether a post on [channelId] would make a sound or vibrate (the channel as the person left it). */
        fun sounds(
            ctx: Context,
            channelId: String,
        ): Boolean {
            val c = ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId) ?: return true
            return c.importance >= NotificationManager.IMPORTANCE_DEFAULT && (c.sound != null || c.shouldVibrate())
        }
    }
}
