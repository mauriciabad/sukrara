// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import app.mauri.sukrara.core.model.storage.StateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * What the lock-screen countdown closed on (#100): the form as typed and when the entries happen, kept so a slow hand
 * during a low never loses the entry. Offered on the next unlock (a notification, *Treatment not saved*) and whenever
 * the quick dialog opens next, for [KEEP] after it was kept. Typed state `treatment-draft`; nothing kept = [EMPTY].
 */
@Serializable
data class TreatmentDraft(
    val rapid: String = "",
    val long: String = "",
    val carbs: String = "",
    val note: String = "",
    /** When the entries happen, epoch ms (the time was resolved when the countdown ended: "now" means then). */
    val atMs: Long = 0,
    /** When it was kept, epoch ms; 0 = no draft. */
    val keptMs: Long = 0,
) {
    val at: Instant get() = Instant.ofEpochMilli(atMs)
    val keptAt: Instant get() = Instant.ofEpochMilli(keptMs)

    /** Still offered at [now]: kept, and not longer ago than [KEEP]. */
    fun offered(now: Instant): Boolean = keptMs > 0 && Duration.between(keptAt, now) < KEEP

    /** The form filled with it, at its time (Save stores it then; *Now* moves it to the moment of saving). */
    fun form(): TreatmentFormState = TreatmentFormState(rapid, long, carbs, note, at = at)

    companion object {
        val EMPTY = TreatmentDraft()

        /** How long a draft is offered: an entry kept longer is rather typed again. */
        val KEEP: Duration = Duration.ofHours(12)

        /** [form] as a draft kept at [now]; null when nothing was typed (nothing to keep). */
        fun of(
            form: TreatmentFormState,
            now: Instant,
        ): TreatmentDraft? =
            if (!form.hasInput) {
                null
            } else {
                TreatmentDraft(form.rapid, form.long, form.carbs, form.note, (form.whenAt(now) ?: now).toEpochMilli(), now.toEpochMilli())
            }
    }
}

/** A draft store that lives as long as the process (tests, previews; the app gives the typed store). */
internal class MemoryDraftStore : StateStore<TreatmentDraft> {
    override val data = MutableStateFlow(TreatmentDraft.EMPTY)

    override suspend fun update(transform: (TreatmentDraft) -> TreatmentDraft) {
        data.value = transform(data.value)
    }
}

/** *Treatment not saved*: the notification that offers a kept draft after the unlock. */
interface DraftNotice {
    /** Shows it with [summary] (the entries as typed, e.g. *4.5u rapid insulin · 30g carbs*; empty = none valid). */
    fun show(summary: String)

    fun clear()

    object None : DraftNotice {
        override fun show(summary: String) = Unit

        override fun clear() = Unit
    }
}

/** Where the draft notification goes (`AppGraph` wires the `:monitor` channel and Sukrara's info group). */
class DraftNotificationPorts(
    val channel: String,
    val group: String,
    val post: (Notification) -> Unit,
    val cancel: () -> Unit,
)

/**
 * The Android notification: *Treatment not saved* · *4.5u rapid insulin · 30g carbs. Tap to finish saving it.*; a tap
 * opens the quick dialog with the draft (over the lock screen it counts down again, as from any lock-screen entry, and a
 * second timeout keeps it again). Silent (its channel), private on the lock screen (only the title shows there), gone
 * with the draft or after [TreatmentDraft.KEEP].
 */
class AndroidDraftNotice(
    private val ctx: Context,
    private val ports: DraftNotificationPorts,
) : DraftNotice {
    override fun show(summary: String) {
        val title = ctx.getString(R.string.treatment_draft_title)
        val text =
            if (summary.isEmpty()) {
                ctx.getString(
                    R.string.treatment_draft_tap,
                )
            } else {
                ctx.getString(R.string.treatment_draft_text, summary)
            }
        val open =
            PendingIntent.getActivity(
                ctx,
                REQUEST,
                QuickTreatmentIntents.open(ctx, QuickSource.Draft),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val public =
            Notification
                .Builder(ctx, ports.channel)
                .setSmallIcon(ctx.applicationInfo.icon)
                .setContentTitle(title)
                .build()
        val n =
            Notification
                .Builder(ctx, ports.channel)
                .setSmallIcon(ctx.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .setTimeoutAfter(TreatmentDraft.KEEP.toMillis())
                .setGroup(ports.group)
                .build()
        ports.post(n)
    }

    override fun clear() = ports.cancel()

    private companion object {
        const val REQUEST = 430
    }
}
