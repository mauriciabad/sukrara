// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The owner, 2026-11-15: in the Metrics rows' icons "the filled ball is slightly smaller than the others and they
 * should be same size". A ring's stroke is centred on its path, so the three places' outer edges are compared: the
 * same size in all three icons, apart from each other and inside the 24 dp grid.
 */
class MetricIconsTest {
    /** One place: its centre and outer radius (a ring's path radius plus half its stroke). */
    private data class Place(
        val centre: Double,
        val outer: Double,
    )

    private val circle = Regex("""M([\d.]+),12a([\d.]+),[\d.]+ 0,1 0""")
    private val stroke = Regex("""strokeWidth="([\d.]+)"""")

    private fun places(name: String): List<Place> {
        val xml = File("src/main/res/drawable/ds_ic_metric_$name.xml").readText()
        return xml.split("<path").drop(1).map { path ->
            val (start, r) = circle.find(path)!!.destructured
            val half =
                stroke
                    .find(path)
                    ?.groupValues
                    ?.get(1)
                    ?.toDouble()
                    ?.div(2) ?: 0.0
            Place(start.toDouble() + r.toDouble(), r.toDouble() + half)
        }
    }

    @Test
    fun theThreePlacesAreTheSameSizeInEveryIcon() {
        val all = listOf("main", "second", "third").map(::places)
        val outer = all.first().first().outer
        for (icon in all) {
            assertEquals(3, icon.size)
            for (p in icon) assertEquals("outer radius of $p", outer, p.outer, 0.001)
            // apart from each other and inside the grid
            for ((a, b) in icon.zipWithNext()) assertTrue("$a touches $b", b.centre - b.outer > a.centre + a.outer)
            assertTrue(icon.first().centre - outer >= 0.0 && icon.last().centre + outer <= 24.0)
        }
        // the same three places in each icon: only which one is filled changes
        val centres = all.map { icon -> icon.map { Math.round(it.centre * 100) } }
        assertEquals(centres[0], centres[1])
        assertEquals(centres[0], centres[2])
    }
}
