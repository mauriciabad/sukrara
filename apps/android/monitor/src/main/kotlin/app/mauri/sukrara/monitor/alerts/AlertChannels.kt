// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.net.toUri
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.monitor.notify.Channels

/**
 * Two channels per alert kind, one per volume, so the person can set each kind's sound, vibration and Do Not
 * Disturb behaviour in the system settings. A channel's audio can't change after it exists, so the per-kind *alarm*
 * setting picks the channel:
 * - `alert_alarm.<kind>`: `USAGE_ALARM` — alarm volume, passes "Alarms" in Do Not Disturb. It overrides Do Not Disturb
 *   only when the person lets Sukrara through ([setDndBypass], after Do Not Disturb access; #184): never by default.
 * - `alert.<kind>`: `USAGE_NOTIFICATION_EVENT` — notification volume, through Do Not Disturb only where the person lets
 *   reminders (or this channel) through.
 * The notification's category ([AlertCategory], chosen at each post) keeps every alert out of Android's notification
 * cooldowns (docs/15).
 * Both IMPORTANCE_HIGH (heads-up; ids, names and groups in [Channels]), sound `content://<appId>.alertsound/<kind>` ([AlertSoundProvider] picks the file at
 * play time), the kind's vibration. Ids never change (renaming a channel resets the person's choices), except an alarm
 * channel's when its Do Not Disturb override changes with the person's choices copied over ([setDndBypass]).
 */
object AlertChannels {
    /** The channel [kind] posts on now: `alert.<kind>`, or its alarm channel's current id ([Channels.currentAlarm]). */
    fun id(
        ctx: Context,
        kind: AlertKind,
        alarm: Boolean,
    ): String = if (alarm) Channels.currentAlarm(Channels.existing(ctx), kind.wire) else Channels.alert(kind.wire)

    /** Every kind's alarm channel id now, from one read of the app's channels. */
    fun alarmIds(ctx: Context): Map<AlertKind, String> {
        val ids = Channels.existing(ctx)
        return AlertKind.entries.associateWith { Channels.currentAlarm(ids, it.wire) }
    }

    fun audioAttributes(alarm: Boolean): AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    fun soundUri(ctx: Context, kind: AlertKind): Uri = "content://${ctx.packageName}.alertsound/${kind.wire}".toUri()

    /**
     * The alert setup of a channel [Channels] creates (sound, usage, vibration). [bypassDnd]: its Do Not Disturb override,
     * the one it has (creating it again never changes it) or false for a new one: Sukrara respects Do Not Disturb by
     * default (#184).
     */
    fun configure(channel: NotificationChannel, ctx: Context, kind: AlertKind, alarm: Boolean, bypassDnd: Boolean) =
        channel.apply {
            setSound(soundUri(ctx, kind), audioAttributes(alarm))
            enableVibration(true)
            vibrationEffect = AlertHaptics.effect(ctx, kind)
            setBypassDnd(bypassDnd)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

    /** Creates the app's channels (registry, idempotent), the alert ones with [configure]. */
    fun ensure(ctx: Context) = Channels.create(ctx)

    /** What [setDndBypass] did: the kinds whose alarm channel moved, and those [kept] where they were. */
    data class Move(
        val moved: Set<AlertKind>,
        /** Their sound is one Sukrara can't read (a file of the person's own), which a new channel would lose. */
        val kept: Set<AlertKind>,
    )

    /**
     * Makes every alarm channel override Do Not Disturb ([bypass]) or not: the person's *Let Sukrara through* / *Don't let
     * Sukrara through* (#184; the override itself #15). Android takes an app's `setBypassDnd` on an existing channel
     * only while the app holds Do Not Disturb access and the person hasn't changed anything on it (AOSP
     * PreferencesHelper.createNotificationChannel: `if (existing.getUserLockedFields() == 0 && hasDndAccess)`), and on a
     * new channel only with that access when it asks for the override. So each alarm channel is created again asking for
     * [bypass] first (an untouched one takes it in place), and each one still different, one the person edited, moves to
     * its next id ([Channels.nextAlarm]) with everything they chose copied over ([copyWithBypass]: importance, sound,
     * vibration, lights, lock screen, badge); its alerts post there from now on and the old channel goes once no
     * notification shows on it. A channel whose sound Sukrara can't [readable] stays as it is (its page in Android's
     * settings is the way). Turning the override on needs the access (nothing happens without it); turning it off
     * doesn't (a new channel without it needs nothing).
     *
     * Only when the person asks (the button, or the access just granted): never on every check, which would undo a
     * choice they made in Android's own settings.
     */
    fun setDndBypass(
        ctx: Context,
        bypass: Boolean,
        readable: (Uri) -> Boolean = { soundReadable(ctx, it) },
    ): Move {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        synchronized(Channels.lock) {
            if (bypass && !nm.isNotificationPolicyAccessGranted) return Move(emptySet(), emptySet())
            Channels.create(ctx, alarmBypass = bypass)
            val ids = Channels.existing(ctx)
            val moved = mutableSetOf<AlertKind>()
            val kept = mutableSetOf<AlertKind>()
            for (kind in AlertKind.entries) {
                val old = nm.getNotificationChannel(Channels.currentAlarm(ids, kind.wire)) ?: continue
                if (old.canBypassDnd() == bypass) continue
                if (old.sound?.let(readable) == false) {
                    kept += kind
                    continue
                }
                nm.createNotificationChannel(copyWithBypass(old, Channels.nextAlarm(old.id, kind.wire), bypass))
                moved += kind
            }
            Channels.dropOldAlarms(ctx)
            return Move(moved, kept)
        }
    }

    /** [old] under [id], overriding Do Not Disturb or not ([bypass]), with every choice a person can make on a channel kept. */
    internal fun copyWithBypass(
        old: NotificationChannel,
        id: String,
        bypass: Boolean = true,
    ): NotificationChannel =
        NotificationChannel(id, old.name, old.importance).apply {
            group = old.group
            description = old.description
            setSound(old.sound, old.audioAttributes)
            old.vibrationEffect?.let { vibrationEffect = it } ?: old.vibrationPattern?.let { vibrationPattern = it }
            enableVibration(old.shouldVibrate())
            enableLights(old.shouldShowLights())
            lightColor = old.lightColor
            lockscreenVisibility = old.lockscreenVisibility
            setShowBadge(old.canShowBadge())
            setBypassDnd(bypass)
        }

    /**
     * Whether Android would play [sound] for a channel Sukrara creates: it replaces a sound the app can't read with the
     * default one (NotificationRecord.visitGrantableUri). Sukrara's own sounds always; another app's or the person's
     * file only when it opens here.
     */
    fun soundReadable(
        ctx: Context,
        sound: Uri,
    ): Boolean =
        sound.authority == "${ctx.packageName}.alertsound" ||
            runCatching { ctx.contentResolver.openAssetFileDescriptor(sound, "r")?.use { true } ?: false }.getOrDefault(false)
}
