// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.service

import android.bluetooth.BluetoothAdapter
import app.mauri.sukrara.core.model.ServiceState
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.StopReason
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.core.testing.TestDispatchers
import app.mauri.sukrara.monitor.hub.GlucoseHubImpl
import app.mauri.sukrara.monitor.hub.MonitorRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** MonitorStarter, HealthCheck, FreshnessTicker and the Bluetooth state mapping. */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorServiceLogicTest {
    private val role = MutableStateFlow<SourceSpec?>(SourceSpec.YourSensor)
    private var allowed = true
    private var launches = 0
    private var readiness = Readiness.Ready
    private val record = InMemoryStateStore(MonitorRecord())
    private val asked = mutableListOf<SourceSpec>()
    private val notice =
        object : StoppedNotice {
            var shown = 0
            var cancelled = 0

            override fun show() {
                shown++
            }

            override fun cancel() {
                cancelled++
            }
        }
    private val alarms =
        object : Alarms {
            val health = mutableListOf<Duration>()
            val steps = mutableListOf<Instant?>()

            override fun scheduleHealthCheck(after: Duration) {
                health += after
            }

            override fun scheduleFreshnessStep(at: Instant?) {
                steps += at
            }
        }

    private fun TestScope.setUp(): Pair<GlucoseHubImpl, MonitorStarter> {
        val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
        val hub =
            GlucoseHubImpl(
                CoroutineScope(backgroundScope.coroutineContext + dispatchers.dispatcher),
                dispatchers,
                FakeClock(),
                InMemoryReadingsRepository(),
                record,
                role,
            )
        hub.start()
        runCurrent()
        val starter =
            MonitorStarter(hub, { r ->
                asked += r
                readiness
            }, {
                launches++
                allowed
            }, notice)
        return hub to starter
    }

    @Test
    fun `a refused start shows Stopped and the resume notice, never silently`() =
        runTest {
            val (hub, starter) = setUp()
            allowed = false
            assertEquals(StartResult.Denied, starter.start(StartReason.Boot))
            assertEquals(ServiceState.Stopped(StopReason.StartDenied), hub.state.value.service)
            assertEquals(1, notice.shown)

            allowed = true
            assertEquals(StartResult.Started, starter.start(StartReason.Resume))
            assertEquals(1, notice.cancelled)
        }

    @Test
    fun `nothing to monitor starts nothing and shows no notice`() =
        runTest {
            val (_, starter) = setUp()
            readiness = Readiness.Nothing
            assertEquals(StartResult.NotNeeded, starter.start(StartReason.HealthCheck))
            role.value = null
            readiness = Readiness.Ready
            runCurrent()
            assertEquals(StartResult.NotNeeded, starter.start(StartReason.Boot))
            assertEquals(0, launches)
            assertEquals(0, notice.shown)
            assertEquals("asked for the hub's role, never without one", listOf(SourceSpec.YourSensor), asked)
        }

    /**
     * The first unlock after a restart: a source only known then (a follow, an install with no answer) reaches the hub
     * a moment later, and the boot's start right after the unlock found the hub's old copy (no source) and started
     * nothing until the app was opened or the health check ran. The starter reads THE source itself.
     */
    @Test
    fun `the start reads the source itself, never the hub's copy that may lag behind the unlock`() =
        runTest {
            val (hub, _) = setUp()
            role.value = null
            runCurrent()
            val now = MutableStateFlow<SourceSpec?>(null)
            val starter =
                MonitorStarter(hub, { r ->
                    asked += r
                    Readiness.Ready
                }, {
                    launches++
                    true
                }, notice, spec = { now.value })
            assertEquals(StartResult.NotNeeded, starter.start(StartReason.LockedBoot))
            // unlocked: the source is decided again (their Sukrara); the hub hasn't seen it yet
            now.value = SourceSpec.TheirSukrara
            assertNull(hub.state.value.spec)
            assertEquals(StartResult.Started, starter.start(StartReason.Boot))
            assertEquals(1, launches)
            assertEquals(listOf(SourceSpec.TheirSukrara), asked)
        }

    @Test
    fun `nothing set up to read is not monitoring, so no no-data alarm stays armed`() =
        runTest {
            // the owner's phone (#213 step 0): monitoring armed by Juggluco, then the sensor chosen with no pairing
            val (hub, starter) = setUp()
            record.update { it.copy(startedAtEpochMs = 1_000) }
            runCurrent()
            readiness = Readiness.Nothing
            assertEquals(StartResult.NotNeeded, starter.start(StartReason.PackageReplaced))
            runCurrent()
            assertNull(hub.state.value.monitoringStartedAt)
            assertEquals(0, launches)
        }

    @Test
    fun `the service stopping for nothing to read settles it at once, never a source that has something`() =
        runTest {
            val (hub, starter) = setUp()
            record.update { it.copy(startedAtEpochMs = 1_000) }
            runCurrent()
            // a race: the service saw no source, but the source has one by now
            starter.nothingToRead()
            runCurrent()
            assertEquals(Instant.ofEpochMilli(1_000), hub.state.value.monitoringStartedAt)

            readiness = Readiness.Nothing
            starter.nothingToRead()
            runCurrent()
            assertNull(hub.state.value.monitoringStartedAt)
        }

    @Test
    fun `a source that waits for the unlock keeps monitoring armed`() =
        runTest {
            val (hub, starter) = setUp()
            record.update { it.copy(startedAtEpochMs = 1_000) }
            runCurrent()
            readiness = Readiness.AfterUnlock
            assertEquals(StartResult.NotNeeded, starter.start(StartReason.LockedBoot))
            runCurrent()
            assertEquals(Instant.ofEpochMilli(1_000), hub.state.value.monitoringStartedAt)
        }

    @Test
    fun `the health check restarts the service and schedules the next check every 15 minutes`() =
        runTest {
            val (_, starter) = setUp()
            val check = HealthCheck(alarms, starter)
            assertEquals(StartResult.Started, check.onFire())
            assertEquals(StartResult.Started, check.onFire())
            assertEquals(2, launches)
            assertEquals(listOf(Duration.ofMinutes(15), Duration.ofMinutes(15)), alarms.health)
        }

    @Test
    fun `the health check reschedules even when there is nothing to monitor`() =
        runTest {
            val (_, starter) = setUp()
            readiness = Readiness.Nothing
            assertEquals(StartResult.NotNeeded, HealthCheck(alarms, starter).onFire())
            assertEquals(1, alarms.health.size)
        }

    @Test
    fun `the freshness ticker wakes only at the renderer's freshness steps`() {
        val t = Instant.parse("2026-01-15T12:00:00Z")
        assertEquals(t.plusSeconds(91), FreshnessTicker.nextStep(t, t.plusSeconds(30)))
        assertEquals(t.plusSeconds(301), FreshnessTicker.nextStep(t, t.plusSeconds(120)))
        assertEquals(t.plusSeconds(1201), FreshnessTicker.nextStep(t, t.plusSeconds(400)))
        assertEquals(t.plusSeconds(10801), FreshnessTicker.nextStep(t, t.plusSeconds(1300)))
        // the reading's time gains the day at 12 h and becomes the date after 6 days
        assertEquals(t.plusSeconds(43201), FreshnessTicker.nextStep(t, t.plusSeconds(20000)))
        assertEquals(t.plusSeconds(518401), FreshnessTicker.nextStep(t, t.plusSeconds(43201)))
        assertNull(FreshnessTicker.nextStep(t, t.plusSeconds(518401)))
        assertNull(FreshnessTicker.nextStep(null, t))

        val ticker = FreshnessTicker(alarms)
        ticker.plan(t, t.plusSeconds(30))
        ticker.plan(t, t.plusSeconds(60))
        assertEquals("no re-arm while nothing changes", listOf(t.plusSeconds(91)), alarms.steps)
        ticker.plan(t, t.plusSeconds(20000))
        ticker.plan(t, t.plusSeconds(600_000))
        assertEquals(listOf(t.plusSeconds(91), t.plusSeconds(43201), null), alarms.steps)
    }

    @Test
    fun `bluetooth adapter states map to on and off`() {
        assertEquals(true, bluetoothPower(BluetoothAdapter.STATE_ON))
        assertEquals(false, bluetoothPower(BluetoothAdapter.STATE_OFF))
        assertEquals(false, bluetoothPower(BluetoothAdapter.STATE_TURNING_OFF))
        assertNull(bluetoothPower(BluetoothAdapter.STATE_TURNING_ON))
    }
}
