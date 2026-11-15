// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.statusbar

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.core.data.settings.HookPosition
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.designsystem.banner.NoticeDefaults
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.ChoiceDialog
import app.mauri.sukrara.designsystem.root.AccessProblem
import app.mauri.sukrara.designsystem.root.AccessStepBanner
import app.mauri.sukrara.designsystem.root.accessStep
import app.mauri.sukrara.feature.settings.R

/** A placement's name. */
@StringRes
fun placementLabel(pos: String): Int =
    when (pos) {
        HookPosition.AFTER_CLOCK -> R.string.st_root_pos_after_clock
        HookPosition.BEFORE_CLOCK -> R.string.st_root_pos_before_clock
        HookPosition.LEFT_OF_ICONS -> R.string.st_root_pos_left_of_icons
        HookPosition.SLOT_PREFIX -> R.string.st_root_pos_with_icons
        "${HookPosition.SLOT_PREFIX}vpn" -> R.string.st_root_pos_after_vpn
        "${HookPosition.SLOT_PREFIX}bluetooth" -> R.string.st_root_pos_after_bluetooth
        HookPosition.DEFAULT -> R.string.st_root_pos_before_mobile
        "${HookPosition.SLOT_PREFIX}mobile" -> R.string.st_root_pos_after_mobile
        else -> R.string.st_root_pos_after_wifi
    }

/**
 * The root choice's missing step with its fix (the shared card, as setup's root step and the lock screen page show it):
 * root for Sukrara first, then the LSPosed module.
 */
@Composable
fun RootAccessCard(
    access: SystemAccessState,
    busy: Boolean,
    problem: AccessProblem?,
    actions: NotificationPageActions,
    modifier: Modifier = Modifier,
) = AccessStepBanner(
    accessStep(access, true),
    access,
    busy,
    problem,
    actions.checkRoot,
    actions.requestScope,
    actions.restart,
    modifier.padding(NoticeDefaults.PageMargins),
)

/** The placement choices; without the module the clock placements are off and the slot ones say what shows meanwhile. */
@Composable
fun PlacementDialog(
    current: String,
    module: Boolean,
    onClose: () -> Unit,
    onPick: (String) -> Unit,
) {
    val options =
        HookPosition.ALL.map { pos ->
            val full = module || !HookPosition.needsModule(pos)
            val icons = !full && HookPosition.isSystemIcons(pos)
            Choice(
                stringResource(placementLabel(pos)),
                pos,
                sub =
                    if (full) {
                        null
                    } else {
                        stringResource(if (icons) R.string.st_root_pos_needs_module_icons else R.string.st_root_module_needed)
                    },
                enabled = full || icons,
            )
        }
    ChoiceDialog(stringResource(R.string.st_root_placement), options, current, onClose, onChoose = onPick)
}
