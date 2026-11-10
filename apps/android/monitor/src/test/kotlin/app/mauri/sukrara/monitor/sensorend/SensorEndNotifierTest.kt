// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.sensorend

import app.mauri.sukrara.core.model.SensorEndConfig
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryStateStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** The notifier on virtual time: what shows when, driven by the hub, the alarm, unlocks and swipes. */
class SensorEndNotifierTest {
    private val clock = FakeClock(Instant.parse("2026-01-15T12:00:00Z"))
    private val store = InMemoryStateStore(SensorEndMemory())
    private val shown = mutableListOf<String>()
    private val cancelled = mutableListOf<SensorNotice>()

    /** The two alarms: the event (exact) and the progress tick. */
    private var alarm: Pair<Long?, Long?>? = null
    private var settings = SensorEndSettings(SensorEndConfig(), countdownHours = 8, chip = true)

    private val presenter =
        object : SensorEndPresenter {
            override fun remind(end: Long, now: Long, text: ReminderText) {
                shown += "remind:$text"
            }

            override fun countdown(end: Long, hours: Int, now: Long, promote: Boolean) {
                shown += "countdown:promote=$promote"
            }

            override fun warmup(start: Long, warmupEnd: Long, now: Long, waiting: Boolean) {
                shown += if (waiting) "almost-ready" else "warmup"
            }

            override fun ready() {
                shown += "ready"
            }

            override fun ended() {
                shown += "ended"
            }

            override fun cancel(notice: SensorNotice) {
                cancelled += notice
            }
        }
    private val notifier = SensorEndNotifier(store, presenter, { event, tick -> alarm = event to tick }, clock) { settings }

    private val end = Instant.parse("2026-01-17T14:30:00Z")

    private suspend fun hub(
        sensor: SensorState = SensorState.Active(end),
        latest: Instant? = clock.now(),
        wearer: Boolean = true,
    ) = notifier.update(wearer, sensor, latest?.epochSecond, settings)

    /** Wakes at the first planned alarm, as AlarmManager would. */
    private suspend fun wake() {
        clock.instant = Instant.ofEpochSecond(requireNotNull(listOfNotNull(alarm?.first, alarm?.second).minOrNull()))
        notifier.check()
    }

    @Test fun remindersThenCountdownThenEndedOnce() =
        runTest {
            hub()
            assertEquals(Instant.parse("2026-01-16T09:00:00Z").epochSecond, alarm?.first)
            wake()
            assertEquals(listOf("remind:ENDS"), shown)
            hub() // a reading's update shows nothing again
            assertEquals(listOf("remind:ENDS"), shown)
            wake() // 06:30 on the end day: the countdown window opens
            assertTrue(shown.last().startsWith("countdown"))
            clock.instant = end
            notifier.check()
            assertEquals("ended", shown.last())
            notifier.check()
            assertEquals(1, shown.count { it == "ended" })
        }

    @Test fun theEndCountdownAsksForTheChipOnlyWhileTheGlucoseChipIsOff() =
        runTest {
            clock.instant = end - Duration.ofHours(2)
            hub()
            assertTrue("countdown:promote=false" in shown)
            settings = settings.copy(chip = false)
            hub()
            assertTrue("countdown:promote=true" in shown)
        }

    @Test fun aSwipedCountdownStaysAwayForThisSensor() =
        runTest {
            clock.instant = end - Duration.ofHours(2)
            hub()
            notifier.dismissed(warmup = false)
            shown.clear()
            hub()
            notifier.check()
            assertFalse(shown.any { it.startsWith("countdown") })
        }

    @Test fun anEarlyEndSaysEndedNow() =
        runTest {
            hub()
            clock.instant = Instant.parse("2026-01-16T20:00:00Z")
            hub(SensorState.Ended)
            assertEquals("ended", shown.last())
        }

    @Test fun warmUpThenAlmostReadyThenReadyOnTheFirstReading() =
        runTest {
            val readyAt = clock.now().plus(Duration.ofMinutes(50))
            hub(SensorState.WarmingUp(readyAt), latest = clock.now().minus(Duration.ofDays(1)))
            assertEquals("warmup", shown.last())
            clock.instant = readyAt.plusSeconds(60)
            notifier.check()
            assertEquals("almost-ready", shown.last())
            hub(SensorState.Active(readyAt.plus(Duration.ofDays(15))), latest = clock.now())
            assertEquals("ready", shown.last())
        }

    /** A new process on the same saved memory (an app update, Android's kill), as the app builds it. */
    private fun restarted(p: SensorEndPresenter = presenter) =
        SensorEndNotifier(store, p, { event, tick -> alarm = event to tick }, clock) { settings }

    /**
     * #102: what was announced is saved, so a restart never announces it again, also when the hub knows no sensor at
     * first (not read yet) and then the same one.
     */
    @Test fun aRestartNeverAnnouncesAStepAgain() =
        runTest {
            hub()
            wake()
            assertEquals(listOf("remind:ENDS"), shown)
            val again = restarted()
            again.update(true, SensorState.None, null, settings)
            again.update(true, SensorState.Active(end), clock.now().epochSecond, settings)
            again.check()
            assertEquals(1, shown.count { it.startsWith("remind") })

            clock.instant = end
            again.check()
            assertEquals("ended", shown.last())
            val third = restarted()
            third.update(true, SensorState.None, null, settings)
            third.update(true, SensorState.Ended, null, settings)
            third.check()
            assertEquals(1, shown.count { it == "ended" })
        }

    @Test fun aSwipedCountdownStaysAwayThroughARestart() =
        runTest {
            clock.instant = end - Duration.ofHours(2)
            hub()
            notifier.dismissed(warmup = false)
            shown.clear()
            val again = restarted()
            again.update(true, SensorState.None, null, settings)
            again.update(true, SensorState.Active(end), clock.now().epochSecond, settings)
            assertFalse(shown.any { it.startsWith("countdown") })
        }

    /** Saved before it is posted: a process killed while posting loses at most that post, it never rings twice. */
    @Test fun aStepIsSavedBeforeItIsPosted() =
        runTest {
            hub()
            clock.instant = end
            val killed =
                object : SensorEndPresenter by presenter {
                    override fun ended() = throw IllegalStateException("process killed")
                }
            runCatching { restarted(killed).check() }
            assertTrue(store.data.first().endedShown)
            restarted().check()
            assertFalse("ended" in shown)
        }

    /** No sensor right now: nothing shows and nothing is planned, also from the alarm, until a sensor is back. */
    @Test fun noSensorShowsNothingUntilASensorIsBack() =
        runTest {
            clock.instant = end - Duration.ofHours(2)
            hub()
            shown.clear()
            hub(SensorState.None)
            notifier.check()
            notifier.unlocked()
            assertTrue(shown.isEmpty())
            assertEquals(null, alarm?.first)
            hub(SensorState.Active(null)) // Juggluco before it knows its sensor: says nothing about this one
            assertTrue(shown.isEmpty())
            hub()
            assertTrue(shown.last().startsWith("countdown"))
        }

    /** #140: the end countdown's timeout at the end sends the swipe's intent; it is no swipe. */
    @Test fun theCountdownsTimeoutAtTheEndIsNoSwipe() =
        runTest {
            clock.instant = end - Duration.ofHours(2)
            hub()
            clock.instant = end
            notifier.dismissed(warmup = false)
            assertFalse(store.data.first().countdownDismissed)
        }

    /** #140: the countdown takes the place of the reminder from before its window. */
    @Test fun theCountdownRemovesTheEarlierReminder() =
        runTest {
            hub()
            wake() // the day before, 09:00
            cancelled.clear()
            wake() // the window opens
            assertTrue(shown.last().startsWith("countdown"))
            assertTrue(SensorNotice.REMINDER in cancelled)
        }

    /** #140: *Sensor ended* again every 2 hours (the default) until a new sensor. */
    @Test fun sensorEndedComesAgainUntilANewSensor() =
        runTest {
            hub()
            clock.instant = end
            notifier.check()
            assertEquals(end.plus(Duration.ofHours(2)).epochSecond, alarm?.first)
            wake()
            assertEquals(2, shown.count { it == "ended" })
            hub(SensorState.Active(end.plus(Duration.ofDays(15))))
            wake()
            assertEquals(2, shown.count { it == "ended" })
        }

    /** A sensor that failed on its first day, replaced 3 h after it started: the new one warms up and gets ready (#157). */
    @Test fun aReplacementOnTheFirstDayWarmsUpAgain() =
        runTest {
            val firstReady = clock.now().plus(Duration.ofMinutes(60))
            hub(SensorState.WarmingUp(firstReady, id = "A"), latest = clock.now().minus(Duration.ofDays(1)))
            clock.instant = firstReady.plusSeconds(60)
            hub(SensorState.Active(firstReady.plus(Duration.ofDays(15)), id = "A"), latest = clock.now())
            assertEquals("ready", shown.last())
            clock.instant = clock.now().plus(Duration.ofHours(2))
            shown.clear()
            val readyAt = clock.now().plus(Duration.ofMinutes(60))
            hub(SensorState.WarmingUp(readyAt, id = "B"), latest = clock.now().minus(Duration.ofMinutes(5)))
            assertEquals(listOf("warmup"), shown)
            clock.instant = readyAt.plusSeconds(60)
            hub(SensorState.Active(readyAt.plus(Duration.ofDays(15)), id = "B"), latest = clock.now())
            assertEquals("ready", shown.last())
        }

    @Test fun aFollowerGetsNothingAndForgets() =
        runTest {
            hub()
            hub(wearer = false)
            assertEquals(SensorEndMemory(), store.data.first())
            assertEquals(null, alarm?.first)
        }

    @Test fun noSensorClearsAndAlarmFindsNothing() =
        runTest {
            hub()
            hub(SensorState.None)
            notifier.check()
            assertTrue(shown.isEmpty())
            assertEquals(null, alarm?.first)
        }
}
