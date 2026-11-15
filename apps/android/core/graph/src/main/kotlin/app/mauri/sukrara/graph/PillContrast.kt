// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import androidx.core.graphics.ColorUtils

/**
 * THE contrast rule of every pill the card draws (the status pill, the range word), on whatever it sits on
 * ([CardStyle.ground]: the colourised notification's range colour; else the palette's own surface): a filled pill's
 * fill reaches [BOUNDARY] against the ground (WCAG 1.4.11), and its words and icon reach [TEXT] against what the fill
 * shows over the ground (WCAG 1.4.3). A tonal pill and the range word are faint tints whose words carry them: only
 * the words count. A pill that fails is drawn in the ground's ink (black or white, whichever stands out more) with
 * the other ink for its words: "Muted · 45m" on the red notification stays readable. Mac: `PillContrast.swift`.
 */
object PillContrast {
    /** WCAG 1.4.11: a filled pill against the ground. */
    const val BOUNDARY = 3.0

    /** WCAG 1.4.3: the words against the pill. */
    const val TEXT = 4.5

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** A pill's colours after the rule: [fill] (may be translucent), [fg] the words and icon; [swapped] = in ink. */
    data class Colors(
        val fill: Int,
        val fg: Int,
        val swapped: Boolean,
    )

    /** The contrast of two colours, both made opaque. */
    fun ratio(
        a: Int,
        b: Int,
    ): Double = ColorUtils.calculateContrast(a or BLACK, b or BLACK)

    /** Black or white, whichever reads better on [ground]. */
    fun ink(ground: Int): Int = if (ratio(BLACK, ground) >= ratio(WHITE, ground)) BLACK else WHITE

    /**
     * [fill] (its alpha over [ground]) with [fg] on it, kept when the pill passes ([boundary]: a filled pill, its fill
     * counts too), else the ground's ink and the other ink.
     */
    fun of(
        fill: Int,
        fg: Int,
        ground: Int,
        boundary: Boolean,
    ): Colors {
        val g = ground or BLACK
        val shown = ColorUtils.compositeColors(fill, g)
        if ((!boundary || ratio(shown, g) >= BOUNDARY) && ratio(fg, shown) >= TEXT) return Colors(fill, fg, false)
        val i = ink(g)
        return Colors(i, if (i == BLACK) WHITE else BLACK, true)
    }
}
