// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TtsSpan
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.designsystem.alerts.AlertWords
import app.mauri.sukrara.designsystem.alerts.MuteText
import app.mauri.sukrara.monitor.AlertActionReceiver
import app.mauri.sukrara.monitor.R
import app.mauri.sukrara.monitor.notify.AlertButton
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * THE alert buttons as Android actions, for the glucose notification that carries the alert and for the separate
 * alerts of *Separate alert notifications*: one place, so both say and do the same. Words without "…" (the owner, 2026-11-14:
 * Treatment opens a dialog without one, so an ellipsis no longer means anything), each with an icon (Android shows
 * action icons only where its templates draw them: Wear OS and the Android Auto / older templates; the phone's
 * standard, Live Update and custom templates on 16 and 17 show the words only, so the words must stand alone).
 * They work from the lock screen without opening the app ([AlertActionReceiver]), except Treatment (Android asks for
 * the unlock before the dialog) and, in a Live Update, Mute (it opens the lengths sheet over the lock screen).
 */
internal class AlertActions(
    private val ctx: Context,
) {
    /** [button] of [kind]'s alert; [promoted] = a Live Update, which drops a direct reply: Mute opens the sheet then. */
    fun action(
        kind: AlertKind,
        button: AlertButton,
        words: AlertWords,
        promoted: Boolean,
    ): Notification.Action =
        when (button) {
            is AlertButton.Treatment -> {
                treatment(kind, button.carbs)
            }

            AlertButton.RemindLess -> {
                broadcastAction(kind, AlertActionReceiver.REMIND_LESS, R.string.alert_action_remind_less, DsR.drawable.ds_ic_snooze)
            }

            AlertButton.RemindAsUsual -> {
                broadcastAction(
                    kind,
                    AlertActionReceiver.REMIND_AS_USUAL,
                    R.string.alert_action_remind_usual,
                    DsR.drawable.ds_ic_notifications_active,
                )
            }

            AlertButton.Unmute -> {
                broadcastAction(kind, AlertActionReceiver.UNMUTE, R.string.alert_action_unmute, DsR.drawable.ds_ic_notifications_active)
            }

            is AlertButton.Mute -> {
                if (promoted) muteSheet(kind) else muteReply(kind, button.minutes, words)
            }
        }

    /**
     * *Mute* as a direct reply with the lengths as one-tap chips, shortest first, in the card's compact words ("15m",
     * "1h"; [Durations.compact]) and a typed one; each chip says its whole meaning to TalkBack ([muteChoices]).
     */
    private fun muteReply(
        kind: AlertKind,
        minutes: List<Int>,
        words: AlertWords,
    ): Notification.Action =
        Notification.Action
            .Builder(
                icon(DsR.drawable.ds_ic_notifications_off),
                ctx.getString(R.string.alert_action_mute),
                broadcast(kind, AlertActionReceiver.MUTE, mutable = true),
            ).addRemoteInput(
                RemoteInput
                    .Builder(AlertActionReceiver.TYPED)
                    .setLabel(ctx.getString(R.string.alert_action_mute_for_label))
                    .setChoices(muteChoices(kind, minutes, words))
                    .setAllowFreeFormInput(true)
                    .setEditChoicesBeforeSending(RemoteInput.EDIT_CHOICES_BEFORE_SENDING_DISABLED)
                    .build(),
            ).build()

    /** *Mute* in a Live Update: opens the lengths sheet over the lock screen (the app's [NavTargets.ACTION_MUTE_ALERT]). */
    private fun muteSheet(kind: AlertKind): Notification.Action {
        val sheet =
            Intent(NavTargets.ACTION_MUTE_ALERT)
                .setPackage(ctx.packageName)
                .putExtra(NavTargets.EXTRA_ALERT_KIND, kind.wire)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi =
            PendingIntent.getActivity(
                ctx,
                SHEET_REQUEST + kind.ordinal,
                sheet,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Action
            .Builder(
                icon(DsR.drawable.ds_ic_notifications_off),
                ctx.getString(R.string.alert_action_mute),
                pi,
            ).build()
    }

    /**
     * The chips, shortest first ("15m", "30m", "1h", "2h"; the person's own first length among them). A chip has no
     * label of its own, so each carries what TalkBack says as a [TtsSpan]: "Low alerts: mute for 30 min". The reply
     * comes back as the chip's text (the span is dropped with it), which [AlertActionReceiver] parses.
     */
    fun muteChoices(
        kind: AlertKind,
        minutes: List<Int>,
        words: AlertWords,
    ): Array<CharSequence> {
        val side = MuteText.sideName(kind.side, words)
        val durations = words.durations
        return minutes
            .distinct()
            .sorted()
            .map { min ->
                val shown = Durations.compact(min.toLong(), durations)
                val spoken = ctx.getString(R.string.alert_mute_choice_a11y, side, Durations.text(min.toLong(), durations))
                SpannableString(shown).apply { setSpan(TtsSpan.TextBuilder(spoken).build(), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE) }
            }.toTypedArray()
    }

    /**
     * *Treatment*, first: the quick treatment dialog with Carbs focused on the low side ([carbs]), the first field
     * (Rapid) on the high side, straight from the button (Android lets only an activity's own PendingIntent start it
     * from a notification action, and asks for the unlock first). The dialog takes the alert's kind as an answer (#39).
     */
    private fun treatment(
        kind: AlertKind,
        carbs: Boolean,
    ): Notification.Action {
        val dialog =
            Intent(NavTargets.ACTION_QUICK_TREATMENT)
                .setPackage(ctx.packageName)
                .putExtra(NavTargets.EXTRA_FOCUS_CARBS, carbs)
                .putExtra(NavTargets.EXTRA_ALERT_KIND, kind.wire)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi =
            PendingIntent.getActivity(
                ctx,
                LOG_REQUEST + kind.ordinal,
                dialog,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Action.Builder(icon(DsR.drawable.ds_ic_add), ctx.getString(R.string.alert_action_treatment), pi).build()
    }

    private fun broadcastAction(
        kind: AlertKind,
        what: String,
        label: Int,
        iconRes: Int,
    ) = Notification.Action.Builder(icon(iconRes), ctx.getString(label), broadcast(kind, what, mutable = false)).build()

    private fun icon(res: Int): Icon = Icon.createWithResource(ctx, res)

    /** A broadcast to the non-exported [AlertActionReceiver]; only the direct reply's is mutable (the system adds the text). */
    private fun broadcast(
        kind: AlertKind,
        what: String,
        mutable: Boolean,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            requestCode(kind, what),
            Intent(ctx, AlertActionReceiver::class.java).setAction(what).putExtra(AlertActionReceiver.KIND, kind.wire),
            PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        /** *Treatment*'s request codes, one per kind (activity PendingIntents: apart from the receiver's broadcasts). */
        private const val LOG_REQUEST = 3000

        /** The lengths sheet's request codes, one per kind. */
        private const val SHEET_REQUEST = 3100

        fun requestCode(
            kind: AlertKind,
            what: String,
        ) = 2000 + kind.ordinal * 16 + AlertActionReceiver.ACTIONS.indexOf(what)
    }
}
