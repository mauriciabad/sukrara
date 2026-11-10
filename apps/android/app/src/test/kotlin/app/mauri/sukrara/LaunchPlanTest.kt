// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.share.transport.RelayAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one gate between a launch and the relays: release always public, debug only where a developer pointed it. */
class LaunchPlanTest {
    private val local = "ws://10.0.2.2:7447"

    @Test fun releaseAlwaysUsesThePublicRelays() {
        for (choice in listOf(null, "", "off", "public", local, "nonsense")) {
            assertEquals(choice.toString(), LaunchPlan(RelayAccess.Public), LaunchPlan.of(debugBuild = false, relayChoice = choice))
        }
        assertEquals(Wire.DEFAULT_RELAYS, RelayAccess.Public.connectTo(Wire.DEFAULT_RELAYS))
        assertEquals(listOf("wss://a.example"), RelayAccess.Public.connectTo(listOf("ws://b.example", "wss://a.example")))
    }

    @Test fun debugWithoutAChoiceReachesNoRelay() {
        for (choice in listOf(null, "", "  ", "off", "OFF")) {
            val plan = LaunchPlan.of(debugBuild = true, relayChoice = choice)
            assertEquals(RelayAccess.Off, plan.relays)
            assertEquals(emptyList<String>(), plan.relays.connectTo(Wire.DEFAULT_RELAYS))
        }
    }

    @Test fun debugPointedAtALocalRelayUsesOnlyIt() {
        val plan = LaunchPlan.of(debugBuild = true, relayChoice = " $local , wss://relay.test/x,$local")
        assertEquals(RelayAccess.Local(listOf(local, "wss://relay.test/x")), plan.relays)
        // whatever the protocol names (the default list, an invite's relays)
        assertEquals(listOf(local, "wss://relay.test/x"), plan.relays.connectTo(Wire.DEFAULT_RELAYS))
        assertEquals(listOf(local, "wss://relay.test/x"), plan.relays.connectTo(emptyList()))
    }

    @Test fun debugReachesThePublicRelaysOnlyWhenAskedTo() {
        assertEquals(RelayAccess.Public, LaunchPlan.of(debugBuild = true, relayChoice = "public").relays)
    }

    @Test fun aChoiceThatIsntOneIsRefused() {
        for (bad in listOf("http://10.0.2.2:7447", "10.0.2.2:7447", "ws://", "ws://:7447", "ws://a b", "$local,https://x", ",")) {
            assertNull(bad, LaunchPlan.relays(bad))
            // a launch with a damaged choice stays offline
            assertEquals(bad, RelayAccess.Off, LaunchPlan.of(debugBuild = true, relayChoice = bad).relays)
        }
    }
}
