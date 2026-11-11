// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.mauri.sukrara.core.model.UpdateFailure
import app.mauri.sukrara.core.model.UpdateRelease
import app.mauri.sukrara.core.model.UpdateState

/**
 * Where the update notification goes (`AppGraph` wires `:monitor`'s table): its own low-importance [channel] in the
 * channel group [channelGroup] (*Other*), the notification group [group] (Sukrara's info group: never the alerts'),
 * [post] / [cancel] under its one id, and [openAbout], the intent of Settings › About (the notification's tap).
 */
class UpdateNotificationPorts(
    val channel: String,
    val channelGroup: String,
    val group: String,
    val post: (Notification) -> Unit,
    val cancel: () -> Unit,
    val openAbout: () -> Intent,
)

/**
 * The update notification (#188): one at a time, replaced as the update moves on, low importance (no sound, no
 * vibration, never over an alert). Each state has its buttons; tapping it opens Settings › About, where every state
 * shows too.
 */
class UpdateNotifier(
    private val ctx: Context,
    private val ports: UpdateNotificationPorts,
    /** The version running now (the failure text says it keeps running). */
    private val installed: String,
) {
    /** The channel, created on first use (Android keeps the person's choices for it). */
    fun ensureChannel() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ports.channel, ctx.getString(R.string.up_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = ctx.getString(R.string.up_channel_description)
                group = ports.channelGroup
                setShowBadge(false)
            },
        )
    }

    /** Shows [state]; states that need no notification ([UpdateState.Checking], up to date, offline) take it away. */
    fun show(
        state: UpdateState,
        automatic: Boolean = false,
        confirm: Intent? = null,
    ) {
        val n =
            when (state) {
                is UpdateState.Available -> available(state.release)
                is UpdateState.Downloading -> downloading(state)
                is UpdateState.Waiting -> waiting(state.release)
                is UpdateState.Confirm -> confirm(state.release, automatic, confirm)
                is UpdateState.Installing -> installing(state.release)
                is UpdateState.Failed -> failed(state.release, state.reason)
                UpdateState.Checking, UpdateState.UpToDate, UpdateState.Offline -> null
            }
        if (n == null) {
            ports.cancel()
            return
        }
        ensureChannel()
        ports.post(n)
    }

    /** After an automatic update: the new version says what happened, with its release notes. */
    fun updated(release: UpdateRelease) {
        ensureChannel()
        ports.post(
            base()
                .setContentTitle(ctx.getString(R.string.up_updated, release.version))
                .setContentText(ctx.getString(R.string.up_updated_text))
                .addAction(notes(release))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancel() = ports.cancel()

    private fun base(): Notification.Builder =
        Notification
            .Builder(ctx, ports.channel)
            .setSmallIcon(R.drawable.up_ic_update)
            .setGroup(ports.group)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(PendingIntent.getActivity(ctx, REQ_ABOUT, ports.openAbout(), PendingIntent.FLAG_IMMUTABLE))

    private fun available(release: UpdateRelease) =
        base()
            .setContentTitle(ctx.getString(R.string.up_available, release.version))
            .setContentText(ctx.getString(if (release.installable) R.string.up_available_text else R.string.up_available_page))
            .addAction(action(R.string.up_action_update, UpdateReceiver.ACTION_UPDATE))
            .addAction(notes(release))
            .build()

    private fun downloading(state: UpdateState.Downloading) =
        base()
            .setContentTitle(ctx.getString(R.string.up_downloading, state.release.version))
            .setProgress(100, ((state.progress ?: 0f) * 100).toInt(), state.progress == null)
            .setOngoing(true)
            .addAction(action(R.string.up_action_cancel, UpdateReceiver.ACTION_CANCEL))
            .build()

    private fun waiting(release: UpdateRelease) =
        base()
            .setContentTitle(ctx.getString(R.string.up_waiting, release.version))
            .setContentText(ctx.getString(R.string.up_waiting_text))
            .addAction(action(R.string.up_action_cancel, UpdateReceiver.ACTION_CANCEL))
            .build()

    private fun confirm(
        release: UpdateRelease,
        automatic: Boolean,
        intent: Intent?,
    ): Notification {
        // Android's own prompt; without it (the process restarted) the button checks and commits again
        val install =
            intent?.let {
                PendingIntent.getActivity(
                    ctx,
                    REQ_CONFIRM,
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
                ?: broadcast(UpdateReceiver.ACTION_UPDATE)
        return base()
            .setContentTitle(ctx.getString(R.string.up_confirm, release.version))
            .setContentText(ctx.getString(if (automatic) R.string.up_confirm_auto else R.string.up_confirm_text))
            .setContentIntent(install)
            .addAction(Notification.Action.Builder(null, ctx.getString(R.string.up_action_install), install).build())
            .addAction(action(R.string.up_action_cancel, UpdateReceiver.ACTION_CANCEL))
            .build()
    }

    private fun installing(release: UpdateRelease) =
        base()
            .setContentTitle(ctx.getString(R.string.up_installing, release.version))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()

    private fun failed(
        release: UpdateRelease,
        reason: UpdateFailure,
    ): Notification {
        val why =
            when (reason) {
                UpdateFailure.Download -> R.string.up_failed_download
                UpdateFailure.Hash -> R.string.up_failed_hash
                UpdateFailure.Signature -> R.string.up_failed_signature
                UpdateFailure.Install -> R.string.up_failed_install
            }
        val text = ctx.getString(why, installed)
        return base()
            .setContentTitle(ctx.getString(R.string.up_failed, release.version))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .addAction(action(R.string.up_action_retry, UpdateReceiver.ACTION_UPDATE))
            .addAction(notes(release))
            .build()
    }

    private fun notes(release: UpdateRelease): Notification.Action {
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(release.notesUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(ctx, REQ_NOTES, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, ctx.getString(R.string.up_action_notes), pending).build()
    }

    private fun broadcast(action: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            action.hashCode(),
            Intent(ctx, UpdateReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(
        label: Int,
        action: String,
    ) = Notification.Action.Builder(null, ctx.getString(label), broadcast(action)).build()

    private companion object {
        const val REQ_ABOUT = 500
        const val REQ_CONFIRM = 501
        const val REQ_NOTES = 502
    }
}
