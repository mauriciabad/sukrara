// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.source

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.rows.groupCard
import kotlin.math.min
import kotlin.math.pow

/** The illustration's height in a [PersonChoiceCard]. */
private val ArtHeight = 132.dp

/**
 * One of the two big choices of *Whose glucose will Sukrara show?* (docs/44 "Setup"): an illustration of [person]'s
 * situation, the answer and what it means, with a radio button (TalkBack: "radio button, selected"; the art is
 * decoration, the words say it). Picking it lifts the card (tonal fill, a stronger outline, a shadow) with the app's
 * spring, its art plays once (see [PersonArt]) and a tick confirms; with reduced motion it changes at once and the art
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
        if (selected) cs.secondaryContainer else cs.groupCard,
        Motion.defaultEffects(reduced),
        label = "person-card",
    )
    val outline by animateColorAsState(
        if (selected) cs.primary else cs.outlineVariant,
        Motion.defaultEffects(reduced),
        label = "person-line",
    )
    // only the card being picked lifts on the spatial spring; the one left settles back quickly, without the pick's spring
    val move = if (selected) Motion.defaultSpatial<Dp>(reduced) else Motion.fastEffects(reduced)
    val lift by animateDpAsState(if (selected) 6.dp else 0.dp, move, label = "person-lift")
    val border by animateDpAsState(if (selected) 2.dp else 1.dp, move, label = "person-border")
    // the art plays its pick once when the card is picked (never on the first draw of an already picked card)
    val pick = remember { Animatable(PICK_S) }
    LaunchedEffect(selected) {
        if (selected && !reduced && pick.value >= PICK_S && pick.targetValue >= PICK_S) {
            pick.snapTo(0f)
            pick.animateTo(PICK_S, tween((PICK_S * 1000).toInt(), easing = LinearEasing))
        }
    }
    // the selectable Surface clips its press ripple to the card's corners (a selectable on the outer modifier drew a
    // square ripple past them); TalkBack still hears a radio button
    Surface(
        selected = selected,
        onClick = {
            if (!selected) haptics.tick()
            onSelect()
        },
        shape = RoundedCornerShape(24.dp),
        color = container,
        border = BorderStroke(border, outline),
        shadowElevation = lift,
        modifier = modifier.fillMaxWidth().semantics { role = Role.RadioButton },
    ) {
        Column(Modifier.padding(16.dp)) {
            PersonArt(person, selected, pick.value, container, Modifier.fillMaxWidth().height(ArtHeight))
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
 * The art of [person], drawn on a 320 × 132 board scaled to fit (Canvas, theme colours: right in light and dark, never
 * a glucose number: it would be a card surface). Decoration only: the card's words say the same to TalkBack.
 * Yours: you, the sensor on your arm sending its signal to the phone in your hand. Someone else's: a small person with a
 * sensor and their own phone (their Sukrara), reaching your phone in two arches: high through the cloud, low over
 * Bluetooth. A tinted circle sits behind the phone, stronger when [selected]. [time] is the seconds since the pick
 * ([PICK_S] = at rest): the sensor pulses, the waves go out or a dot travels each arch, and the phone's line scrolls on
 * as if time passed, landing on the same shape. [background] is the card's fill (the cut-out around the hand).
 * Mac: `PersonArt` in SourcePane.swift, the same drawing.
 */
@Composable
fun PersonArt(
    person: Person,
    selected: Boolean,
    time: Float,
    background: Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val dark = cs.surface.luminance() < 0.5f
    // someone else's in violet: never the theme's tertiary (a mauve close to a low's pink) or a dynamic colour
    val tone =
        when {
            person == Person.Yours -> cs.primary
            dark -> TheirsDark
            else -> TheirsLight
        }
    val soft =
        when {
            person == Person.Yours -> cs.primaryContainer
            dark -> TheirsSoftDark
            else -> TheirsSoftLight
        }
    val glow by animateFloatAsState(if (selected) 0.95f else 0.55f, Motion.defaultEffects(reduced), label = "person-glow")
    val k = ArtColors(tone, soft.copy(alpha = glow), soft, cs.surface, cs.outline, cs.surfaceContainerHighest, background)
    Canvas(modifier.clearAndSetSemantics { }) {
        val s = min(size.width / BOARD_W, size.height / BOARD_H)
        withTransform({
            translate((size.width - BOARD_W * s) / 2, (size.height - BOARD_H * s) / 2)
            scale(s, s, Offset.Zero)
        }) {
            if (person == Person.Yours) yoursArt(k, time) else theirsArt(k, time)
        }
    }
}

/** Seconds a pick's motion lasts; the art is at rest from then on. */
private const val PICK_S = 1.6f
private const val BOARD_W = 320f
private const val BOARD_H = 132f
private val TheirsLight = Color(0xFF6750A4)
private val TheirsSoftLight = Color(0xFFE9DDFF)
private val TheirsDark = Color(0xFFCFBCFF)
private val TheirsSoftDark = Color(0xFF4F378B)
private val SensorDot = Color(0xFFB9BEC4)

/** The dots on the arches: close to linear, a soft start and end. */
private val TravelEasing = CubicBezierEasing(0.33f, 0.12f, 0.67f, 0.88f)

private class ArtColors(
    val tone: Color,
    val circle: Color,
    val soft: Color,
    val screen: Color,
    val line: Color,
    val body: Color,
    val card: Color,
)

/** You (a bust), the arm from your shoulder to the phone in your hand, the sensor on it and its waves. */
private fun DrawScope.yoursArt(
    k: ArtColors,
    t: Float,
) {
    drawCircle(k.circle, 58f, Offset(216f, 64f))
    val arm =
        Path().apply {
            moveTo(144f, 104f)
            quadraticTo(174f, 114f, 198f, 86f)
        }
    // the arm starts inside the shoulder, so it grows out of it
    personGlyph(124f, 96f, 60f, k.tone)
    drawPath(arm, k.tone, style = Stroke(15f, cap = StrokeCap.Round))
    phone(216f, 60f, 80f, 10f, k, t, delay = 0.12f)
    drawCircle(k.tone, 9f, Offset(199f, 85f))
    drawCircle(k.card, 9f, Offset(199f, 85f), style = Stroke(3f))
    pulses(165f, 106f, 8f, k, t)
    sensor(165f, 106f, 8f, k)
    waves(165f, 106f, k, t)
}

/** Their small figure with a sensor and a plain phone; two arches to your phone: the cloud above, Bluetooth below. */
private fun DrawScope.theirsArt(
    k: ArtColors,
    t: Float,
) {
    drawCircle(k.circle, 62f, Offset(240f, 66f))
    personGlyph(52f, 106f, 30f, k.tone)
    pulses(62f, 110f, 4.5f, k, t)
    sensor(62f, 110f, 4.5f, k)
    phone(82f, 100f, 30f, 10f, k, t, plain = true)
    drawCircle(k.tone, 4.5f, Offset(74f, 110f))
    drawCircle(k.card, 4.5f, Offset(74f, 110f), style = Stroke(2f))
    val high =
        Path().apply {
            moveTo(86f, 80f)
            cubicTo(90f, 38f, 160f, 24f, 211f, 44f)
        }
    val low =
        Path().apply {
            moveTo(98f, 104f)
            cubicTo(108f, 92f, 168f, 88f, 211f, 97f)
        }
    dotted(high, k)
    traveller(high, 4.5f, k, t)
    cloud(138.9f, 40f, k) // the high arch's middle
    dotted(low, k)
    traveller(low, 3.5f, k, t)
    bluetooth(153.5f, 92.2f, k) // the low arch's middle
    phone(240f, 66f, 112f, -6f, k, t, chip = true, delay = 0.05f)
}

/** A person: a round head over rounded shoulders, [s] tall-ish, standing at [cx], [cy]. */
private fun DrawScope.personGlyph(
    cx: Float,
    cy: Float,
    s: Float,
    color: Color,
) {
    drawCircle(color, s * 0.3f, Offset(cx, cy - s * 0.55f))
    val top = cy - s * 0.08f
    val body =
        Path().apply {
            moveTo(cx - s * 0.62f, cy + s * 0.62f)
            cubicTo(cx - s * 0.62f, top + s * 0.12f, cx - s * 0.32f, top, cx, top)
            cubicTo(cx + s * 0.32f, top, cx + s * 0.62f, top + s * 0.12f, cx + s * 0.62f, cy + s * 0.62f)
            close()
        }
    drawPath(body, color)
}

/** A Libre seen from above: a white disc with the filament's dot, ringed in the tone. */
private fun DrawScope.sensor(
    x: Float,
    y: Float,
    r: Float,
    k: ArtColors,
) {
    drawCircle(Color.White, r, Offset(x, y))
    drawCircle(SensorDot, r * 0.32f, Offset(x, y))
    drawCircle(k.tone, r, Offset(x, y), style = Stroke(2f))
}

/** Three rings leaving the sensor on a pick, a quarter second apart. */
private fun DrawScope.pulses(
    x: Float,
    y: Float,
    r: Float,
    k: ArtColors,
    t: Float,
) {
    for (i in 0..2) {
        val local = t - 0.25f * i
        if (local <= 0f || local >= 1f) continue
        val e = 1 - (1 - local).pow(2)
        drawCircle(k.tone.copy(alpha = 0.7f * (1 - e)), r * (1 + 1.8f * e), Offset(x, y), style = Stroke(1.5f))
    }
}

/** The sensor's signal: three arcs above the arm, towards the phone; on a pick they appear one after another. */
private fun DrawScope.waves(
    x: Float,
    y: Float,
    k: ArtColors,
    t: Float,
) {
    for ((i, r) in listOf(17f, 24f, 31f).withIndex()) {
        val base = 0.85f - 0.2f * i
        val delay = 0.1f + 0.15f * i
        val alpha = if (t >= PICK_S) base else (((t - delay) / 0.36f).coerceIn(0f, 1f) * base)
        drawArc(
            k.tone.copy(alpha = alpha),
            -108f,
            46f,
            false,
            Offset(x - r, y - r),
            Size(r * 2, r * 2),
            style = Stroke(2.5f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.dotted(
    path: Path,
    k: ArtColors,
) = drawPath(
    path,
    k.tone.copy(alpha = 0.75f),
    style = Stroke(2.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7f))),
)

/** A dot travelling [path] in the first 1.1 s of a pick, fading in and out at its ends. */
private fun DrawScope.traveller(
    path: Path,
    r: Float,
    k: ArtColors,
    t: Float,
) {
    if (t <= 0f || t >= TRAVEL_S) return
    val p = t / TRAVEL_S
    val measure = PathMeasure().apply { setPath(path, false) }
    val at = measure.getPosition(measure.length * TravelEasing.transform(p))
    val alpha =
        when {
            p < 0.12f -> p / 0.12f
            p < 0.85f -> 1f
            else -> (1 - p) / 0.15f
        }
    drawCircle(k.tone.copy(alpha = alpha), r, at)
}

private const val TRAVEL_S = 1.1f

/** A cloud (the internet): three soft shapes, outlined as one (each outline first, every fill over them). */
private fun DrawScope.cloud(
    cx: Float,
    cy: Float,
    k: ArtColors,
) {
    val pill = Offset(cx - 16f, cy - 5f)
    val pillSize = Size(32f, 13f)
    val a = Offset(cx - 6f, cy - 5f)
    val b = Offset(cx + 5f, cy - 8f)
    val edge = Stroke(3f)
    drawRoundRect(k.line, pill, pillSize, CornerRadius(6.5f), style = edge)
    drawCircle(k.line, 8f, a, style = edge)
    drawCircle(k.line, 10f, b, style = edge)
    drawRoundRect(k.screen, pill, pillSize, CornerRadius(6.5f))
    drawCircle(k.screen, 8f, a)
    drawCircle(k.screen, 10f, b)
}

/** A Bluetooth badge: the rune in the tone on a small disc. */
private fun DrawScope.bluetooth(
    x: Float,
    y: Float,
    k: ArtColors,
) {
    drawCircle(k.screen, 10f, Offset(x, y))
    drawCircle(k.line, 10f, Offset(x, y), style = Stroke(1.5f))
    val rune =
        Path().apply {
            moveTo(x - 3.6f, y - 3.4f)
            lineTo(x + 3.4f, y + 3.2f)
            lineTo(x, y + 6.4f)
            lineTo(x, y - 6.4f)
            lineTo(x + 3.4f, y - 3.2f)
            lineTo(x - 3.6f, y + 3.4f)
        }
    drawPath(rune, k.tone, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * A phone [h] tall centred at [cx], [cy], turned [rot]°: the body, the screen and, unless [plain], the range band and a
 * glucose line without digits ([chip]: the followed person's portrait on top). On a pick the line scrolls left by one
 * screen of new readings after [delay] s and lands on the same shape; the newest dot rides it at the right edge.
 */
private fun DrawScope.phone(
    cx: Float,
    cy: Float,
    h: Float,
    rot: Float,
    k: ArtColors,
    t: Float,
    plain: Boolean = false,
    chip: Boolean = false,
    delay: Float = 0f,
) = rotate(rot, Offset(cx, cy)) {
    val w = h * 0.5f
    val x0 = cx - w / 2
    val y0 = cy - h / 2
    val i = w * 0.08f
    drawRoundRect(k.body, Offset(x0, y0), Size(w, h), CornerRadius(w * 0.18f))
    drawRoundRect(k.line, Offset(x0, y0), Size(w, h), CornerRadius(w * 0.18f), style = Stroke(1.5f))
    val sx = x0 + i
    val sy = y0 + i * 1.6f
    val sw = w - 2 * i
    val sh = h - 3.2f * i
    drawRoundRect(k.screen, Offset(sx, sy), Size(sw, sh), CornerRadius(w * 0.1f))
    if (!plain) {
        fun x(f: Float) = sx + sw * f

        fun y(f: Float) = sy + sh * f
        if (chip) {
            drawCircle(k.tone, sw * 0.11f, Offset(x(0.22f), y(0.14f)))
            drawRoundRect(
                k.line.copy(alpha = 0.45f),
                Offset(x(0.38f), y(0.11f)),
                Size(sw * 0.4f, sh * 0.06f),
                CornerRadius(sh * 0.03f),
            )
        }
        drawRect(k.soft.copy(alpha = 0.75f), Offset(sx, y(0.42f)), Size(sw, sh * 0.26f))
        // the line, a bridge of new readings, then the line again one screen [d] later
        val d = sw
        val segments =
            listOf(
                floatArrayOf(x(0.06f), y(0.66f), x(0.3f), y(0.3f), x(0.52f), y(0.82f), x(0.82f), y(0.5f)),
                floatArrayOf(x(0.82f), y(0.5f), x(0.92f), y(0.39f), x(0.98f), y(0.78f), x(0.06f) + d, y(0.66f)),
                floatArrayOf(x(0.06f) + d, y(0.66f), x(0.3f) + d, y(0.3f), x(0.52f) + d, y(0.82f), x(0.82f) + d, y(0.5f)),
            )
        val line =
            Path().apply {
                moveTo(segments[0][0], segments[0][1])
                for (c in segments) cubicTo(c[2], c[3], c[4], c[5], c[6], c[7])
            }
        val p = if (t >= PICK_S) 0f else ((t - delay) / SCROLL_S).coerceIn(0f, 1f)
        val shift = d * easeInOutCubic(p)
        val edge = x(0.82f)
        clipRect(x(0.06f), sy, edge, sy + sh) {
            translate(-shift, 0f) { drawPath(line, k.tone, style = Stroke(2.5f, cap = StrokeCap.Round)) }
        }
        drawCircle(k.tone, 3.5f, Offset(edge, yAtX(segments, edge + shift)))
    }
    drawRoundRect(
        k.line.copy(alpha = 0.5f),
        Offset(cx - w * 0.15f, y0 + h - i * 1.15f),
        Size(w * 0.3f, i * 0.35f),
        CornerRadius(2f),
    )
}

private const val SCROLL_S = 1.1f

private fun easeInOutCubic(p: Float) = if (p < 0.5f) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2

/** The y of the cubic [segments] (each x0 y0 x1 y1 x2 y2 x3 y3, x growing) where x = [x]. */
private fun yAtX(
    segments: List<FloatArray>,
    x: Float,
): Float {
    val c = segments.firstOrNull { x <= it[6] } ?: segments.last()

    fun at(
        t: Float,
        o: Int,
    ): Float {
        val u = 1 - t
        return u * u * u * c[o] + 3 * u * u * t * c[o + 2] + 3 * u * t * t * c[o + 4] + t * t * t * c[o + 6]
    }
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2
        if (at(mid, 0) < x) lo = mid else hi = mid
    }
    return at((lo + hi) / 2, 1)
}
