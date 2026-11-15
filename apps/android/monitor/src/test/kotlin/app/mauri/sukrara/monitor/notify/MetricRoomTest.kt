// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * docs/36 §14: the Live Update's value never loses its arrow to Android's "…". Widths in dp as measured in the Pixel
 * 10 Pro's Android 17.1 style (Google Sans Flex 500 at 28 sp, the arrows from Roboto / Noto Symbols); its expanded
 * metric row is 411.4 − 2 × 16 − 72 − 16 = 291.4 dp, a third 94.5 dp.
 */
class MetricRoomTest {
    private val row = 291.4f
    private val gap = 4f
    private val slack = 4f
    private val widths =
        mapOf(
            "138 ↗" to 85.0f,
            "138 →" to 93.7f,
            "138\u2009→" to 86.4f,
            "13,8 →" to 102.8f,
            "13,8\u2009→" to 95.5f,
        )

    private fun pick(vararg forms: String) = MetricRoom.pick(forms.toList(), { widths.getValue(it) }, row, gap, slack, 3)

    @Test fun `a value that fits keeps its space and all three metrics`() = assertEquals("138 ↗" to 3, pick("138 ↗"))

    @Test fun `the steady arrow, the widest, closes up to a thin space before any slot goes`() =
        assertEquals("138\u2009→" to 3, pick("138 →", "138\u2009→"))

    @Test fun `a decimal that still doesn't fit keeps its space and takes half the row`() =
        assertEquals("13,8 →" to 2, pick("13,8 →", "13,8\u2009→"))

    @Test fun `a value too wide for half the row is laid out alone`() =
        assertEquals(1, MetricRoom.metrics(valuePx = 150f, rowPx = row, gapPx = gap, slackPx = slack, max = 3))
}
