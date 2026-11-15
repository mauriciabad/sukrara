// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.root

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.core.model.RootAccess
import app.mauri.sukrara.core.model.SystemAccessState
import app.mauri.sukrara.designsystem.banner.SettingsBannerCard
import app.mauri.sukrara.designsystem.errors.Trouble
import app.mauri.sukrara.designsystem.errors.UserErrors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/*
 * Root for Sukrara and its System UI module, as every place that needs them says it (the glucose notification's page,
 * the lock screen page, setup's root step): the one step that is missing and its fix, one shared card.
 */

/** Why the last root / LSPosed action failed: what LSPosed or `su` said, or a failure in plain words. */
sealed interface AccessProblem {
    data class Said(val text: String) : AccessProblem

    data class Failed(val why: Trouble) : AccessProblem
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
 * The one step the lock-screen widget is missing (Settings › Lock screen), only while it's on: the module's steps that
 * have a fix on the page (System UI not in its scope, a System UI restart). It needs no root of its own (the restart
 * asks for it); without LSPosed or the module the switch's summary says so, with no card: most phones never have them.
 */
fun lockAccessStep(
    access: SystemAccessState,
    on: Boolean,
): AccessStep? =
    when {
        !on -> null
        access.module == ModuleState.NoScope -> AccessStep.NoScope
        access.module == ModuleState.Restart -> AccessStep.Restart
        access.module == ModuleState.Update -> AccessStep.Update
        else -> null
    }

/** The card for [step] (null: none) with its fix; [busy] = an action runs, [problem] = why the last one failed. */
@Composable
fun AccessStepBanner(
    step: AccessStep?,
    access: SystemAccessState,
    busy: Boolean,
    problem: AccessProblem?,
    checkRoot: () -> Unit,
    requestScope: () -> Unit,
    restart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkAgain = R.string.ds_access_root_action to checkRoot
    val shown: Triple<Int, String, Pair<Int, () -> Unit>?> =
        when (step ?: return) {
            AccessStep.Asking -> {
                Triple(R.string.ds_access_asking_title, stringResource(R.string.ds_access_asking_text), null)
            }

            AccessStep.NoRoot -> {
                Triple(R.string.ds_access_root_title, stringResource(R.string.ds_access_root_text), checkAgain)
            }

            AccessStep.NotAllowed -> {
                Triple(R.string.ds_access_not_allowed_title, stringResource(R.string.ds_access_not_allowed_text), checkAgain)
            }

            AccessStep.Refused -> {
                Triple(R.string.ds_access_refused_title, stringResource(R.string.ds_access_refused_text), checkAgain)
            }

            AccessStep.NoAnswer -> {
                Triple(R.string.ds_access_no_answer_title, stringResource(R.string.ds_access_no_answer_text), checkAgain)
            }

            AccessStep.ModuleOff -> {
                Triple(R.string.ds_access_module_title, stringResource(R.string.ds_access_module_text), null)
            }

            AccessStep.NoScope -> {
                Triple(
                    R.string.ds_access_scope_title,
                    stringResource(R.string.ds_access_scope_text, access.framework ?: "LSPosed"),
                    R.string.ds_access_scope_action to requestScope,
                )
            }

            AccessStep.Restart -> {
                Triple(
                    R.string.ds_access_restart_title,
                    stringResource(R.string.ds_access_restart_text),
                    R.string.ds_access_restart_title to restart,
                )
            }

            AccessStep.Update -> {
                Triple(
                    R.string.ds_access_restart_title,
                    stringResource(R.string.ds_access_restart_text_updated),
                    R.string.ds_access_restart_title to restart,
                )
            }
        }
    val asking = shown.first == R.string.ds_access_asking_title
    SettingsBannerCard(
        if (asking) R.drawable.ds_ic_hourglass_top else R.drawable.ds_ic_warning,
        stringResource(shown.first),
        shown.second,
        // the card grows and shrinks with its text (asking → the answer) instead of jumping
        modifier.animateContentSize(),
        action = shown.third?.let { (label, _) -> stringResource(if (busy) R.string.ds_access_restarting else label) },
        onAction = { shown.third?.second?.invoke() },
        actionEnabled = !busy,
        // waiting on the root manager (its prompt, up to 30 s): something visibly running
        below = { if (asking) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp)) },
        note =
            problem?.let {
                when (it) {
                    is AccessProblem.Said -> stringResource(R.string.ds_access_failed, it.text)
                    is AccessProblem.Failed -> stringResource(it.why.text)
                }
            },
    )
}

/**
 * Runs one root / LSPosed action at a time (Check again, Add System UI, Restart System UI): [busy] while it runs,
 * [problem] = why the last one failed (the access card shows it), cleared when the next one starts.
 */
class AccessRunner(
    private val scope: CoroutineScope,
) {
    val busy = MutableStateFlow(false)
    val problem = MutableStateFlow<AccessProblem?>(null)

    /** [action] answers null when it worked, else what LSPosed or `su` said. */
    fun run(action: suspend () -> String?) {
        if (busy.value) return
        busy.value = true
        problem.value = null
        scope.launch {
            problem.value =
                runCatching { action()?.let(AccessProblem::Said) }.getOrElse {
                    Log.w("Sukrara", "root action: ${it.javaClass.simpleName}")
                    AccessProblem.Failed(UserErrors.of(it))
                }
            busy.value = false
        }
    }
}
