// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.banner

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.ServiceIcon
import app.mauri.sukrara.designsystem.motion.Reveal
import app.mauri.sukrara.designsystem.motion.Swap
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.widget.banner.BannerTitleHeader
import com.android.settingslib.spa.widget.banner.SettingsBanner
import com.android.settingslib.spa.widget.banner.SettingsBannerContent
import com.android.settingslib.spa.widget.ui.SettingsBody

/**
 * One of a banner's equal choices (*LibreView* / *Tidepool*, *Turn on* / *Not now*): a pill, with the [service]'s own
 * app icon ([ServiceIcon], the size AOSP Settings draws app icons at) when it opens a service; null = text only.
 */
data class BannerChoice(
    val label: String,
    val service: Service?,
    val onClick: () -> Unit,
)

/** How much a banner matters: [Safety] = alerts or readings may not work (error colours, filled action); [Info] = a tip. */
enum class BannerSeverity { Safety, Info }

/**
 * One non-glucose banner (set up X, permission missing). [id] keys the cross-fade
 * from one banner to the next; [onDismiss] null = it can't be dismissed (it goes away only when fixed through
 * [action], e.g. a missing permission that makes alerts unreliable) — so every banner has an action or a dismiss.
 */
data class Banner(
    val id: String,
    @param:DrawableRes val icon: Int,
    val title: String,
    val text: String,
    val action: String?,
    val onAction: () -> Unit,
    val onDismiss: (() -> Unit)?,
    val severity: BannerSeverity = BannerSeverity.Info,
    /** A second, quieter way out next to the action (*Turn off*), a text button before it. */
    val secondary: Pair<String, () -> Unit>? = null,
    /** Equal choices side by side instead of one action (each opens its own page). */
    val choices: List<BannerChoice> = emptyList(),
) {
    init {
        require(action != null || onDismiss != null || choices.isNotEmpty()) {
            "banner $id has no way out: give it an action, choices or a dismiss"
        }
    }
}

/**
 * The single banner place above the content: shows at most one banner — the caller passes the highest-priority one
 * (or null) so banners never stack between the person and their glucose. Changes fade/expand (reduced motion: snap)
 * and are announced politely by TalkBack.
 */
@Composable
fun BannerSlot(
    banner: Banner?,
    modifier: Modifier = Modifier,
    /** A tip's card colour: SPA's banner sits on [androidx.compose.material3.ColorScheme.surface] on a settings page. */
    tipContainer: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    // keep the last banner while it animates out
    var shown by remember { mutableStateOf(banner) }
    if (banner != null) shown = banner
    Reveal(banner != null, modifier) {
        shown?.let { b ->
            Swap(b.id) { _ -> if (b.severity == BannerSeverity.Safety) SafetyCard(b) else TipCard(b, tipContainer) }
        }
    }
}

/** A tip in the banner slot: [SettingsBannerCard], with equal [Banner.choices] as pills under the text. */
@Composable
private fun TipCard(
    b: Banner,
    container: Color,
) {
    SettingsBannerCard(
        b.icon,
        b.title,
        b.text,
        action = b.action,
        onAction = b.onAction,
        onDismiss = b.onDismiss,
        secondary = b.secondary,
        containerColor = container,
    ) {
        if (b.choices.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 40.dp, top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                b.choices.forEach { c ->
                    Button(
                        onClick = c.onClick,
                        modifier = Modifier.weight(1f),
                        contentPadding = choicePadding(c),
                    ) {
                        ChoiceLabel(c)
                    }
                }
            }
        }
    }
}

/** A choice pill's padding: tight at the start for a service's icon, the button's own for text alone. */
private fun choicePadding(c: BannerChoice) =
    if (c.service != null) PaddingValues(start = 6.dp, top = 4.dp, end = 16.dp, bottom = 4.dp) else ButtonDefaults.ContentPadding

/** A choice pill's content: the service's icon and its name, or the text alone. */
@Composable
private fun ChoiceLabel(c: BannerChoice) {
    if (c.service != null) {
        ServiceIcon(c.service)
        Text(c.label, Modifier.padding(start = 8.dp), maxLines = 1)
    } else {
        Text(c.label, maxLines = 1)
    }
}

/**
 * Something to set up or fix, SPA's SettingsBanner (the stock Settings app's), expressive layout: the icon in [tint]
 * (the accent colour; the error colour for a warning), the title with the round dismiss button when [onDismiss] isn't
 * null (no [title] or no [text]: a state in one sentence), the text, an optional [note] in the error colour (why the last try failed), [below] (extra content), then the
 * actions as text buttons at the end ([secondary] first). Announced politely by TalkBack when it appears.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsBannerCard(
    @DrawableRes icon: Int,
    title: String?,
    text: String?,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    actionEnabled: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    secondary: Pair<String, () -> Unit>? = null,
    note: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    below: @Composable () -> Unit = {},
) {
    Box(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        SettingsBanner {
            SettingsBannerContent(containerColor = containerColor) {
                Column(
                    Modifier.padding(
                        start = SettingsDimension.paddingLarge,
                        end = SettingsDimension.paddingLarge,
                        top = SettingsDimension.paddingLarge,
                        bottom = SettingsDimension.paddingSmall,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(SettingsDimension.itemIconSize),
                            tint = tint,
                        )
                        Column(
                            Modifier.padding(start = SettingsDimension.paddingLarge),
                            verticalArrangement = Arrangement.spacedBy(SettingsDimension.itemPaddingAround),
                        ) {
                            if (title != null) BannerTitleHeader(title, onDismiss)
                            text?.let { SettingsBody(it) }
                            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    below()
                    if (action != null || secondary != null) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SettingsDimension.itemPaddingEnd, Alignment.End),
                        ) {
                            secondary?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label, color = tint) } }
                            action?.let { label ->
                                TextButton(onClick = onAction, enabled = actionEnabled) {
                                    Text(label, color = if (actionEnabled) tint else Color.Unspecified)
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(SettingsDimension.itemPaddingAround))
                    }
                }
            }
        }
    }
}

/** Alerts or readings may not work: the error card, its action a filled button clear of the floating button. */
@Composable
private fun SafetyCard(b: Banner) {
    val colors = MaterialTheme.colorScheme
    val content = colors.onErrorContainer
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer, contentColor = content),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // a safety banner's icon is never the informational primary blue
                Icon(painterResource(b.icon), contentDescription = null, tint = content, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).padding(top = 10.dp)) {
                    Text(b.title, style = MaterialTheme.typography.titleMedium)
                    Text(b.text, style = MaterialTheme.typography.bodyMedium, color = content)
                }
                b.onDismiss?.let {
                    IconButton(onClick = it) {
                        Icon(painterResource(R.drawable.ds_ic_close), contentDescription = stringResource(R.string.ds_dismiss))
                    }
                }
            }
            if (b.choices.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 40.dp, end = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    b.choices.forEach { c ->
                        FilledTonalButton(
                            onClick = c.onClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = choicePadding(c),
                        ) {
                            ChoiceLabel(c)
                        }
                    }
                }
            }
            b.action?.let { label ->
                // the action starts at the text's edge and stops short of the floating button in the bottom-end corner
                // (at a large font it wraps rather than slide under it)
                Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = FAB_CLEARANCE, top = 8.dp)) {
                    Button(
                        onClick = b.onAction,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

/** A floating button (56 dp) inside the screen edge (16 dp), seen from the card's inner edge (16 dp in): 56 + 8. */
private val FAB_CLEARANCE = 64.dp
