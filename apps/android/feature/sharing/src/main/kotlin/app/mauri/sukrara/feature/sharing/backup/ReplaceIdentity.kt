// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.backup

import android.icu.text.ListFormatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.feature.sharing.OwnerSharing
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.feature.sharing.SetupModule
import app.mauri.sukrara.feature.sharing.restore.RestoreForm
import app.mauri.sukrara.feature.sharing.setup.NoteCard
import app.mauri.sukrara.feature.sharing.setup.RestoreGate
import app.mauri.sukrara.feature.sharing.setup.RestoreUi
import app.mauri.sukrara.feature.sharing.setup.StepButtons
import app.mauri.sukrara.protocol.event.Avatar
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.share.owner.usecase.RestoreIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/** Where Backup › Replace this identity is: the choice (what followers lose, two ways), or the recovery code form. */
enum class ReplaceStep { Choose, Restore }

/** *Start fresh* over the working identity. */
enum class FreshUi { Idle, Busy, Failed, Done }

/** The page's state; [gate] = the restore gate (this phone's own sensor is live), re-read with every reading. */
data class ReplaceUi(
    val step: ReplaceStep = ReplaceStep.Choose,
    val restore: RestoreUi = RestoreUi.Idle,
    val fresh: FreshUi = FreshUi.Idle,
    val gate: RestoreGate = RestoreGate.evaluate(ownSensorLive = false),
) {
    val busy: Boolean get() = restore == RestoreUi.Busy || fresh == FreshUi.Busy

    /** Replaced: the page says what happened and how to go on. */
    val done: Boolean get() = restore is RestoreUi.Restored || fresh == FreshUi.Done
}

/**
 * Backup › Replace this identity: a working identity replaced without reinstalling, by a restored one (its recovery code
 * or the password manager; through the same gate as setup) or a fresh one (how the wearer appears now). Both are
 * confirmed first, naming the followers who lose the readings; the work isn't cancelled by leaving the page.
 */
class ReplaceIdentityViewModel(
    private val sharing: OwnerSharing,
    private val hub: GlucoseHub,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<OwnerShareState> = sharing.state

    private val local = MutableStateFlow(ReplaceUi())

    val ui: StateFlow<ReplaceUi> =
        combine(local, hub.state) { u, glucose ->
            u.copy(gate = RestoreGate.evaluate(RestoreGate.ownSensorLive(glucose, clock.now())))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), local.value)

    fun chooseRestore() = local.update { it.copy(step = ReplaceStep.Restore, restore = RestoreUi.Idle) }

    /** Back: the restore form returns to the choice; on the choice, while working, or once replaced, [onLeave] runs. */
    fun back(onLeave: () -> Unit) {
        val u = local.value
        if (u.step == ReplaceStep.Restore && !u.busy && !u.done) {
            local.update { it.copy(step = ReplaceStep.Choose, restore = RestoreUi.Idle) }
        } else {
            onLeave()
        }
    }

    fun clearRestoreError() = local.update { if (it.restore is RestoreUi.Failed) it.copy(restore = RestoreUi.Idle) else it }

    /** The confirmed restore, only through an open gate (re-checked here: the screen may be older than the reading). */
    fun restore(
        code: String,
        password: String?,
    ) {
        if (local.value.busy || !RestoreGate.ownSensorLive(hub.state.value, clock.now())) return
        local.update { it.copy(restore = RestoreUi.Busy) }
        viewModelScope.launch {
            // leaving the page must not stop a replace halfway: the engine finishes it
            val result =
                withContext(NonCancellable) {
                    try {
                        sharing.restore(code.trim(), password?.takeIf { it.isNotEmpty() }, replace = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        RestoreIdentity.Result.RelaysUnreachable
                    }
                }
            local.update {
                it.copy(
                    restore =
                        if (result is RestoreIdentity.Result.Restored) {
                            RestoreUi.Restored(
                                result.followers,
                            )
                        } else {
                            RestoreUi.Failed(result)
                        },
                )
            }
        }
    }

    /** The confirmed *Start fresh*: a new identity with how the wearer appears now (a new picture if none was set). */
    fun startFresh() {
        if (local.value.busy) return
        val profile = (state.value as? OwnerShareState.Sharing)?.profile ?: Profile("", Avatar.random(SecureRandom()))
        local.update { it.copy(fresh = FreshUi.Busy) }
        viewModelScope.launch {
            val created =
                withContext(NonCancellable) {
                    try {
                        sharing.startFresh(profile, replace = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                }
            local.update { it.copy(fresh = if (created) FreshUi.Done else FreshUi.Failed) }
        }
    }

    companion object {
        private const val STOP_AFTER_MS = 5_000L

        fun factory(m: SetupModule) = viewModelFactory { initializer { ReplaceIdentityViewModel(m.sharing, m.hub, m.clock) } }
    }
}

/**
 * The page: what followers lose (named), *Back up first* while the identity exists nowhere else, then *Restore another
 * identity* (→ the recovery code form) or *Start fresh*; each ends in a confirmation that names the followers and is
 * armed only by *I understand they must be invited again*. Every step has a way out: Cancel / Back on the choice and
 * the form, Cancel in the confirmation; once replaced, *Invite someone* or *Done*.
 */
@Composable
fun ReplaceIdentityPage(
    viewModel: ReplaceIdentityViewModel,
    onClose: () -> Unit,
    onBackUp: () -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val u by viewModel.ui.collectAsStateWithLifecycle()
    // the form's Back returns to the choice; the choice's leaves the page (the app's own Back)
    BackHandler(enabled = u.step == ReplaceStep.Restore && !u.busy && !u.done) { viewModel.back(onClose) }
    val sharing = state as? OwnerShareState.Sharing
    val actions =
        remember(viewModel, onClose, onBackUp, onInvite) {
            ReplaceActions(
                chooseRestore = viewModel::chooseRestore,
                back = { viewModel.back(onClose) },
                restore = viewModel::restore,
                clearRestoreError = viewModel::clearRestoreError,
                startFresh = viewModel::startFresh,
                close = onClose,
                backUp = onBackUp,
                invite = onInvite,
            )
        }
    ReplaceIdentityScreen(sharing, u, actions, modifier)
}

/** What the replace screen can do; a plain holder so screenshot tests render every state without a view model. */
data class ReplaceActions(
    val chooseRestore: () -> Unit = {},
    val back: () -> Unit = {},
    val restore: (code: String, password: String?) -> Unit = { _, _ -> },
    val clearRestoreError: () -> Unit = {},
    val startFresh: () -> Unit = {},
    val close: () -> Unit = {},
    val backUp: () -> Unit = {},
    val invite: () -> Unit = {},
)

/** [sharing] = the identity being replaced (null once it is gone or while loading: the done or busy states show). */
@Composable
fun ReplaceIdentityScreen(
    sharing: OwnerShareState.Sharing?,
    u: ReplaceUi,
    actions: ReplaceActions,
    modifier: Modifier = Modifier,
) {
    // the confirmed restore's code waits here (never saved state) until the confirmation is answered
    var pending by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var confirmingFresh by rememberSaveable { mutableStateOf(false) }
    // the followers named before the replace (the state shows the new identity's afterwards)
    val followers = remember(sharing?.followers) { sharing?.followers?.filter { it.active }?.map { it.name } }
    val someone = stringResource(R.string.sh_join_someone)
    val names = remember(followers) { followers?.map { it.ifBlank { someone } }.orEmpty() }
    // the settings group brings its own margins; everything else gets the page's
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            u.done -> Padded { Done(u, actions) }
            u.step == ReplaceStep.Restore -> Padded { RestoreStep(u, actions) { code, password -> pending = code to password } }
            else -> Choose(sharing, names, u, actions) { confirmingFresh = true }
        }
    }
    pending?.let { (code, password) ->
        ReplaceConfirmDialog(
            title = stringResource(R.string.sh_replace_confirm_title_restore),
            confirm = stringResource(R.string.sh_replace_confirm_restore),
            names = names,
            backedUp = sharing?.backedUp != false,
            onConfirm = { actions.restore(code, password) },
            onDismiss = { pending = null },
        )
    }
    if (confirmingFresh) {
        ReplaceConfirmDialog(
            title = stringResource(R.string.sh_replace_confirm_title_fresh),
            confirm = stringResource(R.string.sh_replace_fresh),
            names = names,
            backedUp = sharing?.backedUp != false,
            onConfirm = actions.startFresh,
            onDismiss = { confirmingFresh = false },
        )
    }
}

/** The consequences, named, and the two ways (or Cancel). */
@Composable
private fun ColumnScope.Choose(
    sharing: OwnerShareState.Sharing?,
    names: List<String>,
    u: ReplaceUi,
    actions: ReplaceActions,
    onFresh: () -> Unit,
) {
    Padded { Consequences(sharing, names, actions) }
    SettingsGroup {
        NavigationRow(
            title = stringResource(R.string.sh_replace_restore),
            summary = stringResource(R.string.sh_replace_restore_summary),
            enabled = sharing != null && !u.busy,
            onClick = actions.chooseRestore,
        )
        NavigationRow(
            title = stringResource(R.string.sh_replace_fresh),
            summary = stringResource(R.string.sh_replace_fresh_summary),
            enabled = sharing != null && !u.busy,
            onClick = onFresh,
        )
    }
    Padded {
        when (u.fresh) {
            FreshUi.Busy -> Working(stringResource(R.string.sh_replace_fresh_busy))
            FreshUi.Failed -> NoteCard(stringResource(R.string.sh_replace_fresh_failed), error = true)
            else -> Unit
        }
        StepButtons(null, {}, secondary = listOf(stringResource(R.string.sh_setup_cancel) to actions.close))
    }
}

@Composable
private fun Padded(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** What followers lose, named; *Back up first* while the identity exists nowhere else. */
@Composable
private fun ColumnScope.Consequences(
    sharing: OwnerShareState.Sharing?,
    names: List<String>,
    actions: ReplaceActions,
) {
    Text(stringResource(R.string.sh_replace_intro), style = MaterialTheme.typography.bodyLarge)
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sh_replace_lose_title), style = MaterialTheme.typography.titleMedium)
            Text(lossText(names), style = MaterialTheme.typography.bodyMedium)
            if (names.isNotEmpty()) Text(stringResource(R.string.sh_replace_lose_invite), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.sh_replace_lose_pending), style = MaterialTheme.typography.bodyMedium)
            if (sharing?.backedUp == false) {
                Text(stringResource(R.string.sh_replace_not_backed_up), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = actions.backUp, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sh_replace_back_up_first))
                }
            }
        }
    }
}

/** The recovery code form, its action confirmed first; Back returns to the choice. */
@Composable
private fun ColumnScope.RestoreStep(
    u: ReplaceUi,
    actions: ReplaceActions,
    onRestore: (String, String?) -> Unit,
) {
    RestoreForm(
        gate = u.gate,
        restore = u.restore,
        busy = u.busy,
        onRestore = onRestore,
        onClearError = actions.clearRestoreError,
        action = stringResource(R.string.sh_replace_restore_action),
    )
    if (!u.busy) StepButtons(null, {}, secondary = listOf(stringResource(R.string.sh_replace_back) to actions.back))
}

/** Replaced: what happened, *Invite someone* after a fresh start, *Done*. */
@Composable
private fun ColumnScope.Done(
    u: ReplaceUi,
    actions: ReplaceActions,
) {
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { haptics.confirm() }
    val restored = u.restore as? RestoreUi.Restored
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        NoteCard(
            if (restored != null) {
                stringResource(R.string.sh_replace_done_restore, restored.followers)
            } else {
                stringResource(R.string.sh_replace_done_fresh)
            },
        )
    }
    StepButtons(
        if (restored == null) stringResource(R.string.sh_replace_invite) else stringResource(R.string.sh_replace_done),
        onPrimary = if (restored == null) actions.invite else actions.close,
        secondary = if (restored == null) listOf(stringResource(R.string.sh_replace_done) to actions.close) else emptyList(),
    )
}

@Composable
private fun Working(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "Ana, Bob and the Mac stop getting your glucose…" (the platform's list format), or that no one follows it. */
@Composable
private fun lossText(names: List<String>): String =
    if (names.isEmpty()) {
        stringResource(R.string.sh_replace_lose_none)
    } else {
        stringResource(R.string.sh_replace_lose_named, ListFormatter.getInstance().format(names))
    }

/**
 * The last step: who loses the readings (named), that each needs a new invite, a not-backed-up identity is gone for
 * good; [confirm] is armed only by the *I understand* checkbox. Cancel, a tap outside or Back changes nothing.
 */
@Composable
internal fun ReplaceConfirmDialog(
    title: String,
    confirm: String,
    names: List<String>,
    backedUp: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var understood by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(lossText(names))
                if (names.isNotEmpty()) Text(stringResource(R.string.sh_replace_lose_invite))
                if (!backedUp) Text(stringResource(R.string.sh_replace_not_backed_up), color = MaterialTheme.colorScheme.error)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = understood, role = Role.Checkbox, onValueChange = {
                                understood = it
                                haptics.toggle(it)
                            }),
                ) {
                    Checkbox(checked = understood, onCheckedChange = null)
                    Text(stringResource(R.string.sh_replace_confirm_check), modifier = Modifier.padding(start = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = understood,
                onClick = {
                    haptics.confirm()
                    onDismiss()
                    onConfirm()
                },
            ) { Text(confirm, color = if (understood) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sh_setup_cancel)) } },
    )
}
