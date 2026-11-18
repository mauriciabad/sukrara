// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.testing.FakeClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The end of a new sensor's warm-up through the runtime (the owner's phone, 2026-11-18: a new Libre 3 behind
 * LibreLinkUp rang *No glucose data* for a moment at its first reading). The hub shows the sensor running as soon as
 * the source has that reading, which can reach the runtime a moment later, or after the sensor in the same state.
 */
class AlertWarmupEndTest {
    private val clock = FakeClock(Instant.ofEpochSecond(1_760_000_040L))
    private val rig = AlertRig(clock)
    private val start = clock.now()
    private val ready = start.plusSeconds(3600)
    private val end = start.plusSeconds(15 * 86400L)
    private val old = rig.reading(170, start.minusSeconds(4 * 3600L))

    private fun state(
        latest: Reading,
        sensor: SensorState,
    ): GlucoseState = rig.wearerState(latest).copy(monitoringStartedAt = start.minusSeconds(10 * 60L), sensor = sensor)

    @Test
    fun `the sensor shown running before its first reading reaches the runtime never rings no data`() =
        runBlocking {
            val runtime = rig.runtime()
            runtime.onState(state(old, SensorState.WarmingUp(ready, "0D00NEW456")))
            clock.instant = ready.plusSeconds(60)
            runtime.onState(state(old, SensorState.WarmingUp(ready, "0D00NEW456")))
            // the source has the first reading: the hub shows the sensor running, the reading comes after
            runtime.onState(state(old, SensorState.Active(end, "0D00NEW456")))
            runtime.onState(state(rig.reading(120), SensorState.Active(end, "0D00NEW456")))
            assertFalse(rig.presenter.log.joinToString(), rig.presenter.log.any { "no_data" in it })
        }

    @Test
    fun `a warm-up that brings nothing still rings 15 min after its end`() =
        runBlocking {
            val runtime = rig.runtime()
            runtime.onState(state(old, SensorState.WarmingUp(ready, "0D00NEW456")))
            clock.instant = ready.plusSeconds(16 * 60L)
            runtime.tick()
            assertTrue(rig.presenter.log.joinToString(), rig.presenter.log.any { it == "show no_data" })
        }

    @Test
    fun `another sensor running keeps no warm-up of the one before`() {
        val warming = AlertRuntime.windowOf(SensorState.WarmingUp(ready, "A"))
        assertEquals(warming?.warmupEndEpoch, AlertRuntime.windowAfter(SensorState.Active(end, "A"), warming)?.warmupEndEpoch)
        assertEquals(0L, AlertRuntime.windowAfter(SensorState.Active(end, "B"), null)?.warmupEndEpoch)
        assertEquals(1L, AlertRuntime.windowAfter(SensorState.Ended, warming)?.endEpoch)
    }
}
