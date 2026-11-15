// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.banner

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.motion.Reveal
import app.mauri.sukrara.designsystem.motion.Swap

/**
 * One non-glucose banner (set up X, permission missing), drawn as a [NoticeCard]. [id] keys the cross-fade from one
 * banner to the next; [onDismiss] null = it can't be dismissed (it goes away only when fixed through [action], e.g. a
 * missing permission that makes alerts unreliable) — so every banner has an action, choices or a dismiss.
 */
data class Banner(
    val id: String,
    @param:DrawableRes val icon: Int,
    val title: String,
    val text: String,
    val action: String?,
    val onAction: () -> Unit,
    val onDismiss: (() -> Unit)?,
    val tone: NoticeTone = NoticeTone.Info,
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
 * The single banner place on a page: shows at most one banner — the caller passes the highest-priority one (or null)
 * so banners never stack between the person and their glucose. Changes fade/expand (reduced motion: snap) and are
 * announced politely by TalkBack.
 */
@Composable
fun BannerSlot(
    banner: Banner?,
    modifier: Modifier = Modifier,
    /** Room its buttons keep at the end ([NoticeCard]'s actionsEndInset). */
    actionsEndInset: Dp = 0.dp,
) {
    // keep the last banner while it animates out
    var shown by remember { mutableStateOf(banner) }
    if (banner != null) shown = banner
    Reveal(banner != null, modifier) {
        shown?.let { b ->
            Swap(b.id) { _ ->
                NoticeCard(
                    b.text,
                    Modifier.padding(NoticeDefaults.PageMargins),
                    title = b.title,
                    tone = b.tone,
                    icon = b.icon,
                    action = b.action?.let { NoticeAction(it, b.onAction) },
                    secondary = listOfNotNull(b.secondary?.let { (label, onClick) -> NoticeAction(label, onClick) }),
                    choices = b.choices,
                    onDismiss = b.onDismiss,
                    actionsEndInset = actionsEndInset,
                )
            }
        }
    }
}
