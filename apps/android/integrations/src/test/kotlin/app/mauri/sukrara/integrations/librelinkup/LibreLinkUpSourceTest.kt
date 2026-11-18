// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.librelinkup

import app.mauri.sukrara.core.data.libreview.LibreLinkUp
import app.mauri.sukrara.core.model.CallOutcome
import app.mauri.sukrara.core.model.LifeSettings
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SensorState
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
import kotlinx.serialization.json.JsonObject
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

    /** The person's side of the sensor's life (the app's source file): LibreLinkUp says the start, not the length. */
    private val life = MutableStateFlow(LifeSettings(Person.Yours))

    private fun source(): LibreLinkUpSource {
        val api = LibreLinkUp(OkHttpClient(), "http", host) { host }
        return LibreLinkUpSource(
            config,
            LluAccount(secure, api, clock),
            state,
            NetworkState.Always,
            clock,
            Dispatchers.IO,
            life,
            anotherPerson = { others++ },
        )
    }

    /** How often the app was told another person is about to be read (it deletes the earlier one's history). */
    private var others = 0

    private val leo = "22222222-3333-4444-8555-666666666666"

    @Test
    fun `another person is read only after the earlier person's history is deleted, Appendix G finding 6`() =
        runBlocking {
            config.whose.value = Person.Theirs
            val s = source()
            s.connect("parent@example.com", "pw")
            assertEquals(ana, config.flow.value.patient)
            withTimeout(10_000) { s.readings.first() }
            assertEquals(ana, state.data.first().held)
            assertEquals(0, others)
            // Alex picked on Settings › Source (after its confirmation)
            s.choose(account)
            withTimeout(10_000) { s.readings.first() }
            assertEquals(1, others)
            assertEquals(account, state.data.first().held)
        }

    @Test
    fun `a sign-in that would read another person waits for the person's OK and keeps the one read`() =
        runBlocking {
            config.whose.value = Person.Theirs
            val s = source()
            s.connect("parent@example.com", "pw")
            assertEquals(ana, config.flow.value.patient)
            // another account, which follows only Leo
            people = """[{"patientId":"$leo","firstName":"Leo","lastName":"Example"}]"""
            s.connect("other@example.com", "pw")
            assertEquals(ana, config.flow.value.patient)
            assertEquals(
                leo,
                s.view
                    .first()
                    .pending
                    ?.id,
            )
            assertEquals("Ana Example", state.data.first().name)
            // *Cancel*: Ana stays
            s.keepPerson()
            assertEquals(null, s.view.first().pending)
            assertEquals(ana, config.flow.value.patient)
            // *Read Leo's glucose*
            s.connect("other@example.com", "pw")
            s.choose(leo)
            assertEquals(leo, config.flow.value.patient)
            assertEquals(null, s.view.first().pending)
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
    fun `a call refused for its version sends the version asked for, reads again and keeps it through a restart`() =
        runBlocking {
            source().connect("owner@example.com", "pw")
            graphFailsOnce = MockResponse().setResponseCode(403).setBody("""{"status":920,"data":{"minimumVersion":"4.18.0"}}""")
            val batch = withTimeout(10_000) { source().readings.first() }
            assertTrue(batch.isNotEmpty())
            val graphs = requests.filter { it.path.orEmpty().endsWith("/graph") }
            assertEquals(listOf(LibreLinkUp.VERSION, "4.18.0"), graphs.map { it.getHeader("version") })
            // the token still works: no login for a version
            assertEquals(1, logins())
            // a new start (a new client) sends the raised version from its first call
            requests.clear()
            withTimeout(10_000) { source().readings.first() }
            assertEquals("4.18.0", requests.last { it.path.orEmpty().endsWith("/graph") }.getHeader("version"))
        }

    @Test
    fun `the version asked for stays through a new sign-in and a restart`() =
        runBlocking {
            source().connect("owner@example.com", "pw")
            graphFailsOnce = MockResponse().setResponseCode(403).setBody("""{"status":920,"data":{"minimumVersion":"4.18.0"}}""")
            withTimeout(10_000) { source().readings.first() }
            // the token stops working early: a new sign-in, which must keep the version
            graphFailsOnce = MockResponse().setResponseCode(401)
            withTimeout(10_000) { source().readings.first() }
            assertEquals(2, logins())
            assertEquals("4.18.0", requests.last { it.path == LibreLinkUp.LOGIN }.getHeader("version"))
            requests.clear()
            withTimeout(10_000) { source().readings.first() }
            assertEquals("4.18.0", requests.first().getHeader("version"))
            // a build that sends a newer version itself never goes back to an older one it was asked for
            assertTrue(LibreLinkUp.newer("4.18.0", "4.17.0"))
            assertFalse(LibreLinkUp.newer("4.17.0", "4.17.0"))
            assertFalse(LibreLinkUp.newer("4.9.9", "4.17.0"))
        }

    @Test
    fun `a version refused again says LibreLinkUp stopped answering Sukrara`() =
        runBlocking {
            source().connect("owner@example.com", "pw")
            graph = MockResponse().setResponseCode(403).setBody("""{"status":920,"data":{"minimumVersion":"4.18.0"}}""")
            val s = source()
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            val st = withTimeout(10_000) { s.status.first { it is SourceStatus.Interrupted } }
            assertEquals(SourceProblem.Stopped, (st as SourceStatus.Interrupted).problem)
            // asked once with the version it named, never in a loop
            assertEquals(2, requests.count { it.path.orEmpty().endsWith("/graph") })
            job.cancel()
        }

    @Test
    fun `a minimum version that isn't one is never sent`() {
        assertEquals(
            app.mauri.sukrara.core.data.libreview.LluCallOutcome.Retired,
            LibreLinkUp.parseCall(403, """{"status":920,"data":{"minimumVersion":"4.18.0\r\nX-Evil: 1"}}""").outcome,
        )
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
    fun `the person stops sharing mid-session, LibreLinkUp refuses their graph, Pick again shows at once`() =
        runBlocking {
            val s = source()
            s.connect("owner@example.com", "pw")
            withTimeout(10_000) { s.readings.first() }
            // the invite was removed in the Libre app: the graph is refused, whom the account follows no longer has them
            people = """[{"patientId":"$ana","firstName":"Ana","lastName":"Example"}]"""
            graph = MockResponse().setResponseCode(403).setBody("""{"status":403,"message":"Forbidden"}""")
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            val st = withTimeout(10_000) { s.status.first { it is SourceStatus.Interrupted } }
            // not *LibreLinkUp stopped answering Sukrara* for an hour
            assertEquals(SourceProblem.NotShared, (st as SourceStatus.Interrupted).problem)
            assertEquals(
                listOf(ana),
                s.view
                    .first()
                    .people
                    .map { it.id },
            )
            job.cancel()
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
    fun `asked to wait without a time, nothing calls LibreLinkUp again before 10 min, not even a kick`() =
        runBlocking {
            source().connect("owner@example.com", "pw")
            graph = MockResponse().setResponseCode(429)
            val s = source()
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            withTimeout(10_000) { s.status.first { it is SourceStatus.Interrupted } }
            val graphs = requests.count { it.path.orEmpty().endsWith("/graph") }
            // the network coming back or *Try again* kick the loop: under a 429 they wait (each call may lengthen the lockout)
            s.kick()
            kotlinx.coroutines.delay(1_500)
            s.kick()
            kotlinx.coroutines.delay(1_500)
            assertEquals(graphs, requests.count { it.path.orEmpty().endsWith("/graph") })
            assertEquals(0, logins() - 1)
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
            // the default length: 15 days (a wrong 14 would end a working 3 Plus a day early)
            assertEquals(life.start.plusSeconds(15 * 86400L), life.end)
            // the app that uploads (the graph's device type, the Libre 3 app's): the LibreView upload's identity (docs/45)
            assertEquals(40068, withTimeout(10_000) { s.uploader.first { it?.deviceType != null } }!!.deviceType)
        }

    @Test
    fun `the wear length chosen applies to LibreLinkUp's sensor at once, a manual start never`() =
        runBlocking {
            source().connect("owner@example.com", "pw")
            val s = source()
            val job = launch(Dispatchers.IO) { s.readings.collect { } }
            val start = withTimeout(10_000) { s.sensorLife.first { it != null } }!!.start
            // 14 days (a Libre 3): the end and the hub's state move at once
            life.value = LifeSettings(Person.Yours, 14 * 1440)
            assertEquals(start.plusSeconds(14 * 86400L), withTimeout(10_000) { s.sensorLife.first { it?.wearMin == 14 * 1440 } }!!.end)
            val active = withTimeout(10_000) { s.sensor.first { (it as? SensorState.Active)?.endsAt == start.plusSeconds(14 * 86400L) } }
            assertEquals("0D00ABC123", (active as SensorState.Active).id)
            // LibreLinkUp always says the start: a manual one (it can't be set here) is never used
            life.value = LifeSettings(Person.Yours, 14 * 1440, now.minusSeconds(60).epochSecond)
            assertEquals(start, s.sensorLife.first()!!.start)
            job.cancel()
        }

    @Test
    fun `a new sensor warming up says so, not that nothing new comes`() =
        runBlocking {
            // the owner's test (2026-11-18): a new Libre 3 started after LibreLinkUp's newest reading (the old sensor's,
            // 50 s old here: live, were it not for the new sensor)
            val start = now.minusSeconds(20)
            graph = ok(graphBody(0).replace("1794744000", "${start.epochSecond}").replace("0D00ABC123", "0D00NEW456"))
            source().connect("owner@example.com", "pw")
            val s = source()
            val job = launch { s.readings.collect {} }
            val v = withTimeout(10_000) { s.view.first { it.health != OnlineHealth.Connecting } }
            assertEquals(OnlineHealth.WarmingUp, v.health)
            assertEquals(start.plusSeconds(3600), v.sensor?.warmupEnd)
            // not live: the newest reading is the old sensor's
            kotlinx.coroutines.delay(300)
            assertEquals(SourceStatus.Connecting, s.status.value)
            job.cancel()
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

        fun long(
            o: JsonObject,
            k: String,
        ): Long? = o[k]?.takeIf { it != JsonNull }?.jsonPrimitive?.long
        assertEquals(long(poll, "cadence_s"), LluPoll.CADENCE_S)
        assertEquals(long(poll, "margin_s"), LluPoll.MARGIN_S)
        assertEquals(long(poll, "margin_up_s"), LluPoll.MARGIN_UP_S)
        assertEquals(long(poll, "margin_hold"), LluPoll.MARGIN_HOLD.toLong())
        assertEquals(long(poll, "margin_max_s"), LluPoll.MARGIN_MAX_S)
        assertEquals(long(poll, "min_s"), LluPoll.MIN_S)
        assertEquals(poll.getValue("backoff_s").jsonArray.map { it.jsonPrimitive.long }, LluPoll.BACKOFF_S)
        assertEquals(long(poll, "rateLimited_min_s"), LluPoll.RATE_LIMITED_MIN_S)
        assertEquals(long(poll, "renewBefore_s"), LluAccount.RENEW_BEFORE_S)
        assertEquals(long(poll, "connectionsEvery_s"), LluPoll.CONNECTIONS_EVERY_S)
        assertEquals(long(poll, "terms_s"), LluPoll.TERMS_S)
        assertEquals(long(poll, "retired_s"), LluPoll.RETIRED_S)
        for (c in poll.getValue("cases").jsonArray.map { it.jsonObject }) {
            val rate = long(c, "rateLimited")
            val refused = c["refused"]?.jsonPrimitive?.content == "true"
            val failures = c.getValue("failures").jsonPrimitive.int
            val name = c.getValue("name").jsonPrimitive.content
            assertEquals(name, long(c, "wait_s"), LluPoll.waitS(failures, rate, refused))
            // the source's own decision after a cycle: a 429 without a time (no Retry-After, no lockout) is the minimum
            val outcome =
                when {
                    refused -> CallOutcome.Refused
                    rate != null -> CallOutcome.RateLimited
                    else -> CallOutcome.Server
                }
            assertEquals(name, long(c, "wait_s"), LluPoll.next(outcome, failures, rate?.takeIf { it > 0 }))
        }
        // the wait after a round, by its outcome (the Mac's LluPoll.next passes the same cases)
        for (c in poll.getValue("after").jsonArray.map { it.jsonObject }) {
            val outcome =
                c
                    .getValue("outcome")
                    .takeIf { it != JsonNull }
                    ?.jsonPrimitive
                    ?.content
                    ?.let { o -> CallOutcome.entries.first { it.name.equals(o, ignoreCase = true) } }
            assertEquals(
                c.getValue("name").jsonPrimitive.content,
                long(c, "wait_s"),
                LluPoll.next(
                    outcome,
                    c.getValue("failures").jsonPrimitive.int,
                    long(c, "retryAfter_s"),
                    long(c, "now") ?: 0,
                    long(c, "newest"),
                    long(c, "margin_s") ?: LluPoll.MARGIN_S,
                ),
            )
        }
        // timed to the next reading
        for (c in poll.getValue("timed").jsonArray.map { it.jsonObject }) {
            assertEquals(
                c.getValue("name").jsonPrimitive.content,
                long(c, "wait_s"),
                LluPoll.timedS(long(c, "now")!!, long(c, "newest"), long(c, "margin_s")!!),
            )
        }
        // the margin follows LibreLinkUp
        for (c in poll.getValue("margins").jsonArray.map { it.jsonObject }) {
            val t =
                LluPoll.timing(
                    LluPoll.Timing(long(c, "margin")!!, c.getValue("streak").jsonPrimitive.int),
                    long(c, "now")!!,
                    long(c, "before"),
                    long(c, "found"),
                )
            val name = c.getValue("name").jsonPrimitive.content
            assertEquals(name, LluPoll.Timing(long(c, "margin_s")!!, c.getValue("streak_after").jsonPrimitive.int), t)
        }
        // calls in a row, the timing carried
        for (q in poll.getValue("sequences").jsonArray.map { it.jsonObject }) {
            var t = LluPoll.Timing()
            var newest: Long? = null
            for ((i, step) in q
                .getValue("steps")
                .jsonArray
                .map { it.jsonObject }
                .withIndex()) {
                val now = long(step, "now")!!
                val found = long(step, "found")
                t = LluPoll.timing(t, now, newest, found)
                if (found != null && (newest ?: Long.MIN_VALUE) < found) newest = found
                val name = "${q.getValue("name").jsonPrimitive.content} #$i"
                assertEquals(name, long(step, "margin_s"), t.marginS)
                assertEquals(name, long(step, "wait_s"), LluPoll.timedS(now, newest, t.marginS))
            }
        }
    }
}
