// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import app.mauri.sukrara.share.ble.NearbyServerCore
import app.mauri.sukrara.share.owner.FollowerRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nearby devices: connected first (oldest link first), then the paired ones out of range; never a stranger. */
class NearbyDevicesTest {
    private val ana = FollowerRecord("a", "Ana", 1)
    private val bea = FollowerRecord("b", "bea", 2)
    private val cai = FollowerRecord("c", "Cai", 3)
    private val dan = FollowerRecord("d", "Dan", 4)
    private val gone = FollowerRecord("g", "Gone", 5, revoked = 6)
    private val followers = listOf(ana, bea, cai, dan, gone)

    @Test fun connectedOldestFirstThenDisconnectedByName() {
        val links =
            listOf(
                NearbyServerCore.Named("c", "mac", 300),
                NearbyServerCore.Named("a", "phone", 100),
                NearbyServerCore.Named("c", "phone", 200),
            )
        val rows = NearbyDevices.rows(followers, paired = setOf("d", "b", "a"), links)
        assertEquals(
            listOf("a" to "phone", "c" to "phone", "c" to "mac", "b" to null, "d" to null),
            rows.map { it.follower.key to it.device },
        )
        assertEquals(listOf(true, true, true, false, false), rows.map { it.connected })
    }

    @Test fun aRemovedFollowerOrAnUnnamedLinkIsNeverListed() {
        val rows = NearbyDevices.rows(followers, paired = setOf("g", "x"), listOf(NearbyServerCore.Named("x", "phone", 1)))
        assertTrue(rows.isEmpty())
    }
}
