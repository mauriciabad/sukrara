// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.protocol.KeyPair
import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.event.Rumor
import app.mauri.sukrara.protocol.event.RumorCodec
import app.mauri.sukrara.share.OwnerFixtures
import app.mauri.sukrara.share.OwnerFixtures.NOW
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.owner.usecase.CreateIdentity
import app.mauri.sukrara.share.owner.usecase.HandleInbox
import app.mauri.sukrara.share.owner.usecase.InboxSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * THE VERSION RULE on the wearer (docs/48 "When versions differ"): a follower's message of another version changes
 * nothing and says which side updates (its row on Settings › Sharing); a readable one names the sender, which ends it.
 */
class OtherVersionInboxTest {
    private val ctx = OwnerFixtures.context()
    private val follower = crypto.generate()
    private val s =
        CreateIdentity(ctx)(Profile("Ana", null), NOW).state.let {
            it.copy(followers = listOf(FollowerRecord(follower.public.hex, "Mom", NOW)))
        }

    /** [rumor] as [follower] sends it, its `v` replaced by [v]. */
    private fun wrap(
        rumor: Rumor,
        v: Int,
        from: KeyPair = follower,
    ): SignedEvent {
        val content = RumorCodec.encode(rumor).replace("\"v\":${Wire.V}", "\"v\":$v")
        return crypto.giftWrap(from, s.identity(crypto).public, rumor.kind, content, NOW, NOW + Wire.TTL_REQUEST)
    }

    @Test fun aNewerMessageSaysThisPhoneUpdatesAndChangesNothing() {
        val handled = HandleInbox(ctx)(s, wrap(Rumor.ProfileUpdate(Profile("Mama", null)), Wire.V + 1), NOW)
        assertEquals(InboxSignal.OtherVersion(follower.public.hex, newer = true), handled.signal)
        assertEquals(s, handled.step.state)
        assertNull(handled.readFrom)
    }

    @Test fun anOlderMessageSaysTheyUpdate() {
        val handled = HandleInbox(ctx)(s, wrap(Rumor.ProfileUpdate(Profile("Mama", null)), Wire.V - 1), NOW)
        assertEquals(InboxSignal.OtherVersion(follower.public.hex, newer = false), handled.signal)
    }

    @Test fun aReadableMessageNamesItsSender() {
        val handled = HandleInbox(ctx)(s, wrap(Rumor.ProfileUpdate(Profile("Mama", null)), Wire.V), NOW + 1)
        assertEquals(follower.public.hex, handled.readFrom)
        assertEquals(
            "Mama",
            handled.step.state.followers
                .single()
                .name,
        )
    }
}
