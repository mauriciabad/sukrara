// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.data.libreview.LibreViewAccountState
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.ServiceState
import app.mauri.sukrara.core.model.StopReason
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.feature.settings.sensor.SourceChoice
import app.mauri.sukrara.feature.settings.sensor.SourceLink
import app.mauri.sukrara.sensor.SensorInfo
import app.mauri.sukrara.sensor.SensorProfile
import app.mauri.sukrara.sensor.ble.LinkStatus
import app.mauri.sukrara.sensor.juggluco.JugglucoStatus
import app.mauri.sukrara.sensor.source.SensorSourceKind
import app.mauri.sukrara.sensor.source.SensorStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Sensor over a fake sensor module: the link, a stopped service, and what a source change starts or stops. */
class SensorSettingsBridgeTest {
    private class FakeSensors : SensorSettingsSensors {
        override val sensor = MutableStateFlow<SensorState>(SensorState.None)
        override val detail = MutableStateFlow<SensorStatus>(SensorStatus.NotRunning)
        override val sensorInfo: Flow<SensorInfo?> = flowOf(null)
        override val account: Flow<LibreViewAccountState> = flowOf(LibreViewAccountState.SignedOut)
        override val receiverId: Flow<Long?> = flowOf(null)
        override val tapAvailable = false
        override val tapOn = MutableStateFlow(false)
        var paired = false
        var forgotten = false

        override fun tapBlocked() = false

        override fun setTap(on: Boolean) {
            tapOn.value = on
        }

        override fun openTapAllow() = Unit

        override suspend fun paired() = paired

        override suspend fun forgetPairing() {
            paired = false
            forgotten = true
        }

        override val jugglucoSecretSet = MutableStateFlow<Boolean?>(null)
        var secret: String? = null
        var retries = 0

        override suspend fun refreshJugglucoSecret() {
            jugglucoSecretSet.value = secret != null
        }

        override suspend fun saveJugglucoSecret(secret: String) {
            this.secret = secret.trim().ifEmpty { null }
            refreshJugglucoSecret()
        }

        override fun retry() {
            retries++
            detail.value = if (paired) detail.value else SensorStatus.NoSensor
        }
    }

    private val source = SchemaSettings.text("source.source", SensorSourceKind.Direct.id, SensorSourceKind.entries.map { it.id }.toSet())
    private val sensors = FakeSensors()
    private val settings = InMemorySettingsRepository()
    private val service = MutableStateFlow<ServiceState>(ServiceState.Running)
    private val events = mutableListOf<String>()

    private fun TestScope.bridge() =
        SensorSettingsBridge(
            sensors = sensors,
            settings = settings,
            sourceSetting = source,
            scope = backgroundScope,
            service = service,
            startMonitoring = { events += "start" },
            resetMonitoring = { events += "reset" },
            sourceChanged = { events += "source changed" },
        )

    @Test fun `the link says whether readings really arrive`() =
        runTest {
            val b = bridge()
            sensors.paired = true
            sensors.sensor.value = SensorState.Active(null)
            sensors.detail.value = SensorStatus.Direct(SensorProfile.Libre3, "3MH01", "3MH01", LinkStatus.Connecting)
            assertEquals(SourceLink.Connecting, b.state.first().link)
            sensors.detail.value = SensorStatus.Direct(SensorProfile.Libre3, "3MH01", "3MH01", LinkStatus.Live)
            assertEquals(SourceLink.Receiving, b.state.first().link)
            sensors.detail.value = SensorStatus.Direct(SensorProfile.DexcomG7, "G7", "G7", LinkStatus.Scanning)
            assertEquals(SourceLink.Searching, b.state.first().link)
            sensors.detail.value = SensorStatus.Juggluco(JugglucoStatus.Unreachable("refused"))
            assertEquals(SourceLink.JugglucoUnreachable, b.state.first().link)
            sensors.detail.value = SensorStatus.Juggluco(JugglucoStatus.Refused)
            assertEquals("a wrong or missing API secret is a visible error", SourceLink.JugglucoRefused, b.state.first().link)
        }

    @Test fun `a paired sensor with monitoring stopped says stopped, never no sensor`() =
        runTest {
            val b = bridge()
            sensors.paired = true
            sensors.detail.value = SensorStatus.NotRunning
            service.value = ServiceState.NotRunning
            assertTrue(b.state.first().stopped)

            service.value = ServiceState.Stopped(StopReason.StartDenied)
            sensors.detail.value = SensorStatus.Direct(SensorProfile.Libre3, "3MH01", "3MH01", LinkStatus.Live)
            assertTrue("Android stopped the service", b.state.first().stopped)

            b.resume()
            runCurrent()
            assertEquals(listOf("start"), events)
        }

    @Test fun `without a pairing it is no sensor yet, not stopped`() =
        runTest {
            val b = bridge()
            sensors.detail.value = SensorStatus.NoSensor
            service.value = ServiceState.NotRunning
            val s = b.state.first()
            assertFalse(s.stopped)
            assertFalse(s.paired)
            assertEquals(SensorState.None, s.state)
        }

    @Test fun `to the sensor read directly with a pairing starts monitoring`() =
        runTest {
            settings.set(source, SensorSourceKind.Juggluco.id)
            sensors.paired = true
            bridge().setSource(SourceChoice.Libre3Direct)
            runCurrent()
            assertEquals(SensorSourceKind.Direct.id, settings.flow(source).first())
            assertEquals(listOf("source changed", "start"), events)
        }

    @Test fun `to the sensor read directly with nothing paired stops and disarms instead`() =
        runTest {
            settings.set(source, SensorSourceKind.Juggluco.id)
            bridge().setSource(SourceChoice.Libre3Direct)
            runCurrent()
            assertEquals(listOf("source changed", "reset"), events)
        }

    @Test fun `to Juggluco always starts monitoring and turns the sensor tap off`() =
        runTest {
            sensors.tapOn.value = true
            bridge().setSource(SourceChoice.Juggluco)
            runCurrent()
            assertEquals(listOf("source changed", "start"), events)
            assertFalse(sensors.tapOn.value)
        }

    @Test fun `the Juggluco secret is saved, shown only as set, and Juggluco is asked again at once`() =
        runTest {
            settings.set(source, SensorSourceKind.Juggluco.id)
            val b = bridge()
            assertEquals(false, b.state.first().jugglucoSecret)
            b.setJugglucoSecret(" s3cret ")
            runCurrent()
            assertEquals("s3cret", sensors.secret)
            assertEquals(true, b.state.first { it.jugglucoSecret == true }.jugglucoSecret)
            assertEquals(1, sensors.retries)
            b.setJugglucoSecret("")
            runCurrent()
            assertEquals(null, sensors.secret)
        }

    @Test fun `forgetting the sensor disarms monitoring, then the service picks its source again`() =
        runTest {
            sensors.paired = true
            bridge().forget()
            runCurrent()
            assertTrue(sensors.forgotten)
            assertEquals(listOf("reset", "source changed"), events)
        }
}
