// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.sharing

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.LinkPath

/**
 * THE marks for how readings travel between the wearer's phone and a follower's device (docs/FEATURES.md "Sharing over
 * Bluetooth"): the wearer's header and followers list, the follower's header. Shape and colour both differ (never colour
 * alone), and each has its words for TalkBack ([words]); the icon itself is decorative.
 */
enum class PathMark(
    @param:DrawableRes val icon: Int,
    @param:StringRes val words: Int,
) {
    /** Connected over Bluetooth now. */
    Nearby(R.drawable.ds_ic_bluetooth_connected, R.string.ds_path_nearby),

    /** A follower's device that has connected over Bluetooth, out of range now. */
    Paired(R.drawable.ds_ic_bluetooth, R.string.ds_path_paired),

    /** Over the internet (a relay connected). */
    Internet(R.drawable.ds_ic_public, R.string.ds_path_internet),

    /** No relay and no Bluetooth link: nothing new can arrive. */
    Offline(R.drawable.ds_ic_public_off, R.string.ds_path_offline),
    ;

    companion object {
        fun of(path: LinkPath): PathMark =
            when (path) {
                LinkPath.Nearby -> Nearby
                LinkPath.Internet -> Internet
                LinkPath.Offline -> Offline
            }
    }
}

/** Which [PathMark] a [PathIcon] draws (no TalkBack effect: tests read it). */
val PathMarkKey = SemanticsPropertyKey<PathMark>("PathMark")

/** [PathIcon]'s test tag. */
const val PATH_ICON_TAG = "path-icon"

/**
 * A [PathMark]: Bluetooth connected in the primary colour, Bluetooth out of range dimmed, the globe in [tint], no
 * connection in the error colour. Decorative: the row or line around it says [PathMark.words]. [size] null = the
 * space it's given (a mark in a line of text).
 */
@Composable
fun PathIcon(
    mark: PathMark,
    modifier: Modifier = Modifier,
    size: Dp? = 20.dp,
    tint: Color = LocalContentColor.current,
) {
    val scheme = MaterialTheme.colorScheme
    Icon(
        painterResource(mark.icon),
        contentDescription = null,
        modifier =
            modifier
                .then(if (size != null) Modifier.size(size) else Modifier)
                .testTag(PATH_ICON_TAG)
                .semantics { this[PathMarkKey] = mark },
        tint =
            when (mark) {
                PathMark.Nearby -> scheme.primary
                PathMark.Paired -> scheme.onSurfaceVariant.copy(alpha = PAIRED_ALPHA)
                PathMark.Internet -> tint
                PathMark.Offline -> scheme.error
            },
    )
}

private const val PAIRED_ALPHA = 0.6f

/** A part of a [MarkedLine]: words, a [PathMark], or a plain icon in the text's colour (the people icon). */
sealed interface LinePart {
    data class Words(
        val text: String,
    ) : LinePart

    data class Mark(
        val mark: PathMark,
    ) : LinePart

    data class Glyph(
        @param:DrawableRes val icon: Int,
    ) : LinePart
}

/**
 * A line of text with marks in it, each a little taller than a capital, so it grows with the font size. [description]
 * is what TalkBack reads for the whole line (the marks have no words of their own; null = the caller says it around
 * the line). One line unless [maxLines] says more: [FirstThatFits] picks a shorter line where a long one doesn't fit,
 * so nothing is cut.
 */
@Composable
fun MarkedLine(
    parts: List<LinePart>,
    description: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 1,
) {
    val text =
        buildAnnotatedString {
            parts.forEachIndexed { i, p ->
                when (p) {
                    is LinePart.Words -> append(p.text)
                    is LinePart.Mark, is LinePart.Glyph -> appendInlineContent("$MARK_ID$i", " ")
                }
            }
        }
    val placeholder = Placeholder(MARK_EM.em, MARK_EM.em, PlaceholderVerticalAlign.TextCenter)
    val inline =
        parts
            .mapIndexedNotNull { i, p ->
                when (p) {
                    is LinePart.Words -> {
                        null
                    }

                    is LinePart.Mark -> {
                        "$MARK_ID$i" to InlineTextContent(placeholder) { PathIcon(p.mark, Modifier.fillMaxSize(), size = null) }
                    }

                    is LinePart.Glyph -> {
                        "$MARK_ID$i" to
                            InlineTextContent(placeholder) {
                                Icon(painterResource(p.icon), contentDescription = null, modifier = Modifier.fillMaxSize(), tint = color)
                            }
                    }
                }
            }.toMap()
    Text(
        text,
        if (description != null) modifier.semantics { contentDescription = description } else modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = if (maxLines > 1) TextOverflow.Ellipsis else TextOverflow.Clip,
        inlineContent = inline,
    )
}

private const val MARK_ID = "path-mark-"

/** A mark's height in the line: a little more than a capital, as Material's inline icons. */
private const val MARK_EM = 1.25f

/**
 * The first of [options] that fits on one line in the width given. Each is measured at its own width, so a line never
 * wraps inside a word or loses its end: it is swapped whole for a shorter one. Only the chosen one is placed (so give
 * the whole its words with `clearAndSetSemantics`: the others would be read too). Where not even the last (the most
 * compact) fits, it is drawn smaller to fit, never cut.
 */
@Composable
fun FirstThatFits(
    options: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Layout({ options.forEach { it() } }, modifier) { measurables, constraints ->
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(loose) }
        val max = constraints.maxWidth
        val chosen = placeables.firstOrNull { it.width <= max } ?: placeables.last()
        if (chosen.width <= max) {
            layout(chosen.width.coerceAtLeast(constraints.minWidth), chosen.height) { chosen.placeRelative(0, 0) }
        } else {
            val scale = max.toFloat() / chosen.width
            layout(max, chosen.height) {
                chosen.placeRelativeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
            }
        }
    }
}
