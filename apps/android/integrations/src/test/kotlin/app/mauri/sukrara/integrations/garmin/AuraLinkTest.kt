// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AuraLinkTest {
    private val clock = FakeClock(Instant.parse("2026-11-16T10:09:36Z"))

    /** Garmin Connect as the test sets it: the watches, whether the face is on them, what each send ends in. */
    private class FakePort : ConnectIqPort {
        var gcm = true
        var watches: List<IqDevice>? = listOf(IqDevice(7, "Descent Mk3i 43mm", true))
        var face = true
        var version: Int? = 3
        var answers = ArrayDeque<IqSend>()

        /** What a send ends in once [answers] runs out. */
        var otherwise: IqSend = IqSend.Delivered
        val sent = ArrayList<Map<*, *>>()

        override fun installed() = gcm

        override suspend fun devices() = watches

        override suspend fun appVersion(
            device: IqDevice,
            app: String,
        ): Int? = if (face) version else null

        override suspend fun send(
            device: IqDevice,
            app: String,
            message: ByteArray,
        ): IqSend {
            sent += MonkeyCDecode.decode(message) as Map<*, *>
            return answers.removeFirstOrNull() ?: otherwise
        }
    }

    private fun state(vararg mgdl: Pair<Long, Int>) =
        GlucoseState(
            spec = SourceSpec.YourSensor,
            latest = mgdl.lastOrNull()?.let { reading(it) },
            recent = mgdl.map(::reading),
            sensor = SensorState.Active(clock.now() + Duration.ofDays(5)),
            source = SourceStatus.NotStarted,
            alerts = AlertUiState(),
            link = null,
            monitoringStartedAt = clock.now() - Duration.ofDays(1),
        )

    private fun reading(p: Pair<Long, Int>) = Reading(clock.now().plusSeconds(p.first), p.second, null, SourceId.Libre3)

    private fun TestScope.link(
        port: FakePort,
        glucose: MutableStateFlow<GlucoseState>,
        settings: InMemorySettingsRepository = InMemorySettingsRepository(),
    ): AuraLink {
        val d = StandardTestDispatcher(testScheduler)
        return AuraLink(settings, glucose, InMemoryTreatmentsRepository(), InMemoryReadingsRepository(), port, clock, d, {}, fetchPort = 0)
            .also { it.start(backgroundScope) }
    }

    /** Past the link's settle time: a changed payload is sent. */
    private fun TestScope.settle() {
        advanceTimeBy(AuraLink.SETTLE_MS + 1)
        runCurrent()
    }

    @Test
    fun aReadingThatChangesThePayloadTwiceSendsOneMessage() =
        runTest {
            val port = FakePort()
            val glucose = MutableStateFlow(state(-30L to 112))
            link(port, glucose)
            settle()
            assertEquals(1, port.sent.size)
            clock.advance(Duration.ofMinutes(1))
            glucose.value = state(-90L to 112, -30L to 115)
            advanceTimeBy(1_000)
            glucose.value = glucose.value.copy(alerts = AlertUiState(status = CardStatus.Needed(false)))
            settle()
            assertEquals(2, port.sent.size)
            assertEquals("needed", (port.sent[1]["st"] as Map<*, *>)["k"])
        }

    @Test
    fun everyNewReadingGoesToTheWatchWithTheFace() =
        runTest {
            val port = FakePort()
            val glucose = MutableStateFlow(state(-30L to 112))
            val link = link(port, glucose)
            settle()
            assertEquals(1, port.sent.size)
            assertEquals(112, (port.sent[0]["g"] as Map<*, *>)["v"])
            val w =
                link.state.value.watches
                    .single()
            assertEquals(clock.now(), w.delivered)
            assertTrue(w.installed == true)

            clock.advance(Duration.ofMinutes(1))
            glucose.value = state(-90L to 112, -30L to 115)
            settle()
            assertEquals(2, port.sent.size)
            assertEquals(115, (port.sent[1]["g"] as Map<*, *>)["v"])
        }

    @Test
    fun theSameStateIsNotSentTwice() =
        runTest {
            val port = FakePort()
            val same = state(-30L to 112)
            val glucose = MutableStateFlow(same)
            link(port, glucose)
            settle()
            clock.advance(Duration.ofSeconds(20))
            glucose.value = same.copy()
            settle()
            assertEquals(1, port.sent.size)
        }

    @Test
    fun aLostMessageIsRetriedWithBackoffUntilItArrives() =
        runTest {
            val port =
                FakePort().apply { answers.addAll(listOf(IqSend.NoAnswer, IqSend.Failed(IqSend.DURING_TRANSFER), IqSend.Delivered)) }
            val link = link(port, MutableStateFlow(state(-30L to 112)))
            settle()
            assertEquals(1, port.sent.size)
            assertTrue(
                link.state.value.watches
                    .single()
                    .noAnswer,
            )
            assertNull(
                link.state.value.watches
                    .single()
                    .delivered,
            )
            advanceTimeBy(15_001)
            assertEquals(2, port.sent.size)
            advanceTimeBy(29_000)
            assertEquals(2, port.sent.size)
            advanceTimeBy(1_001)
            assertEquals(3, port.sent.size)
            val w =
                link.state.value.watches
                    .single()
            assertFalse(w.noAnswer)
            assertTrue(w.delivered != null)
        }

    @Test
    fun stuckMessagesAreTriedEveryFiveMinutesUntilOneArrives() =
        runTest {
            val port = FakePort().apply { otherwise = IqSend.NoAnswer }
            val glucose = MutableStateFlow(state(-30L to 112))
            val link = link(port, glucose)
            val watch = {
                link.state.value.watches
                    .single()
            }
            settle()
            assertEquals(1, port.sent.size)
            // 16 min later it is stuck, but its last try was long ago: this reading goes
            clock.advance(Duration.ofMinutes(16))
            glucose.value = state(-30L to 113)
            settle()
            assertEquals(2, port.sent.size)
            assertTrue(watch().stuck(clock.now()))
            // a minute later: no try for this reading, nor retries
            clock.advance(Duration.ofMinutes(1))
            glucose.value = state(-30L to 114)
            settle()
            advanceTimeBy(30_000)
            assertEquals(2, port.sent.size)
            // Send now tries at once
            link.sendNow()
            runCurrent()
            assertEquals(3, port.sent.size)
            // 5 min after the last try the next reading goes; once one arrives, every reading goes again
            port.otherwise = IqSend.Delivered
            clock.advance(Duration.ofMinutes(5))
            glucose.value = state(-30L to 115)
            settle()
            assertEquals(4, port.sent.size)
            assertFalse(watch().stuck(clock.now()))
            clock.advance(Duration.ofMinutes(1))
            glucose.value = state(-30L to 116)
            settle()
            assertEquals(5, port.sent.size)
        }

    @Test
    fun theLogNamesGarminsFailure() {
        assertEquals("Failed FAILURE_DURING_TRANSFER", IqSend.Failed(5).toString())
        assertEquals("Failed FAILURE_DEVICE_NOT_CONNECTED", IqSend.Failed(IqSend.DEVICE_NOT_CONNECTED).toString())
        assertEquals("Failed -1", IqSend.Failed(-1).toString())
        assertEquals("NoAnswer", IqSend.NoAnswer.toString())
    }

    @Test
    fun aWatchWithoutTheFaceGetsNothing() =
        runTest {
            val port = FakePort().apply { face = false }
            val link = link(port, MutableStateFlow(state(-30L to 112)))
            settle()
            assertEquals(0, port.sent.size)
            assertEquals(
                false,
                link.state.value.watches
                    .single()
                    .installed,
            )
            assertEquals(AuraStatus.NotInstalled, AuraStatus.of(link.state.value, clock.now()))
        }

    @Test
    fun aWatchAwayGetsNothingUntilItIsBack() =
        runTest {
            val port = FakePort().apply { watches = listOf(IqDevice(7, "Descent", false)) }
            val link = link(port, MutableStateFlow(state(-30L to 112)))
            settle()
            assertEquals(0, port.sent.size)
            port.watches = listOf(IqDevice(7, "Descent", true))
            advanceTimeBy(60_001)
            assertEquals(1, port.sent.size)
            assertTrue(
                link.state.value.watches
                    .single()
                    .connected,
            )
        }

    @Test
    fun offSendsNothing() =
        runTest {
            val settings = InMemorySettingsRepository()
            settings.set(AuraLink.SETTING, false)
            val port = FakePort()
            val link = link(port, MutableStateFlow(state(-30L to 112)), settings)
            settle()
            assertEquals(0, port.sent.size)
            assertEquals(AuraStatus.Off, AuraStatus.of(link.state.value, clock.now()))
        }

    @Test
    fun theFetchOpensOnlyWhileAWatchHasTheFace() =
        runTest {
            val port = FakePort()
            val link = link(port, MutableStateFlow(state(-30L to 112)))
            settle()
            assertTrue(link.state.value.fetchOpen)
            link.stop()
        }

    @Test
    fun noFaceNoFetch() =
        runTest {
            val port = FakePort().apply { face = false }
            val link = link(port, MutableStateFlow(state(-30L to 112)))
            settle()
            assertFalse(link.state.value.fetchOpen)
        }

    @Test
    fun withoutAnAnswerAboutTheFaceItStillPushesAndOpensTheFetchOnceAPushArrives() =
        runTest {
            val port = FakePort().apply { version = ConnectIqPort.NO_ANSWER }
            port.answers.add(IqSend.NoAnswer)
            val link = link(port, MutableStateFlow(state(-30L to 112)))
            settle()
            assertEquals(1, port.sent.size)
            assertNull(
                link.state.value.watches
                    .single()
                    .installed,
            )
            assertFalse(link.state.value.fetchOpen)
            advanceTimeBy(15_001)
            assertEquals(2, port.sent.size)
            assertTrue(link.state.value.fetchOpen)
            link.stop()
        }

    @Test
    fun theFetchRule() {
        val face = AuraLinkState(garminConnect = true, watches = listOf(AuraWatch(1, "D", true, true)))
        assertTrue(AuraLink.fetchOpen(true, face))
        assertFalse(AuraLink.fetchOpen(false, face))
        assertFalse(AuraLink.fetchOpen(true, face.copy(garminConnect = false)))
        assertFalse(AuraLink.fetchOpen(true, face.copy(watches = listOf(AuraWatch(1, "D", true, null)))))
        assertFalse(AuraLink.fetchOpen(true, face.copy(watches = listOf(AuraWatch(1, "D", true, false)))))
        // a watch away that has the face keeps it open (the face fetches as soon as it is back)
        assertTrue(AuraLink.fetchOpen(true, face.copy(watches = listOf(AuraWatch(1, "D", false, true)))))
    }

    @Test
    fun theFacesReport() {
        val now = clock.now()
        val r =
            AuraReport.parse(
                mapOf(
                    "r" to "p.1794831700.1794831697.1794831696_fx.1794831720.1794831600.1794831590_e-104.1794831800.0.0_bad",
                    "d" to "1794831696.1794831701",
                ),
                now,
            )!!
        assertEquals(
            listOf(
                AuraRun("p", 1794831700, 1794831697, 1794831696),
                AuraRun("fx", 1794831720, 1794831600, 1794831590),
                AuraRun("e-104", 1794831800, 0, 0),
            ),
            r.runs,
        )
        assertEquals(Instant.ofEpochSecond(1794831696), r.drawn)
        // the newest it kept: the push, not the older fetch it refused nor the failed one
        assertEquals(Instant.ofEpochSecond(1794831696), r.has)
        assertEquals(Instant.ofEpochSecond(1794831701), r.drawnSince)
        assertNull(AuraReport.parse(mapOf("x" to "1"), now))
        assertEquals(null, AuraReport.parse(mapOf("r" to "", "d" to "junk"), now)!!.drawn)
    }

    @Test
    fun theFetchIsThePayloadAsJson() {
        val p = mapOf("v" to 1, "g" to mapOf("rate" to 0.4f, "band" to null), "hist" to listOf(100, null), "ok" to true, "u" to "mgdl")
        assertEquals("""{"v":1,"g":{"rate":0.4,"band":null},"hist":[100,null],"ok":true,"u":"mgdl"}""", AuraLink.json(p).toString())
    }
}
