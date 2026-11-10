// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import app.mauri.sukrara.core.graph.R
import app.mauri.sukrara.graph.stats.Band
import app.mauri.sukrara.graph.stats.Bands

/**
 * A value's range in words, so no place says it by colour alone (#26, WCAG 1.4.1: low red and very high orange look
 * alike to protan / deutan eyes). [key] = the contracts/graph/range fixtures' name; [words] = what the card draws.
 * Extremely high from its own bound up, like the value's colour. RangeState.swift on the Mac is the same rule.
 */
enum class RangeState(
    val key: String,
    @param:StringRes val words: Int,
) {
    VERY_LOW("very_low", R.string.card_range_very_low),
    LOW("low", R.string.card_range_low),
    HIGH("high", R.string.card_range_high),
    VERY_HIGH("very_high", R.string.card_range_very_high),
    EXTREMELY_HIGH("extremely_high", R.string.card_range_extremely_high),
    ;

    companion object {
        /** The range of [sgv] in the person's [bands]; null in range (very good included). */
        fun of(
            sgv: Int,
            bands: Bands,
        ): RangeState? =
            if (sgv >= bands.extremelyHigh) {
                EXTREMELY_HIGH
            } else {
                when (bands.of(sgv)) {
                    Band.VERY_LOW -> VERY_LOW
                    Band.LOW -> LOW
                    Band.TIGHT, Band.TARGET -> null
                    Band.HIGH -> HIGH
                    Band.VERY_HIGH -> VERY_HIGH
                }
            }

        /**
         * The range word the value's place draws for [face] (the reading [sgv]) with the status pill [pill]: an
         * out-of-range value says its range unless it is struck (grey: no range colour to back up), is LO / HI (the
         * text already says it) or the pill already says the side in words ("Eat carbs" / "Take insulin"). A tonal pill
         * ("45m left", "Muted") says what was done, not the range: the word shows beside it. contracts/graph/range.
         */
        fun shown(
            face: GlucoseLook,
            sgv: Int,
            bands: Bands,
            pill: StatusBadge.Look?,
        ): RangeState? {
            if (face.state != GlucoseLook.State.VALUE || face.strike || sgv < 40 || sgv > 400) return null
            if (pill != null && !pill.tonal && (pill.kind == "carbs" || pill.kind == "rapid")) return null
            return of(sgv, bands.sane())
        }
    }
}

/**
 * The range word under (or beside) an out-of-range value: the word in the card's primary ink on a pill tinted with
 * the value's colour, so it reads at 4.5:1 on any band's tint (PillContrastTest) and belongs to the number. On a
 * surface painted in the range's own colour (the notification, [CardStyle.valueInk]) it is outlined in that ink instead.
 */
internal object RangeTag {
    private fun paint(
        h: Float,
        color: Int,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = h * 0.56f
        typeface = Typeface.create(Typeface.DEFAULT, 600, false)
    }

    fun width(
        text: String,
        h: Float,
    ) = paint(h, 0).measureText(text) + h * 0.9f

    /** The tint behind the word: [tint], faint (stronger on dark). */
    fun background(
        tint: Int,
        dark: Boolean,
    ) = ColorUtils.setAlphaComponent(tint, if (dark) 72 else 44)

    /** The word's ink: the card's primary ink, or [onSurface] on a surface that paints the value its own colour. */
    fun ink(
        pal: GraphPalette,
        onSurface: Int?,
    ) = onSurface ?: pal.ink

    /** Draws the tag [h] tall at ([x], [y]); returns its box. */
    fun draw(
        cv: Canvas,
        x: Float,
        y: Float,
        h: Float,
        text: String,
        tint: Int,
        pal: GraphPalette,
        onSurface: Int?,
    ): RectF {
        val box = RectF(x, y, x + width(text, h), y + h)
        val shape =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (onSurface == null) {
                    color = background(tint, pal.dark)
                } else {
                    // on a surface in the range's own colour (the notification): outlined, the word keeps that
                    // surface's contrast with the value's ink
                    color = onSurface
                    style = Paint.Style.STROKE
                    strokeWidth = h * 0.08f
                }
            }
        val inset = if (onSurface == null) 0f else h * 0.04f
        cv.drawRoundRect(RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), h / 2, h / 2, shape)
        val p = paint(h, ink(pal, onSurface))
        val fm = p.fontMetrics
        cv.drawText(text, x + h * 0.45f, y + h / 2 - (fm.ascent + fm.descent) / 2, p)
        return box
    }
}
