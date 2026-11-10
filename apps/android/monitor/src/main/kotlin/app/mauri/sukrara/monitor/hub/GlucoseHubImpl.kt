// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.hub

import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.ClockBehind
import app.mauri.sukrara.core.model.DispatcherProvider
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.LinkState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.ReadingSource
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.ServiceState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.model.storage.ReadingsRepository
import app.mauri.sukrara.core.model.storage.StateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/** What the hub keeps across process restarts (a typed state file). */
@Serializable
data class MonitorRecord(
    /** [GlucoseState.monitoringStartedAt] in epoch ms; null = not monitoring yet. */
    val startedAtEpochMs: Long? = null,
    /**
     * Readings stamped at or before this (epoch ms) don't arm monitoring ([GlucoseHubImpl.resetMonitoring] with a
     * fence: a forgotten sensor, a follow that ended); saved, so a restart can't drop it (#128). null = no fence.
     */
    val armAfterEpochMs: Long? = null,
)

/**
 * The one glucose state source ([GlucoseHub]). Its inputs:
 *  - the phone's role (setting),
 *  - the stored readings of the last [WINDOW] (`ReadingsRepository`, which every accepted reading goes through first),
 *  - [MonitorRecord] (when monitoring started),
 *  - exactly one attached [ReadingSource] at a time ([attach], run by the monitoring service),
 *  - the setters of the alert runtime / follower engine and the service's own state.
 *
 * Bookkeeping runs on one serial dispatcher; [state] is updated atomically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseHubImpl(
    private val scope: CoroutineScope,
    dispatchers: DispatcherProvider,
    private val clock: Clock,
    private val readings: ReadingsRepository,
    private val record: StateStore<MonitorRecord>,
    private val role: kotlinx.coroutines.flow.Flow<Role?>,
) : GlucoseHub {
    private val serial = dispatchers.default.limitedParallelism(1)
    private val mutableState = MutableStateFlow(GlucoseState.Initial)
    override val state: StateFlow<GlucoseState> = mutableState.asStateFlow()

    private val sourceSensor = MutableStateFlow<SensorState>(SensorState.None)
    private val bluetoothOn = MutableStateFlow(true)

    // serial dispatcher only
    private var attachedJob: Job? = null

    @Volatile private var attachedSource: ReadingSource? = null

    private val inputsLoaded = CompletableDeferred<Unit>()

    /** Completes once the role and the record have been read (after [start]). */
    suspend fun awaitInputs() = inputsLoaded.await()

    /** Follows the role, the stored readings and the record. Call once, after the app's storage is ready. */
    fun start(): Job =
        scope.launch(serial) {
            // role and record land in one update: a state with the role but not yet `monitoringStartedAt` would
            // briefly look "not monitoring" to the alert runtime (no data off, a showing no-data alert cleared)
            launch {
                combine(role, record.data) { r, rec -> r to rec.startedAtEpochMs?.let(Instant::ofEpochMilli) }.collect { (r, startedAt) ->
                    mutableState.update { it.copy(role = r, monitoringStartedAt = startedAt) }
                    inputsLoaded.complete(Unit)
                }
            }
            // a source that starts arms monitoring, also before its first reading: a sensor paired but silent from the
            // start still rings no data, counted from then. On the start itself (a transition), so a reset while the
            // old source still runs doesn't arm it again
            launch {
                state.map { sourceStarted(it) }.distinctUntilChanged().collect { started -> if (started) arm(clock.now()) }
            }
            launch { windowStarts().flatMapLatest { from -> readings.window(from) }.collect(::applyWindow) }
            launch {
                val wearer = state.map { it.role == Role.Wearer }.distinctUntilChanged()
                combine(sourceSensor, bluetoothOn, wearer) { sensor, on, isWearer ->
                    if (isWearer && !on) SensorState.BluetoothOff else sensor
                }.collect { shown -> mutableState.update { it.copy(sensor = shown) } }
            }
        }

    /**
     * Makes [source] the hub's one input until the caller is cancelled. Attaching another source first stops this one
     * (its collection is cancelled, so it stops), so two sources never feed the hub together.
     */
    suspend fun attach(source: ReadingSource) {
        withContext(serial) {
            attachedJob?.cancelAndJoin()
            val self = coroutineContext[Job]
            attachedJob = self
            attachedSource = source
            // a source attached while Bluetooth is off hears it now, not only at the next change
            if (!bluetoothOn.value) source.onBluetoothOff()
            try {
                coroutineScope {
                    launch { source.sensor.collect(::onSourceSensor) }
                    launch { source.status.collect { s -> mutableState.update { it.copy(source = s) } } }
                    launch { source.readings.collect(::accept) }
                }
            } finally {
                if (attachedJob === self) {
                    attachedJob = null
                    attachedSource = null
                    mutableState.update { it.copy(source = SourceStatus.NotStarted) }
                }
            }
        }
    }

    /** Readings from outside the attached source's flow are accepted only while a source is attached. */
    override suspend fun ingest(
        readings: List<Reading>,
        from: SourceId,
    ) {
        if (attachedSource != null) accept(readings)
    }

    override fun setSource(status: SourceStatus) = mutableState.update { it.copy(source = status) }

    override fun setSensor(sensor: SensorState) = onSourceSensor(sensor)

    override fun setAlerts(alerts: AlertUiState) = mutableState.update { it.copy(alerts = alerts) }

    override fun setLink(link: LinkState?) = mutableState.update { it.copy(link = link) }

    /**
     * Back to "Not monitoring" after the person removed the sensor, changed the role or a follow ended (an explicit
     * action or ending only: a source glitch never disarms the no-data alarm). With [fence] (a forgotten sensor; a
     * follow that ended, was stopped or whose nearby codes differed, #128) a reading still on its way (queued in the
     * source, a relay or Bluetooth before it stopped) is stored but doesn't arm monitoring again: only a reading
     * stamped after this moment does. Without (a join: its backfill is stamped before the join, and must arm no data)
     * any earlier fence goes. The fence is saved with the record.
     */
    suspend fun resetMonitoring(fence: Boolean = false) {
        val at = if (fence) clock.now().toEpochMilli() else null
        record.update { MonitorRecord(armAfterEpochMs = at) }
    }

    fun setService(service: ServiceState) = mutableState.update { it.copy(service = service) }

    /** Bluetooth power changed (the service's receiver): the wearer sees "Bluetooth off"; the source reconnects. */
    fun onBluetoothChanged(on: Boolean) {
        if (bluetoothOn.value == on) return
        bluetoothOn.value = on
        attachedSource?.let { if (on) it.onBluetoothOn() else it.onBluetoothOff() }
    }

    private suspend fun accept(batch: List<Reading>) {
        val now = clock.now()
        // a reading stamped ahead of the phone's clock would look fresh for too long (and the same reading delivered
        // again can't be stored under a substitute time): dropped; the missing data then shows and alarms as no data,
        // and home says the phone's clock is behind (#50); every LO / HI is stored as the flag (39 / 401, no rate)
        // whichever source sent which raw number
        val (future, valid) = batch.mapNotNull(GlucoseRange::normalise).partition { it.t.isAfter(now + FUTURE_TOLERANCE) }
        noteClock(future.maxOfOrNull { it.t }, valid.maxOfOrNull { it.t }, now)
        if (valid.isEmpty()) return
        readings.upsert(valid)
        val startedAt = now.toEpochMilli()
        record.update {
            // the fence is read inside the update (and saved with it): a reset landing meanwhile still holds
            val fence = it.armAfterEpochMs?.let(Instant::ofEpochMilli)
            val arms = fence == null || valid.any { r -> r.t.isAfter(fence) }
            if (it.startedAtEpochMs == null && arms) it.copy(startedAtEpochMs = startedAt, armAfterEpochMs = null) else it
        }
    }

    /** Monitoring starts at [at] unless it already has (the first reading, or the source's start). */
    private suspend fun arm(at: Instant) {
        record.update { if (it.startedAtEpochMs == null) it.copy(startedAtEpochMs = at.toEpochMilli()) else it }
    }

    /**
     * The clock state after a batch whose newest dropped reading is [dropped] and newest accepted one [accepted]: behind
     * while the newest reading heard is a dropped one; an accepted reading at least as new (the clock was set right)
     * clears it, an older one (backfill) doesn't.
     */
    private fun noteClock(
        dropped: Instant?,
        accepted: Instant?,
        now: Instant,
    ) {
        mutableState.update { s ->
            val behind = s.clockBehind
            when {
                dropped != null && (behind == null || dropped >= behind.readingAt) -> {
                    s.copy(clockBehind = ClockBehind(dropped, Duration.between(now, dropped)))
                }

                behind != null && accepted != null && accepted >= behind.readingAt -> {
                    s.copy(clockBehind = null)
                }

                else -> {
                    s
                }
            }
        }
    }

    private fun onSourceSensor(sensor: SensorState) {
        sourceSensor.value = sensor
    }

    private fun applyWindow(stored: List<Reading>) {
        val from = clock.now() - WINDOW
        val recent = stored.filter { !it.t.isBefore(from) }
        mutableState.update { it.copy(latest = recent.lastOrNull(), recent = recent) }
    }

    /** The window's start, moved forward every [WINDOW_REFRESH] so the subscription doesn't grow for days. */
    private fun windowStarts() =
        flow {
            while (true) {
                emit(clock.now() - WINDOW - WINDOW_REFRESH)
                delay(WINDOW_REFRESH.toMillis())
            }
        }

    companion object {
        /**
         * The role's source runs and readings are expected: the wearer's sensor source started (a pairing connecting,
         * Juggluco chosen), a follow the wearer allowed.
         */
        fun sourceStarted(s: GlucoseState): Boolean =
            when (s.role) {
                Role.Wearer -> s.source !is SourceStatus.NotStarted
                Role.Follower -> s.link?.active == true
                null -> false
            }

        /** The recent window every surface can draw (the graph's longest setting is 24 h). */
        val WINDOW: Duration = Duration.ofHours(24)
        private val WINDOW_REFRESH: Duration = Duration.ofHours(1)

        /** A reading stamped further ahead of the phone's clock than this is dropped. */
        val FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)
    }
}
