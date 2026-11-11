// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.debug.SyntheticSource
import app.mauri.sukrara.monitor.AlertsHost
import app.mauri.sukrara.monitor.MonitorHost
import app.mauri.sukrara.monitor.MonitorModule
import app.mauri.sukrara.monitor.MonitoringService
import app.mauri.sukrara.monitor.R
import app.mauri.sukrara.monitor.alerts.AlertsModule
import app.mauri.sukrara.monitor.lockedboot.LockedBootReceiver
import app.mauri.sukrara.monitor.notify.NotificationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.Collections

/**
 * The graph before the first unlock after a restart (#160, docs/30-direct-boot.md), on Android's classes with the
 * platform's lock (Robolectric's UserManager): the wearer's phone with its own sensor (the debug build's made-up one)
 * decides to monitor, starts the service and says so on the lock screen; readings reach the hub and a level alert rings
 * with nothing but device-protected storage; the unlock puts every reading taken before it into the history once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = LockedGraphTest.Host::class)
class LockedGraphTest {
    /** The app's hosts on the real graph, as `App` implements them. */
    class Host :
        Application(),
        MonitorHost,
        AlertsHost {
        val graph by lazy { AppGraph(this) }

        override val monitor: MonitorModule get() = graph.monitor

        override suspend fun awaitReady() = graph.ready.await()

        override suspend fun awaitMonitor() = graph.awaitMonitor()

        override suspend fun awaitAlerts(): AlertsModule? = graph.runningAlerts()

        override val alertsNow: AlertsModule? get() = graph.runningAlertsNow
    }

    private val host: Host = ApplicationProvider.getApplicationContext()
    private val nm = host.getSystemService(NotificationManager::class.java)
    private val users = shadowOf(host.getSystemService(UserManager::class.java))
    private val running = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val crashes = Collections.synchronizedList(mutableListOf<Throwable>())
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before
    fun lockedAfterRestart() {
        Thread.setDefaultUncaughtExceptionHandler { _, e -> crashes += e }
        users.setUserUnlocked(false)
        shadowOf(host.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
    }

    @After
    fun tearDown() {
        running.cancel()
        host.graph.scope.cancel()
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    /** Set up before the restart: the wearer, the made-up sensor with its lowest point now (45 mg/dL: very low). */
    private fun wearerWithLowNow() {
        SyntheticSource.set(
            host,
            on = true,
            frozen = false,
            shift = SyntheticSource.shiftFor(Instant.now(), SyntheticSource.TROUGH_MINUTE),
            endsAt = null,
        )
        runBlocking { host.graph.role.update { it.copy(role = Role.Wearer) } }
    }

    private fun alertNotifications(): List<Notification> = shadowOf(nm).allNotifications.filter { it.channelId.startsWith("alert") }

    @Test
    fun `the wearer's phone monitors before the unlock and the unlock merges what it read`() =
        runBlocking {
            wearerWithLowNow()
            val graph = host.graph
            graph.monitorReady.start()
            assertTrue("the wearer reading its own sensor monitors", graph.monitorsLocked.await())
            assertFalse(graph.unlocked.value)

            // the locked boot: the alert check, the service, the notice that says monitoring runs
            LockedBootReceiver.lockedBootCompleted(host, LockedBootReceiver.lockedBoot(host))
            assertEquals(MonitoringService::class.java.name, shadowOf(host).nextStartedService?.component?.className)
            val notice = requireNotNull(shadowOf(nm).getNotification(NotificationIds.LOCKED_BOOT))
            assertEquals(host.getString(R.string.monitor_locked_boot_monitoring_title), notice.extras.getString(Notification.EXTRA_TITLE))

            // what the service runs: the source into the hub, the alert runtime on the hub
            val source = requireNotNull(graph.monitor.source(Role.Wearer))
            running.launch { graph.monitor.hubImpl.attach(source) }
            requireNotNull(host.awaitAlerts()).attach(running)
            withTimeout(15_000) {
                while (graph.monitor.hub.state.value.latest == null || alertNotifications().isEmpty()) delay(50)
            }
            val read = graph.monitor.hub.state.value.recent
            assertTrue("the made-up sensor's backfill and live reading", read.size > 100)
            assertTrue("a level alert rang", alertNotifications().isNotEmpty())

            // nothing credential-encrypted was touched
            assertFalse(File(host.filesDir, "state").exists())
            assertFalse(File(host.filesDir, "secure").exists())
            assertFalse(File(host.filesDir.parentFile, "datastore").exists())
            assertFalse(host.getDatabasePath("sukrara.db").exists())
            assertEquals(emptyList<Throwable>(), crashes.toList())

            // the unlock: every reading taken before it in the history, once; the hub reads the history now
            users.setUserUnlocked(true)
            graph.onUnlocked()
            assertTrue(graph.unlocked.value)
            val history = graph.data.readings.range(Instant.EPOCH, Instant.now().plusSeconds(60))
            val seen = graph.monitor.hub.state.value.recent
            assertEquals(history.size, history.distinctBy { it.t to it.source }.size)
            assertTrue(
                "no gap: every reading the hub had is in the history",
                read.all { r ->
                    history.any {
                        it.t == r.t &&
                            it.source == SyntheticSource.ID
                    }
                },
            )
            assertEquals(
                history.size,
                graph.hubReadings
                    .window(Instant.EPOCH)
                    .first()
                    .size,
            )
            assertTrue(seen.isNotEmpty())
            graph.onUnlocked()
            assertEquals(
                "a second unlock adds nothing",
                history.size,
                graph.data.readings
                    .range(Instant.EPOCH, Instant.now().plusSeconds(60))
                    .size,
            )
            assertEquals(emptyList<Throwable>(), crashes.toList())
        }

    @Test
    fun `a follower doesn't monitor before the unlock, the waiting notice stands in`() =
        runBlocking {
            host.graph.role.update { it.copy(role = Role.Follower) }
            host.graph.monitorReady.start()
            assertFalse(host.graph.monitorsLocked.await())
            assertNull(host.awaitAlerts())
            assertNull(host.alertsNow)
            LockedBootReceiver.lockedBootCompleted(host, LockedBootReceiver.lockedBoot(host))
            assertNull("no plan saved: nothing set up showed", shadowOf(nm).getNotification(NotificationIds.LOCKED_BOOT))
            assertNull(shadowOf(host).nextStartedService)
            assertNull(host.graph.monitor.source(Role.Follower))
            assertEquals(emptyList<Throwable>(), crashes.toList())
        }

    @Test
    fun `with nothing paired the wearer's phone doesn't monitor before the unlock`() =
        runBlocking {
            host.graph.role.update { it.copy(role = Role.Wearer) }
            host.graph.monitorReady.start()
            assertFalse(host.graph.monitorsLocked.await())
            assertEquals("the hub runs on device-protected storage", Role.Wearer, host.graph.monitor.hub.state.value.role)
            assertNull(host.awaitAlerts())
            assertEquals(emptyList<Throwable>(), crashes.toList())
        }
}
