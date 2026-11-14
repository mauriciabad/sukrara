// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.statusbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.haptics.Haptics
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.rows.ROW_TAG
import app.mauri.sukrara.feature.settings.R
import com.android.settingslib.spa.framework.compose.HighlightBox

/**
 * Settings › Status bar › *Glucose notification*: the chip or the graph, side by side, each with a picture of what it
 * gives (the owner, 2026-11-14: it must be obvious that the graph and the status-bar chip exclude each other). After
 * AOSP Settings' illustrated choices (System navigation: an `IllustrationPreference` over `SelectorWithWidgetPreference`
 * radio rows, packages/apps/Settings `system_navigation_gesture_settings.xml`): here each choice carries its own
 * picture, because the point is comparing them. One radio group for TalkBack (each card a radio button with its title
 * and summary). [forcedCard]: the root value is in the bar, so the graph is in use whatever the choice and the chip
 * can't be picked (the note under the cards says why).
 */
@Composable
fun NotificationStylePicker(
    chip: Boolean,
    forcedCard: Boolean,
    onChoose: (chip: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val heading = stringResource(R.string.st_statusbar_style)
    // a settings row like the others: the search finds it and flashes it (SPA's HighlightBox, keyed by its title)
    Box(modifier.testTag(ROW_TAG)) { HighlightBox(heading) { Picker(heading, chip, forcedCard, onChoose, haptics) } }
}

@Composable
private fun Picker(
    heading: String,
    chip: Boolean,
    forcedCard: Boolean,
    onChoose: (chip: Boolean) -> Unit,
    haptics: Haptics,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            heading,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val showsChip = chip && !forcedCard
            StyleCard(
                title = stringResource(R.string.st_statusbar_style_chip),
                summary = stringResource(R.string.st_statusbar_style_chip_summary),
                selected = showsChip,
                enabled = !forcedCard,
                onClick = {
                    haptics.tick()
                    onChoose(true)
                },
                picture = { drawChipPicture(it) },
                modifier = Modifier.weight(1f),
            )
            StyleCard(
                title = stringResource(R.string.st_statusbar_style_card),
                summary = stringResource(R.string.st_statusbar_style_card_summary),
                selected = !showsChip,
                enabled = !forcedCard,
                onClick = {
                    haptics.tick()
                    onChoose(false)
                },
                picture = { drawCardPicture(it) },
                modifier = Modifier.weight(1f),
            )
        }
        if (forcedCard) {
            Text(
                stringResource(R.string.st_statusbar_style_root),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** The colours a picture is drawn in (the theme's). */
private class PictureInk(
    val bar: Color,
    val surface: Color,
    val line: Color,
    val accent: Color,
    val good: Color,
)

@Composable
private fun StyleCard(
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    picture: DrawScope.(PictureInk) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val border by animateColorAsState(if (selected) scheme.primary else scheme.outlineVariant, label = "border")
    // the chosen card settles in with a small spring (none with reduced motion)
    val scale by animateFloatAsState(
        if (selected && !reduced) {
            1f
        } else if (reduced) {
            1f
        } else {
            0.97f
        },
        spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "scale",
    )
    val ink =
        PictureInk(
            bar = scheme.surfaceContainerHighest,
            surface = scheme.surfaceContainerHigh,
            line = scheme.onSurfaceVariant.copy(alpha = 0.55f),
            accent = scheme.primary,
            good = Color(0xFF2E9E5B),
        )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) scheme.secondaryContainer.copy(alpha = 0.45f) else scheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier =
            modifier
                .scale(scale)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.25f)) { picture(ink) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                // the card is the radio button: this one only shows its state
                RadioButton(selected = selected, onClick = null, enabled = enabled)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The status bar strip both pictures start with: the clock and the system icons, as grey bars. */
private fun DrawScope.statusBar(ink: PictureInk) {
    val h = size.height * 0.16f
    drawRoundRect(ink.bar, size = Size(size.width, h), cornerRadius = CornerRadius(h / 3))
    drawRoundRect(ink.line, Offset(size.width * 0.06f, h * 0.36f), Size(size.width * 0.12f, h * 0.28f), CornerRadius(h))
    drawRoundRect(ink.line, Offset(size.width * 0.78f, h * 0.36f), Size(size.width * 0.16f, h * 0.28f), CornerRadius(h))
}

/** The chip: a filled pill with an arrow next to the clock, and under it a notification of text lines (no graph). */
private fun DrawScope.drawChipPicture(ink: PictureInk) {
    statusBar(ink)
    val h = size.height * 0.16f
    val pill = Offset(size.width * 0.21f, h * 0.14f)
    val pillSize = Size(size.width * 0.3f, h * 0.72f)
    drawRoundRect(ink.accent, pill, pillSize, CornerRadius(pillSize.height / 2))
    arrow(Offset(pill.x + pillSize.height * 0.6f, pill.y + pillSize.height / 2), pillSize.height * 0.32f, Color.White)
    drawRoundRect(
        Color.White.copy(alpha = 0.9f),
        Offset(pill.x + pillSize.width * 0.45f, pill.y + pillSize.height * 0.3f),
        Size(pillSize.width * 0.4f, pillSize.height * 0.4f),
        CornerRadius(pillSize.height),
    )
    val card = notification(ink, top = size.height * 0.3f, height = size.height * 0.5f)
    // the value large and its words: text lines, no chart
    drawRoundRect(ink.good, Offset(card.x, card.y + size.height * 0.16f), Size(size.width * 0.26f, size.height * 0.12f), CornerRadius(8f))
    drawRoundRect(
        ink.line,
        Offset(card.x + size.width * 0.36f, card.y + size.height * 0.18f),
        Size(size.width * 0.22f, size.height * 0.08f),
        CornerRadius(8f),
    )
}

/** The graph: a small number icon in the bar, and the notification as the card with its ring and its chart. */
private fun DrawScope.drawCardPicture(ink: PictureInk) {
    statusBar(ink)
    val h = size.height * 0.16f
    drawRoundRect(ink.line, Offset(size.width * 0.21f, h * 0.3f), Size(h * 0.4f, h * 0.4f), CornerRadius(2f))
    val card = notification(ink, top = size.height * 0.24f, height = size.height * 0.74f)
    drawRoundRect(ink.good, Offset(card.x, card.y + size.height * 0.12f), Size(size.width * 0.22f, size.height * 0.1f), CornerRadius(8f))
    // a ring on the right
    val r = size.height * 0.06f
    drawCircle(ink.good, r, Offset(size.width * 0.84f, card.y + size.height * 0.17f), style = Stroke(r * 0.35f))
    // the chart: a curve on its own surface
    val top = card.y + size.height * 0.28f
    val box = Size(size.width * 0.78f, size.height * 0.24f)
    drawRoundRect(ink.bar, Offset(card.x, top), box, CornerRadius(10f))
    val path = Path()
    val pts = listOf(0.55f, 0.4f, 0.5f, 0.3f, 0.45f, 0.62f, 0.5f, 0.35f)
    pts.forEachIndexed { i, y ->
        val x = card.x + box.width * (0.06f + 0.88f * i / (pts.size - 1))
        val yy = top + box.height * y
        if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
    }
    drawPath(path, ink.good, style = Stroke(width = size.height * 0.02f, cap = StrokeCap.Round))
}

/** A notification's rounded card with the app icon and a title line; returns where its content column starts. */
private fun DrawScope.notification(
    ink: PictureInk,
    top: Float,
    height: Float,
): Offset {
    drawRoundRect(ink.surface, Offset(0f, top), Size(size.width, height), CornerRadius(size.width * 0.06f))
    val icon = size.height * 0.05f
    drawCircle(ink.accent, icon, Offset(size.width * 0.1f, top + size.height * 0.09f))
    val x = size.width * 0.2f
    drawRoundRect(ink.line, Offset(x, top + size.height * 0.06f), Size(size.width * 0.4f, size.height * 0.05f), CornerRadius(8f))
    return Offset(x, top)
}

/** A trend arrow pointing right, centred at [c]. */
private fun DrawScope.arrow(
    c: Offset,
    r: Float,
    color: Color,
) {
    val w = r * 0.45f
    drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), w, StrokeCap.Round)
    drawLine(color, Offset(c.x + r, c.y), Offset(c.x + r * 0.35f, c.y - r * 0.6f), w, StrokeCap.Round)
    drawLine(color, Offset(c.x + r, c.y), Offset(c.x + r * 0.35f, c.y + r * 0.6f), w, StrokeCap.Round)
}
