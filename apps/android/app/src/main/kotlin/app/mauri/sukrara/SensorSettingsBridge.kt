// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.ServiceState
import app.mauri.sukrara.core.model.storage.SettingSpec
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.feature.settings.sensor.LibreViewSummary
import app.mauri.sukrara.feature.settings.sensor.OpenOnScan
import app.mauri.sukrara.feature.settings.sensor.SensorFamily
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsPort
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsState
import app.mauri.sukrara.feature.settings.sensor.SensorSummary
import app.mauri.sukrara.feature.settings.sensor.SourceChoice
import app.mauri.sukrara.feature.settings.sensor.SourceLink
import app.mauri.sukrara.sensor.SensorInfo
import app.mauri.sukrara.sensor.SensorModule
import app.mauri.sukrara.sensor.ble.LinkStatus
import app.mauri.sukrara.sensor.ble.NoValueReason
import app.mauri.sukrara.sensor.juggluco.JugglucoStatus
import app.mauri.sukrara.sensor.pairing.PairingLoad
import app.mauri.sukrara.sensor.source.SensorSourceKind
import app.mauri.sukrara.sensor.source.SensorStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * What Settings › Sensor reads and does on the sensor module (a seam: tests give a fake, `:app` gives
 * [SensorModulePort]).
 */
interface SensorSettingsSensors {
    val sensor: Flow<SensorState>
    val detail: Flow<SensorStatus>
    val sensorInfo: Flow<SensorInfo?>
    val email: Flow<String?>
    val receiverId: Flow<Long?>

    /** *Open Sukrara when you scan a sensor*: whether the phone has NFC, the switch, and Android blocking the taps. */
    val tapAvailable: Boolean
    val tapOn: Flow<Boolean>

    fun tapBlocked(): Boolean

    fun setTap(on: Boolean)

    fun openTapAllow()

    /** Whether a pairing is saved (a damaged one counts: the source then says "scan the sensor again"). */
    suspend fun paired(): Boolean

    suspend fun forgetPairing()

    /** The source starts over (a new driver and connection). */
    fun retry()

    suspend fun signOut()
}

/** [SensorSettingsSensors] on the real [SensorModule]. */
class SensorModulePort(private val m: SensorModule) : SensorSettingsSensors {
    override val sensor: Flow<SensorState> = m.source.sensor
    override val detail: Flow<SensorStatus> = m.source.detail
    override val sensorInfo: Flow<SensorInfo?> = m.source.sensorInfo
    override val email: Flow<String?> = m.libreView.email
    override val receiverId: Flow<Long?> = m.libreView.receiverId
    override val tapAvailable: Boolean get() = m.tapLaunch.available
    override val tapOn: Flow<Boolean> = m.tapLaunch.on

    override fun tapBlocked() = m.tapLaunch.blocked()

    override fun setTap(on: Boolean) = m.tapLaunch.set(on)

    override fun openTapAllow() {
        m.tapLaunch.openAllow()
    }

    override suspend fun paired() = m.pairings.load() != PairingLoad.None

    override suspend fun forgetPairing() = m.pairings.forget()

    override fun retry() = m.source.retry()

    override suspend fun signOut() = m.libreView.signOut()
}

/**
 * Settings › Sensor over the sensor module and the source setting (`source.source`): the settings feature
 * can't see the sensor feature, so the app joins them here.
 *
 * A source change (or *Forget this sensor*) makes the monitoring service pick its source again ([sourceChanged]):
 * with a pairing (or Juggluco) monitoring starts and the page shows it connecting; with nothing to read the service
 * stops and monitoring is reset like after *Forget this sensor* (no "no data" alarm for a source that has nothing to
 * read; home shows *No sensor yet* with *Pair sensor*). While monitoring is stopped ([service]) with something set up,
 * the page says *Monitoring stopped* with *Resume*, never "no sensor".
 */
class SensorSettingsBridge(
    private val sensors: SensorSettingsSensors,
    private val settings: SettingsRepository,
    private val sourceSetting: SettingSpec<String>,
    /** The app's scope: a source change finishes even if the page is left right after confirming. */
    private val scope: CoroutineScope,
    /** The monitoring service's state (the hub's). */
    private val service: Flow<ServiceState>,
    private val startMonitoring: suspend () -> Unit,
    /** Disarms the no-data alarm ("monitoring since" goes; alerts showing now stay as they are). */
    private val resetMonitoring: suspend () -> Unit,
    /** The reading source may have changed: the monitoring service picks it again (nothing to read stops it). */
    private val sourceChanged: () -> Unit,
) : SensorSettingsPort {
    /** Bumped when the page comes back (Android's app page may have allowed or blocked tag intents meanwhile). */
    private val recheck = MutableStateFlow(0)

    /** Whether a pairing is saved: read (Keystore) only when the source's status changes kind (it restarts on every pairing change). */
    private val paired: Flow<Boolean> =
        sensors.detail
            .map { it::class }
            .distinctUntilChanged()
            .map { sensors.paired() }

    override val state: Flow<SensorSettingsState> =
        combine(
            combine(settings.flow(sourceSetting), sensors.sensor, sensors.detail, ::Triple),
            combine(sensors.sensorInfo, sensors.tapOn, recheck, paired) { info, tapOn, _, paired ->
                Triple(info, OpenOnScan(tapOn, tapOn && sensors.tapBlocked()).takeIf { sensors.tapAvailable }, paired)
            },
            sensors.email,
            sensors.receiverId,
            service,
        ) { (source, sensor, detail), (info, openOnScan, paired), email, receiverId, service ->
            val kind = SensorSourceKind.fromId(source)
            SensorSettingsState(
                source = if (kind == SensorSourceKind.Juggluco) SourceChoice.Juggluco else SourceChoice.Libre3Direct,
                state = sensor,
                sensor = info?.let { SensorSummary(it.serial, Instant.ofEpochSecond(it.startEpoch), Instant.ofEpochSecond(it.endEpoch)) },
                account = email?.let { LibreViewSummary(it, receiverId) },
                link = linkOf(detail),
                family = (detail as? SensorStatus.Direct)?.let { SensorFamily(it.profile.label, !it.profile.verified, it.skipped) },
                paired = paired,
                openOnScan = openOnScan,
                stopped = stopped(detail, service, somethingToRead = paired || kind == SensorSourceKind.Juggluco),
            )
        }

    /** Whether readings really arrive: [SensorState] calls a connecting link or a silent Juggluco Active. */
    private fun linkOf(detail: SensorStatus): SourceLink =
        when (detail) {
            is SensorStatus.Juggluco -> {
                when (detail.status) {
                    JugglucoStatus.Live -> SourceLink.Receiving
                    JugglucoStatus.FetchingHistory -> SourceLink.Connecting
                    is JugglucoStatus.Unreachable -> SourceLink.JugglucoUnreachable
                }
            }

            is SensorStatus.Direct -> {
                when (val link = detail.link) {
                    LinkStatus.Live, is LinkStatus.WarmingUp, LinkStatus.SensorEnded, LinkStatus.WaitingForBluetooth,
                    LinkStatus.PermissionMissing,
                    -> {
                        SourceLink.Receiving
                    }

                    LinkStatus.Scanning, LinkStatus.SensorNotFound -> {
                        SourceLink.Searching
                    }

                    is LinkStatus.NoValue -> {
                        when (link.why) {
                            NoValueReason.Calibration -> SourceLink.NoValueCalibration
                            NoValueReason.Temporary -> SourceLink.NoValueTemporary
                            NoValueReason.SensorError -> SourceLink.NoValueSensorError
                            NoValueReason.Missing -> SourceLink.NoValueMissing
                        }
                    }

                    else -> {
                        SourceLink.Connecting
                    }
                }
            }

            else -> {
                SourceLink.Receiving
            }
        }

    override fun setSource(source: SourceChoice) {
        val kind = if (source == SourceChoice.Juggluco) SensorSourceKind.Juggluco else SensorSourceKind.Direct
        // a Libre tap would open the new-sensor flow of a source that isn't read: the row is hidden then, so off
        if (kind == SensorSourceKind.Juggluco) sensors.setTap(false)
        scope.launch {
            settings.set(sourceSetting, kind.id)
            sourceChanged()
            // Juggluco is a source to monitor even with no sensor paired here; the sensor read directly needs a pairing
            if (kind == SensorSourceKind.Juggluco || sensors.paired()) {
                startMonitoring()
            } else {
                // nothing to read: the service stops (above), and no "no data" alarm for it (home: *No sensor yet*)
                resetMonitoring()
            }
        }
    }

    override suspend fun signOut() = sensors.signOut()

    companion object {
        private const val STOP_WAIT_MS = 5_000L

        /**
         * Monitoring isn't running while something is set up to read: Android stopped the service, or the source
         * isn't running ([SensorStatus.NotRunning]). Then the sensor's own state says nothing (never "no sensor").
         */
        fun stopped(
            detail: SensorStatus,
            service: ServiceState,
            somethingToRead: Boolean,
        ): Boolean = somethingToRead && (service is ServiceState.Stopped || detail == SensorStatus.NotRunning)
    }

    override fun setOpenOnScan(on: Boolean) = sensors.setTap(on)

    override fun recheck() {
        recheck.update { it + 1 }
    }

    override fun allowTaps() = sensors.openTapAllow()

    override fun resume() {
        scope.launch { startMonitoring() }
    }

    override fun forget() {
        scope.launch {
            sensors.forgetPairing()
            sensors.retry()
            // removed on purpose: no "no data" alarm for a sensor that isn't read any more. Once the source has
            // stopped (no reading can arrive after the reset and arm it again)
            withTimeoutOrNull(STOP_WAIT_MS) { sensors.detail.first { it == SensorStatus.NoSensor || it == SensorStatus.NotRunning } }
            resetMonitoring()
            // nothing left to read: the service stops
            sourceChanged()
        }
    }
}
