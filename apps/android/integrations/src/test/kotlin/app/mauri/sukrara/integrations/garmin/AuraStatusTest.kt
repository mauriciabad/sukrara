// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Every state the Sukrara Aura row and page say, and which one wins (docs/FEATURES.md "Sukrara Aura", States and exits). */
class AuraStatusTest {
    private val now = Instant.parse("2026-11-16T10:09:36Z")
    private val ago = { m: Long -> now - Duration.ofMinutes(m) }
    private val base = AuraLinkState(on = true, garminConnect = true, answering = true)
    private val watch = AuraWatch(7, "Descent", connected = true, installed = true)

    private fun of(s: AuraLinkState) = AuraStatus.of(s, now)

    @Test
    fun theOrder() {
        assertEquals(AuraStatus.Off, of(base.copy(on = false)))
        assertEquals(AuraStatus.NoGarminConnect, of(base.copy(garminConnect = false)))
        assertEquals(AuraStatus.NotAnswering, of(base.copy(answering = false)))
        assertEquals(AuraStatus.NoWatch, of(base))
        assertEquals(AuraStatus.NotInstalled, of(base.copy(watches = listOf(watch.copy(installed = false)))))
        assertEquals(AuraStatus.Waiting, of(base.copy(watches = listOf(watch))))
        assertEquals(AuraStatus.Sent("Descent", ago(1)), of(base.copy(watches = listOf(watch.copy(delivered = ago(1))))))
    }

    @Test
    fun away() {
        val w = watch.copy(connected = false, delivered = ago(30))
        assertEquals(AuraStatus.Away("Descent", ago(30)), of(base.copy(watches = listOf(w))))
        // a fetch since then is the newest the face got
        assertEquals(AuraStatus.Away("Descent", ago(20)), of(base.copy(watches = listOf(w), fetched = ago(20))))
        assertEquals(AuraStatus.Away("Descent", null), of(base.copy(watches = listOf(watch.copy(connected = false)))))
    }

    @Test
    fun lostMessages() {
        val lost = watch.copy(failed = ago(1), noAnswer = true)
        // the face keeps up by fetching every 5 min
        assertEquals(AuraStatus.Fetching(ago(3)), of(base.copy(watches = listOf(lost), fetched = ago(3))))
        // nothing fetched lately either
        assertEquals(AuraStatus.Failing("Descent"), of(base.copy(watches = listOf(lost), fetched = ago(30))))
        // a delivery after the failure wins
        assertEquals(AuraStatus.Sent("Descent", ago(0)), of(base.copy(watches = listOf(lost.copy(delivered = now)))))
        // Garmin Connect's service down but the face fetches: not "not answering"
        assertEquals(AuraStatus.Fetching(ago(2)), of(base.copy(answering = false, watches = listOf(lost), fetched = ago(2))))
    }

    @Test
    fun aWatchWithUnknownInstallStillCounts() {
        val w = watch.copy(installed = null, delivered = ago(2))
        assertEquals(AuraStatus.Sent("Descent", ago(2)), of(base.copy(watches = listOf(w))))
    }
}
