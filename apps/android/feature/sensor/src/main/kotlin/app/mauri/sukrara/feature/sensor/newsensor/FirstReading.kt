// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sensor.newsensor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.graph.A11y
import app.mauri.sukrara.graph.CardModel
import app.mauri.sukrara.graph.CardPart
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPalette
import app.mauri.sukrara.graph.sensorReadyCardStyle
import app.mauri.sukrara.graph.stats.toPoint
import java.time.Instant

/*
 * The *Sensor ready* page's first reading: the registered `sensor-ready` card surface
 * (contracts/graph/surfaces/surfaces.json). It only chooses its size and style; the renderer (`GlucoseCard`) lays out
 * and draws the value and the trend arrow (LO / HI, the range colour, the freshness steps), as on every other card.
 */

/** The first [reading] at [now] in the person's [config] (unit, ranges); TalkBack reads the renderer's sentence. */
@Composable
internal fun FirstReadingCard(
    reading: Reading,
    now: Instant,
    config: GraphConfig,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val words = remember(resources) { CardWords.of(resources) }
    val palette = GraphPalette.of(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val nowS = now.epochSecond
    val model =
        remember(reading, nowS, words) {
            CardModel(readings = listOf(reading.toPoint()), treatments = emptyList(), nowS = nowS, words = words)
        }
    val style = remember(config) { sensorReadyCardStyle(config) }
    val spoken = remember(model, style) { A11y.reading(model, style, CardSurface.SENSOR_READY).orEmpty() }
    Canvas(
        modifier
            .size(WIDTH, HEIGHT)
            .testTag("first-reading")
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        drawIntoCanvas { canvas ->
            GlucoseCard.draw(
                canvas.nativeCanvas,
                size.width,
                size.height,
                model,
                style,
                palette,
                density,
                CardSurface.SENSOR_READY,
                CardPart.VALUE,
            )
        }
    }
}

/** The surface's one size (surfaces.json `sensor-ready`). */
private val WIDTH = 220.dp
private val HEIGHT = 72.dp
