// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The marker the charts draw for treatment kind [kind] ([TreatmentMarks.marker] in its colour, the theme's palette):
 * rapid ▼ filled, long ▽ outlined, carbs ●, note ◆. The Add treatment dialog's labels and the treatment lists start
 * with it, so an entry looks the same on the chart and in the app. Decoration only: the words beside it say the kind.
 */
@Composable
fun TreatmentKindMark(
    kind: String,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val color = TreatmentMarks.color(TreatmentMarks.palette(dark), kind)
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        drawIntoCanvas {
            TreatmentMarks.marker(
                it.nativeCanvas,
                kind,
                this.size.width / 2,
                this.size.height / 2,
                this.size.width / 2,
                color,
            )
        }
    }
}

/**
 * A list row's leading badge for [kind] (#207): the kind's marker in its tonal ink on a circle of its tonal tint, the
 * colours of the card's tinted pills ([TreatmentMarks.tonalInk] / [TreatmentMarks.tonalTint]: 4.5:1 in both themes).
 */
@Composable
fun TreatmentKindBadge(
    kind: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val ink = TreatmentMarks.tonalInk(kind, dark)
    val tint = TreatmentMarks.tonalTint(TreatmentMarks.color(TreatmentMarks.palette(dark), kind), dark)
    Box(modifier.size(size).background(Color(tint), CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * MARK_IN_BADGE).clearAndSetSemantics { }) {
            drawIntoCanvas {
                TreatmentMarks.marker(
                    it.nativeCanvas,
                    kind,
                    this.size.width / 2,
                    this.size.height / 2,
                    this.size.width / 2,
                    ink,
                )
            }
        }
    }
}

/** The marker's share of the badge (a 14 dp marker in 36 dp, the dialog's 10 dp label marker scaled up). */
private const val MARK_IN_BADGE = 0.39f

/**
 * A field's label for kind [kind]: its marker, then [text] on one line (the Add treatment form's fields, a pen entry's
 * note: every place a treatment field is named looks the same).
 */
@Composable
fun TreatmentKindLabel(
    kind: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TreatmentKindMark(kind)
        Spacer(Modifier.width(6.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
