// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol

import app.mauri.sukrara.protocol.event.AlertCodec
import app.mauri.sukrara.protocol.event.AlertPayload
import app.mauri.sukrara.protocol.event.InvalidPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `alert` event's content (PROTOCOL-v2 "Alerts") with #39's escalation: `esc` on a very low's fire, `answered`. */
class AlertCodecTest {
    private val fire = AlertPayload("a1", "very_low", fire = true, at = 100, t = 90, sgv = 52, rate = null, threshold = 55.0, n = 2, tr = 0)

    @Test fun anEscalatedFireAndItsAnswerRoundTrip() {
        val escalated = fire.copy(esc = 5)
        assertEquals(escalated, AlertCodec.decode(AlertCodec.encode(escalated)))
        assertTrue("\"esc\":5" in AlertCodec.encode(escalated))
        assertFalse("esc" in AlertCodec.encode(fire))
        val answered = fire.copy(fire = false, answered = true, t = null, sgv = null, threshold = null)
        val back = AlertCodec.decode(AlertCodec.encode(answered))
        assertTrue(back.answered && !back.fire)
        assertEquals("answered", back.state)
        assertNull(AlertCodec.decode(AlertCodec.encode(fire.copy(fire = false))).esc)
        assertThrows(InvalidPayload::class.java) {
            AlertCodec.decode("""{"v":2,"type":"alert","id":"a1","k":"very_low","state":"escalate","at":1}""")
        }
    }
}
