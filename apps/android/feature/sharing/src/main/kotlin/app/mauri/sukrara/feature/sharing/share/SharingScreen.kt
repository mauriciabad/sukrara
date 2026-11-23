// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mauri.sukrara.designsystem.state.StateKind
import app.mauri.sukrara.designsystem.state.StateScreen
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.feature.sharing.conflict.IdentityConflictScreen
import app.mauri.sukrara.feature.sharing.followers.FollowersActions
import app.mauri.sukrara.feature.sharing.followers.FollowersPage
import app.mauri.sukrara.feature.sharing.followers.NearbyPage
import app.mauri.sukrara.feature.sharing.rememberAskNearby
import app.mauri.sukrara.share.ble.AndroidNearbyServer
import app.mauri.sukrara.share.owner.OwnerShareState

/**
 * Settings › Sharing: the followers page while sharing (a waiting request opens the approve dialog the app shows over
 * every screen, [onReview]), the conflict screen while another app shares as this identity, and the way into setup's
 * identity step when there is no identity yet. The app places it under the settings top bar (title and Back).
 */
@Composable
fun SharingScreen(
    viewModel: SharingViewModel,
    onSetUpSharing: () -> Unit,
    onShare: () -> Unit,
    onReview: (String) -> Unit,
    onProfile: () -> Unit,
    onBackup: () -> Unit,
    onNearby: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** The followers page's foot: *See also* the other data pages (the app draws them). */
    links: @Composable () -> Unit = {},
    /** *Update Sukrara* (a follower's Sukrara is newer, docs/48). */
    onUpdate: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val writerRefused by viewModel.writerRefused.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    // turned on: Nearby devices asked right then, if it is missing (docs/40)
    val askNearby = rememberAskNearby(AndroidNearbyServer.PERMISSIONS, viewModel::refreshNearby)
    val setNearby: (Boolean) -> Unit = { on ->
        viewModel.setNearby(on)
        if (on) askNearby()
    }
    Box(modifier.fillMaxSize()) {
        when (val s = state) {
            OwnerShareState.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            OwnerShareState.NoIdentity, is OwnerShareState.IdentityUnreadable -> {
                StateScreen(
                    kind = if (s is OwnerShareState.IdentityUnreadable) StateKind.Error else StateKind.Empty,
                    title = stringResource(R.string.sh_identity_title),
                    reason =
                        stringResource(
                            if (s is OwnerShareState.IdentityUnreadable) R.string.sh_identity_unreadable else R.string.sh_identity_body,
                        ),
                    action = stringResource(R.string.sh_sharing_set_up),
                    onAction = onSetUpSharing,
                )
            }

            is OwnerShareState.AnotherAppSharingAsYou -> {
                IdentityConflictScreen(s, viewModel::retryConflict, onClose)
            }

            is OwnerShareState.Sharing -> {
                FollowersPage(
                    s,
                    FollowersActions(
                        review = onReview,
                        remove = { viewModel.remove(it) },
                        setWriter = viewModel::setWriter,
                        share = onShare,
                        profile = onProfile,
                        backup = onBackup,
                        closeFollower = viewModel::writerSeen,
                        setInternet = viewModel::setInternet,
                        setInterval = viewModel::setUploadInterval,
                        setNearby = setNearby,
                        refreshNearby = viewModel::refreshNearby,
                        pairNearby = viewModel::pairNearby,
                        openNearby = onNearby,
                        updateSukrara = onUpdate,
                    ),
                    writerRefused = writerRefused,
                    nearby = nearby,
                    links = links,
                )
            }
        }
    }
}

/**
 * Settings › Sharing › *Over Bluetooth nearby* ([NearbyPage]) while sharing; any other state (no identity, the
 * conflict) leaves for Sharing, which shows it. The app places it under the settings top bar (title and Back).
 */
@Composable
fun NearbyScreen(
    viewModel: SharingViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val writerRefused by viewModel.writerRefused.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    // turned on: Nearby devices asked right then, if it is missing (docs/40)
    val askNearby = rememberAskNearby(AndroidNearbyServer.PERMISSIONS, viewModel::refreshNearby)
    val setNearby: (Boolean) -> Unit = { on ->
        viewModel.setNearby(on)
        if (on) askNearby()
    }
    val close by rememberUpdatedState(onClose)
    when (val s = state) {
        OwnerShareState.Loading -> {
            Box(modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        }

        is OwnerShareState.Sharing -> {
            NearbyPage(
                s,
                nearby,
                FollowersActions(
                    remove = { viewModel.remove(it) },
                    setWriter = viewModel::setWriter,
                    closeFollower = viewModel::writerSeen,
                    setNearby = setNearby,
                    refreshNearby = viewModel::refreshNearby,
                    pairNearby = viewModel::pairNearby,
                ),
                modifier,
                writerRefused = writerRefused,
            )
        }

        else -> {
            LaunchedEffect(Unit) { close() }
        }
    }
}
