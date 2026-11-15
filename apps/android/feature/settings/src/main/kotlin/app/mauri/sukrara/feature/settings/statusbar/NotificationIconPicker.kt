// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.statusbar

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.NotificationIcon
import app.mauri.sukrara.core.model.NotificationStyle
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.designsystem.rows.RadioRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.GlucoseLook
import app.mauri.sukrara.graph.TrendMark
import app.mauri.sukrara.graph.ValueGlyphs
import app.mauri.sukrara.graph.stats.toPoint
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * The picker's pictures (glyph surface `notification-icon-preview`): the person's newest reading as the glucose
 * notification would draw each icon, from the card's own face and glyphs; nothing made up when there's no reading.
 */
object NotificationIconPreview {
    /** The newest of [readings] (the hub's, oldest first, trend already worked out) as the card shows it at [now]. */
    fun look(
        readings: List<Reading>,
        now: Instant,
        unit: GlucoseUnit,
    ): GlucoseLook? =
        readings.lastOrNull()?.let {
            GlucoseCard.face(it.toPoint(), now.epochSecond, null, CardSurface.NOTIFICATION_ICON_PREVIEW, unit)
        }

    /** [icon] for [look] as the notification's small icon draws it (an alpha mask); null = Sukrara's own icon or none. */
    fun bitmap(
        icon: NotificationIcon,
        look: GlucoseLook,
        size: Int = 96,
    ): Bitmap? =
        when (icon) {
            NotificationIcon.ValueTrend -> {
                ValueGlyphs.icon(look, size)
            }

            NotificationIcon.Value -> {
                ValueGlyphs.icon(look, size, trend = false)
            }

            NotificationIcon.Trend -> {
                val arrow = look.trend == TrendMark.ARROW
                ValueGlyphs.trendIcon(look.rate.takeIf { arrow }, look.band.takeIf { arrow }, size)
            }

            NotificationIcon.App, NotificationIcon.None -> {
                null
            }
        }
}

/**
 * *What the icon shows* under the notification icon and the root text: one radio row per icon the [style] offers, each
 * with its picture (the newest reading drawn as that icon), *None* tagged *Root* and saying when the LSPosed module it
 * needs isn't working ([moduleActive]).
 */
@Composable
internal fun NotificationIconPicker(
    style: NotificationStyle,
    icon: NotificationIcon,
    look: GlucoseLook?,
    moduleActive: Boolean,
    onChoose: (NotificationIcon) -> Unit,
) {
    val offered = NotificationIcon.offered(style)
    if (offered.isEmpty()) return
    SettingsGroup(Modifier.selectableGroup(), title = stringResource(R.string.st_statusbar_icon_group)) {
        for (option in offered) {
            RadioRow(
                stringResource(iconTitle(option)),
                option == icon,
                { onChoose(option) },
                summary =
                    if (option == NotificationIcon.None && !moduleActive) {
                        stringResource(R.string.st_statusbar_icon_none_module)
                    } else {
                        null
                    },
                leading = { IconPicture(option, look) },
                tag = if (option == NotificationIcon.None) stringResource(DsR.string.ds_tag_root) else null,
            )
        }
    }
}

private fun iconTitle(icon: NotificationIcon): Int =
    when (icon) {
        NotificationIcon.ValueTrend -> R.string.st_statusbar_icon_value_trend
        NotificationIcon.Value -> R.string.st_statusbar_icon_value
        NotificationIcon.Trend -> R.string.st_statusbar_icon_trend
        NotificationIcon.App -> R.string.st_statusbar_icon_app
        NotificationIcon.None -> R.string.st_statusbar_icon_none
    }

/** The icon at the status bar's look (tinted like the bar's icons), or Sukrara's drop; none for *None*. */
@Composable
private fun IconPicture(
    icon: NotificationIcon,
    look: GlucoseLook?,
) {
    val tint = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
    val modifier = Modifier.size(24.dp)
    when {
        icon == NotificationIcon.App -> {
            Image(painterResource(DsR.drawable.ds_ic_status_sukrara), contentDescription = null, modifier = modifier, colorFilter = tint)
        }

        look != null && icon != NotificationIcon.None -> {
            val bitmap = remember(icon, look) { NotificationIconPreview.bitmap(icon, look) } ?: return
            Image(bitmap.asImageBitmap(), contentDescription = null, modifier = modifier, colorFilter = tint)
        }
    }
}
