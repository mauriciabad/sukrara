// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.pen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.pen.countedUnits
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * The read card at the bottom of the screen: the pen glyph in its reading ring, the title (a polite live region,
 * amounts spoken as words), its lines, and its buttons (*Undo*, the question's answers, *Turn on* / *No*, *Add
 * treatment*, *Close*).
 *
 * Feel: the card springs up from the bottom edge; while reading the wavy ring fills as the pen's records arrive (a
 * tick each, [PenHaptics]); a logged dose turns the glyph into a check that pops and its amount counts up in the pen
 * dial's 0.5u steps; a failure shakes the card once. Reduced motion: none of it (a still ring that only fills, the
 * final glyph and numbers at once). TalkBack hears only the final title.
 */
@Composable
fun PenReadCard(
    card: PenCard,
    reading: Boolean,
    canUndo: Boolean,
    onAction: (PenAction) -> Unit,
    onClose: () -> Unit,
    onTouch: () -> Unit,
    text: PenCardText,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val paneLabel = stringResource(R.string.pen_card_label)
    val reduced = LocalReducedMotion.current
    val rise = remember { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(card.error) {
        if (card.error && !reduced) {
            shake.snapTo(0f)
            shake.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 1200f), initialVelocity = -900f)
        }
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (card.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .graphicsLayer {
                    translationY = rise.value * size.height * 1.2f
                    translationX = shake.value * density
                }.semantics { paneTitle = paneLabel }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            onTouch()
                        }
                    }
                },
    ) {
        AnimatedContent(
            card,
            transitionSpec = { Motion.fadeThrough(reduced) },
            contentKey = { it.title to it.lines },
            label = "pen card",
        ) { c ->
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PenGlyph(c.glyph, reading, progress)
                    Box(Modifier.width(16.dp))
                    // a logged dose counts up from 0 (for the eye; TalkBack hears the final title)
                    val shown = c.count?.let { countedUnits(it, from = 0.0) }
                    Text(
                        if (shown != null && shown != c.count) text.logged(shown) else c.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (c.error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        modifier =
                            Modifier.weight(1f).semantics {
                                heading()
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = text.spoken(c.title)
                            },
                    )
                }
                c.lines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (c.error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = text.spoken(line) },
                    )
                }
                if (!reading) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(onClick = onClose) { Text(stringResource(R.string.pen_card_close)) }
                        c.actions.filter { it != PenAction.Undo || canUndo }.forEachIndexed { i, a ->
                            val label = label(a)
                            if (i == 0 && a !is PenAction.Undo && a != PenAction.No) {
                                Button(onClick = { onAction(a) }) { Text(label) }
                            } else {
                                TextButton(onClick = { onAction(a) }) { Text(label) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The pen glyph in its 40 dp ring: while [reading] the wavy ring, indeterminate until the pen says how many records it
 * holds, then filling with [progress]; afterwards a check (scale pop) or the error mark. Reduced motion: a still ring
 * that only fills, and the glyph at once.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PenGlyph(
    glyph: PenCard.Glyph,
    reading: Boolean,
    progress: Float?,
) {
    val reduced = LocalReducedMotion.current
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        if (reading) {
            when {
                reduced -> CircularProgressIndicator(progress = { progress ?: 0f }, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                progress == null -> CircularWavyProgressIndicator(Modifier.size(40.dp))
                else -> CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(40.dp))
            }
        }
        Crossfade(glyph to reading, animationSpec = if (reduced) snap() else tween(200), label = "pen glyph") { (g, r) ->
            val pop = remember { Animatable(if (g == PenCard.Glyph.Check && !r && !reduced) 0.5f else 1f) }
            LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 700f)) }
            val icon =
                when {
                    r -> DsR.drawable.ds_ic_dia_pen
                    g == PenCard.Glyph.Check -> DsR.drawable.ds_ic_check_circle
                    g == PenCard.Glyph.Error -> DsR.drawable.ds_ic_error
                    else -> DsR.drawable.ds_ic_dia_pen
                }
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint =
                    when {
                        r -> cs.onSurfaceVariant
                        g == PenCard.Glyph.Check -> cs.primary
                        g == PenCard.Glyph.Error -> cs.error
                        else -> cs.onSurfaceVariant
                    },
                modifier =
                    Modifier.size(if (r) 20.dp else 28.dp).graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
            )
        }
    }
}

@Composable
private fun label(a: PenAction): String =
    when (a) {
        PenAction.Undo -> stringResource(R.string.pen_undo)
        PenAction.AddTreatment -> stringResource(R.string.pen_add_treatment)
        PenAction.TurnOn -> stringResource(R.string.pen_turn_on)
        PenAction.No -> stringResource(R.string.pen_no)
        is PenAction.Suggest -> a.label
        is PenAction.AllPriming -> stringResource(R.string.pen_all_priming)
        is PenAction.Same -> stringResource(if (a.entry != null) R.string.pen_m_same else R.string.pen_m_different)
        is PenAction.OpenPen -> stringResource(R.string.pen_check_it)
        is PenAction.Review -> stringResource(R.string.pen_review)
        is PenAction.Choose -> stringResource(R.string.pen_choose)
    }
