// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.notification

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.rows.RadioRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup

/**
 * THE glucose notification choice (docs/36 §11), the same on Settings › Glucose notification and in setup: AOSP
 * Settings' pattern for choosing how the system shows something (System navigation: an `IllustrationPreference` of the
 * selected option over `SelectorWithWidgetPreference` radio rows, packages/apps/Settings
 * `system_navigation_gesture_settings.xml`): one picture of the selected choice (the status bar and the notification it
 * gives, what shows the glucose highlighted), fading through on a change, then one radio row per choice (equal rows,
 * the title what the status bar shows, the summary what the notification is). [rootOffered] false (a build without
 * root features) leaves the root choice out; it is chosen even before root works: choosing it is the request.
 */
@Composable
fun GlucoseNotificationChoice(
    style: NotificationStyle,
    rootOffered: Boolean,
    onChoose: (NotificationStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Preview(style)
        SettingsGroup(Modifier.selectableGroup(), title = stringResource(R.string.ds_notif_style_heading)) {
            for (option in NotificationStyle.entries) {
                if (option == NotificationStyle.Root && !rootOffered) continue
                RadioRow(
                    stringResource(titleOf(option)),
                    option == style,
                    { onChoose(option) },
                    summary = stringResource(summaryOf(option)),
                )
            }
        }
    }
}

/** A choice's name: what the status bar shows. */
fun titleOf(style: NotificationStyle): Int =
    when (style) {
        NotificationStyle.Chip -> R.string.ds_notif_style_chip
        NotificationStyle.Icon -> R.string.ds_notif_style_icon
        NotificationStyle.Root -> R.string.ds_notif_style_root
    }

private fun summaryOf(style: NotificationStyle): Int =
    when (style) {
        NotificationStyle.Chip -> R.string.ds_notif_style_chip_summary
        NotificationStyle.Icon -> R.string.ds_notif_style_icon_summary
        NotificationStyle.Root -> R.string.ds_notif_style_root_summary
    }

private fun previewOf(style: NotificationStyle): Int =
    when (style) {
        NotificationStyle.Chip -> R.string.ds_notif_style_chip_preview
        NotificationStyle.Icon -> R.string.ds_notif_style_icon_preview
        NotificationStyle.Root -> R.string.ds_notif_style_root_preview
    }

/** The colours a preview is drawn in: the theme's, the notification on its own surface with an outline (both themes). */
private class Ink(
    val screen: Color,
    val outline: Color,
    val card: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val good: Color,
)

@Composable
private fun Preview(style: NotificationStyle) {
    val scheme = MaterialTheme.colorScheme
    val ink =
        Ink(
            screen = scheme.surfaceContainerLowest,
            outline = scheme.outline,
            card = scheme.surfaceContainerHighest,
            line = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            good = Color(0xFF2E9E5B),
        )
    val description = stringResource(previewOf(style))
    val reduced = LocalReducedMotion.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(style, transitionSpec = { Motion.fadeThrough(reduced) }, label = "preview") { shown ->
            Canvas(
                Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .height(176.dp)
                    .semantics { contentDescription = description },
            ) { phone(shown, ink) }
        }
    }
}

/** The top of a phone: its status bar (the glucose highlighted where [style] shows it) and the glucose notification. */
private fun DrawScope.phone(
    style: NotificationStyle,
    ink: Ink,
) {
    val r = 20.dp.toPx()
    drawRoundRect(ink.screen, cornerRadius = CornerRadius(r))
    drawRoundRect(ink.outline, cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
    val pad = 16.dp.toPx()
    val barY = 14.dp.toPx()
    val barH = 20.dp.toPx()
    val cy = barY + barH / 2
    // the clock, and the system icons at the end
    bar(Offset(pad, cy), 28.dp.toPx(), ink.line)
    bar(Offset(size.width - pad - 40.dp.toPx(), cy), 40.dp.toPx(), ink.line)
    when (style) {
        NotificationStyle.Chip -> {
            // the chip after the clock: an arrow and the number in a pill
            val x = pad + 36.dp.toPx()
            val w = 56.dp.toPx()
            drawRoundRect(ink.accent, Offset(x, barY), Size(w, barH), CornerRadius(barH / 2))
            arrow(Offset(x + 12.dp.toPx(), cy), 5.dp.toPx(), ink.onAccent)
            bar(Offset(x + 22.dp.toPx(), cy), 24.dp.toPx(), ink.onAccent)
        }

        NotificationStyle.Icon -> {
            // the number as the notification's icon after the clock, ringed
            val c = Offset(pad + 46.dp.toPx(), cy)
            drawCircle(ink.accent.copy(alpha = 0.22f), 13.dp.toPx(), c)
            drawRoundRect(
                ink.accent,
                Offset(c.x - 7.dp.toPx(), c.y - 7.dp.toPx()),
                Size(14.dp.toPx(), 14.dp.toPx()),
                CornerRadius(3.dp.toPx()),
            )
        }

        NotificationStyle.Root -> {
            // the value as text before the system icons, highlighted
            val x = size.width - pad - 40.dp.toPx() - 52.dp.toPx()
            drawRoundRect(ink.accent.copy(alpha = 0.22f), Offset(x - 6.dp.toPx(), barY), Size(48.dp.toPx(), barH), CornerRadius(barH / 2))
            bar(Offset(x, cy), 24.dp.toPx(), ink.accent)
            arrow(Offset(x + 31.dp.toPx(), cy), 5.dp.toPx(), ink.accent)
        }
    }
    // the notification
    val top = barY + barH + 12.dp.toPx()
    // basic: as short as the notification is; with the graph: the rest of the screen
    val full = size.height - top - pad
    val card = Size(size.width - 2 * pad, if (style == NotificationStyle.Chip) minOf(full, 62.dp.toPx()) else full)
    drawRoundRect(ink.card, Offset(pad, top), card, CornerRadius(16.dp.toPx()))
    drawRoundRect(ink.outline.copy(alpha = 0.5f), Offset(pad, top), card, CornerRadius(16.dp.toPx()), style = Stroke(1.dp.toPx()))
    val ix = pad + 22.dp.toPx()
    drawCircle(ink.accent, 9.dp.toPx(), Offset(ix, top + 22.dp.toPx()))
    val x = ix + 20.dp.toPx()
    bar(Offset(x, top + 18.dp.toPx()), 64.dp.toPx(), ink.line)
    // the value, large
    drawRoundRect(ink.good, Offset(x, top + 30.dp.toPx()), Size(52.dp.toPx(), 20.dp.toPx()), CornerRadius(6.dp.toPx()))
    if (style == NotificationStyle.Chip) {
        // basic: the value and its words, no graph
        bar(Offset(x + 64.dp.toPx(), top + 40.dp.toPx()), 44.dp.toPx(), ink.line)
        bar(Offset(x + 120.dp.toPx(), top + 40.dp.toPx()), 44.dp.toPx(), ink.line)
    } else {
        // complete: a ring and the graph
        drawCircle(ink.good, 10.dp.toPx(), Offset(pad + card.width - 24.dp.toPx(), top + 40.dp.toPx()), style = Stroke(3.dp.toPx()))
        val gTop = top + 58.dp.toPx()
        val gBox = Size(card.width - (x - pad) - 14.dp.toPx(), card.height - 66.dp.toPx())
        if (gBox.height > 8.dp.toPx()) {
            drawRoundRect(ink.screen, Offset(x, gTop), gBox, CornerRadius(8.dp.toPx()))
            val path = Path()
            val pts = listOf(0.6f, 0.35f, 0.5f, 0.25f, 0.45f, 0.7f, 0.5f, 0.3f)
            pts.forEachIndexed { i, y ->
                val px = x + gBox.width * (0.06f + 0.88f * i / (pts.size - 1))
                val py = gTop + gBox.height * y
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, ink.good, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.bar(
    start: Offset,
    w: Float,
    color: Color,
) {
    val h = 6.dp.toPx()
    drawRoundRect(color, Offset(start.x, start.y - h / 2), Size(w, h), CornerRadius(h / 2))
}

/** A trend arrow pointing right, centred at [c]. */
private fun DrawScope.arrow(
    c: Offset,
    r: Float,
    color: Color,
) {
    val w = r * 0.5f
    drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), w, StrokeCap.Round)
    drawLine(color, Offset(c.x + r, c.y), Offset(c.x + r * 0.3f, c.y - r * 0.65f), w, StrokeCap.Round)
    drawLine(color, Offset(c.x + r, c.y), Offset(c.x + r * 0.3f, c.y + r * 0.65f), w, StrokeCap.Round)
}
