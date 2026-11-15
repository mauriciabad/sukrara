// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.BlockedFeature
import app.mauri.sukrara.core.model.FeatureChecksSource

/** A blocked feature's headline: home's banner title and the Settings row's warning (THE one wording). */
@StringRes
fun BlockedFeature.titleRes(): Int =
    when (this) {
        BlockedFeature.BluetoothSharing -> R.string.ds_blocked_bluetooth_sharing
        BlockedFeature.BluetoothFollowing -> R.string.ds_blocked_bluetooth_following
        BlockedFeature.HealthConnect -> R.string.ds_blocked_health_connect
        BlockedFeature.HomeNetwork -> R.string.ds_blocked_home_network
    }

/** What allowing it gives back (home's banner text). */
@StringRes
fun BlockedFeature.textRes(): Int =
    when (this) {
        BlockedFeature.BluetoothSharing -> R.string.ds_blocked_bluetooth_sharing_text
        BlockedFeature.BluetoothFollowing -> R.string.ds_blocked_bluetooth_following_text
        BlockedFeature.HealthConnect -> R.string.ds_blocked_health_connect_text
        BlockedFeature.HomeNetwork -> R.string.ds_blocked_home_network_text
    }

/**
 * *Allow* for a blocked feature, the same on home and in setup: the system's permission dialog the first time in this
 * screen (in context, as Android asks), and its settings screen after that (Android shows the dialog no more after
 * *Don't allow* twice) or when the grant isn't a runtime permission. The checklist looks again on the answer.
 */
@Composable
fun rememberAllowFeature(source: FeatureChecksSource): (BlockedFeature) -> Unit {
    val checks by rememberUpdatedState(source)
    var asked by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { checks.refresh() }
    return { feature ->
        val permissions = checks.permissions(feature)
        if (permissions.isNotEmpty() && feature.name !in asked) {
            asked = asked + feature.name
            ask.launch(permissions.toTypedArray())
        } else {
            checks.open(feature)
        }
    }
}
