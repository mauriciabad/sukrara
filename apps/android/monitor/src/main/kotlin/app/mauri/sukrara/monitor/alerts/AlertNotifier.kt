// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TtsSpan
import androidx.annotation.StringRes
import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.MuteLine
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.designsystem.alerts.AlertWords
import app.mauri.sukrara.designsystem.alerts.MuteText
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.graph.TrendAngle
import app.mauri.sukrara.monitor.AlertActionReceiver
import app.mauri.sukrara.monitor.R
import app.mauri.sukrara.monitor.notify.AppActivities
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.NotificationGroups
import app.mauri.sukrara.monitor.notify.NotificationIds
import app.mauri.sukrara.monitor.notify.SoundGap
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The alert notifications ([AlertPresenter]): one slot per [AlertGroup] (level, rate, no data), all in the `alerts`
 * notification group ([NotificationGroups]). Self-contained text: the headline names the alert, the text gives the
 * value, the arrow and what it means; the actions work from the lock screen without opening the app
 * ([AlertActionReceiver]). Alarm-level kinds (the kind's *alarm* setting) also get a full-screen intent, only when
 * Android allows it ([NotificationManager.canUseFullScreenIntent]; otherwise it is a heads-up and the reliability row
 * says so). The tap opens the app. When the alert can't be built or posted, the runtime falls back to [showPlain]
 * (same channel, fixed resources and plain values), then to [showProblem] (*Monitoring problems*).
 */
class AlertNotifier(
    private val ctx: Context,
    /** The alert sound Sukrara plays itself while Android only vibrates (*Vibrate only when unlocked*). */
    private val unlocked: UnlockedAlertSound? = null,
    /** The person's glucose unit now (`general.unit`): the alert's value and rate are written in it. */
    private val unit: () -> GlucoseUnit = { GlucoseUnit.MGDL },
) : AlertPresenter {
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)

    /** The app's words now, read once per post: a language change renames the next alert at once. */
    private fun words(): AlertWords = AlertWords.of(ctx, AlertWords.Ids + AlertCopy.Ids, strict = true)

    override fun show(firing: Firing, settings: AlertSettings, silent: Boolean, who: String?, relaxedMin: Int) {
        val kind = firing.kind
        // #39: the wearer's very low nobody answered rings like an alarm here, whatever this kind's setting
        val alarm = settings.rule(kind).alarm || firing.escalated
        val words = words()
        val b =
            base(kind, alarm, silent)
                .setContentTitle(AlertCopy.headline(kind, firing.treated, words, firing.escalated))
                .setContentText(AlertCopy.body(firing, words, unit()))
                .setWhen(firing.at * 1000)
                .setShowWhen(true)
                .setAutoCancel(true)
        val relaxed =
            relaxedMin
                .takeIf {
                    it > 0
                }?.let { ctx.getString(R.string.alert_relaxed_subtext, Durations.text(it.toLong(), words.durations)) }
        listOfNotNull(who, relaxed).takeIf { it.isNotEmpty() }?.let { b.setSubText(it.joinToString(" · ")) }
        b.addMuteAction(kind, ctx.getString(R.string.alert_action_mute), settings.muteChoices(), words)
        when {
            relaxedMin > 0 -> b.addAction(action(kind, AlertActionReceiver.REMIND_AS_USUAL, R.string.alert_action_remind_usual))
            settings.canRelax(kind) -> b.addAction(action(kind, AlertActionReceiver.REMIND_LESS, R.string.alert_action_remind_less))
        }
        if (alarm && !silent && nm.canUseFullScreenIntent()) b.setFullScreenIntent(openApp(), true)
        post(kind.group, b.build(), AlertChannels.id(ctx, kind, alarm))
        // the app's info notifications wait until Android's one-sound-per-second limit can't mute this one
        if (!silent) {
            SoundGap.shared.alertRang()
            unlocked?.ringing(kind.group, AlertChannels.id(ctx, kind, alarm), alarm)
        }
    }

    /**
     * [show]'s stand-in when it failed: "High glucose" / "220 mg/dL ↗" / *Open Sukrara*, from [headlineRes] and
     * [plainValue] only (no lookup by name, no format string), on the same channel, category, group and slot as the
     * alert, full screen where the alert would be. No Mute or Remind actions (they need the alert's words): the app
     * has them, one tap away. Once posted it counts as shown: what follows can't turn it into a failure.
     */
    override fun showPlain(firing: Firing, settings: AlertSettings, silent: Boolean, who: String?) {
        val kind = firing.kind
        val alarm = settings.rule(kind).alarm || firing.escalated
        val channel = AlertChannels.id(ctx, kind, alarm)
        val open = openApp()
        val b =
            base(kind, alarm, silent)
                .setContentTitle(ctx.getString(headlineRes(kind)))
                .setWhen(firing.at * 1000)
                .setShowWhen(true)
                .setAutoCancel(true)
                .addAction(Notification.Action.Builder(null, ctx.getString(R.string.monitor_action_open_app), open).build())
        plainValue(firing, unit())?.let(b::setContentText)
        who?.let(b::setSubText)
        if (alarm && !silent && nm.canUseFullScreenIntent()) b.setFullScreenIntent(open, true)
        post(kind.group, b.build(), channel)
        if (!silent) {
            runCatching {
                SoundGap.shared.alertRang()
                unlocked?.ringing(kind.group, channel, alarm)
            }
        }
    }

    /**
     * The last resort: "Sukrara couldn't show an alert" / "Open Sukrara to see your glucose." on `status` (*Monitoring
     * problems*: its own sound and importance, the person's Do Not Disturb choice for it), fixed words only, in
     * [group]'s slot but outside the alerts group, so a later alert replaces it and [clear] removes it. A silent
     * re-post doesn't sound again.
     */
    override fun showProblem(group: AlertGroup, silent: Boolean) {
        Channels.createCore(ctx)
        val open = openApp()
        val n =
            Notification
                .Builder(ctx, Channels.STATUS)
                .setSmallIcon(ctx.applicationInfo.icon)
                .setContentTitle(ctx.getString(R.string.alert_problem_title))
                .setContentText(ctx.getString(R.string.alert_problem_text))
                .setCategory(AlertCategory.of(alarm = false, AlertCategory.Dnd.of(nm)))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, ctx.getString(R.string.monitor_action_open_app), open).build())
                .setOnlyAlertOnce(silent)
                .setAutoCancel(true)
                .build()
        nm.notify(idOf(group), n)
    }

    /** An info notification sounded less than a second ago: its sound would mute this alert's, so it waits the rest. */
    override suspend fun beforeRing() = delay(SoundGap.shared.alertWaitMs())

    override fun showMuted(group: AlertGroup, side: Side, kind: AlertKind, until: Long, settings: AlertSettings, now: Long) {
        val alarm = settings.rule(kind).alarm
        val words = words()
        val b =
            base(kind, alarm, silent = true)
                .setContentTitle(MuteText.line(MuteLine.SideMuted(side, null), words))
                .setContentText(MuteText.line(MuteLine.NotifUntil(until), words, ::clockTime))
                .setWhen(until * 1000)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(((until - now) * 1000).coerceAtLeast(1_000))
                .addAction(action(kind, AlertActionReceiver.UNMUTE, R.string.alert_action_unmute))
        b.addMuteAction(kind, ctx.getString(R.string.alert_action_change), settings.muteChoices(), words)
        unlocked?.stop(group)
        post(group, b.build(), AlertChannels.id(ctx, kind, alarm))
    }

    /**
     * Settings › an alert › *Send a test alert*: [kind]'s notification on its real channel (its sound, vibration and
     * Do Not Disturb behaviour), worded as a test with no reading in it, in its own slot ([NotificationIds.ALERT_TEST])
     * and without Mute / Remind actions, so it can't touch the alert runtime, replace a real alert or be taken for one.
     * No full-screen intent (the person is in the app). It goes away on a tap, a swipe or after [TEST_TIMEOUT_MS].
     * Same category rule as the real alert ([AlertCategory]), so it rings at full volume after another alert.
     * [ownUnlockedSound] false: the checklist's unlocked test, which must show what Android does while the phone is
     * unlocked, so Sukrara doesn't add its own sound (#183).
     */
    fun showTest(kind: AlertKind, settings: AlertSettings, ownUnlockedSound: Boolean = true) {
        val alarm = settings.rule(kind).alarm
        val n =
            base(kind, alarm, silent = false)
                .setContentTitle(ctx.getString(R.string.alert_test_title, ctx.getString(headlineRes(kind))))
                .setContentText(ctx.getString(R.string.alert_test_text))
                .setShowWhen(true)
                .setAutoCancel(true)
                .setTimeoutAfter(TEST_TIMEOUT_MS)
                .build()
        post(NotificationIds.ALERT_TEST, n, AlertChannels.id(ctx, kind, alarm))
        SoundGap.shared.alertRang()
        if (ownUnlockedSound) unlocked?.ringing(kind.group, AlertChannels.id(ctx, kind, alarm), alarm)
    }

    override fun clear(group: AlertGroup) {
        unlocked?.stop(group)
        NotificationGroups.cancel(ctx, idOf(group), NotificationGroups.Group.ALERTS)
    }

    @Volatile private var channelsReady = false

    private fun post(group: AlertGroup, n: Notification, channelId: String) = post(idOf(group), n, channelId)

    private fun post(id: Int, n: Notification, channelId: String) {
        // the channels exist before the first alert of this process (also created by AlertsModule.attach)
        if (!channelsReady) {
            AlertChannels.ensure(ctx)
            channelsReady = true
        }
        NotificationGroups.post(ctx, id, n, NotificationGroups.Group.ALERTS, channelId)
    }

    private fun base(kind: AlertKind, alarm: Boolean, silent: Boolean): Notification.Builder =
        Notification
            .Builder(ctx, AlertChannels.id(ctx, kind, alarm))
            .setSmallIcon(ctx.applicationInfo.icon)
            // read right before every post: exempt from Android's notification cooldown without changing DND
            .setCategory(AlertCategory.of(alarm, AlertCategory.Dnd.of(nm)))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            .setDeleteIntent(broadcast(kind, AlertActionReceiver.TIDY, mutable = false))
            .setGroup(NotificationGroups.Group.ALERTS.key)
            // silent = what NotificationCompat.setSilent does: a child that defers alerting to the summary, which never
            // alerts (GROUP_ALERT_CHILDREN); a ringing alert keeps GROUP_ALERT_ALL and rings as a child
            .apply { if (silent) setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY) }

    private fun clockTime(epochS: Long): String = ClockText.time(epochS)

    /**
     * *Mute…* / *Change time*: a direct reply with the lengths as one-tap chips, shortest first (and a typed one); each
     * chip says what it does to TalkBack ([muteChoices]).
     */
    private fun Notification.Builder.addMuteAction(kind: AlertKind, title: String, choices: List<Int>, words: AlertWords) =
        addAction(
            Notification.Action
                .Builder(null, title, broadcast(kind, AlertActionReceiver.MUTE, mutable = true))
                .addRemoteInput(
                    RemoteInput
                        .Builder(AlertActionReceiver.TYPED)
                        .setLabel(ctx.getString(R.string.alert_action_mute_for_label))
                        .setChoices(muteChoices(kind, choices, words))
                        .setAllowFreeFormInput(true)
                        .setEditChoicesBeforeSending(RemoteInput.EDIT_CHOICES_BEFORE_SENDING_DISABLED)
                        .build(),
                ).build(),
        )

    /**
     * The chips, shortest first ("15 min", "30 min", "1 h", "2 h"; the person's own first length among them). A chip has
     * no label of its own, so each carries what TalkBack says as a [TtsSpan]: "Low alerts: mute for 30 min". The reply
     * comes back as the chip's text (the span is dropped with it), which [AlertActionReceiver] parses.
     */
    internal fun muteChoices(kind: AlertKind, minutes: List<Int>, words: AlertWords = words()): Array<CharSequence> {
        val side = MuteText.sideName(kind.side, words)
        val durations = words.durations
        return minutes
            .distinct()
            .sorted()
            .map { min ->
                val shown = Durations.text(min.toLong(), durations)
                val spoken = ctx.getString(R.string.alert_mute_choice_a11y, side, shown)
                SpannableString(shown).apply { setSpan(TtsSpan.TextBuilder(spoken).build(), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE) }
            }.toTypedArray()
    }

    private fun action(kind: AlertKind, what: String, label: Int) =
        Notification.Action.Builder(null, ctx.getString(label), broadcast(kind, what, mutable = false)).build()

    /** A broadcast to the non-exported [AlertActionReceiver]; only the direct reply's is mutable (the system adds the text). */
    private fun broadcast(kind: AlertKind, what: String, mutable: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            requestCode(kind, what),
            Intent(ctx, AlertActionReceiver::class.java).setAction(what).putExtra(AlertActionReceiver.KIND, kind.wire),
            PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE,
        )

    /** The app (found before the first unlock too: from the lock screen Android asks for the unlock first). */
    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            1,
            AppActivities.launch(ctx).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        /** One notification id per alert group ([NotificationIds]). */
        fun idOf(group: AlertGroup): Int = NotificationIds.ALERTS_BASE + group.ordinal

        const val TEST_TIMEOUT_MS = 2 * 60_000L

        private fun requestCode(kind: AlertKind, what: String) = 2000 + kind.ordinal * 16 + AlertActionReceiver.ACTIONS.indexOf(what)

        /** "Low", "Very high"…: the kind's name (the sound's title in Android's channel settings). */
        fun kindLabel(ctx: Context, kind: AlertKind): String =
            ctx.getString(
                when (kind) {
                    AlertKind.VERY_LOW -> R.string.alert_kind_very_low
                    AlertKind.LOW -> R.string.alert_kind_low
                    AlertKind.HIGH -> R.string.alert_kind_high
                    AlertKind.VERY_HIGH -> R.string.alert_kind_very_high
                    AlertKind.EXTREMELY_HIGH -> R.string.alert_kind_extremely_high
                    AlertKind.FALLING_FAST -> R.string.alert_kind_falling_fast
                    AlertKind.RISING_FAST -> R.string.alert_kind_rising_fast
                    AlertKind.NO_DATA -> R.string.alert_kind_no_data
                },
            )

        /**
         * "High glucose": the kind's headline as a resource the code names (the plain alert and the test alert), so
         * neither depends on a lookup by name the resource shrinker can't see.
         */
        @StringRes
        fun headlineRes(kind: AlertKind): Int =
            when (kind) {
                AlertKind.VERY_LOW -> R.string.alert_headline_very_low
                AlertKind.LOW -> R.string.alert_headline_low
                AlertKind.HIGH -> R.string.alert_headline_high
                AlertKind.VERY_HIGH -> R.string.alert_headline_very_high
                AlertKind.EXTREMELY_HIGH -> R.string.alert_headline_extremely_high
                AlertKind.FALLING_FAST -> R.string.alert_headline_falling_fast
                AlertKind.RISING_FAST -> R.string.alert_headline_rising_fast
                AlertKind.NO_DATA -> R.string.alert_headline_no_data
            }

        /**
         * The plain alert's text: "220 mg/dL ↗" (the value in [unit] with its symbol, then the arrow; a LO / HI as "< 40
         * mg/dL" / "> 400 mg/dL"), plain values only. Null without a reading and for no data (its last value isn't now).
         */
        fun plainValue(
            f: Firing,
            unit: GlucoseUnit,
            locale: Locale = Locale.getDefault(),
        ): String? {
            val r = f.reading?.takeIf { f.kind != AlertKind.NO_DATA } ?: return null
            val value =
                when {
                    r.isLo -> "< " + unit.text(GlucoseRange.FLOOR, locale)
                    r.isHi -> "> " + unit.text(GlucoseRange.CEILING, locale)
                    else -> unit.text(r.mgdl, locale)
                }
            return "$value ${unit.symbol} ${TrendAngle.text(r.rate)}"
        }
    }
}
