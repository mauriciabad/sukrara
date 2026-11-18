// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import app.mauri.sukrara.core.model.LifeSettings
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceProblem
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

/** The Nightscout source against a fake site (MockWebServer on loopback): no real Nightscout is ever contacted. */
class NightscoutSourceTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val clock = FakeClock(now, ZoneOffset.UTC)
    private val secure = InMemorySecureStore()
    private val treatments = InMemoryTreatmentsRepository()
    private val state = InMemoryStateStore(NsSourceState())
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var entries = "[]"
    private var siteTreatments = "[]"
    private var refuseExchange = false
    private var refuseWrites = false
    private val http = OkHttpClient()
    private lateinit var root: String

    private val config =
        object : NsSourceConfig {
            val flow = MutableStateFlow(NsSourceSettings())
            override val data: Flow<NsSourceSettings> = flow

            override suspend fun set(settings: NsSourceSettings) {
                flow.value = settings
            }
        }

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.path.orEmpty()
                    return when {
                        path.startsWith("/api/v2/authorization/request/") -> {
                            if (refuseExchange) {
                                MockResponse().setResponseCode(401)
                            } else {
                                MockResponse().setBody("""{"token":"jwt-1","exp":${now.epochSecond + 3600}}""")
                            }
                        }

                        path.startsWith("/api/v1/entries/sgv.json") -> {
                            MockResponse().setBody(entries)
                        }

                        path.startsWith("/api/v1/treatments.json") -> {
                            MockResponse().setBody(if (path.contains("identifier")) "[]" else siteTreatments)
                        }

                        request.method == "POST" && path.startsWith("/api/v1/treatments") -> {
                            if (refuseWrites) MockResponse().setResponseCode(403) else MockResponse().setBody("[]")
                        }

                        else -> {
                            MockResponse().setResponseCode(404)
                        }
                    }
                }
            }
        server.start()
        root = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() = server.shutdown()

    private fun account() = NightscoutAccount(secure, http, clock, Dispatchers.IO)

    /** The person's side of the sensor's life (the app's source file): whose glucose, the wear length, the manual start. */
    private val life = MutableStateFlow(LifeSettings(Person.Yours))

    private fun source(account: NightscoutAccount = account()) =
        NightscoutSource(config, account, state, treatments, { "Sukrara-aaaaaa" }, NetworkState.Always, http, clock, Dispatchers.IO, life)

    private fun entry(
        at: Instant,
        mgdl: Int,
        device: String = "xDrip-DexcomG7",
    ) = """{"type":"sgv","sgv":$mgdl,"date":${at.toEpochMilli()},"device":"$device"}"""

    private suspend fun connect(credential: String = "reader-0123456789abcdef"): NightscoutAccount {
        val a = account()
        val (site, r) = a.connect(root, credential)
        assertTrue("$r", r is NsConnectResult.Connected)
        config.set(NsSourceSettings(url = site!!.root))
        return a
    }

    @Test
    fun `connect saves the token only when the site answers, a refused one is never saved`() =
        runBlocking {
            refuseExchange = true
            val a = account()
            // a token that's refused, then tried as an API secret: the fake refuses the exchange but serves entries to
            // any api-secret, so the secret answers
            val (_, r) = a.connect(root, "reader-0123456789abcdef")
            assertTrue(r is NsConnectResult.Connected)
            assertTrue(requests.any { it.getHeader("api-secret") != null })
            refuseExchange = false
            assertEquals(NightscoutAccount.Saved.Readable, a.saved.value)
        }

    @Test
    fun `a pasted address with its token connects without typing it`() =
        runBlocking {
            val a = account()
            val (site, r) = a.connect("$root/?token=reader-0123456789abcdef", "")
            assertTrue(r is NsConnectResult.Connected)
            assertEquals(root, site!!.root)
            assertTrue(requests.any { it.path.orEmpty().startsWith("/api/v2/authorization/request/reader-0123456789abcdef") })
        }

    @Test
    fun `plain http to another device and a non-Nightscout answer are refused before saving`() =
        runBlocking {
            val a = account()
            assertEquals(NsConnectResult.NeedsHttps, a.connect("http://192.168.1.20:1337", "secret-1234567").second)
            entries = "<html>login</html>"
            assertEquals(NsConnectResult.NotNightscout, a.connect(root, "reader-0123456789abcdef").second)
            assertEquals(NightscoutAccount.Saved.None, a.saved.value)
        }

    @Test
    fun `the first call reads back 24 h and brings the site's readings, never this install's own`() =
        runBlocking {
            entries =
                "[${entry(
                    now.minusSeconds(60),
                    120,
                )},${entry(now.minusSeconds(120), 999, "Sukrara-aaaaaa")},${entry(now.minusSeconds(360), 118)}]"
            val s = source(connect())
            val batch = withTimeout(10_000) { s.readings.first() }
            assertEquals(listOf(118, 120), batch.map { it.mgdl })
            assertTrue(batch.all { it.source == SourceId.Nightscout })
            val read =
                requests.first { r ->
                    val p = URLDecoder.decode(r.path.orEmpty(), "UTF-8")
                    p.startsWith("/api/v1/entries/sgv.json") && p.contains("[\$gte]")
                }
            val from =
                URLDecoder
                    .decode(read.path, "UTF-8")
                    .substringAfter("[\$gte]=")
                    .substringBefore('&')
                    .toLong()
            assertEquals(now.minus(Duration.ofHours(24)).toEpochMilli(), from)
        }

    @Test
    fun `the site's Sensor Start gives the sensor's life, warming up and then ended`() =
        runBlocking {
            // started 30 min ago, the newest reading is older: warming up (the reminders, the card, no data read it)
            entries = "[${entry(now.minusSeconds(2400), 120)}]"
            siteTreatments = """[{"_id":"s1","created_at":"2026-11-18T11:30:00.000Z","eventType":"Sensor Start"}]"""
            val s = source(connect())
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            val warming = withTimeout(10_000) { s.sensor.first { it is SensorState.WarmingUp } }
            assertEquals(Instant.parse("2026-11-18T12:30:00Z"), (warming as SensorState.WarmingUp).readyAt)
            assertEquals("s1", withTimeout(10_000) { s.sensorLife.first { it != null } }!!.id)
            job.cancel()
            // 16 days later the default wear is over: ended, so no data stays quiet
            clock.advance(Duration.ofDays(16))
            entries = "[${entry(clock.now().minusSeconds(60), 110)}]"
            val again = launch(Dispatchers.IO) { s.readings.collect { } }
            withTimeout(10_000) { s.sensor.first { it == SensorState.Ended } }
            again.cancel()
        }

    @Test
    fun `no Sensor Start on the site, a manual start gives the life at once, its length the wear setting`() =
        runBlocking {
            entries = "[${entry(now.minusSeconds(60), 120)}]"
            val s = source(connect())
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            // nothing known: an active sensor whose end isn't known (never Sensor ended, no reminders)
            assertEquals(SensorState.Active(null), withTimeout(10_000) { s.sensor.first { it is SensorState.Active } })
            assertEquals(null, s.sensorLife.first())
            // set 3 days ago with 10 days (a Dexcom G7): it ends in 7 days
            val start = now.minus(Duration.ofDays(3))
            life.value = LifeSettings(Person.Yours, 10 * 1440, start.epochSecond)
            val active = withTimeout(10_000) { s.sensor.first { it is SensorState.Active && it.endsAt != null } } as SensorState.Active
            assertEquals(start.plus(Duration.ofDays(10)), active.endsAt)
            assertEquals(true, s.sensorLife.first()!!.manual)
            // a start whose length is over: ended (no data stays quiet)
            life.value = LifeSettings(Person.Yours, 2 * 1440, start.epochSecond)
            withTimeout(10_000) { s.sensor.first { it == SensorState.Ended } }
            // a start ahead of now (the clock moved back): not used, nothing claims the sensor ended
            life.value = LifeSettings(Person.Yours, 10 * 1440, now.plusSeconds(600).epochSecond)
            assertEquals(SensorState.Active(null), withTimeout(10_000) { s.sensor.first { it == SensorState.Active(null) } })
            job.cancel()
        }

    @Test
    fun `the site's newer Sensor Start wins over a manual start, someone else's glucose never takes one`() =
        runBlocking {
            entries = "[${entry(now.minusSeconds(60), 120)}]"
            siteTreatments = """[{"_id":"s2","created_at":"2026-11-17T12:00:00.000Z","eventType":"Sensor Start"}]"""
            life.value = LifeSettings(Person.Yours, 0, now.minus(Duration.ofDays(3)).epochSecond)
            val s = source(connect())
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            // the site's Sensor Start (yesterday) is newer than the manual one (3 days ago): a new sensor from the source
            val site = withTimeout(10_000) { s.sensorLife.first { it?.id == "s2" } }!!
            assertEquals(false, site.manual)
            assertEquals(Instant.parse("2026-11-17T12:00:00Z").plus(Duration.ofDays(15)), site.end)
            // a manual start after the site's: yours wins (a sensor the site doesn't know yet)
            life.value = LifeSettings(Person.Yours, 0, now.minusSeconds(3600).epochSecond)
            assertEquals(true, withTimeout(10_000) { s.sensorLife.first { it?.manual == true } }!!.manual)
            // someone else's glucose: their reminders are theirs, a manual start is never used
            life.value = LifeSettings(Person.Theirs, 0, now.minusSeconds(3600).epochSecond)
            assertEquals("s2", withTimeout(10_000) { s.sensorLife.first { it?.manual == false } }!!.id)
            job.cancel()
        }

    @Test
    fun `a refused token shows as signed out`() =
        runBlocking {
            val a = connect()
            refuseExchange = true
            val s = source(a)
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            val status = withTimeout(10_000) { s.status.first { it is SourceStatus.Interrupted } }
            job.cancel()
            assertEquals(SourceProblem.SignedOut, (status as SourceStatus.Interrupted).problem)
        }

    @Test
    fun `treatments go both ways, the site's arrive read-only and this phone's go up by identifier`() =
        runBlocking {
            entries = "[${entry(now.minusSeconds(60), 120)}]"
            siteTreatments =
                """[{"_id":"a1","created_at":"2026-11-18T11:50:00.000Z","eventType":"Meal Bolus","insulin":4.5,"carbs":40,"enteredBy":"AndroidAPS"}]"""
            val own =
                Treatment(
                    "6f1c2b9a-0d3e-4c5f-8a7b-0123456789ab",
                    TreatmentKind.RapidInsulin,
                    2.0,
                    now.minusSeconds(300),
                    modifiedAt = now.minusSeconds(290),
                    origin = TreatmentOrigin.Phone,
                )
            treatments.upsertIfNewer(TreatmentRecord(own))
            val s = source(connect())
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            withTimeout(10_000) { state.data.first { it.ledger.pushed.isNotEmpty() } }
            job.cancel()
            val pulled =
                treatments.all
                    .filter { it.fromSite }
                    .map { it.treatment.kind to it.treatment.amount }
                    .toSet()
            assertEquals(setOf(TreatmentKind.RapidInsulin to 4.5, TreatmentKind.Carbs to 40.0), pulled)
            val post = requests.first { it.method == "POST" && it.path.orEmpty().startsWith("/api/v1/treatments") }
            assertTrue(post.body.readUtf8().contains(own.id))
            assertEquals(
                own.modifiedAt.toEpochMilli(),
                state.data
                    .first()
                    .ledger.pushed[own.id],
            )
        }

    @Test
    fun `a token that can only read keeps pulling and stops sending, said on the page`() =
        runBlocking {
            refuseWrites = true
            entries = "[${entry(now.minusSeconds(60), 120)}]"
            treatments.upsertIfNewer(
                TreatmentRecord(
                    Treatment(
                        "0b7d2f4e-9a61-4c3b-8e2d-5f6a7b8c9d0e",
                        TreatmentKind.Carbs,
                        10.0,
                        now.minusSeconds(200),
                        modifiedAt = now.minusSeconds(190),
                        origin = TreatmentOrigin.Phone,
                    ),
                ),
            )
            val s = source(connect())
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            withTimeout(10_000) { state.data.first { it.readOnly } }
            assertEquals(NsTreatments.ReadOnly, withTimeout(10_000) { s.view.first { it.treatments == NsTreatments.ReadOnly } }.treatments)
            job.cancel()
        }
}
