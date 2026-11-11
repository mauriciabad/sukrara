// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.graph.CardLayout
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.GlucoseGraph
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPlace
import app.mauri.sukrara.graph.id
import app.mauri.sukrara.graph.stats.GlucoseStats
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * The app panel scrolled and zoomed (contracts/graph/layout/scenes.json `scroll`; the Mac's dropdown checks the same
 * cases): PanelCard itself draws each case with a [ChartScroll] at the window's end and length, and the card it draws
 * (GlucoseCard.observer) shows only what is in its window. Its chart equals the panel drawn with the visible data
 * alone (every reading and treatment outside the window removed, now = the window's end, the place's time shown = the
 * window's length): the % column, the y range, the range labels, the lines, the distribution and the plot; the y range
 * is the visible readings'; the Now pill shows only while scrolled or zoomed; treatment labels only up to rules.json
 * `treatmentLabels`; and the font-independent `expect` of each case. Then a tap on the Now pill goes back to now and a
 * drag to the right goes into the past.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PanelScrollTest {
    @get:Rule val compose = createComposeRule()

    private val savedZone = TimeZone.getDefault()
    private val savedLocale = Locale.getDefault()
    private val sceneFile by lazy { json("graph/layout/scenes.json") }
    private val now by lazy { sceneFile.getValue("now").jsonPrimitive.long }
    private val captured = ArrayList<CardLayout>()
    private var panel by mutableStateOf<(@Composable () -> Unit)?>(null)

    private class Scene(
        val id: String,
        val readings: List<Reading>,
        val treatments: List<Treatment>,
    )

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        ClockText.hour24Source = { true }
        GlucoseCard.observer = { l -> if (l.surface == CardSurface.APP_PANEL) synchronized(captured) { captured += l } }
        compose.setContent { SukraraTheme(dark = false, dynamic = false) { panel?.invoke() } }
    }

    @After fun tearDown() {
        GlucoseCard.observer = null
        ClockText.hour24Source = null
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    private fun json(path: String) = Json.parseToJsonElement(Contracts.file(path).readText()) as JsonObject

    /** The app panel's first registered width (surfaces.json `app-panel`, dp). */
    private val width by lazy {
        json("graph/surfaces/surfaces.json")
            .getValue("android")
            .jsonObject
            .getValue("surfaces")
            .jsonArray
            .map { it.jsonObject }
            .first { it["id"]?.jsonPrimitive?.content == CardSurface.APP_PANEL.id }
            .getValue("sizes")
            .jsonArray[0]
            .jsonArray[0]
            .jsonPrimitive
            .long
            .toInt()
    }

    private fun JsonObject.num(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull

    /** scenes.json's maths (CardLayoutTest and the Mac's LayoutTests read the same): every 5 min, a triangle wave. */
    private fun scene(o: JsonObject): Scene {
        val days = o.num("days") ?: 0
        val readings = ArrayList<Reading>()
        if (days > 0) {
            val lo = o.num("lo")!!.toInt()
            val hi = o.num("hi")!!.toInt()
            val period = o.num("period")!!
            val half = period / 2
            val first = now - days * 86_400
            val last = now - o.num("age")!!
            var t = first
            while (t < last) {
                val k = Math.floorMod(t - first, period)
                val tri = if (k <= half) k else period - k
                readings += Reading(Instant.ofEpochSecond(t), (lo + (hi - lo) * tri / half).toInt(), null, SourceId.Libre3)
                t += 300
            }
            val rate = (o["rate"] as? JsonPrimitive)?.doubleOrNull
            readings += Reading(Instant.ofEpochSecond(last), o.num("last")!!.toInt(), rate, SourceId.Libre3)
        }
        val treatments =
            o["treatments"]?.jsonArray.orEmpty().map { it.jsonObject }.map {
                val at = Instant.ofEpochSecond(now + it.num("t")!!)
                Treatment(
                    it.getValue("id").jsonPrimitive.content,
                    TreatmentKind.entries.first { k -> k.id == it.getValue("k").jsonPrimitive.content },
                    (it["a"] as JsonPrimitive).doubleOrNull!!,
                    at,
                    note = (it["n"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    modifiedAt = at,
                    origin = TreatmentOrigin.Phone,
                )
            }
        return Scene(o.getValue("id").jsonPrimitive.content, readings, treatments)
    }

    /** The panel's card for [readings] at [at] with [scroll], as PanelCard draws it (a fresh composition: no easing). */
    private fun panel(
        readings: List<Reading>,
        treatments: List<Treatment>,
        at: Long,
        scroll: ChartScroll,
        config: GraphConfig = GraphConfig(),
    ): CardLayout {
        val latest = readings.last()
        val glucose =
            GlucoseState(
                role = Role.Wearer,
                latest = latest,
                recent = readings.filter { it.t.epochSecond > at - 12 * 3600 },
                sensor = SensorState.Active(null),
                source = SourceStatus.Live(latest.t),
                alerts = AlertUiState(),
                link = null,
                monitoringStartedAt = readings.first().t,
            )
        panel = null
        compose.waitForIdle()
        synchronized(captured) { captured.clear() }
        panel = {
            Box(Modifier.width(width.dp)) { PanelCard(glucose, readings, treatments, Instant.ofEpochSecond(at), config, scroll = scroll) }
        }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        return synchronized(captured) { captured.last() }
    }

    private fun info(
        l: CardLayout,
        n: String,
    ) = l.all(n).joinToString(",") { it.info }

    private fun box(
        l: CardLayout,
        n: String,
    ) = l.first(n)?.box?.let {
        "${CardLayout.dp(it.left, l.d)},${CardLayout.dp(it.top, l.d)},${CardLayout.dp(it.right, l.d)},${CardLayout.dp(it.bottom, l.d)}"
    } ?: "-"

    @Test fun scrolledAndZoomedWindowsShowOnlyTheirData() {
        val scroll = sceneFile.getValue("scroll").jsonObject
        val all = (scroll.getValue("scenes").jsonArray + sceneFile.getValue("scenes").jsonArray).map { scene(it.jsonObject) }
        val config = GraphConfig()
        val windowS = config.hours(GraphPlace.APP) * 3600L
        val b = config.bands
        val maxLabelS = json("graph/layout/rules.json").getValue("treatmentLabels").jsonObject.num("maxHours")!! * 3600
        val problems = ArrayList<String>()
        if (GlucoseGraph.LABELS_MAX_S !=
            maxLabelS
        ) {
            problems += "labels: GlucoseGraph.LABELS_MAX_S ${GlucoseGraph.LABELS_MAX_S}, rules.json $maxLabelS"
        }
        val cases = scroll.getValue("cases").jsonArray.map { it.jsonObject }
        for (c in cases) {
            val sc = all.first { it.id == c.getValue("scene").jsonPrimitive.content }
            val back = c.num("back")!!
            // a zoomed case: the window's length; its chart must equal a place set to that "time shown"
            val span = c.num("span")
            val w = span ?: windowS
            val end = now - back
            val from = end - w
            val name = "${sc.id} back ${back}s" + (span?.let { " span ${it}s" } ?: "")
            val scrolled = panel(sc.readings, sc.treatments, now, ChartScroll(if (back > 0) end else null, span))
            val visible = sc.readings.filter { it.t.epochSecond in (from - 60)..end }
            val alone =
                panel(
                    visible,
                    sc.treatments.filter { it.at.epochSecond in from..end },
                    end,
                    ChartScroll(),
                    config.copy(placeHours = config.placeHours + (GraphPlace.APP to (w / 3600).toInt())),
                )
            val markers = scrolled.first("chart.markers") != null
            for (n in listOf("chart.pct", "chart.y", "chart.values", "chart.markers", "chart.lines") +
                if (markers) emptyList() else listOf("time.text")) {
                if (info(scrolled, n) !=
                    info(alone, n)
                ) {
                    problems += "$name: $n \"${info(scrolled, n)}\", the visible data alone \"${info(alone, n)}\""
                }
            }
            for (n in listOf("chart.distribution", "chart.distribution.dots", "chart.distribution.cut", "chart.plot")) {
                if (box(scrolled, n) !=
                    box(alone, n)
                ) {
                    problems += "$name: $n at ${box(scrolled, n)}, the visible data alone ${box(alone, n)}"
                }
            }
            // the y range is the visible readings'
            val lo = visible.minOf { it.mgdl }
            val hi = visible.maxOf { it.mgdl }
            val want =
                "${maxOf(GlucoseStats.MIN_MG, minOf(b.low - 22, b.veryLow - 6, lo - 6))}-" +
                    "${minOf(GlucoseStats.MAX_MG, maxOf(b.high + 40, b.veryHigh + 8, hi + 8))}"
            if (info(scrolled, "chart.y") != want) problems += "$name: y range ${info(scrolled, "chart.y")}, want $want (visible $lo..$hi)"
            val window = if (back > 0) "${(from - now) / 60}m..${(end - now) / 60}m" else ""
            if (info(scrolled, "chart.window") !=
                window
            ) {
                problems += "$name: window \"${info(scrolled, "chart.window")}\", want \"$window\""
            }
            // treatment labels only up to rules.json treatmentLabels
            val labelled = scrolled.all("chart.label").isNotEmpty()
            if (w > maxLabelS &&
                labelled
            ) {
                problems += "$name: a ${w / 3600} h window draws treatment labels (over ${maxLabelS / 3600} h: none)"
            }
            val labelledInWindow =
                sc.treatments.any {
                    it.kind != TreatmentKind.Note && it.at.epochSecond in from..end &&
                        config.marks.label(it.kind.id)
                }
            if (w <= maxLabelS && !labelled && labelledInWindow) problems += "$name: a ${w / 3600} h window with treatments draws no labels"
            val away = back > 0 || span != null
            if ((scrolled.first("chart.now") != null) !=
                away
            ) {
                problems += "$name: the Now pill ${if (away) "is missing" else "shows at now"}"
            }
            // the shared, font-independent answer (the Mac's LayoutTests checks the same)
            val expect = c.getValue("expect").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val got =
                expect.keys.associateWith { n ->
                    scrolled.all(n).joinToString(",") { e -> e.info.replace(Regex("\\s*\\[[^]]*]"), "") }
                }
            if (got != expect) problems += "$name: expect $expect, got $got"
        }

        // a tap on the Now pill goes back to now; a drag to the right goes into the past
        val live = all.first { it.id == "live" }
        val pos = ChartScroll(now - 21_600)
        val pill = panel(live.readings, live.treatments, now, pos).first("chart.now")?.box
        if (pill == null) {
            problems += "tap: no Now pill to tap"
        } else {
            compose.onNodeWithTag("home-card").performTouchInput { click(Offset(pill.centerX(), pill.centerY())) }
            compose.waitForIdle()
            if (pos.away) problems += "tap: a tap on the Now pill left the chart ${pos.endS?.minus(now)} s back"
        }
        compose.onNodeWithTag("home-card").performTouchInput { swipeRight(startX = centerX - 150f, endX = centerX + 150f) }
        compose.waitForIdle()
        if ((pos.endS ?: now) >= now) problems += "drag: a drag to the right didn't scroll into the past"

        if (problems.isNotEmpty()) {
            fail("${problems.size} scroll failures (contracts/graph/layout/scenes.json \"scroll\"):\n  " + problems.joinToString("\n  "))
        }
    }
}
