// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.localserver

import app.mauri.sukrara.core.model.GlucoseRange
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.SecretUnreadableException
import app.mauri.sukrara.core.model.storage.SecureStore
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** The Nightscout read API on this phone: routes, filters, labels, and that nothing is served without the secret. */
class NightscoutApiTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val secret = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"

    private fun r(
        minAgo: Long,
        mgdl: Int,
        source: SourceId = SourceId.Libre3,
        rate: Double? = null,
    ) = Reading(now - Duration.ofMinutes(minAgo), mgdl, rate, source)

    private val readings = (0L until 30L).map { r(it, 100 + it.toInt(), rate = if (it % 2 == 0L) 0.4 else null) }
    private val treatments =
        listOf(
            Treatment("a", TreatmentKind.RapidInsulin, 4.5, now - Duration.ofHours(1), "", now, false, TreatmentOrigin.Phone),
            Treatment("b", TreatmentKind.Carbs, 30.0, now - Duration.ofHours(2), "lunch", now, false, TreatmentOrigin.Phone),
            Treatment("c", TreatmentKind.Note, 0.0, now - Duration.ofHours(3), "gone", now, true, TreatmentOrigin.Phone),
        )

    private fun call(
        path: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = mapOf("api-secret" to NightscoutApi.sha1(secret)),
        list: List<Reading> = readings,
    ) = NightscoutApi.route(HttpRequest("GET", path, query, headers), secret) {
        NightscoutApi.Snapshot(list, treatments.filter { !it.deleted }, now, ZoneOffset.UTC)
    }

    private fun array(a: HttpAnswer): JsonArray = Json.parseToJsonElement(a.body).jsonArray

    @Test
    fun `every data route needs the secret, status answers without anything about the person`() {
        for (p in listOf(
            "api/v1/entries.json",
            "api/v1/entries/current.json",
            "api/v1/treatments.json",
            "api/v2/properties",
            "api/v1/verifyauth",
        )) {
            assertTrue(p, call(p, headers = emptyMap()).status.startsWith("401"))
        }
        val status = call("api/v1/status.json", headers = emptyMap())
        assertTrue(status.status.startsWith("200"))
        val o = Json.parseToJsonElement(status.body).jsonObject
        assertEquals("Sukrara", o["name"]!!.jsonPrimitive.content)
        assertFalse(status.body.contains("thresholds"))
        assertFalse(status.body.contains("sgv"))
        assertEquals("null", o["authorized"].toString())
    }

    @Test
    fun `the secret is accepted as sha1 header, plain header, token or bearer, nothing else`() {
        fun ok(
            headers: Map<String, String>,
            query: Map<String, String> = emptyMap(),
        ) = NightscoutApi.authorized(secret, HttpRequest("GET", "api/v1/entries", query, headers))
        assertTrue(ok(mapOf("api-secret" to NightscoutApi.sha1(secret).uppercase())))
        assertTrue(ok(mapOf("api-secret" to secret)))
        assertTrue(ok(mapOf("api-secret" to "token=$secret")))
        assertTrue(ok(emptyMap(), mapOf("token" to secret)))
        assertTrue(ok(mapOf("authorization" to "Bearer $secret")))
        assertFalse(ok(emptyMap(), mapOf("secret" to NightscoutApi.sha1(secret))))
        assertFalse(ok(mapOf("api-secret" to "wrong")))
        assertFalse(ok(emptyMap()))
        assertFalse(NightscoutApi.authorized("", HttpRequest("GET", "x", mapOf("token" to ""), emptyMap())))
    }

    @Test
    fun `entries are newest first, filtered by date and capped`() {
        val all = array(call("api/v1/entries.json", mapOf("count" to "5")))
        assertEquals(5, all.size)
        val dates = all.map { it.jsonObject["date"]!!.jsonPrimitive.long }
        assertEquals(dates.sortedDescending(), dates)
        assertEquals(now.toEpochMilli(), dates.first())
        val since = (now - Duration.ofMinutes(3)).toEpochMilli()
        val filtered = array(call("api/v1/entries/sgv.json", mapOf("count" to "100", "find[date][\$gte]" to "$since")))
        assertEquals(4, filtered.size)
        val iso =
            array(
                call(
                    "api/v1/entries.json",
                    mapOf(
                        "count" to "100",
                        "find[dateString][\$gt]" to (now - Duration.ofMinutes(2)).toString(),
                    ),
                ),
            )
        assertEquals(2, iso.size)
        assertEquals(0, array(call("api/v1/entries.json", mapOf("find[type]" to "mbg"))).size)
        val huge = array(call("api/v1/entries.json", mapOf("count" to "999999")))
        assertEquals(readings.size, huge.size)
    }

    @Test
    fun `each entry says where it came from, unknown sources are never served`() {
        val list = listOf(r(0, 120, SourceId.Relay), r(1, 121, SourceId.DexcomG7), r(2, 122, SourceId("synthetic")), r(3, 123))
        val devices =
            array(call("api/v1/entries.json", mapOf("count" to "10"), list = list)).map {
                it.jsonObject["device"]!!.jsonPrimitive.content
            }
        assertEquals(listOf("Sukrara (following)", "Sukrara · Dexcom G7 (untested)", "Sukrara · FreeStyle Libre 3"), devices)
    }

    @Test
    fun `a LO or HI reading has no direction or slope`() {
        val list =
            listOf(Reading.lo(now, SourceId.Libre3), r(5, 60, rate = -1.5), Reading.hi(now - Duration.ofMinutes(10), SourceId.Libre3))
        val e = array(call("api/v1/entries.json", mapOf("count" to "10"), list = list)).map { it.jsonObject }
        assertEquals(GlucoseRange.LO, e[0]["sgv"]!!.jsonPrimitive.int)
        assertEquals("NONE", e[0]["direction"]!!.jsonPrimitive.content)
        assertEquals(0, e[0]["trend"]!!.jsonPrimitive.int)
        assertEquals("FortyFiveDown", e[1]["direction"]!!.jsonPrimitive.content)
        assertEquals(5, e[1]["trend"]!!.jsonPrimitive.int)
        assertEquals("NONE", e[2]["direction"]!!.jsonPrimitive.content)
    }

    @Test
    fun `treatments leave deleted ones out, v3 is not found, the rest is empty`() {
        val t = array(call("api/v1/treatments.json"))
        assertEquals(2, t.size)
        assertEquals("Correction Bolus", t[0].jsonObject["eventType"]!!.jsonPrimitive.content)
        assertEquals(
            24,
            t[0]
                .jsonObject["_id"]!!
                .jsonPrimitive.content.length,
        )
        assertTrue(call("api/v3/entries").status.startsWith("404"))
        assertEquals("[]", call("api/v1/devicestatus.json").body)
        assertTrue(call("api/v1/nope").status.startsWith("404"))
        val props = Json.parseToJsonElement(call("api/v2/properties").body).jsonObject
        assertEquals(100, props["bgnow"]!!.jsonObject["last"]!!.jsonPrimitive.int)
        assertNull(props["iob"])
        // the list form clients use answers the same object
        val listed = Json.parseToJsonElement(call("api/v2/properties/bgnow,delta,direction").body)
        assertTrue(listed is JsonObject)
    }

    @Test
    fun `two sources' readings in the same second never share an id`() {
        val t = now.plusMillis(250)
        val libre = Reading(t, 120, null, SourceId.Libre3)
        val juggluco = Reading(t.plusMillis(400), 121, null, SourceId.Juggluco)
        val ids = listOf(libre, juggluco).map { NightscoutApi.entryId(it) }
        assertNotEquals(ids[0], ids[1])
        assertTrue(ids.all { it.length == 24 && it.all { c -> c in "0123456789abcdef" } })
        // the same reading keeps its id; the id starts with its second, as a Nightscout id does
        assertEquals(ids[0], NightscoutApi.entryId(libre.copy(mgdl = 125)))
        assertTrue(ids[0].startsWith(t.epochSecond.toString(16).padStart(8, '0')))
        val both = array(call("api/v1/entries.json", list = listOf(juggluco, libre)))
        assertEquals(ids.reversed(), both.map { it.jsonObject["_id"]!!.jsonPrimitive.content })
    }

    @Test
    fun `a follower's served entries say they are the followed person's`() {
        val zone = ZoneOffset.UTC
        val wearer = NightscoutApi.treatment(treatments[0], zone, Role.Wearer)
        assertEquals("Sukrara", wearer["enteredBy"]!!.jsonPrimitive.content)
        val following = NightscoutApi.treatment(treatments[0], zone, Role.Follower)
        assertEquals("Sukrara (following)", following["enteredBy"]!!.jsonPrimitive.content)
        assertEquals("Sukrara (following)", following["device"]!!.jsonPrimitive.content)
        val served =
            NightscoutApi.route(
                HttpRequest("GET", "api/v1/treatments.json", emptyMap(), mapOf("api-secret" to NightscoutApi.sha1(secret))),
                secret,
            ) {
                NightscoutApi.Snapshot(emptyList(), treatments.filter { !it.deleted }, now, zone, Role.Follower)
            }
        assertTrue(array(served).all { it.jsonObject["enteredBy"]!!.jsonPrimitive.content == "Sukrara (following)" })
    }

    @Test
    fun `each role serves only its own person's readings`() {
        assertTrue(SourceId.Libre3 in SourceId.of(Role.Wearer))
        assertFalse(SourceId.Relay in SourceId.of(Role.Wearer))
        assertEquals(setOf(SourceId.Relay, SourceId.Nearby), SourceId.of(Role.Follower))
    }

    private class Unreadable : SecureStore {
        override suspend fun read(name: String): ByteArray? = throw SecretUnreadableException(name, true, null)

        override suspend fun write(
            name: String,
            bytes: ByteArray,
        ) {}

        override suspend fun delete(name: String) {}
    }

    private fun server(secure: SecureStore = InMemorySecureStore()) =
        NightscoutApiServer(
            InMemorySettingsRepository(),
            secure,
            InMemoryReadingsRepository(),
            InMemoryTreatmentsRepository(),
            flowOf(Role.Wearer),
            FakeClock(now),
            Dispatchers.IO,
            InMemoryStateStore(LanPin()),
            flowOf(null),
            { true },
            port = 0,
        )

    @Test
    fun `switching on makes a secret, keeps it when off, new secret replaces it`() =
        runBlocking<Unit> {
            val secure = InMemorySecureStore()
            val api = server(secure)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                api.start(scope)
                api.setOn(true)
                withTimeout(5_000) { api.state.first { it is NsApiState.Listening } }
                val first = api.secret()!!
                assertEquals(32, first.length)
                api.setOn(false)
                withTimeout(5_000) { api.state.first { it == NsApiState.Off } }
                assertEquals(first, api.secret())
                val renewed = api.renewSecret()
                assertTrue(renewed != first)
                assertEquals(renewed, api.secret())
            } finally {
                api.stop()
                scope.cancel()
            }
        }

    @Test
    fun `the home-network option follows the Wi-Fi, the permission and the API switch`() =
        runBlocking<Unit> {
            val settings = InMemorySettingsRepository()
            val wifi = MutableStateFlow<WifiNet?>(null)
            var allowed = true
            val pin = InMemoryStateStore(LanPin())
            val api =
                NightscoutApiServer(
                    settings,
                    InMemorySecureStore(),
                    InMemoryReadingsRepository(),
                    InMemoryTreatmentsRepository(),
                    flowOf(Role.Wearer),
                    FakeClock(now),
                    Dispatchers.IO,
                    pin,
                    wifi,
                    { allowed },
                    port = 0,
                )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            fun net(secured: Boolean) = WifiNet(listOf(InetAddress.getByName("192.168.77.5") to 24), "192.168.77.1", null, null, secured)
            try {
                api.start(scope)
                api.setOn(true)
                withTimeout(5_000) { api.state.first { it is NsApiState.Listening } }
                // off by default; turning on away from a qualifying Wi-Fi is refused and pins nothing
                assertEquals(LanState.Off, api.lan.value)
                assertEquals(LanState.NoWifi, api.setLan(true))
                wifi.value = net(secured = false)
                assertEquals(LanState.OpenWifi, api.setLan(true))
                assertFalse(settings.flow(NightscoutApiServer.LAN_SETTING).first())
                assertEquals(null, pin.data.first().fingerprint)
                // on the home Wi-Fi: pinned at once; a private address this machine doesn't have can't be bound
                wifi.value = net(secured = true)
                assertEquals(null, api.setLan(true))
                withTimeout(5_000) { api.lan.first { it == LanState.PortBusy } }
                assertTrue(pin.data.first().fingerprint != null)
                allowed = false
                api.recheckLan()
                withTimeout(5_000) { api.lan.first { it == LanState.NoPermission } }
                allowed = true
                api.recheckLan()
                withTimeout(5_000) { api.lan.first { it == LanState.PortBusy } }
                // another (password) Wi-Fi: paused, never pinned on its own; Allow on this Wi-Fi moves it
                wifi.value = WifiNet(listOf(InetAddress.getByName("10.1.2.3") to 24), "10.1.2.1", null, null, true)
                withTimeout(5_000) { api.lan.first { it == LanState.OtherWifi } }
                wifi.value = null
                withTimeout(5_000) { api.lan.first { it == LanState.NoWifi } }
                wifi.value = WifiNet(listOf(InetAddress.getByName("10.1.2.3") to 24), "10.1.2.1", null, null, true)
                withTimeout(5_000) { api.lan.first { it == LanState.OtherWifi } }
                assertEquals(null, api.allowHere())
                withTimeout(5_000) { api.lan.first { it == LanState.PortBusy } }
                // turning the API off turns the home-network option off too
                api.setOn(false)
                withTimeout(5_000) { api.lan.first { it == LanState.Off } }
                assertFalse(settings.flow(NightscoutApiServer.LAN_SETTING).first())
            } finally {
                api.stop()
                scope.cancel()
            }
        }

    @Test
    fun `the home-network listener has its own secret, replaced on its own`() =
        runBlocking<Unit> {
            val wifi =
                MutableStateFlow<WifiNet?>(WifiNet(listOf(InetAddress.getByName("192.168.77.5") to 24), "192.168.77.1", null, null, true))
            val secure = InMemorySecureStore()
            val api =
                NightscoutApiServer(
                    InMemorySettingsRepository(),
                    secure,
                    InMemoryReadingsRepository(),
                    InMemoryTreatmentsRepository(),
                    flowOf(Role.Wearer),
                    FakeClock(now),
                    Dispatchers.IO,
                    InMemoryStateStore(LanPin()),
                    wifi,
                    { true },
                    port = 0,
                )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            fun read(key: String) = HttpRequest("GET", "api/v1/entries.json", mapOf("token" to key), emptyMap())
            try {
                api.start(scope)
                api.setOn(true)
                withTimeout(5_000) { api.state.first { it is NsApiState.Listening } }
                assertNull(api.lanSecret())
                assertEquals(null, api.setLan(true))
                // the address isn't this machine's: it can't bind, but it has read its secret by then
                withTimeout(5_000) { api.lan.first { it == LanState.PortBusy } }
                val phone = api.secret()!!
                val lan = api.lanSecret()!!
                assertEquals(32, lan.length)
                assertNotEquals(phone, lan)
                // each listener takes only its own secret: the one that travels over Wi-Fi never opens the phone's
                assertTrue(api.answerLan(read(lan)).status.startsWith("200"))
                assertTrue(api.answerLan(read(phone)).status.startsWith("401"))
                assertTrue(api.answerLocal(read(phone)).status.startsWith("200"))
                assertTrue(api.answerLocal(read(lan)).status.startsWith("401"))
                // New Wi-Fi secret: only the Wi-Fi devices need the new one
                val renewed = api.renewLanSecret()
                assertNotEquals(lan, renewed)
                assertEquals(phone, api.secret())
                assertTrue(api.answerLan(read(renewed)).status.startsWith("200"))
                assertTrue(api.answerLan(read(lan)).status.startsWith("401"))
                // turned off and on again: the same Wi-Fi secret (replacing it is the person's choice)
                api.setLan(false)
                assertEquals(null, api.setLan(true))
                assertEquals(renewed, api.lanSecret())
            } finally {
                api.stop()
                scope.cancel()
            }
        }

    @Test
    fun `an unreadable secret serves nothing`() =
        runBlocking<Unit> {
            val api = server(Unreadable())
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                api.start(scope)
                api.setOn(true)
                withTimeout(5_000) { api.state.first { it == NsApiState.SecretUnreadable } }
            } finally {
                api.stop()
                scope.cancel()
            }
        }

    @Test
    fun `over the socket, no secret is 401 and the secret is 200`() {
        val callers = mutableListOf<String?>()
        val server =
            LocalHttpServer(0, { req ->
                callers += req.caller
                call(req.path, req.query, req.headers)
            })
        try {
            assertTrue(server.bind())

            fun get(raw: String) =
                Socket(InetAddress.getLoopbackAddress(), server.boundPort).use { s ->
                    s.soTimeout = 10_000
                    s.getOutputStream().write(raw.toByteArray())
                    s.getInputStream().readBytes().decodeToString()
                }
            assertTrue(get("GET /api/v1/entries.json HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").startsWith("HTTP/1.1 401"))
            val ok = get("GET /api/v1/entries.json?token=$secret HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
            assertTrue(ok.startsWith("HTTP/1.1 200"))
            assertTrue(Json.parseToJsonElement(ok.substringAfter("\r\n\r\n")) is JsonArray)
            assertFalse(Json.parseToJsonElement(ok.substringAfter("\r\n\r\n")) is JsonObject)
            // the caller's address comes with the request (the Wi-Fi listener shows the last one)
            assertEquals(listOf("127.0.0.1", "127.0.0.1"), callers)
        } finally {
            server.stop()
        }
    }
}
