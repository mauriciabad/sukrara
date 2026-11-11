// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.readiness

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import app.mauri.sukrara.core.model.UnlockedSound

/**
 * Android's *Notification cooldown › Vibrate only when unlocked* (Android 15+, off by default).
 *
 * With it on, NotificationAttentionHelper drops every notification sound while the person is using the phone, alarms
 * included: `vibrateOnly = hasValidVibrate && mNotificationCooldownVibrateUnlocked && mUserPresent`, then
 * `if (hasValidSound && !vibrateOnly) playSound(…)` (AOSP frameworks/base main, NotificationAttentionHelper
 * `buzzBeepBlinkLocked`). `mUserPresent` turns true on ACTION_USER_PRESENT and false on SCREEN_OFF; no category or
 * channel is exempt, and a channel without vibration still sounds. The value is `Settings.System`
 * `notification_cooldown_vibrate_unlocked`, `@hide` without `@Readable`, so for an app targeting S+ the read throws
 * ("settings keys annotated with @hide are restricted to system_server and system apps only", Settings.java
 * `NameValueCache`): [vibrateOnlyWhenUnlocked] tries it and answers null when Android won't say, and the person tells
 * the checklist what a test alert did instead ([UnlockedSound]: sent with the phone unlocked and Sukrara's own sound off,
 * so it shows Android's behaviour; #183). The cooldown page has no public action; Settings declares
 * `android.settings.MANAGE_ADAPTIVE_NOTIFICATIONS` for it (packages/apps/Settings AndroidManifest,
 * PoliteNotificationsPreferenceFragment).
 */
object NotificationCooldown {
    private const val KEY = "notification_cooldown_vibrate_unlocked"

    /** The cooldown page (Settings › Notifications › Notification cooldown), then the notifications page. */
    val PAGES =
        listOf(
            Intent("android.settings.MANAGE_ADAPTIVE_NOTIFICATIONS"),
            Intent("android.settings.NOTIFICATION_SETTINGS"),
        )

    /** On / off when Android lets this app read it; null when it doesn't (every current Android for this app). */
    fun vibrateOnlyWhenUnlocked(ctx: Context): Boolean? =
        try {
            Settings.System.getInt(ctx.contentResolver, KEY, 0) == 1
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    /** Android's own "user present": the screen on and past the lock screen (when it only vibrates). */
    fun userPresent(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isInteractive &&
            !ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    /** Whether Sukrara plays the alert sound itself while unlocked: Android's answer when it gives one, else the person's. */
    fun appPlays(
        vibrateOnly: Boolean?,
        answer: UnlockedSound,
    ): Boolean = vibrateOnly ?: (answer == UnlockedSound.OnlyVibrated)

    /** The checklist item is missing while nobody knows: Android won't say and the person hasn't answered. */
    fun unanswered(
        vibrateOnly: Boolean?,
        answer: UnlockedSound,
    ): Boolean = vibrateOnly == null && answer == UnlockedSound.Unchecked

    /**
     * The kind the unlocked test sends among the alerts [on]: the first whose channel [soundsAndVibrates] (Android drops
     * the sound only where there is a vibration: a test on a channel without one would sound either way), those that ring
     * like an alarm ([alarm]) first; else the first of those; null when none is on.
     */
    fun <K> testKind(
        on: List<K>,
        alarm: (K) -> Boolean,
        soundsAndVibrates: (K) -> Boolean,
    ): K? {
        val ordered = on.filter(alarm) + on.filterNot(alarm)
        return ordered.firstOrNull(soundsAndVibrates) ?: ordered.firstOrNull()
    }
}
