// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Every state the Garmin row and page say, and which one wins (docs/FEATURES.md "Garmin watch face (Sukrara Aura)",
 * States and exits): the row's one line, each watch's line, and the face's.
 */
class GarminStatusTest {
    private val now = Instant.parse("2026-11-16T10:09:36Z")
    private val ago = { m: Long -> now - Duration.ofMinutes(m) }
    private val base = AuraLinkState(on = true, garminConnect = true, answering = true)
    private val watch = AuraWatch(7, "Descent", connected = true, installed = true)

    private fun of(s: AuraLinkState) = AuraStatus.of(s, now)

    private fun line(
        w: AuraWatch,
        s: AuraLinkState = base.copy(watches = listOf(w)),
    ) = WatchLine.of(w, s, now)

    @Test
    fun theOrder() {
        assertEquals(AuraStatus.Off, of(base.copy(on = false)))
        assertEquals(AuraStatus.NoGarminConnect, of(base.copy(garminConnect = false)))
        assertEquals(AuraStatus.NotAnswering, of(base.copy(answering = false)))
        assertEquals(AuraStatus.NoWatch, of(base))
        assertEquals(AuraStatus.NotInstalled, of(base.copy(watches = listOf(watch.copy(installed = false)))))
        assertEquals(AuraStatus.Waiting, of(base.copy(watches = listOf(watch))))
        val sent = watch.copy(delivered = ago(1), reading = ago(1))
        assertEquals(AuraStatus.Sent("Descent", ago(1)), of(base.copy(watches = listOf(sent))))
    }

    @Test
    fun away() {
        val w = watch.copy(connected = false, delivered = ago(30), reading = ago(30))
        assertEquals(AuraStatus.Away("Descent"), of(base.copy(watches = listOf(w))))
        // the watch's line keeps the reading it has, as a clock time
        assertEquals(WatchLine(false, ago(30), null), line(w))
        assertEquals(WatchLine(false, null, null), line(watch.copy(connected = false)))
    }

    @Test
    fun lostMessages() {
        val lost = watch.copy(failed = ago(1), noAnswer = true)
        // the face keeps up by fetching every 5 min
        assertEquals(AuraStatus.Fetching(ago(3)), of(base.copy(watches = listOf(lost), fetched = ago(3))))
        assertEquals(WatchLine.Problem.Slow, line(lost, base.copy(watches = listOf(lost), fetched = ago(3))).problem)
        // nothing fetched lately either
        assertEquals(AuraStatus.Failing("Descent"), of(base.copy(watches = listOf(lost), fetched = ago(30))))
        assertEquals(WatchLine.Problem.NoAnswer, line(lost, base.copy(watches = listOf(lost), fetched = ago(30))).problem)
        assertEquals(WatchLine.Problem.Failed, line(watch.copy(failed = ago(1))).problem)
        // a delivery after the failure wins
        val back = lost.copy(delivered = now, reading = ago(1))
        assertEquals(AuraStatus.Sent("Descent", ago(1)), of(base.copy(watches = listOf(back))))
        assertEquals(WatchLine(true, ago(1), null), line(back))
        // Garmin Connect's service down but the face fetches: not "not answering"
        assertEquals(AuraStatus.Fetching(ago(2)), of(base.copy(answering = false, watches = listOf(lost), fetched = ago(2))))
    }

    @Test
    fun stuckMessages() {
        val fetching = { w: AuraWatch -> base.copy(watches = listOf(w), fetched = ago(3)) }
        // Garmin Connect ends its own stalls within minutes: still "loses messages"
        val failing = watch.copy(failed = ago(1), noAnswer = true, failingSince = ago(14))
        assertEquals(WatchLine.Problem.Slow, line(failing, fetching(failing)).problem)
        // nothing through for 15 min (#242): say so and what brings them back, whether or not the face fetches
        val stuck = failing.copy(failingSince = ago(15))
        assertEquals(WatchLine.Problem.Stuck, line(stuck, fetching(stuck)).problem)
        assertEquals(WatchLine.Problem.Stuck, line(stuck).problem)
        // a delivery ends it; away, there's nothing to say about messages
        assertEquals(null, line(stuck.copy(delivered = now, failed = null, failingSince = null)).problem)
        assertEquals(null, line(stuck.copy(connected = false)).problem)
    }

    @Test
    fun aWatchWithUnknownInstallStillCounts() {
        val w = watch.copy(installed = null, delivered = ago(2), reading = ago(2))
        assertEquals(AuraStatus.Sent("Descent", ago(2)), of(base.copy(watches = listOf(w))))
    }

    @Test
    fun theFacesReportCountsOnlyWhileOneWatchHasTheFace() {
        val report = AuraReport(ago(1), emptyList(), drawn = ago(1), drawnSince = ago(4))
        val w = watch.copy(delivered = ago(6), reading = ago(6))
        assertEquals(ago(1), line(w, base.copy(watches = listOf(w), report = report)).reading)
        // two watches with the face: the fetch doesn't say which one asked
        val other = watch.copy(id = 8, name = "Forerunner")
        assertEquals(ago(6), line(w, base.copy(watches = listOf(w, other), report = report)).reading)
        // a watch without the face says so, without a reading
        val noFace = watch.copy(installed = false)
        assertEquals(WatchLine(true, null, WatchLine.Problem.NoFace), line(noFace, base.copy(watches = listOf(noFace), report = report)))
    }

    @Test
    fun theFace() {
        val w = listOf(watch)
        assertEquals(FaceLine.Unknown, FaceLine.of(base, now))
        assertEquals(FaceLine.NotInstalled, FaceLine.of(base.copy(watches = listOf(watch.copy(installed = false))), now))
        assertEquals(FaceLine.InUse(ago(3)), FaceLine.of(base.copy(watches = w, fetched = ago(3)), now))
        assertEquals(FaceLine.Idle, FaceLine.of(base.copy(watches = w, fetched = ago(30)), now))
        assertEquals(FaceLine.Unknown, FaceLine.of(base.copy(watches = listOf(watch.copy(installed = null))), now))
        assertEquals(FaceLine.PortBusy, FaceLine.of(base.copy(watches = w, portBusy = true), now))
    }
}
