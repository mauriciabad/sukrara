// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.librelinkup

import app.mauri.sukrara.core.data.libreview.LibreLinkUp
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceProblem
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.testing.Contracts
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.integrations.nightscout.site.NetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The LibreLinkUp source against a fake LibreLinkUp (MockWebServer on loopback) answering with the contract's synthetic
 * bodies (contracts/sources/librelinkup): no real account is ever contacted.
 */
class LibreLinkUpSourceTest {
    private val server = MockWebServer()
    private val graphFile = Json.parseToJsonElement(Contracts.file("sources/librelinkup/graph.json").readText()).jsonObject
    private val now = Instant.ofEpochSecond(graphFile.getValue("now").jsonPrimitive.long)
    private val clock = FakeClock(now, ZoneOffset.UTC)
    private val secure = InMemorySecureStore()
    private val state = InMemoryStateStore(LluSourceState())
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    private val account = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"
    private val ana = "11111111-2222-4333-8444-555555555555"
    private val ticket = """{"token":"t1","expires":${now.epochSecond + 3600}}"""
    private var login: MockResponse = ok("""{"status":0,"data":{"user":{"id":"$account"},"authTicket":$ticket}}""")
    private var people =
        """[{"patientId":"$ana","firstName":"Ana","lastName":"Example"},""" +
            """{"patientId":"$account","firstName":"Alex","lastName":"Example"}]"""
    private var graph: MockResponse = ok(graphBody(0))
    private var graphFailsOnce: MockResponse? = null

    private val config =
        object : LluSourceConfig {
            val flow = MutableStateFlow(LluSourceSettings())
            val whose = MutableStateFlow<Person?>(Person.Yours)
            override val data: Flow<LluSourceSettings> = flow
            override val person: Flow<Person?> = whose

            override suspend fun set(settings: LluSourceSettings) {
                flow.value = settings
            }
        }

    private fun ok(body: String) = MockResponse().setBody(body)

    /** The contract's [i]th graph answer. */
    private fun graphBody(i: Int) =
        graphFile
            .getValue("cases")
            .jsonArray[i]
            .jsonObject
            .getValue("body")
            .toString()

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.path.orEmpty()
                    return when {
                        path == LibreLinkUp.LOGIN -> {
                            login
                        }

                        path == LibreLinkUp.CONNECTIONS -> {
                            ok("""{"status":0,"data":$people}""")
                        }

                        path.startsWith("/llu/connections/") && path.endsWith("/graph") -> {
                            graphFailsOnce?.also { graphFailsOnce = null } ?: graph
                        }

                        else -> {
                            MockResponse().setResponseCode(404)
                        }
                    }
                }
            }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private val host get() = "127.0.0.1:${server.port}"

    private fun source(): LibreLinkUpSource {
        val api = LibreLinkUp(OkHttpClient(), "http", host) { host }
        return LibreLinkUpSource(config, LluAccount(secure, api, clock), state, NetworkState.Always, clock, Dispatchers.IO)
    }

    private fun logins() = requests.count { it.path == LibreLinkUp.LOGIN }

    @Test
    fun `your own glucose, the account that follows itself is picked, and its readings come with the arrow`() =
        runBlocking {
            val s = source()
            val r = s.connect("owner@example.com", "pw-SYNTHETIC")
            assertEquals(account, (r as LluConnectResult.Connected).picked)
            assertEquals(account, config.flow.value.patient)
            assertEquals("Alex Example", state.data.first().name)
            val batch = withTimeout(10_000) { s.readings.first() }
            assertEquals(listOf(104, 107, 110, 112), batch.map { it.mgdl })
            assertEquals(SourceId.LibreLinkUp, batch.last().source)
            assertEquals(0.0, batch.last().sensorRate)
            assertTrue(batch.dropLast(1).all { it.source == SourceId.LibreLinkUpGraph })
            // the calls name the product, the version and the account (hashed), never the password
            val call = requests.last { it.path.orEmpty().endsWith("/graph") }
            assertEquals("llu.android", call.getHeader("product"))
            assertEquals(LibreLinkUp.VERSION, call.getHeader("version"))
            assertEquals(LibreLinkUp.sha256(account), call.getHeader("Account-Id"))
            assertEquals("Bearer t1", call.getHeader("Authorization"))
            assertFalse(
                state.data
                    .first()
                    .toString()
                    .contains("pw-SYNTHETIC"),
            )
        }

    @Test
    fun `the saved token is used until it nears its end, a restart doesn't sign in again`() =
        runBlocking {
            source().connect("owner@example.com", "pw")
            assertEquals(1, logins())
            val s = source()
            withTimeout(10_000) { s.readings.first() }
            assertEquals(1, logins())
            // 58 min later the token is within its renewal margin: one new login
            clock.advance(java.time.Duration.ofMinutes(58))
            withTimeout(10_000) { source().readings.first() }
            assertEquals(2, logins())
        }

    @Test
    fun `a token that stops working before its time signs in again once, then reads`() =
        runBlocking {
            source().connect("owner@example.com", "pw")
            graphFailsOnce = MockResponse().setResponseCode(401)
            val batch = withTimeout(10_000) { source().readings.first() }
            assertTrue(batch.isNotEmpty())
            assertEquals(2, logins())
        }

    @Test
    fun `a refused password is never saved nor retried`() =
        runBlocking {
            login = ok("""{"status":2,"error":{"message":"notAuthenticated"}}""")
            val s = source()
            assertEquals(LluConnectResult.BadCredentials, s.connect("a@b.c", "wrong"))
            assertEquals(1, logins())
            assertEquals(LluAccount.Saved.None, s.account.saved.value)
        }

    @Test
    fun `new terms and too many attempts say so at Connect`() =
        runBlocking {
            login = ok("""{"status":4,"data":{"step":{"type":"tou"}}}""")
            assertEquals(LluConnectResult.Terms, source().connect("a@b.c", "pw"))
            login = MockResponse().setResponseCode(429).setHeader("Retry-After", "900")
            assertEquals(LluConnectResult.RateLimited, source().connect("a@b.c", "pw"))
        }

    @Test
    fun `the region redirect signs in again at the region's host`() =
        runBlocking {
            var first = true
            val accountLogin = login
            login = ok("""{"status":0,"data":{"redirect":true,"region":"eu"}}""")
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        requests += request
                        return when (request.path) {
                            LibreLinkUp.LOGIN -> if (first) login.also { first = false } else accountLogin
                            LibreLinkUp.CONNECTIONS -> ok("""{"status":0,"data":$people}""")
                            else -> graph
                        }
                    }
                }
            assertTrue(source().connect("a@b.c", "pw") is LluConnectResult.Connected)
            assertEquals(2, logins())
        }

    @Test
    fun `someone else's glucose, several people to pick from, then the one picked`() =
        runBlocking {
            config.whose.value = Person.Theirs
            people =
                """[{"patientId":"$ana","firstName":"Ana","lastName":"Example"},{"patientId":"66666666-7777-4888-9999-aaaaaaaaaaaa","firstName":"Leo"}]"""
            val s = source()
            val r = s.connect("parent@example.com", "pw") as LluConnectResult.Connected
            assertEquals(null, r.picked)
            assertEquals(listOf("Ana Example", "Leo"), r.people.map { it.name })
            s.choose(ana)
            assertEquals(ana, config.flow.value.patient)
            assertEquals("Ana Example", state.data.first().name)
        }

    @Test
    fun `no one shares with the account yet, Connect says so, nothing is picked`() =
        runBlocking {
            people = "[]"
            assertEquals(LluConnectResult.NoOne, source().connect("a@b.c", "pw"))
            assertEquals("", config.flow.value.patient)
        }

    @Test
    fun `asked to wait, the source says so and the person is told it tries again by itself`() =
        runBlocking {
            source().connect("owner@example.com", "pw")
            graph = MockResponse().setResponseCode(429)
            val s = source()
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            val st = withTimeout(10_000) { s.status.first { it is SourceStatus.Interrupted } }
            assertEquals(SourceProblem.RateLimited, (st as SourceStatus.Interrupted).problem)
            assertEquals(OnlineHealth.RateLimited, withTimeout(10_000) { s.view.first { it.health == OnlineHealth.RateLimited } }.health)
            job.cancel()
        }

    @Test
    fun `the sensor LibreLinkUp says gives its life`() =
        runBlocking {
            source().connect("owner@example.com", "pw")
            val s = source()
            withTimeout(10_000) { s.readings.first() }
            val life = withTimeout(10_000) { s.sensorLife.first { it != null } }!!
            assertEquals("0D00ABC123", life.id)
            assertEquals(now.minusSeconds(3 * 86400), life.start)
        }

    @Test
    fun `signing out keeps nothing of the account`() =
        runBlocking {
            val s = source()
            s.connect("owner@example.com", "pw")
            s.signOut()
            assertEquals(LluAccount.Saved.None, s.account.saved.value)
            assertEquals(null, secure.read(LluAccount.NAME))
            assertEquals("", config.flow.value.patient)
        }

    @Test
    fun `when to ask again is the contract's`() {
        val poll = Json.parseToJsonElement(Contracts.file("sources/librelinkup/poll.json").readText()).jsonObject
        assertEquals(poll.getValue("every_s").jsonPrimitive.long, LluPoll.EVERY_S)
        assertEquals(poll.getValue("rateLimited_min_s").jsonPrimitive.long, LluPoll.RATE_LIMITED_MIN_S)
        assertEquals(poll.getValue("renewBefore_s").jsonPrimitive.long, LluAccount.RENEW_BEFORE_S)
        assertEquals(poll.getValue("connectionsEvery_s").jsonPrimitive.long, LluPoll.CONNECTIONS_EVERY_S)
        for (c in poll.getValue("cases").jsonArray.map { it.jsonObject }) {
            val rate = c["rateLimited"]?.takeIf { it != JsonNull }?.jsonPrimitive?.long
            val refused = c["refused"]?.jsonPrimitive?.content == "true"
            val want =
                c
                    .getValue("wait_s")
                    .takeIf { it != JsonNull }
                    ?.jsonPrimitive
                    ?.long
            assertEquals(
                c.getValue("name").jsonPrimitive.content,
                want,
                LluPoll.waitS(c.getValue("failures").jsonPrimitive.int, rate, refused),
            )
        }
    }
}
