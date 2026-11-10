// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.core.model.AlertNote
import app.mauri.sukrara.core.model.AlertNoteType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Settings › Alerts › Lately: what the runtime remembers, newest first, bounded, and kept across a restart. */
class AlertNotesTest {
    @Test fun alertMutesAndClearAreRememberedAcrossARestart() =
        runBlocking {
            val rig = AlertRig()
            val runtime = rig.runtime()
            runtime.onState(rig.wearerState(rig.reading(120)))
            rig.clock.instant = rig.clock.instant + Duration.ofMinutes(5)
            runtime.onState(rig.wearerState(rig.reading(60)))
            runtime.mute("low", 30)
            runtime.muteAll(60)
            runtime.unmuteAll()
            val types = rig.published.lately.map { it.type }
            assertEquals(
                listOf(AlertNoteType.AllUnmuted, AlertNoteType.AllMuted, AlertNoteType.Muted, AlertNoteType.Fired),
                types.take(4),
            )
            val fired = rig.published.lately.first { it.type == AlertNoteType.Fired }
            assertEquals("low", fired.kind)
            assertEquals(60, fired.mgdl)
            assertEquals(
                60,
                rig.published.lately
                    .first { it.type == AlertNoteType.AllMuted }
                    .minutes,
            )

            // a process death: the new runtime publishes the same history
            val again = rig.runtime()
            again.start()
            again.onState(rig.wearerState(rig.reading(60)))
            assertEquals(
                types,
                rig.published.lately
                    .map { it.type }
                    .drop(rig.published.lately.size - types.size),
            )
        }

    /** Low → very low inside one alert: the very low gets its own line; its repeats don't. */
    @Test fun aLevelChangeInsideOneAlertGetsALine() =
        runBlocking {
            val rig = AlertRig()
            val runtime = rig.runtime()
            runtime.onState(rig.wearerState(rig.reading(65)))
            rig.clock.instant = rig.clock.instant + Duration.ofMinutes(1)
            runtime.onState(rig.wearerState(rig.reading(50)))
            rig.clock.instant = rig.clock.instant + Duration.ofMinutes(5)
            runtime.onState(rig.wearerState(rig.reading(50)))
            val fired = rig.published.lately.filter { it.type == AlertNoteType.Fired }
            assertEquals(listOf("very_low" to 50, "low" to 65), fired.map { it.kind to it.mgdl })
            assertEquals(0, rig.published.lately.count { it.type == AlertNoteType.Cleared })
        }

    @Test fun aTimedMuteAllEndingByItselfIsAlertsBackOnOnceAlsoAcrossARestart() =
        runBlocking {
            val rig = AlertRig()
            val runtime = rig.runtime()
            runtime.onState(rig.wearerState(rig.reading(120)))
            runtime.muteAll(60)
            rig.clock.instant = rig.clock.instant + Duration.ofMinutes(61)
            runtime.tick()
            // newest first: what it lets ring (no data, 61 min without a reading) comes after Alerts back on
            val types = rig.published.lately.map { it.type }
            assertEquals(listOf(AlertNoteType.Fired, AlertNoteType.AllBack, AlertNoteType.AllMuted), types.take(3))

            // a process death later: the end is not noted again
            val again = rig.runtime()
            again.start()
            again.tick()
            assertEquals(1, rig.published.lately.count { it.type == AlertNoteType.AllBack })
        }

    @Test fun anEndThatHappenedWhileTheProcessWasDeadIsNotedAtTheRestart() =
        runBlocking {
            val rig = AlertRig()
            val runtime = rig.runtime()
            runtime.onState(rig.wearerState(rig.reading(120)))
            runtime.muteAll(30)
            rig.clock.instant = rig.clock.instant + Duration.ofMinutes(45)
            val again = rig.runtime()
            again.start()
            again.tick()
            assertEquals(
                listOf(AlertNoteType.AllBack, AlertNoteType.AllMuted),
                rig.published.lately
                    .map { it.type }
                    .filter { it != AlertNoteType.Fired }
                    .take(2),
            )
            assertEquals(1, rig.published.lately.count { it.type == AlertNoteType.AllBack })
        }

    @Test fun unmuteAllOrMuteAllUntilTurnedOffIsNeverAlertsBackOn() {
        val now = 10_000L
        assertEquals(
            listOf(AlertNoteType.AllUnmuted),
            AlertNotes.of(app.mauri.sukrara.alerts.AlertInput.UnmuteAll, emptyList(), now, muteAllBefore = now + 600).map { it.type },
        )
        assertEquals(emptyList<AlertNoteType>(), AlertNotes.of(null, emptyList(), now, muteAllBefore = 0).map { it.type })
        assertEquals(
            emptyList<AlertNoteType>(),
            AlertNotes.of(null, emptyList(), now, muteAllBefore = now - 1, muteAllAfter = now + 600).map { it.type },
        )
        assertEquals(
            listOf(AlertNoteType.AllBack),
            AlertNotes.of(null, emptyList(), now, muteAllBefore = now - 1).map { it.type },
        )
    }

    @Test fun anAlertShownAgainSilentlyAfterARestartIsResumed() =
        runBlocking {
            val rig = AlertRig()
            val runtime = rig.runtime()
            runtime.onState(rig.wearerState(rig.reading(120)))
            rig.clock.instant = rig.clock.instant + Duration.ofMinutes(5)
            runtime.onState(rig.wearerState(rig.reading(60)))
            assertEquals(
                AlertNoteType.Fired,
                rig.published.lately
                    .first()
                    .type,
            )

            val again = rig.runtime()
            again.start()
            again.tick()
            val resumed = rig.published.lately.first()
            assertEquals(AlertNoteType.Resumed, resumed.type)
            assertEquals("low", resumed.kind)
            assertEquals("the ring is still counted once", 1, rig.published.lately.count { it.type == AlertNoteType.Fired })
        }

    @Test fun keepsTheNewestLines() {
        val at = Instant.ofEpochSecond(1_000)
        val many = List(AlertNotes.MAX + 5) { AlertNote(at.plusSeconds(it.toLong()), AlertNoteType.AllUnmuted) }
        val kept = AlertNotes.append(emptyList(), many)
        assertEquals(AlertNotes.MAX, kept.size)
        assertEquals(many.last().at.epochSecond, kept.last().t)
    }

    @Test fun unknownSavedTypeIsDropped() {
        assertEquals(null, SavedNote(1, "Gone").note())
    }
}
