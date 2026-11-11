// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.LinkState
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.ReadingHistory
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.graph.Freshness
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.stats.MetricsCache
import app.mauri.sukrara.graph.stats.toPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.Locale
import app.mauri.sukrara.graph.Clock as GraphClock

/** Everything a widget's picture is drawn from, at [now]; [dark] = the night palette. */
data class WidgetFrame(
    val glucose: GlucoseState,
    val treatments: List<Treatment>,
    val config: GraphConfig,
    val placements: WidgetPlacements,
    val now: Instant,
    val dark: Boolean,
    /** The stored days before the live window (the multi-day slots count them). */
    val stored: List<Reading> = emptyList(),
    /** The lock-screen widget's look (Settings › Lock screen). */
    val lock: LockLook = LockLook(),
) {
    /** The slots' numbers over the stored days and the live window: counted once per frame, for every widget and size. */
    val slots: MetricsCache by lazy {
        MetricsCache(ReadingHistory.join(stored, glucose.recent).map { it.toPoint() }, now.epochSecond)
    }
}

/** The inputs of a frame, before the clock and the theme are read. */
data class WidgetInputs(
    val glucose: GlucoseState,
    val treatments: List<Treatment>,
    val config: GraphConfig,
    val placements: WidgetPlacements,
    val stored: List<Reading> = emptyList(),
    val lock: LockLook = LockLook(),
)

/**
 * Redraws the widgets only when what they show changes: the latest reading, its freshness step (monitor's
 * FreshnessTicker alarm sends [freshnessTicks]), the sensor, *Mute all*, the treatments, the settings, the theme, or how
 * a time is written (12/24-hour, time zone, language: the monitor sends a tick on each of those changes).
 * While the screen is off nothing is drawn (no launcher shows it); when it comes back on, one redraw catches up if
 * anything changed. No polling: it reacts only to its inputs.
 *
 * A widget is a picture the launcher keeps after the app's process dies, and the monitor's alarm is armed only while
 * its service posts. So every picture asks [nextRedraw] for its own next freshness step (a time shown at 90 s, struck
 * through at 5 min, …; null = it never changes): the widgets' alarm, which starts the process when needed and calls
 * [tick]. No widget ever shows a value as current while nothing will redraw it (#98).
 */
class WidgetUpdater(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val glucose: Flow<GlucoseState>,
    private val treatments: Flow<List<Treatment>>,
    private val config: Flow<GraphConfig>,
    private val placements: Flow<WidgetPlacements>,
    private val stored: Flow<List<Reading>>,
    private val freshnessTicks: Flow<Unit>,
    private val screenOn: Flow<Boolean>,
    private val dark: () -> Boolean,
    private val redraw: suspend () -> Unit,
    private val nextRedraw: (Instant?) -> Unit = {},
    private val lockLook: Flow<LockLook> = flowOf(LockLook()),
) {
    private val latestInputs = MutableStateFlow<WidgetInputs?>(null)
    private val drawnFrame = MutableStateFlow<WidgetFrame?>(null)
    private var drawnKey: DrawKey? = null
    private val drawing = Mutex()

    /** The frame the widgets show; set just before every redraw. */
    val frame: StateFlow<WidgetFrame?> = drawnFrame.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job != null) return
        job =
            scope.launch {
                val inputs =
                    combine(glucose, treatments, config, placements, combine(stored, lockLook, ::Pair)) { g, t, c, p, (s, l) ->
                        WidgetInputs(g, t, c, p, s, l)
                    }
                combine(inputs, freshnessTicks.onStart { emit(Unit) }, screenOn) { i, _, on -> i to on }
                    .collect { (i, on) -> onChange(i, on) }
            }
    }

    /**
     * The widgets' redraw alarm came: redraws now if the step changed, before it returns (the receiver waits), also
     * with the screen off: the process may be gone again by the time it comes on, and nothing else would draw.
     */
    suspend fun tick() {
        val inputs = latestInputs.filterNotNull().first()
        onChange(inputs, screenOn = true)
    }

    private suspend fun onChange(
        inputs: WidgetInputs,
        screenOn: Boolean,
    ) = drawing.withLock {
        latestInputs.value = inputs
        val now = clock.now()
        val night = dark()
        val key = DrawKey.of(inputs, now, night)
        if (key == drawnKey || !screenOn) return@withLock
        drawnKey = key
        drawnFrame.value =
            WidgetFrame(inputs.glucose, inputs.treatments, inputs.config, inputs.placements, now, night, inputs.stored, inputs.lock)
        redraw()
        // the picture's own next step, whoever else is alive by then
        val latest =
            inputs.glucose.latest
                ?.t
                ?.epochSecond
        nextRedraw(Freshness.nextChangeAt(latest, now.epochSecond)?.let(Instant::ofEpochSecond))
    }

    /**
     * The frame to compose now, also when nothing was drawn yet in this process (a system update after a restart,
     * possibly with the screen off): built from the latest inputs.
     */
    suspend fun current(): WidgetFrame {
        drawnFrame.value?.let { return it }
        val i = latestInputs.filterNotNull().first()
        return WidgetFrame(i.glucose, i.treatments, i.config, i.placements, clock.now(), dark(), i.stored, i.lock)
    }

    /**
     * What a picture depends on: equal keys draw the same picture. [clock] = [GraphClock.signature] (12/24-hour, zone)
     * and [locale] the language every time and word is written in.
     */
    internal data class DrawKey(
        val inputs: WidgetInputs,
        val freshnessStep: Long?,
        val dark: Boolean,
        val clock: String,
        val locale: Locale,
    ) {
        companion object {
            fun of(
                inputs: WidgetInputs,
                now: Instant,
                dark: Boolean,
                clock: String = GraphClock.signature(),
                locale: Locale = Locale.getDefault(),
            ): DrawKey {
                // the next step's time is constant within one step, so it identifies the step the reading is in
                val step =
                    Freshness.nextChangeAt(
                        inputs.glucose.latest
                            ?.t
                            ?.epochSecond,
                        now.epochSecond,
                    )
                return DrawKey(inputs.copy(glucose = inputs.glucose.drawn()), step, dark, clock, locale)
            }

            /** Only what the picture, its description and its tap use (not the link's heartbeat or the service). */
            private fun GlucoseState.drawn() =
                GlucoseState(
                    role = role,
                    latest = latest,
                    recent = recent,
                    sensor = sensor,
                    source = SourceStatus.NotStarted,
                    alerts =
                        AlertUiState(
                            firingKinds = alerts.firingKinds,
                            status = alerts.status,
                            treatmentQuiet = alerts.treatmentQuiet,
                            mutedUntil = alerts.mutedUntil,
                        ),
                    link = link?.let { LinkState(it.wearerName, lastHeardAt = null, revoked = it.revoked) },
                )
        }
    }
}
