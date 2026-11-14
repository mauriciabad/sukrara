// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.time

import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.DurationWords
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import android.icu.text.MeasureFormat as IcuMeasureFormat

/**
 * A length in full words for TalkBack ("15 minutes", "1 hour", "1 hour, 30 minutes"; Spanish "15 minutos", "1 hora"):
 * the platform's wide unit names, so a compact "15m" on screen is never read as metres.
 */
fun spokenLength(
    minutes: Int,
    locale: Locale,
): String {
    val m = minutes.coerceAtLeast(0)
    val format = IcuMeasureFormat.getInstance(locale, IcuMeasureFormat.FormatWidth.WIDE)
    val h = m / 60
    val rest = m % 60
    return when {
        h == 0 -> format.format(Measure(rest, MeasureUnit.MINUTE))
        rest == 0 -> format.format(Measure(h, MeasureUnit.HOUR))
        else -> format.formatMeasures(Measure(h, MeasureUnit.HOUR), Measure(rest, MeasureUnit.MINUTE))
    }
}

/** [spokenLength] in the app's current language. */
@Composable
fun spokenLength(minutes: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(minutes, locale) { spokenLength(minutes, locale) }
}

/**
 * THE row of lengths side by side (#181: "(5m) (15m) (30m) (1h)", so more fit on one line): one M3 filter chip per
 * length in the card's compact words ([Durations.compact]: "5m", "1h", "1h 30m"; Spanish "5 min", "1 h": "m" is the
 * metre there), TalkBack hearing the full words ([spokenLength]). [selected] marks the chosen length (null = these
 * are actions, none chosen); the caller gives the haptic its pick deserves. Wraps to a second line when they don't fit (large fonts).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LengthChips(
    minutes: List<Int>,
    words: DurationWords,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selected: Int? = null,
) {
    FlowRow(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (m in minutes) {
            val spoken = spokenLength(m)
            FilterChip(
                selected = m == selected,
                onClick = { onPick(m) },
                label = { Text(Durations.compact(m.toLong(), words), maxLines = 1) },
                modifier = Modifier.semantics { contentDescription = spoken }.testTag("length-$m"),
            )
        }
    }
}

/**
 * THE length wheel (#181: the owner's "spinner like in iOS"): one scrolling column of lengths, minutes and hours in
 * the same scroll ("45 min", "1 h 5 min"), the centred one chosen inside a tonal band; it snaps to a length after a
 * fling, ticks under the finger on every length it passes, and a tap on a row scrolls it to the centre. [value] set
 * from outside (a [LengthChips] tap) scrolls the wheel there (a jump with reduced motion); [onValue] hears the length
 * the wheel settles on. TalkBack meets one adjustable control: [label], the length in full words, swipe up / down
 * (or volume keys) for the next / previous length.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LengthWheel(
    values: List<Int>,
    value: Int,
    onValue: (Int) -> Unit,
    label: String,
    words: DurationWords,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState(nearestIndex(values, value))
    val itemPx = with(LocalDensity.current) { WHEEL_ITEM.toPx() }
    val latestValue by rememberUpdatedState(value)
    val report by rememberUpdatedState(onValue)
    val centred by remember(values) { derivedStateOf { centredIndex(state, itemPx, values.size) } }

    // a length set from outside (a chip): the wheel scrolls there
    LaunchedEffect(value, values) {
        val target = nearestIndex(values, value)
        if (target != centredIndex(state, itemPx, values.size) || state.firstVisibleItemScrollOffset != 0) {
            if (reduced) state.scrollToItem(target) else state.animateScrollToItem(target)
        }
    }
    // a tick per length passed; the value once the wheel rests
    LaunchedEffect(state, values) {
        launch { snapshotFlow { centred }.drop(1).collect { haptics.scrollTick() } }
        snapshotFlow { state.isScrollInProgress to centred }
            .filter { !it.first }
            .distinctUntilChanged()
            .collect { (_, i) -> values.getOrNull(i)?.let { if (it != latestValue) report(it) } }
    }

    val shown = values.getOrElse(centred) { value }
    val spoken = spokenLength(shown)
    Box(
        modifier
            .fillMaxWidth()
            .height(WHEEL_ITEM * WHEEL_ROWS)
            .clearAndSetSemantics {
                testTag = "length-wheel"
                contentDescription = label
                stateDescription = spoken
                progressBarRangeInfo =
                    ProgressBarRangeInfo(
                        centred.toFloat(),
                        0f..(values.size - 1).coerceAtLeast(1).toFloat(),
                        (values.size - 2).coerceAtLeast(0),
                    )
                setProgress { target ->
                    val i = target.roundToInt().coerceIn(0, values.size - 1)
                    scope.launch { state.scrollToItem(i) }
                    report(values[i])
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // the band behind the chosen length
        Box(
            Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM)
                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.large),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = WHEEL_ITEM * (WHEEL_ROWS / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(values) { i, m ->
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM)
                        .graphicsLayer {
                            // a drum: rows fade and shrink away from the centre
                            val d = (abs(rowOffset(state, i, itemPx)) / itemPx).coerceAtMost(WHEEL_ROWS / 2f + 0.5f)
                            val scale = 1f - 0.12f * d
                            scaleX = scale
                            scaleY = scale
                            alpha = (1f - 0.3f * d).coerceAtLeast(0.15f)
                        }.clickable(interaction, indication = null) {
                            scope.launch { if (reduced) state.scrollToItem(i) else state.animateScrollToItem(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        Durations.text(m.toLong(), words),
                        style = MaterialTheme.typography.headlineSmall,
                        color =
                            if (i == centred) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The index of the value closest to [value] (a length that isn't on the wheel lands on its neighbour). */
fun nearestIndex(
    values: List<Int>,
    value: Int,
): Int = values.indices.minByOrNull { abs(values[it] - value) } ?: 0

/** The row in the band: the first visible one, or the next once it is more than half scrolled away. */
private fun centredIndex(
    state: LazyListState,
    itemPx: Float,
    size: Int,
): Int {
    val i = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
    return i.coerceIn(0, (size - 1).coerceAtLeast(0))
}

/** How far row [i] sits from the band, in pixels (0 = in it). */
private fun rowOffset(
    state: LazyListState,
    i: Int,
    itemPx: Float,
): Float = (i - state.firstVisibleItemIndex) * itemPx - state.firstVisibleItemScrollOffset

private val WHEEL_ITEM = 48.dp

/** Rows in sight: the chosen one and two each side. */
private const val WHEEL_ROWS = 5
