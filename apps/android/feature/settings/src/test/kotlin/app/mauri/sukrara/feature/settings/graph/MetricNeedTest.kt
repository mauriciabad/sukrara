// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.graph

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.graph.GraphConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * A metric hidden on the card for want of readings says why in Settings › Metrics (docs/44 Appendix G §4, Q3): the
 * renderer's 25 % rule over the metric's window, in the person's durations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MetricNeedTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-18T20:00:00Z")
    private val week = 7 * 86_400L

    @Test
    fun `12 h of a week is under the card's 25 %, 42 h needed`() {
        // LibreLinkUp's 12 h backfill, a reading every 5 min
        val readings = (0 until 144).map { Reading(now.minusSeconds(it * 300L), 120, null, SourceId.LibreLinkUp) }
        val coverage = MetricNeed.coverage(readings, now, week)
        val need = MetricNeed.of(coverage, week)!!
        assertEquals(12.0, need.haveS / 3600.0, 0.5)
        assertEquals(42 * 3600L, need.needS)
        assertNull(MetricNeed.of(0.25, week))
        assertNull(MetricNeed.of(null, week))
    }

    @Test
    fun `the metric's row says it`() {
        val c = GraphConfig()
        val main = c.slots.first()
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                MetricsContent(c, GraphActions(), {}, coverage = mapOf(main.windowS to 0.1))
            }
        }
        compose.onNodeWithText("Needs more readings", substring = true, useUnmergedTree = true).assertExists()
    }
}
