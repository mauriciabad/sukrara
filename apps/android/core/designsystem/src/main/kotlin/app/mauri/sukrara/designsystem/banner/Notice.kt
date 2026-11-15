// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.banner

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.ServiceIcon
import com.android.settingslib.spa.framework.theme.SettingsShape

/*
 * THE notice of the app (the owner, 2026-11-15: "it doesn't look like warnings that are similar are using a shared
 * component"): every banner, warning, tip, state and in-flow note is a [NoticeCard], so a warning looks and closes the
 * same on home, on a settings page and in a setup step. Only [NoticeTone] changes it: colours and the main action's
 * weight. No other file draws one (NoticeCardTest.noOtherNoticeCards).
 */

/** How much a notice matters: its colours and how heavy its main action is. */
enum class NoticeTone(
    /** The icon a notice of this tone shows when it has no icon of its own. */
    @param:DrawableRes val icon: Int,
) {
    /** A tip, a state (Mute all is on), a note in a flow: the accent colour, text buttons. */
    Info(R.drawable.ds_ic_info),

    /** Something the person has to fix, nothing has failed yet (a blocked feature, an upload behind): icon and buttons in the error colour. */
    Warning(R.drawable.ds_ic_warning),

    /** Alerts or readings may not work, data or the identity is at risk: the error container, a filled action. */
    Error(R.drawable.ds_ic_error),
}

/** One button of a notice. */
data class NoticeAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/** One of a notice's equal choices (*LibreView* / *Tidepool*, *Turn on* / *Not now*); [service]: its app icon. */
data class BannerChoice(
    val label: String,
    val service: Service?,
    val onClick: () -> Unit,
)

/**
 * The neutral notices' container, one step off the page behind it. Home and flows (page in surface): null =
 * surfaceContainerHigh; a settings page (surfaceContainer) provides surface, SPA's banner colour ([SettingsChrome]).
 */
val LocalNoticeContainer = staticCompositionLocalOf<Color?> { null }

object NoticeDefaults {
    /** A notice placed straight on an edge-to-edge page (home, a settings page): SPA's banner margins. */
    val PageMargins = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
}

/** The dismiss button's test tag (one per notice). */
const val NOTICE_DISMISS_TAG = "notice-dismiss"

/**
 * A notice: the [icon] (the tone's own by default) top-aligned with the first line, the [title] (none: a state in one
 * sentence), the [text], an optional [note] (why the last try failed), [below] (a progress bar, answers), equal
 * [choices] side by side, then the buttons at the end, [secondary] first and the main [action] last (Material's banner
 * and SPA's SettingsBanner order). The title is a TalkBack heading. [onDismiss]: the close button in the top-end corner, Material's standard icon button
 * (24 dp icon, 48 dp target, as Android's Safety Center issue cards and every other close in the app); null = it goes
 * only when its problem is fixed. Announced politely by TalkBack when it appears. No outer margin: a notice straight on
 * a page adds [NoticeDefaults.PageMargins].
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoticeCard(
    text: String?,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: NoticeTone = NoticeTone.Info,
    @DrawableRes icon: Int = tone.icon,
    action: NoticeAction? = null,
    secondary: List<NoticeAction> = emptyList(),
    choices: List<BannerChoice> = emptyList(),
    onDismiss: (() -> Unit)? = null,
    note: String? = null,
    /** What TalkBack says for [text] when its words read badly aloud ("1.5u" → "1.5 units"). */
    spokenText: String? = null,
    /** Room the buttons keep at the end (home's safety banner over the floating button); 0 = none. */
    actionsEndInset: Dp = 0.dp,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val error = tone == NoticeTone.Error
    val container = if (error) colors.errorContainer else LocalNoticeContainer.current ?: colors.surfaceContainerHigh
    val content = if (error) colors.onErrorContainer else colors.onSurface
    val support = if (error) colors.onErrorContainer else colors.onSurfaceVariant
    // the icon and text buttons: the accent for a tip, the error colour for a warning (never the informational blue)
    val accent =
        when (tone) {
            NoticeTone.Info -> colors.primary
            NoticeTone.Warning -> colors.error
            NoticeTone.Error -> colors.onErrorContainer
        }
    val buttons = action != null || secondary.isNotEmpty()
    // the first line stops short of the close button (its 48 dp target reaches 4 dp from the corner)
    val clear = if (onDismiss != null) DISMISS_CLEARANCE else 0.dp
    Card(
        modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = SettingsShape.CornerExtraLarge1,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Box {
            Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (buttons) 8.dp else 16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = accent)
                    Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        title?.let {
                            Text(
                                it,
                                Modifier.padding(end = clear).semantics { heading() },
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                        }
                        text?.let {
                            Text(
                                it,
                                Modifier
                                    .padding(end = if (title == null) clear else 0.dp)
                                    .semantics { spokenText?.let { contentDescription = it } },
                                style = MaterialTheme.typography.bodyMedium,
                                color = support,
                            )
                        }
                        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (error) content else colors.error) }
                        below()
                        if (choices.isNotEmpty()) Choices(choices)
                    }
                }
                if (buttons) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = 4.dp, end = actionsEndInset),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        secondary.forEach { TextAction(it, accent) }
                        action?.let { a ->
                            if (error) {
                                Button(
                                    onClick = a.onClick,
                                    enabled = a.enabled,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                                ) { Text(a.label) }
                            } else {
                                TextAction(a, accent)
                            }
                        }
                    }
                }
            }
            onDismiss?.let {
                IconButton(
                    onClick = it,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).testTag(NOTICE_DISMISS_TAG),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = support),
                ) {
                    Icon(painterResource(R.drawable.ds_ic_close), contentDescription = stringResource(R.string.ds_dismiss))
                }
            }
        }
    }
}

/** A text button in the tone's colour (greyed while disabled). */
@Composable
private fun TextAction(
    a: NoticeAction,
    color: Color,
) {
    TextButton(onClick = a.onClick, enabled = a.enabled, colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Text(a.label)
    }
}

/** Equal choices as pills filling the text's width, each with its service's app icon when it opens one. */
@Composable
private fun Choices(choices: List<BannerChoice>) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { c ->
            Button(
                onClick = c.onClick,
                modifier = Modifier.weight(1f),
                contentPadding =
                    if (c.service !=
                        null
                    ) {
                        PaddingValues(start = 6.dp, top = 4.dp, end = 16.dp, bottom = 4.dp)
                    } else {
                        ButtonDefaults.ContentPadding
                    },
            ) {
                if (c.service != null) {
                    ServiceIcon(c.service)
                    Text(c.label, Modifier.padding(start = 8.dp), maxLines = 1)
                } else {
                    Text(c.label, maxLines = 1)
                }
            }
        }
    }
}

/** How far the first line stays from the card's end: the close button's 48 dp target, 4 dp in, less the 16 dp padding. */
private val DISMISS_CLEARANCE = 36.dp
