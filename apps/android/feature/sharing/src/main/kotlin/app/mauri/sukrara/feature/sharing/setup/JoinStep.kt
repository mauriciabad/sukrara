// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.avatar.AvatarImage
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.feature.sharing.PairingCodeCard
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.protocol.follow.FollowStatus
import app.mauri.sukrara.protocol.invite.Invite
import app.mauri.sukrara.share.follower.FollowState
import app.mauri.sukrara.share.follower.JoinResult

/**
 * The follower's join step: paste the wearer's invite link (an invite opened on this phone fills it in and is looked up
 * by itself; [ScanInvite] reads it from their QR code, live or from a picture), with the name the wearer will see;
 * *Join* looks it up and shows who is inviting ([InviteConfirm]: *Follow* / *Cancel*, nothing sent before *Follow*);
 * then "waiting for Ana" with the 4-digit code the wearer may ask for (a *Pair nearby* join shows
 * the 6-digit pairing code to compare instead, [PairingCodeCard]; *Different code* ends it). A follow that ended
 * (declined, the invite used by another phone, access removed) says so and asks for a new link here. Back returns to the
 * role (this phone wears the sensor after all).
 */
@Composable
internal fun ColumnScope.JoinStep(
    u: SetupUi,
    actions: SetupActions,
) {
    val follow = u.follow
    StepHeader(stringResource(R.string.sh_join_title), stringResource(R.string.sh_join_body))
    if (follow is FollowState.Following && follow.status in WAITING) {
        val name = follow.wearer?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.sh_join_someone)
        val pairing = follow.pairing
        if (pairing != null) {
            // Pair nearby: both phones show one code; nothing from the wearer is used until the person confirms it
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarImage(follow.wearer?.avatar, name, size = 48.dp)
                Text(
                    stringResource(R.string.sh_join_waiting, name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            PairingCodeCard(pairing, name, onSame = { actions.confirmNearby(true) }, onDifferent = { actions.confirmNearby(false) })
            if (pairing.confirmed) NoteCard(stringResource(R.string.sh_join_waiting_note, name))
            // never blocks: the Following page shows the same card until the codes are compared
            StepButtons(stringResource(R.string.sh_setup_continue), onPrimary = actions.completeJoin)
            return
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarImage(follow.wearer?.avatar, name, size = 48.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        stringResource(
                            if (follow.status ==
                                FollowStatus.Active
                            ) {
                                R.string.sh_join_active
                            } else {
                                R.string.sh_join_waiting
                            },
                            name,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (follow.status != FollowStatus.Active) {
                        Text(stringResource(R.string.sh_join_code_hint), style = MaterialTheme.typography.bodyMedium)
                        val spaced = follow.code.toList().joinToString(" ")
                        Text(
                            follow.code,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { contentDescription = spaced },
                        )
                    }
                }
            }
        }
        if (follow.status != FollowStatus.Active) NoteCard(stringResource(R.string.sh_join_waiting_note, name))
        StepButtons(stringResource(R.string.sh_setup_continue), onPrimary = actions.completeJoin)
        return
    }
    if (follow is FollowState.Following) {
        val name = follow.wearer?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.sh_join_someone)
        val ended =
            when (follow.status) {
                FollowStatus.Declined -> R.string.sh_join_declined
                FollowStatus.InviteUsed -> R.string.sh_join_used
                else -> R.string.sh_join_revoked
            }
        NoteCard(stringResource(ended, name), error = true)
    }
    var name by rememberSaveable { mutableStateOf("") }
    // kept while the confirmation shows: Cancel returns to a pasted link (an opened one leaves with the invite)
    var link by rememberSaveable(u.invite) { mutableStateOf(u.invite.orEmpty()) }
    // an invite link opened on this phone: looked up by itself, so who is inviting shows at once (nothing is sent)
    LaunchedEffect(u.invite) { u.invite?.let { actions.lookUp(it, true) } }
    val confirm = u.join as? JoinUi.Confirm
    if (confirm != null) {
        InviteConfirm(confirm.invite, name, { name = it }, actions)
        return
    }
    val busy = u.join == JoinUi.Busy
    OutlinedTextField(
        value = link,
        onValueChange = {
            link = it
            actions.clearJoinError()
        },
        label = { Text(stringResource(R.string.sh_join_link)) },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    ScanInvite(
        enabled = !busy,
        onInvite = {
            link = it
            actions.clearJoinError()
        },
    )
    NameField(name, { name = it }, enabled = !busy)
    (u.join as? JoinUi.Failed)?.let { NoteCard(stringResource(joinError(it.result)), error = true) }
    if (busy) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.sh_join_looking), style = MaterialTheme.typography.bodyMedium)
        }
    }
    StepButtons(
        stringResource(R.string.sh_join_action),
        onPrimary = { actions.lookUp(link, false) },
        enabled = link.isNotBlank() && !busy,
    )
    FindNearby(u.nearby, name, enabled = !busy, actions = actions)
}

/** The name the wearer will see (optional). */
@Composable
private fun NameField(
    name: String,
    onName: (String) -> Unit,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.sh_join_name)) },
        supportingText = { Text(stringResource(R.string.sh_join_name_hint)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The invite was found: who is inviting (the name and picture in the invite) with *Follow* / *Cancel* (Back cancels
 * too). Nothing is saved or sent before *Follow*. The name and picture are the inviter's own words, so the note says to
 * follow only someone you know sent the link (another app could open a link it made up). It arrives with a tick and
 * is read by TalkBack as one polite announcement.
 */
@Composable
private fun ColumnScope.InviteConfirm(
    invite: Invite,
    name: String,
    onName: (String) -> Unit,
    actions: SetupActions,
) {
    val haptics = rememberHaptics()
    LaunchedEffect(invite) { haptics.tick() }
    val who = invite.profile?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.sh_join_someone)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(invite.profile?.avatar, who, size = 56.dp)
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.sh_join_confirm_title, who), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.sh_join_confirm_body, who), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    NoteCard(stringResource(R.string.sh_join_confirm_note))
    NameField(name, onName)
    StepButtons(
        stringResource(R.string.sh_join_follow),
        onPrimary = { actions.join(name) },
        secondary = listOf(stringResource(R.string.sh_setup_cancel) to actions.cancelInvite),
    )
}

private val WAITING = setOf(FollowStatus.New, FollowStatus.Requested, FollowStatus.Active)

private fun joinError(r: JoinResult): Int =
    when (r) {
        JoinResult.InvalidLink -> R.string.sh_join_err_invalid
        JoinResult.NotFound -> R.string.sh_join_err_not_found
        JoinResult.Offline -> R.string.sh_join_err_offline
        JoinResult.Expired -> R.string.sh_join_err_expired
        JoinResult.WrongWearer -> R.string.sh_join_err_wrong
        JoinResult.AlreadyFollowing -> R.string.sh_join_err_already
        JoinResult.CodesDiffered -> R.string.sh_join_err_mismatch
        JoinResult.KeyStoreFailed, JoinResult.Joined -> R.string.sh_join_err_store
    }
