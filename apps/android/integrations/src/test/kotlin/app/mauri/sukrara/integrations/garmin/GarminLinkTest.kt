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
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.TreatmentQuiet
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.graph.GraphSettings
import app.mauri.sukrara.graph.LastTreatment
import app.mauri.sukrara.graph.RingSpec
import app.mauri.sukrara.graph.WatchDisplay
import app.mauri.sukrara.graph.id
import app.mauri.sukrara.graph.muteRuleOf
import app.mauri.sukrara.integrations.localserver.HttpAnswer
import app.mauri.sukrara.integrations.localserver.HttpRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GarminLinkTest {
    private val clock = FakeClock(Instant.parse("2026-11-16T10:09:36Z"))

    /** Garmin Connect as the test sets it: the watches, whether the face is on them, what each send ends in. */
    private class FakePort : ConnectIqPort {
        var gcm = true
        var watches: List<IqDevice>? = listOf(IqDevice(7, "Descent Mk3i 43mm", true))
        var face = true

        /** The Connect IQ app ids on the watch ([face] off = none). */
        var apps = mutableSetOf(GarminApp.Aura.id)
        var version: Int? = 3
        var answers = ArrayDeque<IqSend>()

        /** What a send ends in once [answers] runs out; per app id in [answersFor] first. */
        var otherwise: IqSend = IqSend.Delivered
        val answersFor = HashMap<String, ArrayDeque<IqSend>>()
        val sent = ArrayList<Map<*, *>>()
        val sentTo = ArrayList<String>()

        override fun installed() = gcm

        override suspend fun devices() = watches

        override suspend fun appVersion(
            device: IqDevice,
            app: String,
        ): Int? = if (face && app in apps) version else null

        override suspend fun send(
            device: IqDevice,
            app: String,
            message: ByteArray,
        ): IqSend {
            sent += MonkeyCDecode.decode(message) as Map<*, *>
            sentTo += app
            return answersFor[app]?.removeFirstOrNull() ?: answers.removeFirstOrNull() ?: otherwise
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
        treatments: InMemoryTreatmentsRepository = InMemoryTreatmentsRepository(),
        key: WatchKey? = null,
        own: Map<Long, WatchDisplay> = emptyMap(),
    ): GarminLink {
        val d = StandardTestDispatcher(testScheduler)
        return GarminLink(
            settings,
            glucose,
            treatments,
            InMemoryReadingsRepository(),
            port,
            clock,
            d,
            {},
            fetchPort = 0,
            key = key,
            watchDisplays = flowOf(own),
        ).also { it.start(backgroundScope) }
    }

    @Test
    fun eachWatchGetsTheAppWithItsOwnValuesAndItsId() =
        runTest {
            val port =
                FakePort().apply {
                    watches = listOf(IqDevice(7, "Descent Mk3i 43mm", true), IqDevice(8, "Venu 3", true))
                    apps = mutableSetOf(GarminApp.App.id)
                }
            val own = WatchDisplay(hours = 3, side = false, reportDays = 7, ringOrder = RingSpec.DEFAULT_RING_ORDER.reversed())
            val link = link(port, MutableStateFlow(state(-30L to 112)), own = mapOf(8L to own))
            settle()
            val byWatch = port.sent.associateBy { it["w"] }
            assertEquals(setOf("7", "8"), byWatch.keys)
            val ds7 = byWatch.getValue("7")["ds"] as Map<*, *>
            val ds8 = byWatch.getValue("8")["ds"] as Map<*, *>
            // watch 7 follows the app; watch 8 has its own chart time, % column, report period and ring order
            assertEquals(12, ds7["hours"])
            assertEquals(true, ds7["side"])
            assertEquals(14, byWatch.getValue("7")["repD"])
            assertEquals(3, ds8["hours"])
            assertEquals(false, ds8["side"])
            assertEquals(7, byWatch.getValue("8")["repD"])
            assertEquals(RingSpec.DEFAULT_RING_ORDER.reversed().map { it.ordinal }, byWatch.getValue("8")["ro"])

            // the fetch: the asking watch's own values by its w; never w itself
            fun fetch(w: String?) =
                link.answer(HttpRequest("GET", "sukrara/app", w?.let { mapOf("w" to it) } ?: emptyMap(), emptyMap())).body
            assertTrue(fetch("8").contains("\"repD\":7"))
            assertTrue(fetch("7").contains("\"repD\":14"))
            assertFalse(fetch("8").contains("\"w\":\"8\""))
            // without w and two watches with the app: the app's own values
            assertTrue(fetch(null).contains("\"repD\":14"))
            link.stop()
        }

    /** Watch 7's own *Last treatment* [own7] over the app's [app]; watch 8 the same as the app: what each one's app and face get. */
    private suspend fun TestScope.lastByWatch(
        app: Boolean,
        own7: Boolean,
    ): FakePort {
        val port =
            FakePort().apply {
                watches = listOf(IqDevice(7, "Descent Mk3i 43mm", true), IqDevice(8, "Venu 3", true))
                apps = mutableSetOf(GarminApp.App.id, GarminApp.Aura.id)
            }
        val settings = InMemorySettingsRepository()
        settings.set(GraphSettings.Last, app)
        val treatments = InMemoryTreatmentsRepository()
        treatments.upsertIfNewer(TreatmentRecord(treatment("r1", TreatmentKind.RapidInsulin, 3.0, 600)))
        link(port, MutableStateFlow(state(-30L to 180)), settings, treatments, own = mapOf(7L to WatchDisplay(lastOn = own7)))
        return port
    }

    private fun FakePort.appLast(w: String) = sent.last { it["p"] == GarminApp.App.key && it["w"] == w }["last"]

    @Test
    fun theAppsLastTreatmentFollowsTheWatchsOwnSwitch() =
        runTest {
            // watch 7 has its own Last treatment off, the app's is on: no last on 7, the app's rule on 8
            val off = lastByWatch(app = true, own7 = false)
            settle()
            assertNull(off.appLast("7"))
            assertEquals(listOf("rapid"), ((off.appLast("8") as Map<*, *>)["items"] as List<*>).map { (it as Map<*, *>)["k"] })
            // the face's treatment field is its own: it travels whatever the card's switch says
            assertTrue(off.sent.filter { it["p"] == GarminApp.Aura.key }.all { it["last"] != null })
        }

    @Test
    fun aWatchsOwnLastTreatmentOnShowsItWhileTheAppsIsOff() =
        runTest {
            val on = lastByWatch(app = false, own7 = true)
            settle()
            assertNotNull(on.appLast("7"))
            assertNull(on.appLast("8"))
        }

    @Test
    fun aFetchWithoutItsIdIsTheOneWatchWithTheApp() =
        runTest {
            val port = FakePort().apply { apps = mutableSetOf(GarminApp.App.id) }
            val link = link(port, MutableStateFlow(state(-30L to 112)), own = mapOf(7L to WatchDisplay(reportDays = 30)))
            settle()
            val body = link.answer(HttpRequest("GET", "sukrara/app", emptyMap(), emptyMap())).body
            assertTrue(body.contains("\"repD\":30"))
            link.stop()
        }

    @Test
    fun eachSukraraAppGetsItsOwnProfileAndOnlyTheAppTheKey() =
        runTest {
            val port = FakePort().apply { apps = mutableSetOf(GarminApp.Aura.id, GarminApp.App.id, GarminApp.Field.beta) }
            val key = WatchKey(InMemorySecureStore())
            val link = link(port, MutableStateFlow(state(-30L to 112)), key = key)
            settle()
            // the field's beta id is the one on the watch: it is pushed to, its public id isn't
            assertEquals(listOf(GarminApp.Aura.id, GarminApp.App.id, GarminApp.Field.beta), port.sentTo)
            assertEquals(listOf("aura", "app", "field"), port.sent.map { it["p"] })
            assertTrue(port.sent.all { it["v"] == WatchPayload.VERSION })
            assertEquals(WatchKey.hex(key.get()!!), port.sent[1]["key"])
            assertNull(port.sent[0]["key"])
            assertNull(port.sent[2]["key"])
            assertEquals(288, (port.sent[1]["hist"] as List<*>).size)
            assertEquals(36, (port.sent[2]["hist"] as List<*>).size)
            val w =
                link.state.value.watches
                    .single()
            assertEquals(true, w.has(GarminApp.App))
            assertEquals(true, w.has(GarminApp.Field))
            assertTrue(link.state.value.fetchOpen)
            // the fetch: each app's own profile, never the key
            val app = link.answer(HttpRequest("GET", "sukrara/app", emptyMap(), emptyMap()))
            assertTrue(app.status.startsWith("200"))
            assertTrue(app.body.contains("\"p\":\"app\""))
            assertFalse(app.body.contains("\"key\""))
            assertTrue(link.answer(HttpRequest("GET", "sukrara/field", emptyMap(), emptyMap())).body.contains("\"p\":\"field\""))
            assertEquals(HttpAnswer.NOT_FOUND, link.answer(HttpRequest("GET", "sukrara/other", emptyMap(), emptyMap())))
            link.stop()
        }

    @Test
    fun aFailedAppIsRetriedAloneAndTheOthersAreNotSentAgain() =
        runTest {
            val port =
                FakePort().apply {
                    apps = mutableSetOf(GarminApp.Aura.id, GarminApp.App.id)
                    answersFor[GarminApp.App.id] = ArrayDeque(listOf(IqSend.Failed(IqSend.DURING_TRANSFER)))
                }
            val link = link(port, MutableStateFlow(state(-30L to 112)))
            settle()
            assertEquals(listOf(GarminApp.Aura.id, GarminApp.App.id), port.sentTo)
            val w = {
                link.state.value.watches
                    .single()
            }
            assertTrue(w().link(GarminApp.App)!!.failed != null)
            assertTrue(w().link(GarminApp.Aura)!!.delivered != null)
            // the watch line: a delivery to one app is a delivery
            assertNull(w().failingSince)
            advanceTimeBy(15_001)
            assertEquals(listOf(GarminApp.Aura.id, GarminApp.App.id, GarminApp.App.id), port.sentTo)
            assertTrue(w().link(GarminApp.App)!!.delivered != null)
            link.stop()
        }

    @Test
    fun aWatchWithOnlyTheDataFieldOpensThePort() =
        runTest {
            val port = FakePort().apply { apps = mutableSetOf(GarminApp.Field.id) }
            val link = link(port, MutableStateFlow(state(-30L to 112)))
            settle()
            assertEquals(listOf(GarminApp.Field.id), port.sentTo)
            assertTrue(link.state.value.fetchOpen)
            assertEquals(
                false,
                link.state.value.watches
                    .single()
                    .has(GarminApp.Aura),
            )
            link.stop()
        }

    private fun treatment(
        id: String,
        kind: TreatmentKind,
        amount: Double,
        agoS: Long,
    ): Treatment {
        val at = clock.now().minusSeconds(agoS)
        return Treatment(id, kind, amount, at, modifiedAt = at, origin = TreatmentOrigin.Phone)
    }

    /** What the face shows at [s] (phone time) from a payload's `last`: README "On the watch". */
    private fun shown(
        last: Map<*, *>?,
        s: Long,
    ): List<Pair<Any?, Double>>? {
        var step = last
        while (step != null) {
            val until = step["until"] as Int
            if (until == WatchPayload.NEVER || s < until) {
                return (step["items"] as List<*>).map { (it as Map<*, *>)["k"] to (it["a"] as Float).toDouble() }
            }
            step = step["next"] as Map<*, *>?
        }
        return null
    }

    /** Past the link's settle time: a changed payload is sent. */
    private fun TestScope.settle() {
        advanceTimeBy(GarminLink.SETTLE_MS + 1)
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
    fun aLoggedDoseGoesToTheWatchWithItsEnd() =
        runTest {
            val port = FakePort()
            val treatments = InMemoryTreatmentsRepository()
            treatments.upsertIfNewer(TreatmentRecord(treatment("r1", TreatmentKind.RapidInsulin, 3.0, 600)))
            val quiet = AlertUiState(treatmentQuiet = TreatmentQuiet(rapidS = 3600, carbsS = 900))
            link(port, MutableStateFlow(state(-30L to 180).copy(alerts = quiet)), treatments = treatments)
            settle()
            val last = port.sent.single()["last"] as Map<*, *>
            assertEquals(listOf("rapid" to 3.0f), (last["items"] as List<*>).map { (it as Map<*, *>)["k"] to it["a"] })
            // the card keeps rapid insulin 4 h: the watch lets it go then by itself
            assertEquals((clock.now().epochSecond - 600 + 4 * 3600).toInt(), last["until"])
            assertNull(last["next"])
        }

    @Test
    fun theFacesTreatmentIsTheCardsSlotAtEveryMoment() {
        val now = clock.now().epochSecond
        val quiet = TreatmentQuiet(rapidS = 3600, carbsS = 900)
        val rapid = TreatmentKind.RapidInsulin
        val carbs = TreatmentKind.Carbs
        val cases =
            listOf(
                // a meal: insulin and carbs together
                Triple(quiet, CardStatus.None, listOf(treatment("r", rapid, 4.5, 600), treatment("c", carbs, 45.0, 600))),
                // a correction, then snacks: the carbs ride along while the insulin's quiet runs, then the newest kept
                Triple(
                    quiet,
                    CardStatus.None,
                    listOf(treatment("r", rapid, 2.0, 1800), treatment("c1", carbs, 60.0, 3000), treatment("c2", carbs, 20.0, 600)),
                ),
                // carbs covering a low past their own keep
                Triple(quiet, CardStatus.Taken(carbs, now - 300, now + 2400), listOf(treatment("c", carbs, 5.0, 300))),
                // no quiet rules: only the keeps; long-acting and notes never
                Triple(
                    TreatmentQuiet(),
                    CardStatus.None,
                    listOf(
                        treatment("l", TreatmentKind.LongInsulin, 18.0, 60),
                        treatment("n", TreatmentKind.Note, 0.0, 30),
                        treatment("r", rapid, 1.0, 9000),
                        treatment("c", carbs, 80.0, 3600),
                    ),
                ),
                Triple(quiet, CardStatus.None, listOf(treatment("l", TreatmentKind.LongInsulin, 18.0, 60))),
            )
        for ((q, status, trs) in cases) {
            val state = state(-30L to 120).copy(alerts = AlertUiState(status = status, treatmentQuiet = q))
            val last = WatchPayload.last(state, trs, now)
            val ends =
                generateSequence<Map<*, *>>(
                    last,
                ) { it["next"] as Map<*, *>? }.map { (it["until"] as Int).toLong() }.filter { it > 0 }
            val moments = (0..8 * 60).map { now + it * 60L } + ends.flatMap { sequenceOf(it - 1, it) }
            for (s in moments) {
                // the alert runtime's status as the watch counts it: a quiet after a treatment ends at its end
                val st = if (status is CardStatus.Taken && s >= status.until) CardStatus.None else status
                val card = LastTreatment.of(trs, muteRuleOf(state.alerts), st, s)?.let { i -> listOfNotNull(i.tr, i.carbs?.tr) }
                assertEquals("${trs.map { it.id }} at +${s - now} s", card?.map { it.kind.id to it.amount }, shown(last, s))
            }
        }
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
            assertEquals(GarminStatus.NotInstalled, GarminStatus.of(link.state.value, clock.now()))
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
            settings.set(GarminLink.SETTING, false)
            val port = FakePort()
            val link = link(port, MutableStateFlow(state(-30L to 112)), settings)
            settle()
            assertEquals(0, port.sent.size)
            assertEquals(GarminStatus.Off, GarminStatus.of(link.state.value, clock.now()))
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
        val face = GarminLinkState(garminConnect = true, watches = listOf(GarminWatch(1, "D", true, true)))
        assertTrue(GarminLink.fetchOpen(true, face))
        assertFalse(GarminLink.fetchOpen(false, face))
        assertFalse(GarminLink.fetchOpen(true, face.copy(garminConnect = false)))
        assertFalse(GarminLink.fetchOpen(true, face.copy(watches = listOf(GarminWatch(1, "D", true, null)))))
        assertFalse(GarminLink.fetchOpen(true, face.copy(watches = listOf(GarminWatch(1, "D", true, false)))))
        // a watch away that has the face keeps it open (the face fetches as soon as it is back)
        assertTrue(GarminLink.fetchOpen(true, face.copy(watches = listOf(GarminWatch(1, "D", false, true)))))
    }

    @Test
    fun theFacesReport() {
        val now = clock.now()
        val r =
            LinkReport.parse(
                mapOf(
                    "r" to "p.1794831700.1794831697.1794831696_fx.1794831720.1794831600.1794831590_e-104.1794831800.0.0_bad",
                    "d" to "1794831696.1794831701",
                ),
                now,
            )!!
        assertEquals(
            listOf(
                LinkRun("p", 1794831700, 1794831697, 1794831696),
                LinkRun("fx", 1794831720, 1794831600, 1794831590),
                LinkRun("e-104", 1794831800, 0, 0),
            ),
            r.runs,
        )
        assertEquals(Instant.ofEpochSecond(1794831696), r.drawn)
        // the newest it kept: the push, not the older fetch it refused nor the failed one
        assertEquals(Instant.ofEpochSecond(1794831696), r.has)
        assertEquals(Instant.ofEpochSecond(1794831701), r.drawnSince)
        assertNull(LinkReport.parse(mapOf("x" to "1"), now))
        assertEquals(null, LinkReport.parse(mapOf("r" to "", "d" to "junk"), now)!!.drawn)
    }

    @Test
    fun theFetchIsThePayloadAsJson() {
        val p = mapOf("v" to 1, "g" to mapOf("rate" to 0.4f, "band" to null), "hist" to listOf(100, null), "ok" to true, "u" to "mgdl")
        assertEquals("""{"v":1,"g":{"rate":0.4,"band":null},"hist":[100,null],"ok":true,"u":"mgdl"}""", GarminLink.json(p).toString())
    }
}
