// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.monitor.AlertActionReceiver
import app.mauri.sukrara.monitor.LiveNotification
import app.mauri.sukrara.monitor.R
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.LiveNotificationContent
import app.mauri.sukrara.monitor.notify.NotificationIds
import app.mauri.sukrara.monitor.notify.NotificationPrefs
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
import java.time.Duration
import java.time.Instant

/**
 * docs/36, SPEC §1 "Showing an alert": on Android the glucose notification IS the alert while one is up. Read back
 * from the notification manager for every state: the channel (the kind's on a ring and through the ring window, the
 * silent one otherwise), the flags (only-alert-once except on a ring, ongoing), the title, the buttons and their order
 * (never an ellipsis), the full-screen intent only on silent → alarm, the swipe that hides the alert and comes back
 * silent, the muted note, and *Alerts on my watch*: two notifications then, the glucose one without the alert.
 * (The Live Update's promotion needs Android 16 QPR2+, which Robolectric doesn't have: QA.md and the docs/36 pictures
 * check it on the emulators.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OneNotificationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val now = Instant.now()
    private val settings = AlertSettings()
    private val alarmSettings = settings.withRule(AlertKind.LOW, settings.rule(AlertKind.LOW).copy(alarm = true))

    @Before fun setUp() {
        // a library test has no launcher icon; the notifier uses the app's
        ctx.applicationInfo.icon = android.R.drawable.star_on
        Channels.createCore(ctx)
        AlertChannels.ensure(ctx)
    }

    private fun state(mgdl: Int = 62): GlucoseState {
        val reading = Reading(now.minusSeconds(30), mgdl, -2.0, SourceId.Libre3)
        return GlucoseState.Initial.copy(
            role = Role.Wearer,
            latest = reading,
            recent = listOf(reading),
            sensor = SensorState.Active(null),
            monitoringStartedAt = now - Duration.ofHours(1),
        )
    }

    // the monitoring service runs (it owns the glucose notification)
    private val live = LiveNotification(ctx, { state() }, serviceRunning = { true })

    private fun presenter(watch: Boolean = false) = GlucoseAlertPresenter(AlertNotifier(ctx, canLog = { true }), live, watch = { watch })

    private fun fire(
        kind: AlertKind,
        mgdl: Int = 62,
        id: String = "e-${kind.wire}",
    ) = Firing(kind, id, 1, AlertReading(t = now.epochSecond - 30, mgdl = mgdl, rate = -2.0), threshold = 70.0, at = now.epochSecond)

    /** The service has posted the glucose notification ([prefs]: the card by default); waits for its post to land. */
    private fun serviceShows(prefs: NotificationPrefs = NotificationPrefs(chip = false)) {
        live.post(LiveNotificationContent.from(state(), now, LiveNotification::face, prefs), now)
        val until = System.currentTimeMillis() + 3_000
        while (glucose() == null && System.currentTimeMillis() < until) Thread.sleep(10)
        Thread.sleep(50)
    }

    private fun glucose(): Notification? = nm.activeNotifications.firstOrNull { it.id == NotificationIds.GLUCOSE }?.notification

    private fun posted(): Notification = glucose().also(::assertNotNull)!!

    private fun title(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun titles(n: Notification) = n.actions.orEmpty().map { it.title.toString() }

    private fun label(id: Int) = ctx.getString(id)

    private fun ringing(n: Notification) = n.flags and Notification.FLAG_ONLY_ALERT_ONCE == 0

    @Test fun `a ring is the glucose notification on the kind's channel, alerting, with the alert's title and buttons`() {
        serviceShows()
        presenter().show(fire(AlertKind.LOW), settings, silent = false, who = null, relaxedMin = 0)
        val n = posted()
        assertEquals("the kind's own channel: its sound, volume, Do Not Disturb", AlertChannels.id(ctx, AlertKind.LOW, false), n.channelId)
        assertTrue("a ring alerts every time", ringing(n))
        assertTrue("still the ongoing glucose notification", n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.CATEGORY_ALARM, n.category)
        assertEquals(label(R.string.alert_headline_low), title(n))
        assertEquals(
            "Treatment, Remind less, Mute",
            listOf(label(R.string.alert_action_treatment), label(R.string.alert_action_remind_less), label(R.string.alert_action_mute)),
            titles(n),
        )
        assertTrue("no ellipsis", titles(n).none { it.contains('…') || it.endsWith("...") })
        assertTrue(
            "not promoted: Mute keeps its one-tap lengths",
            n.actions
                .last()
                .remoteInputs
                ?.isNotEmpty() == true,
        )
        assertNull("only an alarm-level kind opens full screen", n.fullScreenIntent)
        assertEquals("no separate alert on the phone", 1, nm.activeNotifications.size)
    }

    @Test fun `posts during the ring window stay on the alert's channel without alerting, then it is silent with the alert`() {
        serviceShows()
        val p = presenter()
        p.show(fire(AlertKind.LOW), settings, silent = false, who = null, relaxedMin = 0)
        val content = LiveNotificationContent.from(state(61), now, LiveNotification::face, NotificationPrefs(chip = false))
        val update = live.build(content, now)
        assertEquals("a switch to the silent channel would stop the sound", AlertChannels.id(ctx, AlertKind.LOW, false), update.channelId)
        assertFalse(ringing(update))

        // a clear in the window: posted at once on the silent channel (the sound stops), the alert gone
        p.clear(AlertGroup.LEVEL)
        val after = posted()
        assertEquals(Channels.GLUCOSE, after.channelId)
        assertFalse(ringing(after))
        assertTrue(titles(after).isEmpty())
    }

    @Test fun `full screen only when an alarm-level kind comes from silent`() {
        serviceShows()
        val p = presenter()
        // where Android allows full-screen intents at all (Robolectric answers as the platform does)
        val allowed = nm.canUseFullScreenIntent()
        p.show(fire(AlertKind.LOW, id = "a"), alarmSettings, silent = false, who = null, relaxedMin = 0)
        assertEquals("silent → alarm", allowed, posted().fullScreenIntent != null)
        p.show(fire(AlertKind.LOW, id = "b"), alarmSettings, silent = false, who = null, relaxedMin = 0)
        assertNull("a re-ring while it still sounds: Android wouldn't start it again", posted().fullScreenIntent)
        assertTrue("but it rings", ringing(posted()))
        live.alert(null, GlucoseAlertSink.Post.QUIET)
        p.show(fire(AlertKind.LOW, id = "c"), alarmSettings, silent = false, who = null, relaxedMin = 0)
        assertEquals("from silent again", allowed, posted().fullScreenIntent != null)
    }

    @Test fun `a silent show is the alert on the silent channel`() {
        serviceShows()
        val p = presenter()
        p.show(fire(AlertKind.LOW), settings, silent = true, who = null, relaxedMin = 0)
        val n = live.build(LiveNotificationContent.from(state(), now, LiveNotification::face, NotificationPrefs(chip = false)), now)
        assertEquals(Channels.GLUCOSE, n.channelId)
        assertFalse(ringing(n))
        assertEquals(label(R.string.alert_headline_low), title(n))
        assertNull(n.fullScreenIntent)
    }

    @Test fun `the swipe answers the alert, hides it until the next ring and comes back silent`() {
        serviceShows()
        val p = presenter()
        p.show(fire(AlertKind.LOW), settings, silent = false, who = null, relaxedMin = 0)
        val delete = shadowOf(posted().deleteIntent).savedIntent
        assertEquals(AlertActionReceiver.TIDY, delete.action)
        assertEquals("low", delete.getStringExtra(AlertActionReceiver.KIND))
        assertEquals("e-low", delete.getStringExtra(AlertActionReceiver.FIRING))
        assertTrue(delete.getBooleanExtra(AlertActionReceiver.GLUCOSE, false))

        nm.cancel(NotificationIds.GLUCOSE) // the swipe
        p.swiped(AlertKind.LOW, "e-low") // what TIDY does after the answer
        val back = posted()
        assertEquals("a re-post on the alert's channel would ring again", Channels.GLUCOSE, back.channelId)
        assertFalse(ringing(back))
        assertTrue("no alert part until the next ring", titles(back).isEmpty())
        assertFalse(p.isShowing(AlertGroup.LEVEL))

        // the swipe of an older firing doesn't hide a newer ring
        p.show(fire(AlertKind.LOW, id = "newer"), settings, silent = false, who = null, relaxedMin = 0)
        p.swiped(AlertKind.LOW, "e-low")
        assertEquals(label(R.string.alert_headline_low), title(posted()))
    }

    @Test fun `a muted side shows its note with only Treatment and Unmute, silently`() {
        serviceShows()
        val p = presenter()
        p.show(fire(AlertKind.LOW), settings, silent = false, who = null, relaxedMin = 0)
        p.showMuted(AlertGroup.LEVEL, Side.LOW, AlertKind.LOW, now.epochSecond + 1800, settings, now.epochSecond)
        val n = posted()
        assertEquals("the mute stops the sound at once", Channels.GLUCOSE, n.channelId)
        assertFalse(ringing(n))
        assertEquals(listOf(label(R.string.alert_action_treatment), label(R.string.alert_action_unmute)), titles(n))
    }

    @Test fun `two groups, the ringing one in front and the other a line`() {
        serviceShows()
        val p = presenter()
        val s = settings.withRule(AlertKind.FALLING_FAST, settings.rule(AlertKind.FALLING_FAST).copy(on = true))
        p.show(fire(AlertKind.LOW), s, silent = false, who = null, relaxedMin = 0)
        p.show(fire(AlertKind.FALLING_FAST), s, silent = false, who = null, relaxedMin = 0)
        val n = posted()
        assertEquals(AlertChannels.id(ctx, AlertKind.FALLING_FAST, false), n.channelId)
        assertEquals(label(R.string.alert_headline_falling_fast), title(n))
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.contains(label(R.string.alert_headline_low)))
        assertEquals("still one notification", 1, nm.activeNotifications.size)
    }

    @Test fun `the Live Update says the alert and the value without a unit`() {
        serviceShows(NotificationPrefs(chip = true, promotionAllowed = true))
        presenter().show(fire(AlertKind.LOW), settings, silent = false, who = null, relaxedMin = 0)
        val t = title(posted())
        assertTrue(t, t.startsWith("62 "))
        assertTrue(t, t.contains(label(R.string.alert_headline_low)))
        assertFalse("no unit anywhere", t.contains("mg/dL"))
    }

    @Test fun `Alerts on my watch makes the alert a notification of its own and the glucose one carries none`() {
        serviceShows()
        val p = presenter(watch = true)
        p.show(fire(AlertKind.LOW), settings, silent = false, who = null, relaxedMin = 0)
        val alert = nm.activeNotifications.firstOrNull { it.id == AlertNotifier.idOf(AlertGroup.LEVEL) }?.notification
        assertNotNull("the watch gets a separate notification", alert)
        assertEquals(label(R.string.alert_headline_low), title(alert!!))
        assertEquals(
            listOf(label(R.string.alert_action_treatment), label(R.string.alert_action_remind_less), label(R.string.alert_action_mute)),
            titles(alert),
        )
        val g = posted()
        assertEquals(Channels.GLUCOSE, g.channelId)
        assertTrue("no alert context on the glucose notification", titles(g).isEmpty())
    }

    @Test fun `the plain stand-in stays a notification of its own`() {
        serviceShows()
        presenter().showPlain(fire(AlertKind.LOW), settings, silent = false, who = null)
        assertNotNull(nm.activeNotifications.firstOrNull { it.id == AlertNotifier.idOf(AlertGroup.LEVEL) })
        assertEquals(
            listOf(label(R.string.monitor_action_open_app)),
            titles(
                nm.activeNotifications
                    .first {
                        it.id ==
                            AlertNotifier.idOf(AlertGroup.LEVEL)
                    }.notification,
            ),
        )
    }
}
