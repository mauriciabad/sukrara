// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import android.graphics.Bitmap
import android.graphics.Canvas
import app.mauri.sukrara.graph.stats.GlucosePoint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.management.ManagementFactory
import java.util.Locale
import kotlin.math.sin

/**
 * The cost of one frame of the scrolled app panel (its style and a [ChartView], as PanelCard draws every frame of a
 * drag or fling) over the wearer's whole store: 31 days of 1-minute readings (44 640), plus the y range it eases towards
 * ([GlucoseCard.yRange]); then the widest zoom (the whole 31 days). Prints the median / p95 time and the bytes allocated
 * per frame (JVM, Robolectric's native canvas: a proxy for the phone, not its numbers). A frame over the whole store
 * must cost about what the same frames cost over only the hours they show (a change that makes scrolling scan or sort
 * the whole history again multiplies it), measured side by side so a slower machine changes both; the widest zoom
 * only fails far above a frame's budget.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h900dp-xxhdpi")
class ChartScrollPerfTest {
    @Test fun scrolledFrameStaysCheap() {
        RoundedFont.init(RuntimeEnvironment.getApplication())
        val now = 1_800_000_000L
        val days = 31
        val readings =
            (0 until days * 1440).map { i ->
                GlucosePoint(now - (days * 1440 - i) * 60L, (130 + 70 * sin(i / 180.0) + 25 * sin(i / 37.0)).toInt(), 0.0)
            }
        val d = 2.625f
        val w = 379 * d
        val h = PANEL_HEIGHT * d
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val st = GraphConfig().appPanelStyle()
        val graph = st.graph!!
        val model = CardModel(readings, emptyList(), now, Scenes.words())
        // the same frames' readings alone: the 12 h windows they show (with the minute before)
        val shown = readings.filter { it.t >= now - 900 * 97L - graph.windowS - 60 }
        val shownModel = CardModel(shown, emptyList(), now, Scenes.words())
        val pal = GraphPalette.of(false)
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val tid = Thread.currentThread().id

        fun frame(
            end: Long,
            g: GraphStyle = graph,
            m: CardModel = model,
        ) {
            val y = GlucoseCard.yRange(m.readings, end, g)
            val spanS = if (g.windowS == graph.windowS) null else g.windowS
            GlucoseCard.draw(cv, w, h, m, st, pal, d, CardSurface.TEST, CardPart.GRAPH, view = ChartView(end, y, 1f, spanS = spanS))
        }

        fun median(times: LongArray) = times.sorted()[times.size / 2] / 1e6

        fun p95(times: LongArray) = times.sorted()[times.size * 95 / 100] / 1e6
        // ~ a fling's step at 60 fps over a 12 h window, over the whole store and over the shown hours alone, interleaved
        repeat(300) {
            frame(now - it * 97L)
            frame(now - it * 97L, m = shownModel)
        } // warm up (JIT)
        val times = LongArray(600)
        val alone = LongArray(600)
        var allocated = 0L
        for (k in times.indices) {
            val end = now - (300 + k) * 97L
            val a0 = bean.getThreadAllocatedBytes(tid)
            val t0 = System.nanoTime()
            frame(end)
            times[k] = System.nanoTime() - t0
            allocated += bean.getThreadAllocatedBytes(tid) - a0
            val t1 = System.nanoTime()
            frame(end, m = shownModel)
            alone[k] = System.nanoTime() - t1
        }
        val med = median(times)
        val aloneMed = median(alone)
        println(
            "scrolled frame over ${readings.size} readings: median %.2f ms, p95 %.2f ms, %d KB allocated per frame; over the %d shown: %.2f ms"
                .format(Locale.US, med, p95(times), allocated / times.size / 1024, shown.size, aloneMed),
        )
        // the widest zoom (the whole 31 days) over the same history: every reading in the window
        val wideStyle = graph.copy(windowS = ChartView.MAX_SPAN_S)
        repeat(50) { frame(now - it * 7L, wideStyle) }
        val wide = LongArray(300)
        for (k in wide.indices) {
            val t0 = System.nanoTime()
            frame(now - (50 + k) * 7L, wideStyle)
            wide[k] = System.nanoTime() - t0
        }
        val wideMed = median(wide)
        println("zoomed out (31 d) frame: median %.2f ms, p95 %.2f ms".format(Locale.US, wideMed, p95(wide)))
        bmp.recycle()
        assertTrue(
            "a scrolled frame over the whole store takes %.2f ms, %.1f× the %.2f ms of its shown hours alone (want < %.1f×)"
                .format(Locale.US, med, med / aloneMed, aloneMed, MAX_RATIO),
            med < aloneMed * MAX_RATIO,
        )
        assertTrue("a 31-day frame takes %.1f ms (median): far above a frame".format(Locale.US, wideMed), wideMed < WIDE_MS)
    }

    private companion object {
        /** The panel's chart height (dp) the frames are drawn at. */
        const val PANEL_HEIGHT = 210f

        /** The whole store against the shown hours alone: a scan or sort of the whole history costs far more. */
        const val MAX_RATIO = 1.5

        /** The widest zoom draws every reading: it fails only far above a frame (16 ms at 60 fps), on any machine. */
        const val WIDE_MS = 64.0
    }
}
