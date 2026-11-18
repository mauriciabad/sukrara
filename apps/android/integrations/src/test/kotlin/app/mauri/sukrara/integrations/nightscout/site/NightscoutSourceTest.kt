// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

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

    private fun source(account: NightscoutAccount = account()) =
        NightscoutSource(config, account, state, treatments, { "Sukrara-aaaaaa" }, NetworkState.Always, http, clock, Dispatchers.IO)

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
