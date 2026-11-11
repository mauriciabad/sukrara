// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.DndStatus
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion

/**
 * [app.mauri.sukrara.core.model.AlertRequirement.DoNotDisturb], the same on setup's permission step and Settings ›
 * Alert reliability (#184): where Sukrara stands, as a plain statement, never a fault (*Sukrara doesn't alert in Do Not
 * Disturb* / *Sukrara alerts in Do Not Disturb*), what that means, what a mode on now holds back, and one button that
 * changes it either way ([onChange] gets the new choice). The statement is a polite live region, so TalkBack hears it
 * change when the person comes back from Android's grant.
 */
@Composable
fun DndChoice(
    status: DndStatus,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedContent(status.through, transitionSpec = { Motion.fadeThrough(reduced) }, label = "dnd-status") { through ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(if (through) R.drawable.ds_ic_notifications_active else R.drawable.ds_ic_bedtime),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    )
                    Text(
                        stringResource(if (through) R.string.ds_dnd_through else R.string.ds_dnd_respected),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    stringResource(if (through) R.string.ds_dnd_through_detail else R.string.ds_dnd_respected_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val held =
            when (status.heldNow) {
                DndStatus.Held.None -> null
                DndStatus.Held.Some -> R.string.ds_dnd_held_some
                DndStatus.Held.All -> R.string.ds_dnd_held_all
            }
        if (held != null) {
            Text(
                stringResource(held),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = {
                haptics.confirm()
                onChange(!status.through)
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag("dnd-choice"),
        ) { Text(stringResource(if (status.through) R.string.ds_dnd_hold_back else R.string.ds_dnd_let_through)) }
    }
}
