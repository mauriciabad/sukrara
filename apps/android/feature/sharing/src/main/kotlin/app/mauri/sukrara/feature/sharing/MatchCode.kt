// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.protocol.follow.FollowStatus

/**
 * The match code a follower that joined with a link shows while it waits (PROTOCOL-v2 "Invite link / QR"). The wearer
 * types it only when another device also tried the same link (otherwise their dialog shows no code), so it is one
 * quiet line, in the Mac's words too: "If Ana asks for a code: 4821". TalkBack reads the code digit by digit.
 */
@Composable
internal fun MatchCodeLine(
    wearer: String,
    code: String,
    modifier: Modifier = Modifier,
) {
    val spoken = stringResource(R.string.sh_follow_code, wearer, code.toList().joinToString(" "))
    Text(
        stringResource(R.string.sh_follow_code, wearer, code),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.semantics { contentDescription = spoken },
    )
}

/**
 * A confirm haptic when the wearer's Allow arrives while the screen shows (the status turns active by itself), not
 * when the screen opens already following.
 */
@Composable
internal fun ConfirmWhenAllowed(status: FollowStatus) {
    val haptics = rememberHaptics()
    var seen by rememberSaveable { mutableStateOf(status.name) }
    LaunchedEffect(status) {
        if (status == FollowStatus.Active && seen != FollowStatus.Active.name) haptics.confirm()
        seen = status.name
    }
}
