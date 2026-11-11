// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
// Runs inside a scratch copy of poc/spikes/showcase-android (never in poc/ itself, which is frozen):
// contracts/graph/README.md, "The PoC's widgets".
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.Reading
import app.mauri.sukrara.core.SensorInfo
import app.mauri.sukrara.core.SensorLife
import app.mauri.sukrara.treatments.Treatment
import app.mauri.sukrara.widgets.WidgetKind
import app.mauri.sukrara.widgets.WidgetUpdater
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.TimeZone

/** Scratch: the PoC's widget layouts over the contracts' scenes, as data for contracts/graph/widgets/poc.json. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PocWidgetDump {
    private val contracts = File(System.getenv("CONTRACTS") ?: "/nonexistent")
    private val scenesJson by lazy { JSONObject(File(contracts, "graph/layout/scenes.json").readText()) }
    private val now by lazy { scenesJson.getLong("now") }

    private class Scene(val id: String, val readings: List<Reading>, val treatments: List<Treatment>, val sensor: SensorInfo?)

    private fun scenes(): List<Scene> {
        val a = scenesJson.getJSONArray("scenes")
        return (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val days = o.optInt("days", 0)
            val readings = if (days == 0) emptyList() else {
                val lo = o.getInt("lo"); val hi = o.getInt("hi"); val period = o.getLong("period"); val half = period / 2
                val first = now - days * 86400L
                val last = now - o.getLong("age")
                val out = ArrayList<Reading>()
                var t = first
                while (t < last) {
                    val k = Math.floorMod(t - first, period)
                    val tri = if (k <= half) k else period - k
                    out += Reading(t, (lo + (hi - lo) * tri / half).toInt(), null)
                    t += 300
                }
                out += Reading(last, o.getInt("last"), if (o.isNull("rate")) null else o.getDouble("rate"))
                out
            }
            val trs = o.optJSONArray("treatments")?.let { t ->
                (0 until t.length()).map { t.getJSONObject(it) }.map {
                    Treatment(id = it.getString("id"), k = it.getString("k"), a = it.getDouble("a"), t = now + it.getLong("t"),
                        n = it.optString("n", ""), u = 0, o = "phone")
                }
            } ?: emptyList()
            val sensor = o.optJSONObject("sensor")?.let {
                SensorInfo("S", now + it.getLong("start"), now + it.getLong("end"), now + it.getLong("warmupEnd"))
            }
            Scene(o.getString("id"), readings, trs, sensor)
        }
    }

    private fun box(b: android.graphics.RectF?, d: Float): JSONArray? = b?.let {
        JSONArray(listOf(it.left, it.top, it.right, it.bottom).map { v -> Math.round(v / d * 2) / 2.0 })
    }

    @Test fun dump() {
        if (!contracts.isDirectory) return
        val ctx = RuntimeEnvironment.getApplication()
        RoundedFont.init(ctx)
        app.mauri.sukrara.core.Words.loader = { app.mauri.sukrara.core.Words.of(ctx.resources) }
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        Clock.hour24Source = { true }
        val captured = ArrayList<CardLayout>()
        GlucoseCard.observer = { synchronized(captured) { captured += it } }
        val kinds = listOf(WidgetKind.VALUE to "widget-value", WidgetKind.GRAPH to "widget-graph", WidgetKind.COMBINED to "widget-combined")
        val sizes = JSONObject(File(System.getenv("SIZES")!!).readText())
        val variants = listOf<Pair<String, (GraphConfig) -> GraphConfig>>(
            "default" to { it },
            "rings-off" to { it.copy(ringCount = 0) },
            "three-end" to { it.copy(ringCount = 3, lastPlace = LastPlace.END) },
        )
        val base = GraphSettings.value
        val out = JSONArray()
        try {
            for ((vid, apply) in variants) {
                GraphSettings.update { apply(base) }
                for (sc in scenes()) {
                    SensorLife.update(ctx, sc.sensor)
                    for ((kind, id) in kinds) {
                        val list = sizes.getJSONArray(id)
                        for (i in 0 until list.length()) {
                            val w = list.getJSONArray(i).getDouble(0).toFloat()
                            val h = list.getJSONArray(i).getDouble(1).toFloat()
                            synchronized(captured) { captured.clear() }
                            val frame = WidgetUpdater.Frame(sc.readings, now, sc.treatments)
                            app.mauri.sukrara.widgets.renderWidget(ctx, kind, w, h, frame, app.mauri.sukrara.graph.WidgetGraphConfig(),
                                { r, b -> GlucoseMetrics.of(sc.readings, now - r.windowS, now, b) }).recycle()
                            val l = synchronized(captured) { captured.last() }
                            val els = JSONArray()
                            for (e in l.elements) {
                                els.put(JSONObject().put("n", e.name).put("i", e.info).put("b", box(e.box, l.d) ?: JSONObject.NULL))
                            }
                            out.put(JSONObject().put("surface", id).put("size", JSONArray(listOf(w, h))).put("scene", sc.id)
                                .put("settings", vid).put("card", JSONArray(listOf(Math.round(l.w / l.d * 2) / 2.0, Math.round(l.h / l.d * 2) / 2.0)))
                                .put("elements", els))
                        }
                    }
                }
            }
        } finally {
            GraphSettings.update { base }
            SensorLife.update(ctx, null)
            GlucoseCard.observer = null
        }
        File(System.getenv("DUMP_OUT")!!).writeText(out.toString(1))
    }
}
