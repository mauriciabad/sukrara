// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import android.graphics.RectF
import app.mauri.sukrara.graph.stats.TirRange

/**
 * One home-screen widget's own settings (settings.json `widget.overrides`, Reconfigure); null = follow the Widgets
 * place's settings ([GraphConfig] with [GraphPlace.WIDGETS]).
 */
data class PlaceOverrides(
    val hours: Int? = null,
    val rings: Int? = null,
    val ringRange: TirRange? = null,
    val side: Boolean? = null,
    val sensorTime: Boolean? = null,
)

/**
 * The card style of a home-screen widget surface: the ONE mapping from the settings to what each widget kind shows,
 * used by the widgets and by CardLayoutTest. A value-only card still colours by the user's bands.
 */
fun widgetCardStyle(
    surface: CardSurface,
    config: GraphConfig,
    own: PlaceOverrides = PlaceOverrides(),
): CardStyle {
    val graph = config.graphStyle(own.hours ?: config.hours(GraphPlace.WIDGETS)).copy(side = own.side ?: config.side)
    val rings = config.rings(own.rings ?: config.ringCount, own.ringRange ?: config.ringRange)
    val sensorTime = own.sensorTime ?: config.sensorTime(GraphPlace.WIDGETS)
    return when (surface) {
        CardSurface.WIDGET_VALUE -> {
            CardStyle(
                graph = null,
                rings = rings,
                last = config.lastPlace,
                lastElse = config.lastElseRing(),
                sensorTime = sensorTime,
                bands = config.bands,
                unit = config.unit,
            )
        }

        CardSurface.WIDGET_GRAPH -> {
            CardStyle(graph = graph, rings = emptyList(), value = false, sensorTime = sensorTime, bands = config.bands, unit = config.unit)
        }

        CardSurface.WIDGET_COMBINED -> {
            CardStyle(
                graph = graph,
                rings = rings,
                last = config.lastPlace,
                lastElse = config.lastElseRing(),
                sensorTime = sensorTime,
                bands = config.bands,
                unit = config.unit,
            )
        }

        else -> {
            throw IllegalArgumentException("$surface is not a home-screen widget")
        }
    }
}

/**
 * The card style of the glucose notification's custom card (surface `notification`, drawn in two parts: the header
 * row and the chart) from the Notification place's settings: the ONE mapping, used by `:monitor` and CardLayoutTest.
 * On the card's range colour the value is in [valueInk] and the slots sit on discs of [slotDisc].
 */
fun notificationCardStyle(
    config: GraphConfig,
    valueInk: Int? = null,
    slotDisc: Int? = null,
): CardStyle =
    CardStyle(
        graph = config.graphStyle(config.hours(GraphPlace.NOTIFICATION)),
        rings = config.rings(),
        last = config.lastPlace,
        lastElse = config.lastElseRing(),
        sensorTime = config.sensorTime(GraphPlace.NOTIFICATION),
        slotDisc = slotDisc,
        valueInk = valueInk,
        bands = config.bands,
        unit = config.unit,
    )

/**
 * The card style of the *Sensor ready* page's first reading (surface `sensor-ready`, drawn as the value part only) from
 * the App place's settings: the ONE mapping, used by `:feature:sensor` and CardLayoutTest.
 */
fun sensorReadyCardStyle(config: GraphConfig): CardStyle =
    CardStyle(
        graph = null,
        rings = config.rings(),
        last = config.lastPlace,
        lastElse = config.lastElseRing(),
        sensorTime = config.sensorTime(GraphPlace.APP),
        bands = config.bands,
        unit = config.unit,
    )

/**
 * The card style of the lock-screen widget (surface `lock-widget`, #66) from the Widgets place's settings, as the home
 * widgets' *Glucose and graph*: the ONE mapping, used by `:widgets` and CardLayoutTest.
 */
fun lockWidgetCardStyle(config: GraphConfig): CardStyle = widgetCardStyle(CardSurface.WIDGET_COMBINED, config)

/**
 * Where the lock icon (the fingerprint sensor) is in the lock-screen widget, dp from the widget's top-left: [left] to
 * [right] wide, from the widget's top down to [bottom]. System UI's module measures it and passes it in the widget's
 * options (`LockWidgetLink`).
 */
data class LockHole(
    val left: Float,
    val right: Float,
    val bottom: Float,
) {
    companion object {
        /** Before the module's numbers arrive: a 76 dp icon in the middle, as on a Pixel (docs/17). */
        fun centred(widthDp: Float) = LockHole(widthDp / 2 - 44, widthDp / 2 + 44, 76f)
    }
}

/**
 * The lock-screen widget's one card in three boxes (px, in a [w] × [h] picture, [d] px per dp) around the lock icon
 * [hole]: the value and its arrow left of it (a little shorter than the icon's row, so the number isn't taller than the
 * icon), the slots right of it, the chart below (when there is room for one). [boxed] = a card or frosted glass is
 * drawn around the widget: the content keeps 12 dp off its edges. The ONE layout, used by `:widgets` and CardLayoutTest.
 */
fun lockWidgetBoxes(
    w: Float,
    h: Float,
    hole: LockHole,
    d: Float,
    boxed: Boolean,
): List<Pair<CardPart, RectF>> {
    val pad = if (boxed) 12 * d else 0f
    val right = w - pad
    val bottom = h - pad
    val rowH = hole.bottom * d
    val slotsH = rowH * 0.9f - pad
    val chart = RectF(pad, rowH + 8 * d, right, bottom)
    return listOfNotNull(
        CardPart.VALUE to RectF(pad, rowH * 0.1f, hole.left * d, rowH * 0.9f),
        CardPart.SLOTS to RectF(hole.right * d, (rowH - slotsH) / 2, right, (rowH + slotsH) / 2),
        (CardPart.GRAPH to chart).takeIf { chart.height() > 24 * d },
    )
}
