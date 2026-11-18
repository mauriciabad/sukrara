// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.source

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion

/** The illustration's height in a [PersonChoiceCard]. */
private val ArtHeight = 132.dp

/**
 * One of the two big choices of *Whose glucose will Sukrara show?* (docs/44 "Setup"): an illustration of [person]'s
 * situation, the answer and what it means, with a radio button (TalkBack: "radio button, selected"; the art is
 * decoration, the words say it). Picking it lifts the card (tonal fill, a stronger outline, a shadow) with the app's
 * spring, its art's ring breathes out once and a tick confirms; with reduced motion it changes at once and the ring
 * stays still. Put both in a `Modifier.selectableGroup()`.
 */
@Composable
fun PersonChoiceCard(
    person: Person,
    title: String,
    means: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val container by animateColorAsState(
        if (selected) cs.secondaryContainer else cs.surfaceContainerLow,
        Motion.defaultEffects(reduced),
        label = "person-card",
    )
    val outline by animateColorAsState(
        if (selected) cs.primary else cs.outlineVariant,
        Motion.defaultEffects(reduced),
        label = "person-line",
    )
    val lift by animateDpAsState(if (selected) 6.dp else 0.dp, Motion.defaultSpatial(reduced), label = "person-lift")
    val border by animateDpAsState(if (selected) 2.dp else 1.dp, Motion.defaultSpatial(reduced), label = "person-border")
    // the ring breathes out once when the card is picked (never on the first draw of an already picked card)
    val breath = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduced && breath.value >= 1f && breath.targetValue >= 1f) {
            breath.snapTo(0f)
            breath.animateTo(1f, tween(BREATH_MS, easing = FastOutSlowInEasing))
        }
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = container,
        border = BorderStroke(border, outline),
        shadowElevation = lift,
        modifier =
            modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) {
                if (!selected) haptics.tick()
                onSelect()
            },
    ) {
        Column(Modifier.padding(16.dp)) {
            PersonArt(person, selected, breath.value, Modifier.fillMaxWidth().height(ArtHeight))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                    Text(means, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}

/**
 * The art of [person] (Canvas, theme colours: right in light and dark, never a glucose number: it would be a card
 * surface). Decoration only: the card's words say the same to TalkBack. Yours: an arm with a sensor and a phone beside it, the sensor's signal going to the phone. Someone else's:
 * a smaller person with a sensor, and a phone further away showing their line, a dotted path between them. [breath]
 * (0..1) is the picked ring's way out; 1 = at rest.
 */
@Composable
fun PersonArt(
    person: Person,
    selected: Boolean,
    breath: Float,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tone = if (person == Person.Yours) cs.primary else cs.tertiary
    val toneSoft = if (person == Person.Yours) cs.primaryContainer else cs.tertiaryContainer
    val colors = ArtColors(tone, toneSoft, cs.surface, cs.outline, cs.surfaceContainerHighest, cs.onSurfaceVariant)
    Canvas(modifier.clearAndSetSemantics { }) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.height * 0.46f
        drawCircle(toneSoft.copy(alpha = if (selected) 0.9f else 0.55f), r, c)
        if (breath < 1f) drawCircle(tone.copy(alpha = 0.5f * (1 - breath)), r * (1f + 0.22f * breath), c, style = Stroke(3.dp.toPx()))
        if (person == Person.Yours) yoursArt(c, r, colors) else theirsArt(c, r, colors)
    }
}

private class ArtColors(
    val tone: Color,
    val toneSoft: Color,
    val screen: Color,
    val line: Color,
    val body: Color,
    val muted: Color,
)

/** An arm across the circle with the sensor on it, its signal, and a phone to the right with a smooth line. */
private fun DrawScope.yoursArt(
    c: Offset,
    r: Float,
    k: ArtColors,
) {
    val armW = r * 0.42f
    rotate(-28f, c) {
        drawRoundRect(
            k.body,
            Offset(c.x - r * 1.25f, c.y - armW / 2 + r * 0.18f),
            Size(r * 1.7f, armW),
            CornerRadius(armW / 2),
        )
        drawRoundRect(
            k.line,
            Offset(c.x - r * 1.25f, c.y - armW / 2 + r * 0.18f),
            Size(r * 1.7f, armW),
            CornerRadius(armW / 2),
            style = Stroke(1.5.dp.toPx()),
        )
    }
    val sensor = Offset(c.x - r * 0.38f, c.y + r * 0.02f)
    sensorDisc(sensor, r * 0.2f, k)
    // the signal: two arcs from the sensor towards the phone
    for (i in 1..2) {
        val a = r * (0.18f + 0.16f * i)
        drawArc(
            k.tone.copy(alpha = 0.85f - 0.25f * i),
            -60f,
            70f,
            false,
            Offset(sensor.x - a, sensor.y - a),
            Size(a * 2, a * 2),
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    phone(Offset(c.x + r * 0.62f, c.y), r * 0.95f, k)
}

/** A smaller person on the left with a sensor, a dotted path to a phone on the right showing their line. */
private fun DrawScope.theirsArt(
    c: Offset,
    r: Float,
    k: ArtColors,
) {
    val who = Offset(c.x - r * 0.55f, c.y + r * 0.05f)
    val head = r * 0.2f
    drawCircle(k.tone, head, Offset(who.x, who.y - r * 0.38f))
    drawRoundRect(
        k.tone,
        Offset(who.x - r * 0.3f, who.y - r * 0.12f),
        Size(r * 0.6f, r * 0.72f),
        CornerRadius(r * 0.28f),
    )
    sensorDisc(Offset(who.x + r * 0.2f, who.y + r * 0.12f), r * 0.12f, k)
    val phoneAt = Offset(c.x + r * 0.62f, c.y)
    val path =
        Path().apply {
            moveTo(who.x + r * 0.34f, who.y - r * 0.42f)
            quadraticTo(c.x + r * 0.1f, c.y - r * 1.0f, phoneAt.x - r * 0.3f, phoneAt.y - r * 0.5f)
        }
    drawPath(
        path,
        k.tone.copy(alpha = 0.8f),
        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7.dp.toPx()))),
    )
    phone(phoneAt, r * 0.95f, k)
}

/** A Libre seen from above: a flat white disc with the filament's dot, ringed in the tone. */
private fun DrawScope.sensorDisc(
    at: Offset,
    r: Float,
    k: ArtColors,
) {
    drawCircle(Color.White, r, at)
    drawCircle(Color.LightGray, r * 0.32f, at)
    drawCircle(k.tone, r, at, style = Stroke(2.dp.toPx()))
}

/** A phone [h] tall at [at] (its centre): the body, the screen and a smooth glucose line without numbers. */
private fun DrawScope.phone(
    at: Offset,
    h: Float,
    k: ArtColors,
) {
    val w = h * 0.52f
    val tl = Offset(at.x - w / 2, at.y - h / 2)
    drawRoundRect(k.body, tl, Size(w, h), CornerRadius(w * 0.18f))
    drawRoundRect(k.line, tl, Size(w, h), CornerRadius(w * 0.18f), style = Stroke(1.5.dp.toPx()))
    val inset = w * 0.1f
    val screenTl = Offset(tl.x + inset, tl.y + inset * 1.6f)
    val screen = Size(w - inset * 2, h - inset * 3.2f)
    drawRoundRect(k.screen, screenTl, screen, CornerRadius(w * 0.08f))
    // the in-range band and a gentle line ending in a dot: a glucose graph, no digits
    drawRect(
        k.toneSoft.copy(alpha = 0.6f),
        Offset(screenTl.x, screenTl.y + screen.height * 0.38f),
        Size(screen.width, screen.height * 0.3f),
    )
    val line =
        Path().apply {
            moveTo(screenTl.x + screen.width * 0.06f, screenTl.y + screen.height * 0.6f)
            cubicTo(
                screenTl.x + screen.width * 0.3f,
                screenTl.y + screen.height * 0.25f,
                screenTl.x + screen.width * 0.55f,
                screenTl.y + screen.height * 0.8f,
                screenTl.x + screen.width * 0.82f,
                screenTl.y + screen.height * 0.5f,
            )
        }
    drawPath(line, k.tone, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(k.tone, 3.5.dp.toPx(), Offset(screenTl.x + screen.width * 0.82f, screenTl.y + screen.height * 0.5f))
    drawRoundRect(
        k.muted.copy(alpha = 0.5f),
        Offset(at.x - w * 0.15f, tl.y + h - inset * 1.1f),
        Size(w * 0.3f, inset * 0.35f),
        CornerRadius(inset),
    )
}

private const val BREATH_MS = 700
