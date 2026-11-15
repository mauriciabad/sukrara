// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.followers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.designsystem.rows.MainSwitchRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SplitSwitchRow
import app.mauri.sukrara.feature.sharing.BLOCKED
import app.mauri.sukrara.feature.sharing.NearbyDevices
import app.mauri.sukrara.feature.sharing.NearbyFixRow
import app.mauri.sukrara.feature.sharing.NearbyUi
import app.mauri.sukrara.feature.sharing.PairNearbyRow
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.feature.sharing.mark
import app.mauri.sukrara.feature.sharing.nearbySummary
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.share.ble.AndroidNearbyServer
import app.mauri.sukrara.share.ble.NearbyStatus
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * *Over Bluetooth nearby* on Settings › Sharing, as stock Settings lists a feature with its own page (Display › Night
 * Light, Connected devices' Bluetooth): the switch turns it on or off, the row opens [NearbyPage]. While Bluetooth or
 * the permission blocks it, the row is a warning row (the page has the fix).
 */
@Composable
fun NearbyRow(
    nearby: NearbyUi,
    actions: FollowersActions,
) {
    SplitSwitchRow(
        stringResource(R.string.sh_nearby_title),
        nearby.on,
        actions.setNearby,
        actions.openNearby,
        summary = nearbySummary(nearby.status, nearby.on, wearer = true, internet = nearby.internet),
        icon = DsR.drawable.ds_ic_bluetooth,
        warning = nearby.on && nearby.status in FIXABLE,
    )
}

/** Blocked states the page has a fix row for (Bluetooth off, the permission). */
private val FIXABLE = setOf(NearbyStatus.BluetoothOff, NearbyStatus.NeedsPermission)

/**
 * Settings › Sharing › *Over Bluetooth nearby*, like stock Bluetooth's page: the main switch with what its state
 * means, the fix while Bluetooth or the permission blocks it, *Pair nearby* (greyed while off or blocked; an open
 * pairing keeps its switch) and the follower devices nearby (a follower opens [FollowerSheet]). Back leaves.
 */
@Composable
fun NearbyPage(
    state: OwnerShareState.Sharing,
    nearby: NearbyUi,
    actions: FollowersActions,
    modifier: Modifier = Modifier,
    writerRefused: String? = null,
    /** The clock for *Pair nearby*'s countdown (epoch s; screenshot tests fix it). */
    now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MainSwitchRow(
            stringResource(R.string.sh_nearby_use),
            nearby.on,
            actions.setNearby,
            note = nearbySummary(nearby.status, nearby.on, wearer = true, internet = nearby.internet),
        )
        SettingsGroup {
            if (nearby.on) NearbyFixRow(nearby.status, AndroidNearbyServer.PERMISSIONS, actions.refreshNearby)
            PairNearbyRow(nearby.pairingUntil, actions.pairNearby, now, enabled = nearby.on && nearby.status !in BLOCKED)
        }
        // only links whose identity proof named an active follower: an unnamed one is never listed (no made-up name)
        val devices = NearbyDevices.rows(state.followers, state.nearbyPaired, nearby.links)
        if (nearby.on && devices.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.sh_nearby_devices)) {
                for (row in devices) {
                    val f = row.follower
                    val title =
                        row.device?.let {
                            val device = stringResource(if (it == "mac") R.string.sh_nearby_mac else R.string.sh_nearby_phone)
                            stringResource(R.string.sh_nearby_device_name, Profile.shownName(f.name), device)
                        } ?: f.name
                    PersonRow(
                        f.avatarJson,
                        title,
                        stringResource(if (row.connected) R.string.sh_nearby_device_connected else R.string.sh_nearby_device_disconnected),
                    ) { open = f.key }
                }
            }
        }
    }
    state.followers.firstOrNull { it.active && it.key == open }?.let { f ->
        FollowerSheet(
            f,
            nearby = nearby.mark(f.key, state.nearbyPaired),
            writer = f.key in state.writers,
            refused = writerRefused == f.key,
            onWriter = { actions.setWriter(f.key, it) },
            onRemove = { actions.remove(f.key) },
            onClose = {
                open = null
                actions.closeFollower()
            },
        )
    }
}
