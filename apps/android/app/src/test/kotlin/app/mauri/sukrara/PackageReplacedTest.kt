// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.debug.SyntheticSource
import app.mauri.sukrara.monitor.BootReceiver
import app.mauri.sukrara.monitor.MonitoringService
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * #188: an update (the GitHub version's own, Play's, or adb's) replaces the app and ends its process; the new version's
 * first broadcast, `MY_PACKAGE_REPLACED`, must bring monitoring back by itself, without the app being opened. The
 * real app and graph: the wearer's phone with a (synthetic) sensor, the system's broadcast to BootReceiver.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = App::class)
class PackageReplacedTest {
    private val app: App = ApplicationProvider.getApplicationContext()

    @After fun tearDown() = app.graph.scope.cancel()

    @Test fun `monitoring starts again by itself right after the app was updated`() {
        SyntheticSource.set(app, on = true, frozen = false, shift = 0, endsAt = null)
        runBlocking { app.graph.role.update { RoleRecord(Role.Wearer) } }
        shadowOf(app).clearStartedServices()

        BootReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        val until = System.nanoTime() + 20_000_000_000L
        var started: Intent? = null
        while (started == null && System.nanoTime() < until) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            started = shadowOf(app).nextStartedService
            if (started == null) Thread.sleep(20)
        }
        assertEquals(MonitoringService::class.java.name, started?.component?.className)
    }

    @Test fun `with nothing to monitor an update starts nothing`() {
        SyntheticSource.set(app, on = false, frozen = false, shift = 0, endsAt = null)
        shadowOf(app).clearStartedServices()
        BootReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        val until = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < until) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertNull(shadowOf(app).nextStartedService)
    }
}
