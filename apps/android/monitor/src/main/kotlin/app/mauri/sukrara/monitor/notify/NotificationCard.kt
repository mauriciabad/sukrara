// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.graph.A11y
import app.mauri.sukrara.graph.CardModel
import app.mauri.sukrara.graph.CardPart
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.GlucoseLook
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPalette
import app.mauri.sukrara.graph.RoundedFont
import app.mauri.sukrara.graph.notificationCardStyle
import app.mauri.sukrara.graph.stats.MetricsCache
import app.mauri.sukrara.graph.stats.toPoint
import java.time.Instant
import kotlin.math.min

/**
 * What the custom card draws besides the content: the hub state, the treatments, the graph settings, the theme, and the
 * stored days before the live window ([stored], for the slots' 7–30-day windows).
 */
data class CardFrame(
    val state: GlucoseState,
    val treatments: List<Treatment>,
    val config: GraphConfig,
    val dark: Boolean,
    val stored: List<Reading> = emptyList(),
) {
    /** The stored days as points, mapped once per frame (they change hourly; the live window is joined per call). */
    private val storedPoints by lazy { stored.map { it.toPoint() } }

    /** The slots' numbers over the stored days and the live window, as home and the widgets count them. */
    fun slots(now: Instant): MetricsCache {
        val start = state.recent.firstOrNull()?.t
        val older = if (start == null) storedPoints else storedPoints.filter { it.t < start.epochSecond }
        return MetricsCache(older + state.recent.map { it.toPoint() }, now.epochSecond)
    }
}

/** The custom card's pictures: the header row expanded and collapsed, and the chart on its rounded surface. */
class NotificationArt(
    val header: Bitmap,
    val collapsed: Bitmap,
    val graph: Bitmap?,
    /** What TalkBack says for the card: the renderer's one sentence ([A11y.card]). */
    val description: String,
)

/**
 * The glucose notification's custom card (surface `notification`), shown when the notification isn't the Live Update
 * chip: the card's value ([CardPart.VALUE]: value, trend or "no trend" ring, the late reading's time, the status pill)
 * and its slots ([CardPart.SLOTS], two rings where the column allows, as on the widget) side by side in one header
 * picture, as the renderer draws them on every card, and the chart ([CardPart.GRAPH]) below on a rounded surface of the
 * theme. The value is drawn a little shorter than the slots (the owner, 2026-11-14: the value was too large, the
 * collapsed ring too small). Android 12+ clips custom content right of the icon column, so the pictures fill the
 * content column. Drawn at most at 2x density so the three pictures stay far below the binder's parcel limit.
 *
 * Its ground: by default the system's notification colour (the value in its range colour, the theme's palette);
 * with *Card in the range colour* the range colour itself ([background]: the value in black or white ink, the slots
 * on discs), the renderer's pill rule keeping every pill legible on it ([notificationCardStyle] `ground`).
 */
object NotificationCard {
    const val HEADER_DP = 64f

    /** Android 12+ gives a collapsed custom view at most 48 dp. */
    const val COLLAPSED_DP = 48f
    const val GRAPH_DP = 96f

    /** The value's box in the expanded header (its slots take the header's whole height). */
    const val VALUE_DP = 48f

    /** The value's box in the collapsed header. */
    const val COLLAPSED_VALUE_DP = 40f

    /** The slots' room: at most this share of the column, else two slots whole. */
    private const val SLOTS_SHARE = 0.5f
    private const val SLOT_GAP_DP = 6f
    private const val MAX_DENSITY = 2f

    /** A struck-through value (5-20 min old) colours the card grey instead of its range. */
    const val STALE_BG = 0xFF8E9099.toInt()

    /** The card's colour for [look] of [mgdl]: its range's (the light palette: it doesn't follow the theme), grey when struck. */
    fun background(
        look: GlucoseLook,
        mgdl: Int,
        config: GraphConfig,
    ): Int = if (look.strike) STALE_BG else GraphPalette.LIGHT.at(config.bands, mgdl)

    /** Black or white, whichever reads better on [bg] (the system does the same for the header). */
    fun ink(bg: Int): Int {
        val opaque = bg or 0xFF000000.toInt()
        return if (ColorUtils.calculateContrast(Color.BLACK, opaque) >=
            ColorUtils.calculateContrast(Color.WHITE, opaque)
        ) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    /** The content column's width (dp) on this screen: the shade's 16 dp sides, the 72 dp icon column, a 16 dp end margin. */
    fun columnDp(context: Context): Float {
        val dm = context.resources.displayMetrics
        return min(dm.widthPixels, dm.heightPixels) / dm.density - 32f - 72f - 16f
    }

    /** The theme's notification surface (the ground of a card that isn't colourised, and of the chart). */
    fun surface(
        context: Context,
        dark: Boolean,
    ): Int =
        context.getColor(
            if (dark) android.R.color.system_surface_container_high_dark else android.R.color.system_surface_container_high_light,
        )

    /**
     * The card for [model] at [wDp] wide; [bg] = the range colour it is painted in (*Card in the range colour*), null =
     * the system's notification colour; [graph] false skips the chart (reused from before); [rangeSaid] = the alert's
     * title above it names the value's range, so the card draws no range word (contracts/graph: pills per surface).
     * One whole-card style for all parts ([notificationCardStyle]), so the whole-card decisions are made once.
     */
    fun renderNotificationArt(
        context: Context,
        model: CardModel,
        config: GraphConfig,
        bg: Int?,
        dark: Boolean,
        wDp: Float,
        graph: Boolean = true,
        rangeSaid: Boolean = false,
    ): NotificationArt {
        RoundedFont.init(context)
        val d = min(context.resources.displayMetrics.density, MAX_DENSITY)
        // the chart's words follow the system font size (the renderer caps it)
        val fontScale = context.resources.configuration.fontScale
        val surface = surface(context, dark)
        // on the range colour the header is drawn in the light palette with black / white ink; on the system's colour in the theme's
        val headerPalette = if (bg != null) GraphPalette.LIGHT else GraphPalette.of(dark)
        val disc = context.getColor(android.R.color.system_surface_container_high_light)
        val style =
            notificationCardStyle(
                config,
                valueInk = bg?.let(::ink),
                slotDisc = disc.takeIf { bg != null },
                ground = bg ?: surface,
                rangeSaid = rangeSaid,
            )

        // the slots right-aligned at the header's full height (two whole where the column allows), the value in the rest
        fun header(
            hDp: Float,
            valueDp: Float,
        ) = createBitmap((wDp * d).toInt(), (hDp * d).toInt()).also { bmp ->
            val cv = Canvas(bmp)
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()
            val slotsW = min(w * SLOTS_SHARE, (2 * hDp + SLOT_GAP_DP) * d)
            cv.save()
            cv.translate(w - slotsW, 0f)
            val slots =
                GlucoseCard.draw(
                    cv,
                    slotsW,
                    h,
                    model,
                    style,
                    headerPalette,
                    d,
                    CardSurface.NOTIFICATION,
                    CardPart.SLOTS,
                    fontScale = fontScale,
                )
            cv.restore()
            val used = slots.first("slots")?.box?.let { slotsW - it.left } ?: 0f
            val valueW = w - if (used > 0f) used + SLOT_GAP_DP * d else 0f
            val valueH = valueDp * d
            cv.save()
            cv.translate(0f, (h - valueH) / 2)
            GlucoseCard.draw(
                cv,
                valueW,
                valueH,
                model,
                style,
                headerPalette,
                d,
                CardSurface.NOTIFICATION,
                CardPart.VALUE,
                fontScale = fontScale,
            )
            cv.restore()
        }

        val chart =
            if (!graph || style.graph == null) {
                null
            } else {
                val w = (wDp * d).toInt()
                val h = (GRAPH_DP * d).toInt()
                val pad = 6 * d
                val plot = createBitmap((w - 2 * pad).toInt(), (h - 2 * pad).toInt())
                GlucoseCard.draw(
                    Canvas(plot),
                    plot.width.toFloat(),
                    plot.height.toFloat(),
                    model,
                    style,
                    GraphPalette.of(dark),
                    d,
                    CardSurface.NOTIFICATION,
                    CardPart.GRAPH,
                    fontScale = fontScale,
                )
                createBitmap(w, h).also { out ->
                    Canvas(out).apply {
                        drawRoundRect(
                            RectF(0f, 0f, w.toFloat(), h.toFloat()),
                            12 * d,
                            12 * d,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface },
                        )
                        drawBitmap(plot, pad, pad, null)
                    }
                    plot.recycle()
                }
            }
        return NotificationArt(
            header(HEADER_DP, VALUE_DP),
            header(COLLAPSED_DP, COLLAPSED_VALUE_DP),
            chart,
            A11y.card(model, style, CardSurface.NOTIFICATION),
        )
    }
}
