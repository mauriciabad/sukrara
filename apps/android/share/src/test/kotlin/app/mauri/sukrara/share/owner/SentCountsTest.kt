// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.Wire
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings › Advanced › Statistics: the readings' live events and hour chunks are counted apart. */
class SentCountsTest {
    private fun event(kind: Int) = SignedEvent("id$kind", "pub", 0, kind, emptyList(), "", "sig")

    @Test
    fun `live events and chunks add up separately`() {
        val counts =
            SentCounts()
                .plus(listOf(event(Wire.KIND_LIVE), event(Wire.KIND_DATA)))
                .plus(listOf(event(Wire.KIND_LIVE)))
                .plus(emptyList())
        assertEquals(SentCounts(live = 2, chunks = 1), counts)
    }
}
