// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.monitor.AlertActionReceiver
import app.mauri.sukrara.monitor.R
import app.mauri.sukrara.monitor.notify.NotificationGroups
import app.mauri.sukrara.monitor.notify.NotificationIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The alert notification as Android gets it (#82), beside the channel / category rules of
 * [AlertNotificationAttributesTest]: every alert is a child of the one alerts group, whose summary never alerts and
 * lives exactly as long as a child does; the actions (Mute with typed and one-tap lengths as a direct reply, Remind less /
 * as usual, Unmute, Change time) reach the non-exported receiver with the right action and kind, and only the direct
 * reply's intent is mutable; a muted card is silent and counts down to its end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertNotificationShapeTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val settings =
        AlertSettings().let { it.withRule(AlertKind.FALLING_FAST, it.rule(AlertKind.FALLING_FAST).copy(on = true, alarm = false)) }
    private val now = 1_000L

    @Before fun setUp() {
        // a library test has no launcher icon; the notifier uses the app's
        ctx.applicationInfo.icon = android.R.drawable.star_on
        AlertChannels.ensure(ctx)
    }

    private fun fire(
        kind: AlertKind,
        mgdl: Int,
    ) = Firing(kind, "e-${kind.wire}", 1, AlertReading(t = now, mgdl = mgdl, rate = -2.5), threshold = 70.0, at = now)

    private fun active(id: Int): Notification? = nm.activeNotifications.firstOrNull { it.id == id }?.notification

    private fun posted(group: AlertGroup): Notification = active(AlertNotifier.idOf(group)).also(::assertNotNull)!!

    private fun summary(): Notification? = active(NotificationIds.ALERTS_SUMMARY)

    private fun label(id: Int) = ctx.getString(id)

    /** The receiver action a notification action's broadcast carries, its kind, and whether the system may add to it. */
    private fun target(pi: PendingIntent): Triple<String?, String?, Boolean> {
        val shadow = shadowOf(pi)
        assertTrue("actions go to the app's own receiver", shadow.isBroadcast)
        val intent = shadow.savedIntent
        assertEquals(AlertActionReceiver::class.java.name, intent.component?.className)
        return Triple(intent.action, intent.getStringExtra(AlertActionReceiver.KIND), shadow.flags and PendingIntent.FLAG_MUTABLE != 0)
    }

    private fun Notification.action(label: String): Notification.Action =
        actions.orEmpty().firstOrNull { it.title.toString() == label }
            ?: throw AssertionError("no '$label' action in ${actions.orEmpty().map { it.title }}")

    @Test fun `an alert is a child of the alerts group, after a summary that never alerts`() {
        AlertNotifier(ctx).show(fire(AlertKind.LOW, 62), settings, silent = false, who = null, relaxedMin = 0)

        val child = posted(AlertGroup.LEVEL)
        assertEquals(NotificationGroups.Group.ALERTS.key, child.group)
        assertEquals("a ringing alert rings as a child", Notification.GROUP_ALERT_ALL, child.groupAlertBehavior)
        assertEquals(Notification.VISIBILITY_PUBLIC, child.visibility)
        assertNotNull("a tap opens the app", child.contentIntent)

        val s = summary()
        assertNotNull("the group's summary is posted with its first child", s)
        assertEquals(NotificationGroups.Group.ALERTS.key, s!!.group)
        assertTrue(s.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertEquals(Notification.GROUP_ALERT_CHILDREN, s.groupAlertBehavior)
        assertTrue(s.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test fun `the summary stays while one alert is left and goes with the last one`() {
        val notifier = AlertNotifier(ctx)
        notifier.show(fire(AlertKind.LOW, 62), settings, silent = false, who = null, relaxedMin = 0)
        notifier.show(fire(AlertKind.FALLING_FAST, 62), settings, silent = false, who = null, relaxedMin = 0)
        assertEquals("one summary for both", 1, nm.activeNotifications.count { it.id == NotificationIds.ALERTS_SUMMARY })

        notifier.clear(AlertGroup.LEVEL)
        assertNull(active(AlertNotifier.idOf(AlertGroup.LEVEL)))
        assertNotNull("one child left: the summary stays", summary())

        notifier.clear(AlertGroup.RATE)
        assertNull("no child left: a summary alone would be bundled with anything", summary())
    }

    @Test fun `a swiped-away alert takes the summary with it (its delete intent tidies the group)`() {
        AlertNotifier(ctx).show(fire(AlertKind.LOW, 62), settings, silent = false, who = null, relaxedMin = 0)
        val child = posted(AlertGroup.LEVEL)
        assertEquals(Triple(AlertActionReceiver.TIDY, "low", false), target(child.deleteIntent))

        nm.cancel(AlertNotifier.idOf(AlertGroup.LEVEL)) // the swipe
        NotificationGroups.tidy(ctx, NotificationGroups.Group.ALERTS) // what TIDY does
        assertNull(summary())
    }

    @Test fun `Mute is a direct reply with one-tap lengths and a typed one, and only its intent is mutable`() {
        AlertNotifier(ctx).show(fire(AlertKind.LOW, 62), settings, silent = false, who = null, relaxedMin = 0)
        val mute = posted(AlertGroup.LEVEL).action(label(R.string.alert_action_mute))

        val input = mute.remoteInputs.single()
        assertEquals(AlertActionReceiver.TYPED, input.resultKey)
        assertTrue("a length of one's own can be typed", input.allowFreeFormInput)
        assertEquals("a chip sends at once", RemoteInput.EDIT_CHOICES_BEFORE_SENDING_DISABLED, input.editChoicesBeforeSending)
        assertEquals(settings.muteChoices().distinct().size, input.choices.size)
        assertEquals(Triple(AlertActionReceiver.MUTE, "low", true), target(mute.actionIntent))
    }

    @Test fun `Remind less only where it slows the alert down, Remind as usual once relaxed`() {
        val notifier = AlertNotifier(ctx)
        // low repeats every 5 min, slower than nothing: Remind less (every 30 min) helps
        notifier.show(fire(AlertKind.LOW, 62), settings, silent = false, who = null, relaxedMin = 0)
        val low = posted(AlertGroup.LEVEL)
        assertEquals(
            listOf(label(R.string.alert_action_mute), label(R.string.alert_action_remind_less)),
            low.actions.map { it.title.toString() },
        )
        assertEquals(
            Triple(AlertActionReceiver.REMIND_LESS, "low", false),
            target(low.action(label(R.string.alert_action_remind_less)).actionIntent),
        )

        // already relaxed: the way back
        notifier.show(fire(AlertKind.LOW, 62), settings, silent = false, who = null, relaxedMin = 30)
        val relaxed = posted(AlertGroup.LEVEL)
        assertEquals(
            listOf(label(R.string.alert_action_mute), label(R.string.alert_action_remind_usual)),
            relaxed.actions.map { it.title.toString() },
        )
        assertEquals(
            Triple(AlertActionReceiver.REMIND_AS_USUAL, "low", false),
            target(relaxed.action(label(R.string.alert_action_remind_usual)).actionIntent),
        )

        // no data repeats every 60 min: relaxing to 30 would make it louder, so only Mute
        notifier.show(fire(AlertKind.NO_DATA, 0), settings, silent = false, who = null, relaxedMin = 0)
        assertEquals(listOf(label(R.string.alert_action_mute)), posted(AlertGroup.NO_DATA).actions.map { it.title.toString() })
    }

    @Test fun `a muted card is silent, counts down to its end and offers Unmute and Change time`() {
        val until = now + 30 * 60
        AlertNotifier(ctx).showMuted(AlertGroup.LEVEL, Side.LOW, AlertKind.LOW, until, settings, now)
        val muted = posted(AlertGroup.LEVEL)

        assertEquals(NotificationGroups.Group.ALERTS.key, muted.group)
        assertEquals("silent: defers to the summary, which never alerts", Notification.GROUP_ALERT_SUMMARY, muted.groupAlertBehavior)
        assertTrue(muted.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(muted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(muted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(until * 1000, muted.`when`)
        assertEquals("gone when the mute ends", 30 * 60 * 1000L, muted.timeoutAfter)
        assertNull("never full screen", muted.fullScreenIntent)

        assertEquals(
            Triple(AlertActionReceiver.UNMUTE, "low", false),
            target(muted.action(label(R.string.alert_action_unmute)).actionIntent),
        )
        val change = muted.action(label(R.string.alert_action_change))
        assertEquals(AlertActionReceiver.TYPED, change.remoteInputs.single().resultKey)
        assertEquals(Triple(AlertActionReceiver.MUTE, "low", true), target(change.actionIntent))
    }

    @Test fun `a re-shown alert that doesn't ring defers to the summary and never goes full screen`() {
        val alarm = settings.withRule(AlertKind.LOW, settings.rule(AlertKind.LOW).copy(alarm = true))
        AlertNotifier(ctx).show(fire(AlertKind.LOW, 62), alarm, silent = true, who = null, relaxedMin = 0)
        val n = posted(AlertGroup.LEVEL)
        assertEquals(Notification.GROUP_ALERT_SUMMARY, n.groupAlertBehavior)
        assertNull(n.fullScreenIntent)
    }

    @Test fun `a test alert has no actions that could touch the alert runtime and times out`() {
        AlertNotifier(ctx).showTest(AlertKind.LOW, settings)
        val test = active(NotificationIds.ALERT_TEST)!!
        assertTrue(test.actions.isNullOrEmpty())
        assertEquals(AlertNotifier.TEST_TIMEOUT_MS, test.timeoutAfter)
        assertFalse("a test never replaces a real alert", nm.activeNotifications.any { it.id == AlertNotifier.idOf(AlertGroup.LEVEL) })
    }
}
