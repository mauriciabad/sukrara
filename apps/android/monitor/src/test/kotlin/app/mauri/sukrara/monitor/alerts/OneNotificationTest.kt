// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.widget.FrameLayout
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
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.monitor.AlertActionReceiver
import app.mauri.sukrara.monitor.LiveNotification
import app.mauri.sukrara.monitor.R
import app.mauri.sukrara.monitor.notify.CardFrame
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
 * silent, the muted note, and *Separate alert notifications*: two notifications then, the glucose one without the alert.
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

    /** The presenter's clock (elapsed ms): moved by hand to leave a ring window. */
    private var elapsed = 1_000_000L

    /** What the presenter runs later (the end of a full-screen ring's own notification): run by hand. */
    private val later = mutableListOf<() -> Unit>()

    private fun presenter(
        watch: Boolean = false,
        fullScreen: Boolean = true,
    ) = GlucoseAlertPresenter(
        AlertNotifier(ctx, canLog = { true }),
        live,
        watch = { watch },
        clock = { elapsed },
        canFullScreen = { fullScreen },
        later = { _, run -> later += run },
    )

    private fun separate(group: AlertGroup = AlertGroup.LEVEL): Notification? =
        nm.activeNotifications.firstOrNull { it.id == AlertNotifier.idOf(group) }?.notification

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

    /** The alert's headline in the glucose notification's title (after the value while the card isn't drawn). */
    private fun assertHeadline(
        id: Int,
        n: Notification,
    ) = assertTrue(title(n), title(n).contains(label(id)))

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
        assertHeadline(R.string.alert_headline_low, n)
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

    /**
     * Android 16 starts a full-screen intent only for a notification it adds, never for an update of the glucose
     * notification (measured on the Android 16 QPR2 emulator): the alarm-level ring is its own notification for the
     * ring window.
     */
    @Test fun `an alarm-level ring that opens full screen is a notification Android adds, for its ring window`() {
        serviceShows()
        val p = presenter()
        p.show(fire(AlertKind.LOW, id = "a"), alarmSettings, silent = false, who = null, relaxedMin = 0)
        val own = separate()
        assertNotNull("a notification Android adds", own)
        // with the platform's answer (Robolectric refuses full-screen intents)
        assertEquals("its full-screen intent", nm.canUseFullScreenIntent(), own!!.fullScreenIntent != null)
        assertEquals(AlertChannels.id(ctx, AlertKind.LOW, true), own.channelId)
        assertTrue("it rings", own.flags and Notification.FLAG_ONLY_ALERT_ONCE == 0)
        assertEquals(label(R.string.alert_headline_low), title(own))
        val until = System.currentTimeMillis() + 3_000
        while (!title(posted()).contains(label(R.string.alert_headline_low)) && System.currentTimeMillis() < until) Thread.sleep(10)
        val g = posted()
        assertNull("the glucose notification never carries one", g.fullScreenIntent)
        assertEquals("it shows the alert silently", Channels.GLUCOSE, g.channelId)
        assertHeadline(R.string.alert_headline_low, g)

        // the ring window ends: one notification again, the glucose one with the alert
        later.toList().forEach { it() }
        assertNull(separate())
        assertHeadline(R.string.alert_headline_low, posted())
    }

    @Test fun `without full screen an alarm-level ring is the glucose notification's`() {
        serviceShows()
        presenter(fullScreen = false).show(fire(AlertKind.LOW), alarmSettings, silent = false, who = null, relaxedMin = 0)
        assertNull(separate())
        assertEquals(AlertChannels.id(ctx, AlertKind.LOW, true), posted().channelId)
        assertTrue(ringing(posted()))
    }

    @Test fun `a mute or a swipe of the full-screen ring takes its own notification away at once`() {
        serviceShows()
        val p = presenter()
        p.show(fire(AlertKind.LOW, id = "a"), alarmSettings, silent = false, who = null, relaxedMin = 0)
        p.showMuted(AlertGroup.LEVEL, Side.LOW, AlertKind.LOW, now.epochSecond + 1800, alarmSettings, now.epochSecond)
        assertNull("the mute stops its sound", separate())

        p.show(fire(AlertKind.LOW, id = "b"), alarmSettings, silent = false, who = null, relaxedMin = 0)
        nm.cancel(AlertNotifier.idOf(AlertGroup.LEVEL)) // the swipe of its own notification
        p.swiped(AlertKind.LOW, "b") // what TIDY does
        assertTrue("seen: the glucose notification drops it too", titles(posted()).isEmpty())
        later.toList().forEach { it() }
        assertNull(separate())
    }

    @Test fun `a silent show is the alert on the silent channel`() {
        serviceShows()
        val p = presenter()
        p.show(fire(AlertKind.LOW), settings, silent = true, who = null, relaxedMin = 0)
        val n = live.build(LiveNotificationContent.from(state(), now, LiveNotification::face, NotificationPrefs(chip = false)), now)
        assertEquals(Channels.GLUCOSE, n.channelId)
        assertFalse(ringing(n))
        assertHeadline(R.string.alert_headline_low, n)
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
        assertHeadline(R.string.alert_headline_low, posted())
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

    @Test fun `two groups ringing on one reading, the one that must be heard stays in front and keeps its channel`() {
        serviceShows()
        val p = presenter(fullScreen = false)
        val s = settings.withRule(AlertKind.FALLING_FAST, settings.rule(AlertKind.FALLING_FAST).copy(on = true))
        // the runtime's ring order: the very low (alarm-level) first, falling fast right after
        p.show(fire(AlertKind.VERY_LOW, mgdl = 50), s, silent = false, who = null, relaxedMin = 0)
        p.show(fire(AlertKind.FALLING_FAST, mgdl = 50), s, silent = false, who = null, relaxedMin = 0)
        // the line comes with the next post through the gate
        val until = System.currentTimeMillis() + 3_000
        while (posted().extras.getCharSequence(Notification.EXTRA_TEXT) == null && System.currentTimeMillis() < until) Thread.sleep(10)
        val n = posted()
        assertFalse("no second ring: Android would mute it anyway", ringing(n))
        assertEquals(
            "the alarm's channel: its volume and its Do Not Disturb pass",
            AlertChannels.id(ctx, AlertKind.VERY_LOW, true),
            n.channelId,
        )
        assertHeadline(R.string.alert_headline_very_low, n)
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.contains(label(R.string.alert_headline_falling_fast)))
        assertEquals("still one notification", 1, nm.activeNotifications.size)
    }

    @Test fun `two groups, a later ring in front and the other a line`() {
        serviceShows()
        val p = presenter()
        val s = settings.withRule(AlertKind.FALLING_FAST, settings.rule(AlertKind.FALLING_FAST).copy(on = true))
        p.show(fire(AlertKind.LOW), s, silent = false, who = null, relaxedMin = 0)
        elapsed += LiveNotification.RING_WINDOW_MS
        p.show(fire(AlertKind.FALLING_FAST), s, silent = false, who = null, relaxedMin = 0)
        val n = posted()
        assertEquals(AlertChannels.id(ctx, AlertKind.FALLING_FAST, false), n.channelId)
        assertTrue(ringing(n))
        assertHeadline(R.string.alert_headline_falling_fast, n)
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, text.contains(label(R.string.alert_headline_low)))
    }

    /** The card's pictures aren't drawn while the screen is off (a ring at night) or when drawing fails: text then. */
    @Test fun `the card without its pictures still says the value with the alert`() {
        serviceShows()
        presenter(fullScreen = false).show(fire(AlertKind.LOW), settings, silent = false, who = null, relaxedMin = 0)
        val content = LiveNotificationContent.from(state(), now, LiveNotification::face, NotificationPrefs(chip = false))
        assertEquals(LiveNotificationContent.Layout.CARD, content.layout)
        val t = title(live.build(content, now, frame = null, LiveNotification.Mode.RING))
        assertTrue(t, t.startsWith("62 "))
        assertTrue(t, t.contains(label(R.string.alert_headline_low)))
    }

    @Test fun `the Live Update says the alert and the value without a unit`() {
        serviceShows(NotificationPrefs(chip = true, promotionAllowed = true))
        presenter().show(fire(AlertKind.LOW), settings, silent = false, who = null, relaxedMin = 0)
        val t = title(posted())
        assertTrue(t, t.startsWith("62 "))
        assertTrue(t, t.contains(label(R.string.alert_headline_low)))
        assertFalse("no unit anywhere", t.contains("mg/dL"))
    }

    @Test fun `Separate alert notifications make the alert a notification of its own and the glucose one carries none`() {
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

    /**
     * The person turned the glucose notification's channel off: Android drops every post on it, so a ring would stay
     * as it rang (never updated, muted or cleared). The alerts are notifications of their own then, and what is up
     * moves there at once.
     */
    @Test fun `with the glucose channel turned off the alerts are notifications of their own`() {
        serviceShows()
        val p =
            GlucoseAlertPresenter(
                AlertNotifier(ctx, canLog = { true }),
                live,
                watch = { !live.canCarry() },
                clock = { elapsed },
                canFullScreen = { false },
                later = { _, run -> later += run },
            )
        p.show(fire(AlertKind.LOW), settings, silent = false, who = null, relaxedMin = 0)
        assertTrue(live.canCarry())
        assertNull(separate())

        nm.deleteNotificationChannel(Channels.GLUCOSE)
        nm.createNotificationChannel(NotificationChannel(Channels.GLUCOSE, "Glucose", NotificationManager.IMPORTANCE_NONE))
        assertFalse(live.canCarry())
        // the runtime's next look (a reading) finds it up, as a notification of its own
        assertTrue(p.isShowing(AlertGroup.LEVEL))
        val own = separate()
        assertNotNull("moved at once", own)
        assertEquals(label(R.string.alert_headline_low), title(own!!))
        assertEquals("silently: it already rang", Notification.GROUP_ALERT_SUMMARY, own.groupAlertBehavior)
    }

    /** 0.x crash on the emulator: a RemoteViews call a TextView refuses kills the foreground service. */
    @Test fun `the card with an alert inflates its views as System UI does`() {
        val content = LiveNotificationContent.from(state(), now, LiveNotification::face, NotificationPrefs(chip = false))
        val frame = CardFrame(state(), emptyList(), GraphConfig(), dark = true)
        live.post(content, now, frame)
        presenter().show(fire(AlertKind.LOW), settings, silent = false, who = null, relaxedMin = 0)
        val n = live.build(content, now, frame, LiveNotification.Mode.RING)
        val parent = FrameLayout(ctx)
        for (views in listOfNotNull(n.contentView, n.bigContentView, n.headsUpContentView)) views.apply(ctx, parent)
        assertNotNull("the card with the graph", n.bigContentView)
        assertNotNull("the heads-up with the alert's title", n.headsUpContentView)
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
