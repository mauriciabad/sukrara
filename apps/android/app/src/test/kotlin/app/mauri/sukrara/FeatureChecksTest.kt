// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.BlockedFeature
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.integrations.healthconnect.HcStatus
import app.mauri.sukrara.integrations.localserver.LanState
import app.mauri.sukrara.share.ble.NearbyStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/40: a feature counts as blocked only while it is on, in use and Android stops it, on the role that has it. */
class FeatureChecksTest {
    private fun blocked(f: FeatureFacts) = FeatureChecks.blocked(f)

    @Test fun bluetoothSharingCountsOnlyWhileTheServerWantsToRunAndLacksThePermission() {
        // the owner's case (#40): Nearby devices granted for the sensor, not for advertising, someone follows
        assertEquals(
            listOf(BlockedFeature.BluetoothSharing),
            blocked(FeatureFacts(SourceSpec.YourSensor, sharing = NearbyStatus.NeedsPermission)),
        )
        // nobody follows yet (the server isn't wanted: Stopped), Bluetooth off (the alert checklist's), all fine
        for (s in listOf(NearbyStatus.Stopped, NearbyStatus.BluetoothOff, NearbyStatus.Searching, NearbyStatus.Unsupported)) {
            assertEquals(emptyList<BlockedFeature>(), blocked(FeatureFacts(SourceSpec.YourSensor, sharing = s)))
        }
    }

    @Test fun eachFeatureOnlyOnItsRole() {
        val all =
            FeatureFacts(
                SourceSpec.YourSensor,
                sharing = NearbyStatus.NeedsPermission,
                following = NearbyStatus.NeedsPermission,
                healthConnect = HcStatus.NeedsPermission,
                homeNetwork = LanState.NoPermission,
            )
        assertEquals(
            listOf(BlockedFeature.BluetoothSharing, BlockedFeature.HealthConnect, BlockedFeature.HomeNetwork),
            blocked(all),
        )
        assertEquals(
            listOf(BlockedFeature.BluetoothFollowing, BlockedFeature.HomeNetwork),
            blocked(all.copy(role = SourceSpec.TheirSukrara)),
        )
        assertEquals(listOf(BlockedFeature.HomeNetwork), blocked(all.copy(role = null)))
    }

    @Test fun healthConnectAndTheHomeNetworkCountOnlyForTheMissingPermission() {
        assertEquals(emptyList<BlockedFeature>(), blocked(FeatureFacts(SourceSpec.YourSensor, healthConnect = HcStatus.Off)))
        assertEquals(emptyList<BlockedFeature>(), blocked(FeatureFacts(SourceSpec.YourSensor, healthConnect = HcStatus.Unavailable)))
        assertEquals(emptyList<BlockedFeature>(), blocked(FeatureFacts(SourceSpec.YourSensor, homeNetwork = LanState.NoWifi)))
        assertEquals(emptyList<BlockedFeature>(), blocked(FeatureFacts(SourceSpec.YourSensor, homeNetwork = LanState.OtherWifi)))
    }

    @Test fun everyBlockedFeatureHasAWayOutBesidesAllow() {
        // a switch to turn off, or (Bluetooth following, which has none) the tip closes: never a dead end
        assertEquals(
            listOf(BlockedFeature.BluetoothFollowing),
            BlockedFeature.entries.filter { !it.canTurnOff },
        )
    }
}
