// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * What one placement measured ([WidgetProbe]): the launcher reported [reportedW] × [reportedH] dp for it and lays it
 * out at [w] × [h] dp.
 */
@Serializable
data class MeasuredSize(
    val reportedW: Float,
    val reportedH: Float,
    val w: Float,
    val h: Float,
) {
    /** Glance composes for [reported]: this measurement is for it when within a few dp (near-duplicate sizes). */
    fun matches(
        reportedW: Float,
        reportedH: Float,
    ) = abs(this.reportedW - reportedW) <= SAME_DP && abs(this.reportedH - reportedH) <= SAME_DP

    companion object {
        /**
         * Launchers report near-duplicate sizes per orientation (Pixel Launcher: 296×201.9 and 296×201.1), Glance
         * composes one layout for each and the launcher may apply either: one measurement serves both (PoC v0.10.8).
         */
        const val SAME_DP = 4f
    }
}

/**
 * Measures the size a launcher really gives a widget (docs/10-showcase.md, "the launcher's reported size is wrong";
 * ported from the PoC, #181).
 *
 * Glance's Exact mode composes for the sizes the launcher *reports* (OPTION_APPWIDGET_SIZES), and launchers can be
 * wrong: Pixel Launcher on the owner's Pixel 10 Pro reports 296×202 dp for a 4×2 widget it lays out at 244×149 dp,
 * so a card drawn for the reported size came out squashed. The app can't read the real size, but the launcher's
 * AppWidgetHostView knows it: from a size → RemoteViews map it applies the layout that fits its real laid-out size best
 * (RemoteViews.findBestFitLayout, re-picked in onLayout).
 *
 * So a probe sends up to 16 tiny layouts, each an image whose content:// URI names its size, and [ProbeProvider] sees
 * which one the launcher loads. Round 1 brackets the size (100…55 % of the reported one), round 2 narrows it to ≈ 4 %.
 * It runs when a widget is placed or resized, or has no measurement yet, only while the screen is on, about a second
 * (the widget is blank meanwhile). [store] keeps the result per placement and reported size.
 */
class WidgetProbe(
    private val context: Context,
    private val store: WidgetSettingsStore,
) {
    private val mutex = Mutex()

    /** appWidgetId → when a probe last went out: a launcher that doesn't answer (not on screen) is asked every 2 min. */
    private val tried = ConcurrentHashMap<Int, Long>()

    /** A placement was resized or placed again: measure it on the next redraw. */
    fun allow(appWidgetId: Int) {
        tried.remove(appWidgetId)
    }

    /** The home-screen widgets placed now whose current orientation has no measurement yet. */
    fun unmeasured(placements: WidgetPlacements): List<Int> {
        val mgr = AppWidgetManager.getInstance(context)
        return placed(mgr).filter { id ->
            val r = reported(mgr, id) ?: return@filter false
            placements.measured[id].orEmpty().none { it.matches(r.width, r.height) }
        }
    }

    /** Measures every unmeasured placement that is due (call with the screen on). */
    suspend fun measureDue(placements: WidgetPlacements) {
        val now = System.currentTimeMillis()
        for (id in unmeasured(placements)) {
            if ((tried[id] ?: 0) > now - RETRY_MS) continue
            tried[id] = now
            measure(id)
        }
    }

    /** Measures placement [id] (two probe rounds); false when the launcher didn't answer (e.g. not on screen). */
    suspend fun measure(id: Int): Boolean =
        mutex.withLock {
            val mgr = AppWidgetManager.getInstance(context)
            val reported = reported(mgr, id) ?: return false
            val first =
                round(mgr, id, COARSE.map { reported.width * it }, COARSE.map { reported.height * it })
                    ?: return false.also { Log.w(TAG, "widget $id: the launcher didn't answer the probe") }

            fun fine(
                chosen: Float,
                full: Float,
            ) = if (chosen >= full) listOf(full) else (0..3).map { chosen + full * COARSE_STEP * it / 4 }
            val second = round(mgr, id, fine(first.width, reported.width), fine(first.height, reported.height)) ?: first
            Log.i(TAG, "widget $id: reported ${reported.width}×${reported.height} dp, laid out at ≈ ${second.width}×${second.height}")
            store.measured(id, MeasuredSize(reported.width, reported.height, second.width, second.height))
            true
        }

    private suspend fun round(
        mgr: AppWidgetManager,
        id: Int,
        widths: List<Float>,
        heights: List<Float>,
    ): SizeF? {
        val nonce = UUID.randomUUID().toString()
        val answer = CompletableDeferred<SizeF>()
        pending[nonce] = answer
        try {
            val views =
                widths.distinct().flatMap { w -> heights.distinct().map { h -> SizeF(w, h) } }.associateWith { s ->
                    RemoteViews(context.packageName, R.layout.widget_probe).apply {
                        setImageViewUri(R.id.probe, Uri.parse("content://${context.packageName}.widgetprobe/$nonce/${s.width}/${s.height}"))
                    }
                }
            mgr.updateAppWidget(id, RemoteViews(views))
            return withTimeoutOrNull(ANSWER_MS) { answer.await() }
        } finally {
            pending.remove(nonce)
        }
    }

    private fun placed(mgr: AppWidgetManager): List<Int> =
        WidgetKind.entries.flatMap { kind ->
            runCatching { mgr.getAppWidgetIds(ComponentName(context, kind.receiver)).toList() }.getOrDefault(emptyList())
        }

    /** The size the launcher reports for the current orientation (its portrait or its landscape entry). */
    private fun reported(
        mgr: AppWidgetManager,
        id: Int,
    ): SizeF? {
        val sizes =
            mgr
                .getAppWidgetOptions(id)
                .getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                ?.takeIf { it.isNotEmpty() } ?: return null
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        return if (portrait) sizes.minBy { it.width / it.height } else sizes.maxBy { it.width / it.height }
    }

    companion object {
        private const val TAG = "Sukrara.widgets"
        private const val COARSE_STEP = 0.15f
        private val COARSE = listOf(1f, 1f - COARSE_STEP, 1f - 2 * COARSE_STEP, 1f - 3 * COARSE_STEP)
        private const val ANSWER_MS = 3_000L
        private const val RETRY_MS = 2 * 60_000L

        private val pending = ConcurrentHashMap<String, CompletableDeferred<SizeF>>()

        /** [ProbeProvider]: the launcher loaded probe [nonce]'s image for [w] × [h] dp. */
        internal fun answered(
            nonce: String,
            w: Float,
            h: Float,
        ) {
            pending[nonce]?.complete(SizeF(w, h))
        }
    }
}

/**
 * Serves the probe images (a 1×1 transparent PNG) and tells [WidgetProbe] which one the launcher loaded. Exported so
 * the launcher can read it, as in the PoC: it answers only URIs of a probe in progress (a random nonce), returns no
 * data and accepts no writes.
 */
class ProbeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? {
        val seg = uri.pathSegments
        if (seg.size == 3) {
            val w = seg[1].toFloatOrNull()
            val h = seg[2].toFloatOrNull()
            if (w != null && h != null) WidgetProbe.answered(seg[0], w, h)
        }
        val ctx = context ?: return null
        val png = File(ctx.cacheDir, "probe.png")
        if (!png.exists()) {
            png.outputStream().use { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return ParcelFileDescriptor.open(png, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}

/** The size to lay out and draw [reported] (Glance's [androidx.glance.LocalSize]) for: measured when known. */
fun WidgetPlacements.realSize(
    appWidgetId: Int,
    reportedW: Float,
    reportedH: Float,
): Pair<Float, Float> =
    measured[appWidgetId]
        ?.firstOrNull { it.matches(reportedW, reportedH) }
        ?.let { it.w to it.h }
        ?: (reportedW to reportedH)
