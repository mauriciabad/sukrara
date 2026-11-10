// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.ServiceState
import app.mauri.sukrara.core.model.StopReason
import app.mauri.sukrara.monitor.MonitorHost
import app.mauri.sukrara.monitor.MonitorModule
import app.mauri.sukrara.monitor.MonitoringService
import app.mauri.sukrara.monitor.notify.Channels
import app.mauri.sukrara.monitor.notify.NotificationIds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The monitoring service's lifecycle (#82) on the real module graph: created, it is in the foreground at once with
 * the glucose notification (no I/O first: Android kills a service that doesn't call startForeground in time), every
 * start command is answered with startForeground again and asks to be restarted (START_STICKY), and destroyed it
 * leaves nothing behind: not running, the hub says so, no post still waiting. The storage never gets ready here, so
 * it stays in the state before its source starts (no Bluetooth, no relays). Refused the foreground (Android 12+ from
 * the background), it says so and stops instead of running unseen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = MonitoringServiceLifecycleTest.Host::class)
class MonitoringServiceLifecycleTest {
    /** The app's MonitorHost on the real graph, whose storage never becomes ready. */
    class Host :
        Application(),
        MonitorHost {
        val graph by lazy { AppGraph(this) }

        override val monitor: MonitorModule get() = graph.monitor

        override suspend fun awaitReady() = awaitCancellation()
    }

    private val host: Host = ApplicationProvider.getApplicationContext()

    @After fun tearDown() = host.graph.scope.cancel()

    @Test fun `created it is in the foreground with the glucose notification, destroyed it leaves nothing running`() {
        val controller = Robolectric.buildService(MonitoringService::class.java).create()
        val service = shadowOf(controller.get())

        assertEquals(NotificationIds.GLUCOSE, service.lastForegroundNotificationId)
        val n: Notification? = service.lastForegroundNotification
        assertNotNull(n)
        assertEquals(Channels.GLUCOSE, n!!.channelId)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, controller.get().foregroundServiceType)
        assertTrue(MonitoringService.isRunning)

        // a second startForegroundService (the starter after a reboot, a role change) is answered the same way
        assertEquals(Service.START_STICKY, controller.get().onStartCommand(null, 0, 2))
        assertEquals(NotificationIds.GLUCOSE, service.lastForegroundNotificationId)

        controller.destroy()
        assertFalse(MonitoringService.isRunning)
        assertEquals(ServiceState.NotRunning, host.monitor.hub.state.value.service)
    }

    @Test fun `refused the foreground it stops itself and says monitoring stopped`() {
        val controller = Robolectric.buildService(MonitoringService::class.java)
        shadowOf(controller.get()).setThrowInStartForeground(ForegroundServiceStartNotAllowedException("background start"))
        controller.create()

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertFalse(MonitoringService.isRunning)
        assertEquals(ServiceState.Stopped(StopReason.StartDenied), host.monitor.hub.state.value.service)
        controller.destroy()
    }
}
