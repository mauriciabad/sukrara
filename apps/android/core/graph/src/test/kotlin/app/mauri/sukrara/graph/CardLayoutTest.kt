// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.LockBackground
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.graph.Fixtures.bool
import app.mauri.sukrara.graph.Fixtures.double
import app.mauri.sukrara.graph.Fixtures.long
import app.mauri.sukrara.graph.Fixtures.str
import app.mauri.sukrara.graph.stats.GlucosePoint
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * The layout rules of contracts/graph/layout (rules.json over scenes.json; expect.json = the answers the Mac checks
 * too), run on the renderer itself. Every registered card surface is drawn with the style its entry path uses (the
 * widgets' [widgetCardStyle], the notification's [notificationCardStyle], the app panel's
 * [GraphConfig.appPanelStyle]) at its sizes and parts from surfaces.json ([shapesAreTheRegisteredCardSurfaces] keeps
 * them equal): every rule must hold across all of them, and every shared answer they produce must equal expect.json
 * (this test never rewrites it).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CardLayoutTest {
    private val savedZone = TimeZone.getDefault()
    private val savedLocale = Locale.getDefault()
    private val d = 1f

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        RoundedFont.init(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        Clock.hour24Source = null
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    private val sceneFile by lazy { Fixtures.read("layout/scenes.json") }
    private val rulesFile by lazy { Fixtures.read("layout/rules.json") }
    private val now by lazy { sceneFile["now"].long()!! }

    private class Scene(
        val id: String,
        val readings: List<GlucosePoint>,
        val treatments: List<Treatment>,
        val sensor: SensorInfo?,
    )

    /**
     * One stand-in surface: the parts it draws (each with its height; 0 = the size's), and its sizes. [boxes] = a surface
     * that lays its parts out itself (the lock-screen widget): each part's box in the size, from its real layout.
     */
    private class Shape(
        val id: String,
        val place: GraphPlace,
        val parts: List<Pair<CardPart, Float>>,
        val sizes: List<Pair<Float, Float>>,
        val boxes: ((Float, Float) -> List<Pair<CardPart, RectF>>)? = null,
        val style: (GraphConfig) -> CardStyle,
    )

    /**
     * One surface × size × scene × settings render: a card surface's [layouts], or a glyph surface's [faces] (its
     * [GlucoseCard.face], what it writes or reads out; [unit] = the settings' unit).
     */
    private class Render(
        val shape: String,
        val size: String,
        val scene: String,
        val settings: String,
        val layouts: List<CardLayout>,
        val faces: List<GlucoseLook> = emptyList(),
        val unit: GlucoseUnit = GlucoseUnit.MGDL,
    ) {
        val glyph: Boolean get() = layouts.isEmpty()

        override fun toString() = "$shape $size"
    }

    /** Readings every 5 min from -days to -age, a triangle wave lo..hi with the period, then the `last` reading. */
    private fun scenes(): List<Scene> =
        sceneFile["scenes"]!!.jsonArray.map { it.jsonObject }.map { o ->
            val days = o["days"].long() ?: 0
            val readings =
                if (days == 0L) {
                    emptyList()
                } else {
                    val lo = o["lo"].long()!!.toInt()
                    val hi = o["hi"].long()!!.toInt()
                    val period = o["period"].long()!!
                    val half = period / 2
                    val first = now - days * 86_400
                    val last = now - o["age"].long()!!
                    val out = ArrayList<GlucosePoint>()
                    var t = first
                    while (t < last) {
                        val k = Math.floorMod(t - first, period)
                        val tri = if (k <= half) k else period - k
                        out += GlucosePoint(t, (lo + (hi - lo) * tri / half).toInt(), null)
                        t += 300
                    }
                    out += GlucosePoint(last, o["last"].long()!!.toInt(), o["rate"].double())
                    out
                }
            val trs =
                o["treatments"]?.jsonArray.orEmpty().map { it.jsonObject }.map {
                    val at = Instant.ofEpochSecond(now + it["t"].long()!!)
                    Treatment(
                        it["id"].str()!!,
                        TreatmentKind.entries.first { k -> k.id == it["k"].str() },
                        it["a"].double()!!,
                        at,
                        note = it["n"].str().orEmpty(),
                        modifiedAt = at,
                        origin = TreatmentOrigin.Phone,
                    )
                }
            val sensor =
                o["sensor"]?.jsonObject?.let {
                    SensorInfo(
                        now + it["start"].long()!!,
                        now + it["end"].long()!!,
                        now + it["warmupEnd"].long()!!,
                    )
                }
            Scene(o["id"].str()!!, readings, trs, sensor)
        }

    /** scenes.json `settings`: id, the GraphConfig it runs with and the phone's 24-hour setting. */
    private fun settings(): List<Triple<String, GraphConfig, Boolean>> =
        sceneFile["settings"]!!.jsonArray.map { it.jsonObject }.map { o ->
            var x = GraphConfig()
            if (o["sensorTime"].bool() == false) x = x.copy(sensorHidden = GraphPlace.entries.toSet())
            o["side"].bool()?.let { x = x.copy(side = it) }
            o["ringCount"].long()?.let { x = x.copy(ringCount = it.toInt()) }
            o["lastPlace"].str()?.let { x = x.copy(lastPlace = LastPlace.valueOf(it)) }
            o["lines"]?.jsonArray?.let { l -> x = x.copy(lines = l.map { it.long()!!.toInt() }) }
            o["gridHours"].long()?.let { x = x.copy(gridHours = it.toInt()) }
            o["high"].long()?.let { x = x.copy(bands = x.bands.copy(high = it.toInt())) }
            o["unit"].str()?.let { x = x.copy(unit = GlucoseUnit.of(it)) }
            when (val m = o["marks"]) {
                null -> {}

                is JsonPrimitive -> {
                    x = x.copy(marks = MarkShow.ALL)
                }

                is JsonObject -> {
                    x =
                        x.copy(
                            marks =
                                MarkShow.parse(
                                    m.entries.joinToString(",") { (k, v) ->
                                        "$k:" +
                                            v.jsonArray.joinToString("+") { it.str()!! }
                                    },
                                ),
                        )
                }

                else -> {
                    error("marks: $m")
                }
            }
            Triple(o["id"].str()!!, x, o["hour24"].bool() ?: true)
        }

    private fun sizes(id: String): List<Pair<Float, Float>> {
        val reg =
            Fixtures
                .read("surfaces/surfaces.json")["android"]!!
                .jsonObject["surfaces"]!!
                .jsonArray
                .map { it.jsonObject }
        return reg.first { it["id"].str() == id }["sizes"]!!.jsonArray.map { s ->
            s.jsonArray.let {
                it[0].double()!!.toFloat() to
                    it[1].double()!!.toFloat()
            }
        }
    }

    private fun full(
        c: GraphConfig,
        p: GraphPlace,
        graph: Boolean = true,
    ) = CardStyle(
        graph = if (graph) c.graphStyle(c.hours(p)) else null,
        rings = c.rings(),
        last = c.lastPlace,
        lastElse = c.lastElseRing(),
        sensorTime = c.sensorTime(p),
        // a card without a chart still colours the value and the rings by the user's bands
        bands = c.bands,
        unit = c.unit,
    )

    /** The registered card surfaces' shapes (surfaces.json parts and sizes). */
    private fun shapes() =
        listOf(
            // the widgets' real style (WidgetStyle.kt, what :widgets draws with)
            Shape("widget-value", GraphPlace.WIDGETS, listOf(CardPart.ALL to 0f), sizes("widget-value")) {
                widgetCardStyle(CardSurface.WIDGET_VALUE, it)
            },
            Shape("widget-graph", GraphPlace.WIDGETS, listOf(CardPart.ALL to 0f), sizes("widget-graph")) {
                widgetCardStyle(CardSurface.WIDGET_GRAPH, it)
            },
            Shape("widget-combined", GraphPlace.WIDGETS, listOf(CardPart.ALL to 0f), sizes("widget-combined")) {
                widgetCardStyle(CardSurface.WIDGET_COMBINED, it)
            },
            // the panel's real style (GraphConfig.appPanelStyle, what HomeCard's PanelCard draws with)
            Shape("app-panel", GraphPlace.APP, listOf(CardPart.HEADER to 120f, CardPart.GRAPH to 180f), sizes("app-panel")) {
                it.appPanelStyle()
            },
            // the notification's real style (WidgetStyle.kt notificationCardStyle, what :monitor draws with)
            Shape("notification", GraphPlace.NOTIFICATION, listOf(CardPart.HEADER to 64f, CardPart.GRAPH to 96f), sizes("notification")) {
                notificationCardStyle(it)
            },
            // the Sensor ready page's real style (WidgetStyle.kt sensorReadyCardStyle, what :feature:sensor draws with)
            Shape("sensor-ready", GraphPlace.APP, listOf(CardPart.VALUE to 0f), sizes("sensor-ready")) {
                sensorReadyCardStyle(it)
            },
            // the lock-screen widget's real style and boxes (WidgetStyle.kt lockWidgetCardStyle / lockWidgetBoxes, what
            // :widgets draws with), around a centred 76 dp lock icon, with the default background (frosted glass: boxed)
            Shape(
                "lock-widget",
                GraphPlace.WIDGETS,
                listOf(CardPart.VALUE to 0f, CardPart.SLOTS to 0f, CardPart.GRAPH to 0f),
                sizes("lock-widget"),
                boxes = { w, h -> lockWidgetBoxes(w * d, h * d, LockHole.centred(w), d, LockBackground.DEFAULT.boxed) },
            ) { lockWidgetCardStyle(it) },
            // Layout coverage only, not a registered surface (not in the app; surfaces.json "about"): the shape the Mac
            // shares answers with in expect.json (a wide 3-ring lock-screen card). Drop it only together with the
            // expect.json answers it alone produces.
            Shape("lockscreen-shape", GraphPlace.WIDGETS, listOf(CardPart.ALL to 0f), listOf(380f to 200f, 360f to 170f)) {
                full(it, GraphPlace.WIDGETS)
            },
        )

    /** The shapes that are layout coverage only, not surfaces of the app (see [shapes]). */
    private val coverageOnly = setOf("lockscreen-shape", "value-only-shape")

    /**
     * The shapes are the registry: one per Android card surface of surfaces.json with exactly its parts and sizes, so
     * a surface added, resized or split into other parts is drawn here the same round.
     */
    @Test
    fun shapesAreTheRegisteredCardSurfaces() {
        val registered =
            Fixtures
                .read("surfaces/surfaces.json")["android"]!!
                .jsonObject["surfaces"]!!
                .jsonArray
                .map { it.jsonObject }
                .filter { it["kind"].str() == "card" }
        val shapes = shapes().associateBy { it.id }
        assertEquals("card surfaces of surfaces.json", registered.map { it["id"].str()!! }.toSet(), shapes.keys - coverageOnly)
        for (s in registered) {
            val id = s["id"].str()!!
            val parts = s["parts"]!!.jsonArray.map { it.str()!! }
            assertEquals("$id parts", parts, shapes.getValue(id).parts.map { it.first.name.lowercase() })
            assertEquals("$id sizes", sizes(id), shapes.getValue(id).sizes)
            // its value among its parts, or the registry says why not (the Mac's LayoutTests checks the same)
            assertTrue(
                "$id draws $parts but not its value: or set \"no_value\" with the reason",
                s["no_value"] != null || parts.any { it in setOf("all", "header", "value") },
            )
        }
    }

    /** The glyph surfaces of surfaces.json: each takes the value from [GlucoseCard.face] ([renderAll] asks it as they do). */
    private fun glyphSurfaces(): List<CardSurface> {
        val ids =
            Fixtures
                .read("surfaces/surfaces.json")["android"]!!
                .jsonObject["surfaces"]!!
                .jsonArray
                .map { it.jsonObject }
                .filter { it["kind"].str() == "glyph" }
                .map { it["id"].str()!! }
        return ids.map { id -> CardSurface.entries.first { it.id == id } }
    }

    /** The alert settings' default mutes after insulin and carbs (the schema's defaults). */
    private val defaultMutes =
        MuteRule { t ->
            when (t.kind) {
                TreatmentKind.RapidInsulin -> SettingsSpec.MUTE_INSULIN.default
                TreatmentKind.Carbs -> SettingsSpec.MUTE_CARBS.default
                else -> null
            }?.let { t.at.epochSecond + (it * 60).toLong() }
        }

    /**
     * What the alert runtime says for a scene with the default thresholds, reduced to what these scenes
     * need: a current reading past the low / high threshold needs action, unless the newest carbs (low) / rapid insulin
     * (high) still mutes it (the default mutes; the Mac's DefaultAlertRules is the same).
     */
    private fun standInStatus(sc: Scene): CardStatus {
        val r = sc.readings.lastOrNull() ?: return CardStatus.None
        if (!Freshness.of(now - r.t).showGraph) return CardStatus.None
        val low =
            when {
                r.sgv < SettingsSpec.THRESHOLD_LOW.default -> true
                r.sgv > SettingsSpec.THRESHOLD_HIGH.default -> false
                else -> return CardStatus.None
            }
        val kind = if (low) TreatmentKind.Carbs else TreatmentKind.RapidInsulin
        val cover =
            sc.treatments
                .filter { it.kind == kind && it.at.epochSecond <= now }
                .mapNotNull { t -> defaultMutes.muteUntil(t)?.let { t to it } }
                .filter { it.second > now }
                .maxByOrNull { it.first.at }
        return cover?.let { (t, until) -> CardStatus.Taken(kind, t.at.epochSecond, until) } ?: CardStatus.Needed(lowSide = low)
    }

    /** Every scene × settings × shape × size; [fontScale] = the system font size the surfaces pass. */
    private fun renderAll(fontScale: Float = 1f): List<Render> {
        val words = Scenes.words()
        val pal = GraphPalette.of(false)
        val out = ArrayList<Render>()
        val scenes = scenes()
        for ((sid, cfg, h24) in settings()) {
            Clock.hour24Source = { h24 }
            for (sc in scenes) {
                val model =
                    CardModel(
                        readings = sc.readings,
                        treatments = sc.treatments,
                        nowS = now,
                        words = words,
                        status = standInStatus(sc),
                        muteRule = defaultMutes,
                        sensor = sc.sensor,
                    )
                // the glyph surfaces (status bar, tile, notification text, widget description, read aloud): what
                // GlucoseCard.face says for the newest reading, the sensor and the unit, as each of them asks it; the
                // settings that change it (the unit) and the clock variants, as on the Mac (LayoutTests)
                if ((sid == "default" || sid == "mmol" || sid.startsWith("clock")) && sc.readings.isNotEmpty()) {
                    val latest = sc.readings.last()
                    val look = SensorBadge.look(sc.sensor, now, latest.t, words)
                    for (g in glyphSurfaces()) {
                        val face = GlucoseCard.face(latest, now, look, g, cfg.unit, words.locale)
                        out += Render(g.id, "-", sc.id, sid, emptyList(), listOf(face), cfg.unit)
                    }
                }
                for (shape in shapes()) {
                    val st = shape.style(cfg)
                    for ((w, h) in shape.sizes) {
                        val boxes = shape.boxes?.invoke(w, h)?.toMap()
                        val layouts =
                            shape.parts.mapNotNull { (part, ph) ->
                                // a laid-out part draws in its box (px); a part without one (no room) isn't drawn
                                val box = if (boxes != null) boxes[part] ?: return@mapNotNull null else null
                                val bw = box?.width()?.div(d) ?: w
                                val bh = box?.height()?.div(d) ?: if (ph > 0) ph else h
                                val bmp = Bitmap.createBitmap((bw * d).toInt(), (bh * d).toInt(), Bitmap.Config.ARGB_8888)
                                GlucoseCard
                                    .draw(
                                        Canvas(bmp),
                                        bw * d,
                                        bh * d,
                                        model,
                                        st,
                                        pal,
                                        d,
                                        CardSurface.TEST,
                                        part,
                                        fontScale = fontScale,
                                    ).also { bmp.recycle() }
                            }
                        out += Render(shape.id, "${w.toInt()}x${h.toInt()}", sc.id, sid, layouts)
                    }
                }
            }
        }
        return out
    }

    private fun strip(info: String) = info.replace(Regex("\\s*\\[[^]]*]"), "").replace(Regex("\\s*\\(no room\\)"), "").trim()

    private fun wh(
        w: Float,
        h: Float,
    ) = "${Math.round(w / d)}x${Math.round(h / d)}"

    /** The inputs a rule's `by` may name, for one render (the same facts as the Mac's -graphselftest). */
    private fun facts(r: Render): Map<String, String> {
        if (r.glyph) {
            // a glyph surface has no card style: only its scene, settings, unit and whether it shows a face
            return mapOf("scene" to r.scene, "settings" to r.settings, "unit" to r.unit.key, "face" to "${r.faces.isNotEmpty()}") +
                listOf("labels", "side", "hours", "sensorTime", "badge", "rings", "chart", "pctWide", "part", "box").associateWith { "-" } +
                mapOf("value" to "glyph", "graph" to "false", "lane2" to "false", "sensorShown" to "false")
        }
        val st = r.layouts.first().style
        val chart = r.layouts.firstNotNullOfOrNull { l -> l.first("chart")?.box }
        val slots = r.layouts.firstOrNull { it.first("slots") != null }
        val slotsBox = slots?.first("slots.box")?.box
        return mapOf(
            "scene" to r.scene,
            "settings" to r.settings,
            "unit" to st.unit.key,
            "face" to "${st.value || (st.graph != null && chart == null && r.layouts.any { it.part == CardPart.ALL })}",
            "value" to "${st.value}",
            "graph" to "${st.graph != null}",
            "labels" to "${st.graph?.axisLabels ?: "-"}",
            "side" to "${st.graph?.side ?: "-"}",
            "hours" to "${st.graph?.windowS?.div(3600) ?: "-"}",
            "sensorTime" to "${st.sensorTime}",
            "badge" to "${st.badge}",
            "rings" to ringsSignature(st),
            "chart" to (chart?.let { wh(it.width(), it.height()) } ?: "-"),
            "pctWide" to (chart?.let { if (it.width() / d >= GlucoseGraph.PCT_MIN_WIDTH_DP) "1" else "0" } ?: "-"),
            "lane2" to "${r.layouts.any { it.first("chart.lane2") != null }}",
            "sensorShown" to "${r.layouts.any { l -> l.all("chart.sensor").any { !it.info.contains("(no room)") } }}",
            "part" to (slots?.part?.name?.lowercase() ?: "-"),
            "box" to (slotsBox?.let { wh(it.width(), it.height()) } ?: "-"),
        )
    }

    private fun ringsSignature(st: CardStyle) =
        st.rings.joinToString(",") { "${it.kind.name}:${it.windowS}:${it.range.name}:${if (it.marks) 1 else 0}" } +
            "|${st.last.name}|" + (st.lastElse?.let { "${it.kind.name}:${it.windowS}" } ?: "-")

    private fun descriptor(
        r: Render,
        rule: JsonObject,
    ): List<String> {
        val names = rule["elements"]!!.jsonArray.map { it.str()!! }.toSet()
        if (r.glyph) {
            return r.faces
                .flatMap(::faceElements)
                .filter { it.first in names }
                .map { "${it.first} ${it.second}".trim() }
                .sorted()
        }
        val box = rule["box"].bool() == true
        val chart = r.layouts.firstNotNullOfOrNull { it.first("chart")?.box }
        val els = r.layouts.flatMap { l -> l.elements.filter { it.name in names }.map { l to it } }
        if (rule["mode"].str() == "subset") return els.sortedBy { it.second.box?.left ?: 0f }.map { strip(it.second.info) }
        return els
            .map { (l, e) ->
                val b = e.box
                val geo =
                    if (box && b != null && chart != null) {
                        " @${CardLayout.dp(b.left - chart.left, l.d)},${CardLayout.dp(chart.bottom - b.top, l.d)}," +
                            "${CardLayout.dp(b.width(), l.d)}x${CardLayout.dp(b.height(), l.d)}"
                    } else {
                        ""
                    }
                "${e.name} ${strip(e.info)}".trim() + geo
            }.distinct()
            .sorted()
    }

    /** What a glyph surface shows of the value's place, named as the card's elements (the Mac's faceElements). */
    private fun faceElements(f: GlucoseLook): List<Pair<String, String>> =
        when (f.state) {
            GlucoseLook.State.VALUE -> {
                val rate = f.rate
                listOf(
                    "value.number" to (f.text.orEmpty() + if (f.strike) " struck" else ""),
                    "value.trend" to
                        when {
                            f.trend != TrendMark.ARROW || rate == null -> "ring"
                            TrendAngle.isDouble(rate) -> "arrow double"
                            else -> "arrow single"
                        },
                )
            }

            GlucoseLook.State.WARMING_UP -> {
                listOf("value.warmup" to f.warmup.orEmpty())
            }

            GlucoseLook.State.ENDED -> {
                listOf("value.ended" to "")
            }

            else -> {
                listOf("value.none" to f.state.name)
            }
        }

    private fun isSubsequence(
        a: List<String>,
        b: List<String>,
    ): Boolean {
        var i = 0
        for (x in b) if (i < a.size && a[i] == x) i++
        return i == a.size
    }

    @Test
    fun everySurfaceShapeFollowsTheSameRules() {
        val answers = sortedMapOf<String, String>()
        val renders = renderAll()
        val problems = ruleProblems(renders, answers)
        if (problems.isNotEmpty()) {
            fail("${problems.size} layout rule failures (contracts/graph/layout/rules.json):\n  " + problems.take(40).joinToString("\n  "))
        }
        compareAnswers(renders, answers)
    }

    /**
     * A large system font (2×: the chart's words at their 1.3× cap, `GlucoseGraph.MAX_TEXT_SCALE`) keeps every rule
     * too: the same pass over the same renders. The shared answers are the default size's (the Mac draws at 1×).
     */
    @Test
    fun largeFontFollowsTheSameRules() {
        val renders = renderAll(fontScale = 2f)
        val problems = ruleProblems(renders, sortedMapOf())
        // the words did grow: a treatment label is 1.3× as tall as at 1×
        val grown = renders.flatMap { it.layouts }.mapNotNull { it.first("chart.label")?.box }
        assertTrue("no render shows a treatment label at 2×", grown.isNotEmpty())
        assertTrue(
            "treatment labels at 2× are ${grown.map {
                it.height()
            }.distinct()} tall, want ${GlucoseGraph.TEXT_ROW_DP * GlucoseGraph.MAX_TEXT_SCALE}",
            grown.all { abs(it.height() / d - GlucoseGraph.TEXT_ROW_DP * GlucoseGraph.MAX_TEXT_SCALE) < 0.05f },
        )
        if (problems.isNotEmpty()) {
            fail(
                "${problems.size} layout rule failures at 2× font (contracts/graph/layout/rules.json):\n  " +
                    problems.take(40).joinToString("\n  "),
            )
        }
    }

    /** Every rule of rules.json over [renders]; the shared rules' answers go into [answers]. */
    private fun ruleProblems(
        renders: List<Render>,
        answers: MutableMap<String, String>,
    ): List<String> {
        val problems = ArrayList<String>()

        // the rules: same inputs → same elements, whatever the surface; shared rules give the answers the Mac checks
        for (rule in rulesFile["rules"]!!.jsonArray.map { it.jsonObject }) {
            val name = rule["name"].str()!!
            val by = rule["by"]!!.jsonArray.map { it.str()!! }
            val groups = renders.groupBy { r -> facts(r).let { f -> by.joinToString(";") { "$it=${f[it]}" } } }
            for ((key, rs) in groups) {
                val ds = rs.map { it to descriptor(it, rule) }
                val answer =
                    if (rule["mode"].str() == "subset") {
                        val longest = ds.maxBy { it.second.size }.second
                        for ((r, dd) in ds) if (!isSubsequence(dd, longest)) problems += "$name [$key]: $r shows $dd, not a cut of $longest"
                        longest
                    } else {
                        val distinct = ds.groupBy({ it.second }, { it.first })
                        if (distinct.size > 1) {
                            problems += "$name [$key] differs between surfaces:\n" +
                                distinct.entries.joinToString("\n") { (dd, who) -> "      $dd  ← ${who.distinct().take(4).joinToString()}" }
                        }
                        ds.first().second
                    }
                if (rule["shared"].bool() == true) answers["$name|$key"] = answer.joinToString(" | ")
            }
        }

        // the gaps: a column is its widest drawn label plus one constant margin on each side
        for (g in rulesFile["gaps"]!!.jsonArray.map { it.jsonObject }) {
            val gap = g["gap"].double()!!
            val column = g["column"].str()!!
            var seen = 0
            for (r in renders) {
                for (l in r.layouts) {
                    val col = l.first(column)?.box ?: continue
                    val ls = l.all(g["labels"].str()!!).mapNotNull { e -> e.box?.let { e.info to it } }
                    if (ls.isEmpty()) {
                        problems += "$column: $r draws no labels"
                        continue
                    }
                    seen++
                    val alignRight = g["align"].str() == "right"
                    val each = ls.map { (_, b) -> (if (alignRight) col.right - b.right else b.left - col.left) / l.d }
                    val widest =
                        (if (alignRight) ls.minOf { it.second.left } - col.left else col.right - ls.maxOf { it.second.right }) / l.d
                    if (abs(widest - gap) > 0.05 || each.any { abs(it - gap) > 0.05 }) {
                        problems += "$column gaps on $r (${r.scene}/${r.settings}): widest $widest, aligned $each; want $gap"
                    }
                }
            }
            if (seen == 0) problems += "$column: no render shows it (gaps rule untested)"
        }

        // the sensor badge never moves a label: with and without it, the same layout
        val badge = rulesFile["badge"]!!.jsonObject
        val (withBadge, noBadge) = badge["compare"]!!.jsonArray.map { it.str()!! }
        val badgeEls = badge["elements"]!!.jsonArray.map { it.str()!! }.toSet()
        // an hour label the badge covers is left out but laid out as if drawn: compared as one
        val covered = badge["covered"].str()!!

        fun badgeDesc(r: Render) =
            r.layouts
                .flatMap { l ->
                    l.elements.filter { it.name in badgeEls || it.name == covered }.map { e ->
                        "${if (e.name == covered) "time.text" else e.name} ${e.info}" +
                            (
                                e.box?.let { b ->
                                    " @${CardLayout.dp(
                                        b.left,
                                        l.d,
                                    )},${CardLayout.dp(b.top, l.d)},${CardLayout.dp(b.right, l.d)},${CardLayout.dp(b.bottom, l.d)}"
                                }
                                    ?: ""
                            )
                    }
                }.sorted()
        var compared = 0
        for (r in renders.filter {
            it.settings == withBadge &&
                it.layouts.any { l -> l.all("chart.sensor").any { e -> !e.info.contains("(no room)") } }
        }) {
            val o =
                renders.firstOrNull { it.shape == r.shape && it.size == r.size && it.scene == r.scene && it.settings == noBadge }
                    ?: continue
            compared++
            val a = badgeDesc(r)
            val b = badgeDesc(o)
            if (a !=
                b
            ) {
                problems +=
                    "badge on $r (${r.scene}): the layout changes with the sensor badge: with ${a - b.toSet()} without ${b - a.toSet()}"
            }
        }
        if (compared == 0) problems += "badge: no render shows the sensor badge (rule untested)"
        // no hour label is drawn under the badge (#71)
        var coveredSeen = 0
        val cut = GlucoseGraph.BADGE_CUT_DP
        for (r in renders) {
            for (l in r.layouts) {
                val s = l.first("chart.sensor.drawn")?.box ?: continue
                val erasedRight = s.right + cut * l.d
                coveredSeen += l.all(covered).size
                for (t in l.all("time.text")) {
                    val b = t.box ?: continue
                    if (b.left < erasedRight) problems += "badge on $r: the hour label '${t.info}' is drawn under the sensor badge"
                }
            }
        }
        if (coveredSeen == 0) problems += "badge: no render has an hour label under the badge (rule untested)"

        // the distribution's erased outline: the dots plus one constant margin
        val dist = rulesFile["distribution"]!!.jsonObject
        if (abs(
                GlucoseGraph.PROFILE_PITCH - dist["pitch"].double()!!,
            ) > 1e-4 || abs(GlucoseGraph.PROFILE_R - dist["dot"].double()!!) > 1e-4 ||
            abs(GlucoseGraph.PROFILE_CUT - dist["cut"].double()!!) > 1e-4
        ) {
            problems +=
                "distribution: GlucoseGraph ${GlucoseGraph.PROFILE_PITCH}/${GlucoseGraph.PROFILE_R}/${GlucoseGraph.PROFILE_CUT}, rules.json $dist"
        }
        var cuts = 0
        for (r in renders) {
            for (l in r.layouts) {
                val cut = l.first("chart.distribution.cut")?.box ?: continue
                val dots = l.first("chart.distribution.dots")?.box ?: continue
                cuts++
                val right = (cut.right - dots.right) / l.d
                val top = (dots.top - cut.top) / l.d
                val want = dist["cut"].double()!!
                if (abs(right - want) > 0.05 ||
                    abs(top - want) > 0.05
                ) {
                    problems += "distribution cut on $r: right $right top $top, want $want"
                }
            }
        }
        if (cuts == 0) problems += "distribution: no render erases its outline (rule untested)"

        // small slots beside the value are at most two (#71)
        val crowded = rulesFile["crowdedSlots"]!!.jsonObject
        val crowdedDp = crowded["dp"].double()!!
        val crowdedMax = crowded["max"].long()!!.toInt()
        if (abs(GlucoseCard.CROWDED_DP - crowdedDp) > 1e-4 || GlucoseCard.CROWDED_MAX != crowdedMax) {
            problems += "crowdedSlots: GlucoseCard ${GlucoseCard.CROWDED_DP}/${GlucoseCard.CROWDED_MAX}, rules.json $crowded"
        }
        var small = 0
        for (r in renders) {
            for (l in r.layouts.filter { it.part != CardPart.SLOTS }) {
                val slots = l.all("slot")
                val size = slots.firstOrNull()?.box?.width() ?: continue
                if (size / l.d >= crowdedDp) continue
                small++
                if (slots.size > crowdedMax) problems += "crowdedSlots on $r: ${slots.size} slots of ${size / l.d} dp"
            }
        }
        if (small == 0) problems += "crowdedSlots: no render has small slots beside the value (rule untested)"

        // a warming-up sensor's time left is labelled and never at a value's size
        val warmup = rulesFile["warmup"]!!.jsonObject
        val maxText = warmup["maxText"].double()!!
        val warmWords = warmup["words"]!!.jsonArray.map { it.str()!! }
        var warmShown = 0
        for (r in renders) {
            for (l in r.layouts) {
                val e = l.first("value.warmup") ?: continue
                warmShown++
                val size = (e.box?.height() ?: Float.MAX_VALUE) / l.d
                if (size > maxText + 0.05) problems += "warmup on $r: text ${size}dp, want ≤ $maxText"
                val text = l.first("value.warmup.text")?.info
                if (text == null ||
                    warmWords.none { text.startsWith(it) }
                ) {
                    problems += "warmup on $r: \"$text\" doesn't say $warmWords first"
                }
            }
        }
        if (warmShown == 0) problems += "warmup: no render shows a warming-up sensor (rule untested)"

        // one clock: every time a render draws follows its settings' 24-hour choice (the Mac's LayoutTests too)
        val hour24 = settings().associate { it.first to it.third }

        fun cycleOf(t: String): Boolean? =
            when {
                Regex("(?i)\\b(am|pm|a\\.\\s?m\\.|p\\.\\s?m\\.)").containsMatchIn(t) -> false
                Regex("\\d{1,2}:\\d{2}").containsMatchIn(t) -> true
                else -> null
            }
        for (r in renders) {
            val want = hour24.getValue(r.settings)
            val bad = r.layouts.flatMap { it.all("time.text") }.firstOrNull { e -> cycleOf(e.info)?.let { it != want } == true }
            if (bad !=
                null
            ) {
                problems += "clock: $r (${r.scene}/${r.settings}) shows \"${bad.info}\", want ${if (want) 24 else 12}-hour time"
            }
        }
        for (v in listOf("clock12", "clock24")) {
            val shown = renders.any { r -> r.settings == v && r.layouts.any { l -> l.all("time.text").any { cycleOf(it.info) != null } } }
            if (!shown) problems += "clock: no render in $v shows a time (rule untested)"
        }

        // treatment labels only up to maxHours
        val maxHours = rulesFile["treatmentLabels"]!!.jsonObject["maxHours"].long()!!
        assertTrue("treatmentLabels maxHours", GlucoseGraph.LABELS_MAX_S == maxHours * 3600)
        return problems
    }

    /** The shared [answers] these shapes produce against expect.json (or written into it with `-Dlayout.write=true`). */
    private fun compareAnswers(
        renders: List<Render>,
        answers: Map<String, String>,
    ) {
        // the shared answers: every one these shapes produce is the one the Mac checks against
        val expectFile = Fixtures.read("layout/expect.json")
        if (System.getProperty("layout.write") == "true") {
            // exactly Android's answers: its card and glyph surfaces cover every input the Mac's surfaces render (the
            // Mac fails an answer that isn't here), so an answer nobody renders any more goes
            val merged = answers.toSortedMap()

            // the file's own style: "/" escaped
            fun q(s: String) = JsonPrimitive(s).toString().replace("/", "\\/")
            val body = merged.entries.joinToString(",\n") { (k, v) -> "  ${q(k)}: ${q(v)}" }
            Fixtures.write("layout/expect.json", "{\n \"about\": ${expectFile["about"]},\n \"answers\": {\n$body\n }\n}\n")
            println("layout: wrote ${merged.size} answers to layout/expect.json")
            return
        }
        val expect = expectFile["answers"]!!.jsonObject
        val diffs =
            answers
                .filter { (k, v) ->
                    expect[k]?.str()?.let { it != v } == true
                }.map { (k, v) ->
                    "changed: $k\n      want ${expect[k].str()}\n      got  $v"
                }
        val matched = answers.keys.count { it in expect }
        assertTrue("no shared answer matched expect.json (the surfaces no longer cover its inputs)", matched > 0)
        if (diffs.isNotEmpty()) {
            fail("${diffs.size} of $matched shared answers differ from layout/expect.json:\n  " + diffs.take(30).joinToString("\n  "))
        }
        // exactly these answers: a new one, or one no surface renders any more, is a change to review (-Dlayout.write=true)
        val missing = answers.keys - expect.keys
        val gone = expect.keys - answers.keys
        if (missing.isNotEmpty() || gone.isNotEmpty()) {
            fail(
                "layout/expect.json is out of date: ${missing.size} new, ${gone.size} no surface renders any more " +
                    "(run with -Dlayout.write=true, review the diff, make the Mac follow):\n  " +
                    (missing.map { "new: $it" } + gone.map { "gone: $it" }).take(30).joinToString("\n  "),
            )
        }
        println("layout: ${renders.size} renders, $matched shared answers equal expect.json")
    }
}
