// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.statusbar

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.data.settings.HookPosition
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.designsystem.banner.SettingsBannerCard
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.ChoiceDialog
import app.mauri.sukrara.designsystem.errors.Trouble
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.core.designsystem.R as DsR

/** The root status bar's settings (`statusbar.hook`, `hookPosition`, `hookColor`, `hideNotifIcon`). */
data class RootSettings(
    val on: Boolean,
    val position: String,
    val color: Boolean,
    val hideIcon: Boolean,
)

/** Why the last root / LSPosed action failed: what LSPosed or `su` said, or a failure in plain words. */
sealed interface AccessProblem {
    data class Said(val text: String) : AccessProblem

    data class Failed(val why: Trouble) : AccessProblem
}

/** What the root section can do; [busy] = a root / LSPosed action is running, [problem] = why the last one failed. */
class RootActions(
    val setOn: (Boolean) -> Unit,
    val setPosition: (String) -> Unit,
    val setColor: (Boolean) -> Unit,
    val setHideIcon: (Boolean) -> Unit,
    val checkRoot: () -> Unit,
    val requestScope: () -> Unit,
    val restart: () -> Unit,
)

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
 * "With root": *Value in the status bar* (its summary says what's missing: root, or the module for this placement),
 * *Colour by range*, *Placement* (dialog; placements that need the module say so, and stay choosable when they show as
 * system icons meanwhile), *Hide Sukrara's notification icon*. Above them the one step that's missing ([AccessBanner]).
 */
@Composable
fun RootStatusBarSection(
    s: RootSettings,
    access: SystemAccessState,
    busy: Boolean,
    problem: AccessProblem?,
    choosing: Boolean,
    onChoosing: (Boolean) -> Unit,
    actions: RootActions,
) {
    val root = access.granted
    val module = access.module.active
    val hookOn = root && s.on
    // root that works now: the one confirmation the person waited for (a refusal is said by the card, not a buzz)
    val haptics = rememberHaptics()
    var wasAsking by remember { mutableStateOf(access.asking) }
    LaunchedEffect(access.asking, root) {
        if (wasAsking && !access.asking && root) haptics.confirm()
        wasAsking = access.asking
    }
    val placement = stringResource(placementLabel(s.position))
    val waiting = HookPosition.needsModule(s.position) && !module
    val meanwhile = HookPosition.isSystemIcons(s.position)
    // why the rows below the switch are off: the value is off, or it is on and waits for root
    val off = stringResource(if (s.on) R.string.st_root_unavailable_no_root else R.string.st_root_unavailable)
    val needs = stringResource(R.string.st_root_module_needed)
    val placementText =
        when {
            !waiting -> placement
            meanwhile -> stringResource(R.string.st_root_needs_module_icons, placement)
            else -> stringResource(R.string.st_root_needs_module, placement)
        }
    AccessBanner(access, s.on, busy, problem, actions)
    SettingsGroup(title = stringResource(R.string.st_root_group)) {
        SwitchRow(
            stringResource(R.string.st_root_value),
            s.on,
            actions.setOn,
            summary =
                when {
                    access.asking && !root -> stringResource(R.string.st_root_value_asking)
                    !s.on -> stringResource(if (root) R.string.st_root_value_off else R.string.st_root_value_off_needs_root)
                    root -> placementText
                    else -> stringResource(R.string.st_root_value_waiting)
                },
            // always switchable: turning it on is the request for root (it can't wait for root it never asked for),
            // and it can always be turned off, root or not
            icon = DsR.drawable.ds_ic_smartphone,
        )
        SwitchRow(
            stringResource(R.string.st_root_colour),
            s.color,
            actions.setColor,
            summary =
                if (!hookOn) {
                    off
                } else if (!module) {
                    needs
                } else {
                    null
                },
            enabled = hookOn && module,
            icon = DsR.drawable.ds_ic_palette,
        )
        ValueRow(stringResource(R.string.st_root_placement), if (hookOn) placementText else off, {
            onChoosing(true)
        }, enabled = hookOn, icon = DsR.drawable.ds_ic_align_horizontal_left)
        SwitchRow(
            stringResource(R.string.st_root_hide_icon),
            s.hideIcon,
            actions.setHideIcon,
            summary =
                when {
                    !hookOn -> off
                    !module -> needs
                    else -> stringResource(R.string.st_root_hide_icon_summary)
                },
            enabled = hookOn && module,
            icon = DsR.drawable.ds_ic_visibility_off,
        )
    }
    FooterText(stringResource(R.string.st_root_footer))
    if (choosing) PlacementDialog(s.position, module, { onChoosing(false) }, actions.setPosition)
}

/** The placement choices; without the module the clock placements are off and the slot ones say what shows meanwhile. */
@Composable
private fun PlacementDialog(
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

/** The one step a root feature is missing ([accessStep]): root for Sukrara first, then the LSPosed module. */
enum class AccessStep {
    /** `su` is running: the root manager's prompt may be showing. */
    Asking,

    /** No `su` for Sukrara and no sign of root on the phone. */
    NoRoot,

    /** No `su` for Sukrara, but the phone has root (LSPosed, the module, a `su` file): KernelSU / APatch haven't allowed it. */
    NotAllowed,

    /** `su` refused. */
    Refused,

    /** `su` didn't answer in time. */
    NoAnswer,

    ModuleOff,
    NoScope,
    Restart,
    Update,
}

/**
 * Which step the card shows, only while the value is on: the root request running, why root isn't there (no root,
 * not allowed, refused, no answer), then the module's steps once root works. Not asked yet ([RootAccess.Unknown]):
 * nothing, the request is on its way (the page asks on resume while the value is on).
 */
fun accessStep(
    access: SystemAccessState,
    valueOn: Boolean,
): AccessStep? =
    when {
        !valueOn -> null
        access.asking && !access.granted -> AccessStep.Asking
        access.root == RootAccess.Missing -> if (access.rootSeen) AccessStep.NotAllowed else AccessStep.NoRoot
        access.root == RootAccess.Refused -> AccessStep.Refused
        access.root == RootAccess.NoAnswer -> AccessStep.NoAnswer
        !access.granted -> null
        access.module == ModuleState.Missing -> AccessStep.ModuleOff
        access.module == ModuleState.NoScope -> AccessStep.NoScope
        access.module == ModuleState.Restart -> AccessStep.Restart
        access.module == ModuleState.Update -> AccessStep.Update
        else -> null
    }

/**
 * The one step a root feature is missing, with its fix: root for Sukrara (how to allow it, *Check again*), then the
 * LSPosed module (enable it; add System UI to its scope; restart System UI). Only while the value is on. SPA's
 * SettingsBanner, the reason the last try failed under its text.
 */
@Composable
fun AccessBanner(
    access: SystemAccessState,
    valueOn: Boolean,
    busy: Boolean,
    problem: AccessProblem?,
    actions: RootActions,
    modifier: Modifier = Modifier,
) {
    val checkAgain = R.string.st_access_root_action to actions.checkRoot
    val step: Triple<Int, String, Pair<Int, () -> Unit>?> =
        when (accessStep(access, valueOn) ?: return) {
            AccessStep.Asking -> {
                Triple(R.string.st_access_asking_title, stringResource(R.string.st_access_asking_text), null)
            }

            AccessStep.NoRoot -> {
                Triple(R.string.st_access_root_title, stringResource(R.string.st_access_root_text), checkAgain)
            }

            AccessStep.NotAllowed -> {
                Triple(R.string.st_access_not_allowed_title, stringResource(R.string.st_access_not_allowed_text), checkAgain)
            }

            AccessStep.Refused -> {
                Triple(R.string.st_access_refused_title, stringResource(R.string.st_access_refused_text), checkAgain)
            }

            AccessStep.NoAnswer -> {
                Triple(R.string.st_access_no_answer_title, stringResource(R.string.st_access_no_answer_text), checkAgain)
            }

            AccessStep.ModuleOff -> {
                Triple(R.string.st_access_module_title, stringResource(R.string.st_access_module_text), null)
            }

            AccessStep.NoScope -> {
                Triple(
                    R.string.st_access_scope_title,
                    stringResource(R.string.st_access_scope_text, access.framework ?: "LSPosed"),
                    R.string.st_access_scope_action to actions.requestScope,
                )
            }

            AccessStep.Restart -> {
                Triple(
                    R.string.st_access_restart_title,
                    stringResource(R.string.st_access_restart_text),
                    R.string.st_access_restart_title to actions.restart,
                )
            }

            AccessStep.Update -> {
                Triple(
                    R.string.st_access_restart_title,
                    stringResource(R.string.st_access_restart_text_updated),
                    R.string.st_access_restart_title to actions.restart,
                )
            }
        }
    val asking = step.first == R.string.st_access_asking_title
    SettingsBannerCard(
        if (asking) DsR.drawable.ds_ic_hourglass_top else DsR.drawable.ds_ic_warning,
        stringResource(step.first),
        step.second,
        // the card grows and shrinks with its text (asking → the answer) instead of jumping
        modifier.animateContentSize(),
        action = step.third?.let { (label, _) -> stringResource(if (busy) R.string.st_access_restarting else label) },
        onAction = { step.third?.second?.invoke() },
        actionEnabled = !busy,
        // waiting on the root manager (its prompt, up to 30 s): something visibly running
        below = { if (asking) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp)) },
        note =
            problem?.let {
                when (it) {
                    is AccessProblem.Said -> stringResource(R.string.st_access_failed, it.text)
                    is AccessProblem.Failed -> stringResource(it.why.text)
                }
            },
    )
}
