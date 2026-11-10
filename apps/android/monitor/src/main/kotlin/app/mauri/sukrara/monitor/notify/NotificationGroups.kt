// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import app.mauri.sukrara.monitor.R

/**
 * Glucose alerts are never bundled with other notifications (0.11.53 lesson). Android 16+ force-groups an app's
 * notifications per shade section (AOSP `GroupHelper`): ≥ 2 *ungrouped* notifications go into one system bundle, and
 * ≥ 3 *sparse* app groups (1–2 children each) are merged too. So every alerting notification of Sukrara is in one of
 * two app groups, each with its own summary: [Group.ALERTS] (every alert and muted card; two alerts at once still
 * each ring and show their own title, the summary never alerts: `GROUP_ALERT_CHILDREN`) and [Group.INFO] (sensor end
 * and its countdowns, status). One group per alert would be sparse groups and get merged with anything (measured on
 * API 36/37). The ongoing glucose notification is the one ungrouped notification: a second one (a sensor countdown,
 * #156) and Android bundles both. A group child can still be a Live Update (only a group summary can't). A post in
 * [Group.INFO] that would sound waits for [SoundGap], so it never mutes an alert.
 */
object NotificationGroups {
    enum class Group(val key: String, val summaryId: Int, private val nameRes: Int) {
        ALERTS("app.mauri.sukrara.ALERTS", NotificationIds.ALERTS_SUMMARY, R.string.monitor_group_alerts),
        INFO("app.mauri.sukrara.INFO", NotificationIds.INFO_SUMMARY, R.string.notification_group_info),
        ;

        fun name(ctx: Context): String = ctx.getString(nameRes)
    }

    /**
     * Posts [n] (built with `setGroup(group.key)`) after its group's summary: an alert at once, an info notification that
     * would sound after [SoundGap]'s wait (a failure then is logged: the caller has moved on).
     */
    fun post(ctx: Context, id: Int, n: Notification, group: Group, channelId: String) {
        check(n.group == group.key) { "notification $id is not in ${group.key}" }
        if (group == Group.ALERTS) return notify(ctx, id, n, group, channelId)
        SoundGap.shared.submit(id, SoundGap.sounds(ctx, channelId)) {
            runCatching { notify(ctx, id, n, group, channelId) }.onFailure { Log.w(TAG, "post $id: ${it.javaClass.simpleName}") }
        }
    }

    private fun notify(ctx: Context, id: Int, n: Notification, group: Group, channelId: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.notify(group.summaryId, summary(ctx, group, channelId, n.smallIcon.resId))
        nm.notify(id, n)
    }

    /**
     * Cancels [id] (and a post of it still waiting in [SoundGap]), and its group's summary once no child is left (a
     * summary alone counts as ungrouped).
     */
    fun cancel(ctx: Context, id: Int, group: Group) {
        SoundGap.shared.cancel(id)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(id)
        tidy(ctx, group, except = id)
    }

    /** Removes [group]'s summary when it has no child (also after a swipe: the children's delete intent calls this). */
    fun tidy(ctx: Context, group: Group, except: Int? = null) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val children = nm.activeNotifications.count { it.notification.group == group.key && it.id != group.summaryId && it.id != except }
        if (children == 0) nm.cancel(group.summaryId)
    }

    private fun summary(ctx: Context, group: Group, channelId: String, icon: Int): Notification =
        Notification
            .Builder(ctx, channelId)
            .setSmallIcon(icon)
            .setContentTitle(group.name(ctx))
            .setGroup(group.key)
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .build()
}

private const val TAG = "Sukrara.notify"
