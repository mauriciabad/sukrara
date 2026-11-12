// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.graph.TrendAngle
import app.mauri.sukrara.monitor.R
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.readiness.ReadinessFacts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The fallback as Android gets it, from the real [AlertNotifier]: with the strings looked up by name gone (what
 * resource shrinking did to 0.14.1's release build, here a context whose name lookups find nothing) the alert is still
 * posted, plain, on its own channel with its kind, value, unit and arrow and *Open Sukrara*; when even that fails, a
 * notice on *Monitoring problems*. Reliability lists it as "An alert couldn't be shown".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertPlainNotificationTest {
    private val app: Context = ApplicationProvider.getApplicationContext()
    private val nm = app.getSystemService(NotificationManager::class.java)

    /** The app as 0.14.1's release build: every string a lookup by name asks for is missing. */
    @Suppress("DEPRECATION")
    private val shrunk: Context =
        object : ContextWrapper(app) {
            private val res =
                object : Resources(app.assets, app.resources.displayMetrics, app.resources.configuration) {
                    override fun getIdentifier(name: String?, defType: String?, defPackage: String?) = 0
                }

            override fun getResources(): Resources = res
        }

    private val rig = AlertRig()

    @Before fun setUp() {
        // a library test has no launcher icon; the notifier uses the app's
        app.applicationInfo.icon = android.R.drawable.star_on
        AlertChannels.ensure(app)
    }

    private fun runtime(presenter: AlertPresenter) =
        AlertRuntime(rig.store, rig.treatments, presenter, rig.alarm, { }, { rig.published = it }, rig.clock)

    private fun posted(group: AlertGroup): Notification =
        nm.activeNotifications
            .firstOrNull { it.id == AlertNotifier.idOf(group) }
            ?.notification
            .also { assertNotNull("a notification in the ${group.wire} slot", it) }!!

    /** The checklist over what the runtime published, everything else granted. */
    private fun readiness() =
        ReadinessFacts(
            failedSteps = rig.published.failedSteps,
            notificationsEnabled = true,
            anyAlarmKind = true,
            fullScreenAllowed = true,
            exactAlarmAllowed = true,
            batteryExempt = true,
            backgroundRestricted = false,
            alertChannelSilenced = false,
            needsBluetooth = false,
            bluetoothOn = true,
            bluetoothPermitted = true,
        ).readiness()

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    /** A high (not very high: below 250). */
    private fun high(mgdl: Int, rate: Double) = rig.wearerState(latest = Reading(rig.clock.now(), mgdl, rate, SourceId.Libre3))

    @Test fun `the real alert can't be built without its strings, so it rings plain on its own channel`() =
        runTest {
            runtime(AlertNotifier(shrunk)).onState(high(220, 2.4))
            val n = posted(AlertGroup.LEVEL)
            assertEquals("the high alert's own channel", Channels.alert("high"), n.channelId)
            assertEquals(app.getString(R.string.alert_headline_high), n.title())
            assertEquals("220 mg/dL ${TrendAngle.text(2.4)}", n.text())
            assertEquals(listOf(app.getString(R.string.monitor_action_open_app)), n.actions.map { it.title.toString() })
            assertNotNull("a tap opens the app", n.contentIntent)
            assertEquals("it rings", Notification.GROUP_ALERT_ALL, n.groupAlertBehavior)
            assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility)

            assertTrue(rig.published.failedSteps.toString(), "Show:level NotFoundException" in rig.published.failedSteps)
            assertEquals("Settings › Alert reliability: An alert couldn't be shown", AlertRequirement.AlertShown, readiness().firstProblem)
        }

    @Test fun `in mmol per litre the value is written in mmol per litre`() =
        runTest {
            runtime(AlertNotifier(shrunk, unit = { GlucoseUnit.MMOL })).onState(high(220, 0.0))
            assertEquals("12.2 mmol/L ${TrendAngle.text(0.0)}", posted(AlertGroup.LEVEL).text())
        }

    @Test fun `an alarm-level kind rings plain on its alarm channel`() =
        runTest {
            rig.store.setSettings(AlertSettings().let { it.withRule(AlertKind.LOW, it.rule(AlertKind.LOW).copy(on = true, alarm = true)) })
            runtime(AlertNotifier(shrunk)).onState(rig.wearerState(latest = rig.reading(60)))
            val n = posted(AlertGroup.LEVEL)
            assertEquals(Channels.alarm("low"), n.channelId)
            assertEquals(app.getString(R.string.alert_headline_low), n.title())
            assertEquals(Notification.CATEGORY_ALARM, n.category)
        }

    @Test fun `every kind's plain alert names its kind on its channel`() {
        val notifier = AlertNotifier(shrunk)
        val settings = AlertSettings()
        for (kind in AlertKind.entries) {
            val reading = if (kind == AlertKind.NO_DATA) null else AlertReading(t = 1_000, mgdl = 120, rate = 1.0)
            notifier.showPlain(Firing(kind, "e", 1, reading, 70.0, 1_000), settings, silent = false, who = null)
            val n = posted(kind.group)
            assertEquals(kind.wire, AlertChannels.id(app, kind, settings.rule(kind).alarm), n.channelId)
            assertEquals(kind.wire, app.getString(AlertNotifier.headlineRes(kind)), n.title())
        }
    }

    @Test fun `a follower's plain alert says whose it is`() {
        val f = Firing(AlertKind.LOW, "e", 1, AlertReading(t = 1_000, mgdl = 60, rate = null), 70.0, 1_000)
        AlertNotifier(shrunk).showPlain(f, AlertSettings(), silent = false, who = "Ana")
        assertEquals("Ana", posted(AlertGroup.LEVEL).extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
    }

    @Test fun `when even the plain alert fails, a notice on Monitoring problems`() =
        runTest {
            val real = AlertNotifier(shrunk)
            val broken =
                object : AlertPresenter by real {
                    override fun showPlain(firing: Firing, settings: AlertSettings, silent: Boolean, who: String?) =
                        throw SecurityException("refused")
                }
            runtime(broken).onState(high(220, 2.4))
            val n = posted(AlertGroup.LEVEL)
            assertEquals(Channels.STATUS, n.channelId)
            assertEquals(app.getString(R.string.alert_problem_title), n.title())
            assertEquals(app.getString(R.string.alert_problem_text), n.text())
            assertEquals(listOf(app.getString(R.string.monitor_action_open_app)), n.actions.map { it.title.toString() })
            assertNotNull(n.contentIntent)
            assertTrue("Plain:level SecurityException" in rig.published.failedSteps)
        }
}
