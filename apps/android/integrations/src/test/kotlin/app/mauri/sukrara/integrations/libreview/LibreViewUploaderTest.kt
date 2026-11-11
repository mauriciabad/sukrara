// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.libreview

import app.mauri.sukrara.core.data.libreview.LibreViewAccountState
import app.mauri.sukrara.core.data.libreview.LibreViewLogin
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.integrations.CurrentSensor
import app.mauri.sukrara.integrations.upload.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The LibreView upload against a fake LibreView (MockWebServer on loopback): no real account is ever contacted. It uses
 * the app's one LibreView account (issue #177): signed in once, never asked for here.
 */
class LibreViewUploaderTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val clock = FakeClock(now, ZoneOffset.UTC)
    private val settings = InMemorySettingsRepository()
    private val secure = InMemorySecureStore()
    private val store = InMemoryStateStore(LvState())
    private val unsent = InMemoryStateStore(LvUnsent())

    /** THE LibreView account of the app (signed in once on the account screen; the sensor uses it too). */
    private val login = LibreViewLogin(secure)
    private val treatments = InMemoryTreatmentsRepository()
    private val role = MutableStateFlow<Role?>(Role.Wearer)
    private val sensor = MutableStateFlow<CurrentSensor?>(null)
    private val requests = mutableListOf<RecordedRequest>()

    /** The account uploads from another device: sign-in without take-over is refused. */
    private var otherDevice = false

    /** `/api/measurements` answers: null = ok. */
    private var measurements: MockResponse? = null
    private var redirectConfig = false

    /** The next `/api/measurements` answers 401: the token expired. */
    private var refuseTokenOnce = false
    private var refuseRegistration = false

    private lateinit var uploader: LibreViewUploader

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val body = request.body.clone().readUtf8()
                    return when (request.path) {
                        "/assets" -> {
                            if (redirectConfig) {
                                MockResponse().setResponseCode(302).setHeader("Location", "https://elsewhere.invalid/")
                            } else {
                                MockResponse().setBody("""{"Configuration":"${server.url("/config")}"}""")
                            }
                        }

                        "/config" -> {
                            MockResponse().setBody("""{"newYuUrl":"${server.url("/").toString().trimEnd('/')}","newYuApiKey":"KEY"}""")
                        }

                        "/api/nisperson/getauthentication" -> {
                            if (otherDevice && "\"SetDevice\":false" in body) {
                                MockResponse().setBody("""{"status":20,"reason":"wrongDeviceForUser"}""")
                            } else if ("\"Password\":\"secret-pw\"" !in body) {
                                MockResponse().setBody("""{"status":2}""")
                            } else {
                                val user = Regex("\"UserName\":\"([^\"]*)\"").find(body)!!.groupValues[1]
                                MockResponse().setBody(
                                    """{"status":0,"result":{"UserToken":"tok-1","AccountId":"${accountOf(user)}",""" +
                                        """"DateOfBirth":"1990-01-01","FirstName":"A","LastName":"B"}}""",
                                )
                            }
                        }

                        "/api/nisperson" -> {
                            MockResponse().setBody(if (refuseRegistration) """{"status":7,"reason":"no"}""" else """{"status":0}""")
                        }

                        "/api/measurements" -> {
                            if (refuseTokenOnce) {
                                refuseTokenOnce = false
                                MockResponse().setResponseCode(401)
                            } else {
                                measurements ?: MockResponse().setBody("""{"status":0}""")
                            }
                        }

                        else -> {
                            MockResponse().setResponseCode(404)
                        }
                    }
                }
            }
        server.start()
        uploader = newUploader()
    }

    /** A fresh uploader over the same stores (as after a process death). */
    private fun newUploader() =
        LibreViewUploader(
            settings,
            secure,
            store,
            unsent,
            treatments,
            emptyFlow(),
            sensor,
            login,
            role,
            LibreViewClient(OkHttpClient(), server.url("/assets").toString(), allowLoopbackHttp = true),
            clock,
            Dispatchers.Unconfined,
        )

    /** The fake's accounts: an alias of "me" is the same account. */
    private fun accountOf(email: String) =
        if (email.startsWith("me")) "01234567-89ab-cdef-0123-456789abcdef" else "fedcba98-7654-3210-fedc-ba9876543210"

    @After
    fun tearDown() = server.shutdown()

    private fun treatment(
        id: String,
        minAgo: Long,
        amount: Double = 2.0,
        modified: Instant = clock.now(),
        deleted: Boolean = false,
        kind: TreatmentKind = TreatmentKind.RapidInsulin,
    ) = TreatmentRecord(
        Treatment(id, kind, amount, now - Duration.ofMinutes(minAgo), "", modified, deleted, TreatmentOrigin.Phone),
    )

    /** Signed in once on the account screen, the upload switched on: its first cycle signs in by itself. */
    private fun signedInAndOn(email: String = "me@example.com") =
        runBlocking {
            login.save(email, "secret-pw")
            uploader.change { it.copy(on = true) }
            uploader.cycle()
            assertTrue("the upload's own token", secure.read(LibreViewUploader.NAME) != null)
        }

    /** Signs out everywhere (the account screen), the upload following it. */
    private suspend fun signOut() {
        login.signOut()
        uploader.followLogin(login.current())
    }

    private fun logins() = requests.filter { it.path == "/api/nisperson/getauthentication" }.map { it.body.clone().readUtf8() }

    private fun uploads() = requests.filter { it.path == "/api/measurements" }.map { it.body.clone().readUtf8() }

    @Test
    fun `issue 177 - the account signed in once is used by itself, and only the token is the upload's`() =
        runBlocking {
            signedInAndOn()
            val saved = secure.read(LibreViewUploader.NAME)!!.decodeToString()
            assertTrue("tok-1" in saved)
            assertFalse(saved.contains("secret-pw"))
            assertFalse("secret-pw" in store.data.first().toString())
            assertFalse("secret-pw" in settings.toString())
            assertEquals("me@example.com", store.data.first().email)
            val page = uploader.page.first()
            assertTrue(page.signedIn)
            assertEquals("me@example.com", page.email)
            assertEquals(UploadStatus.Waiting, page.status)
            // the config's key goes with the sign-in; it never takes over by itself
            val auth = requests.first { it.path == "/api/nisperson/getauthentication" }
            assertEquals("KEY", auth.getHeader("x-api-key"))
            assertEquals(listOf(false), logins().map { "\"SetDevice\":true" in it })
            // the token is kept: no new sign-in per cycle
            treatments.upsertIfNewer(treatment("a", 5, modified = clock.now() + Duration.ofSeconds(1)))
            uploader.cycle()
            assertEquals(1, uploads().size)
            assertEquals(1, logins().size)
        }

    @Test
    fun `no account - nothing is asked here, the page points to the account screen`() =
        runBlocking {
            uploader.change { it.copy(on = true) }
            uploader.cycle()
            assertTrue(requests.isEmpty())
            val page = uploader.page.first()
            assertFalse(page.signedIn)
            assertEquals(UploadStatus.Setup, page.status)
            assertEquals(false, uploader.signedInKnown.first())
        }

    @Test
    fun `another uploading device is taken over only when asked`() =
        runBlocking {
            otherDevice = true
            login.save("me@example.com", "secret-pw")
            uploader.change { it.copy(on = true) }
            uploader.cycle()
            assertEquals(null, secure.read(LibreViewUploader.NAME))
            assertEquals(UploadStatus.AuthFailed, uploader.page.first().status)
            assertTrue(uploader.entryMarks.first()!!.stopped)
            // nothing more is tried until the person chooses
            uploader.cycle()
            assertEquals(1, logins().size)
            assertEquals(LvTakeOver.Done, uploader.takeOver())
            assertEquals(listOf(false, true), logins().map { "\"SetDevice\":true" in it })
            assertEquals(UploadStatus.Waiting, uploader.page.first().status)
            treatments.upsertIfNewer(treatment("a", 5, modified = clock.now() + Duration.ofSeconds(1)))
            uploader.cycle()
            assertEquals(1, uploads().size)
        }

    @Test
    fun `a password LibreView refuses asks to sign in again, on the account screen`() =
        runBlocking {
            login.save("me@example.com", "changed-elsewhere")
            uploader.change { it.copy(on = true) }
            uploader.cycle()
            assertEquals(null, secure.read(LibreViewUploader.NAME))
            assertEquals(LibreViewAccountState.SignInAgain("me@example.com"), login.current())
            val page = uploader.page.first()
            assertTrue(page.signInAgain)
            assertEquals(UploadStatus.Setup, page.status)
            assertTrue("it set the upload up: no clinic banner", uploader.signedInKnown.first() == true)
            // not tried again with the refused password
            uploader.cycle()
            assertEquals(1, logins().size)
            assertEquals(LvTakeOver.SignIn, uploader.takeOver())
            // signed in again on the account screen: it goes on by itself
            login.save("me@example.com", "secret-pw")
            uploader.followLogin(login.current())
            uploader.cycle()
            assertTrue(secure.read(LibreViewUploader.NAME) != null)
        }

    @Test
    fun `a redirect is never followed`() =
        runBlocking {
            redirectConfig = true
            login.save("me@example.com", "secret-pw")
            uploader.change { it.copy(on = true) }
            uploader.cycle()
            assertTrue(uploader.page.first().status is UploadStatus.Failing)
            assertEquals(listOf("/assets"), requests.map { it.path })
        }

    @Test
    fun `only entries changed after switching on go up, and edits retract the old version`() =
        runBlocking {
            treatments.upsertIfNewer(treatment("old", 60, modified = now - Duration.ofHours(1)))
            signedInAndOn()
            clock.advance(Duration.ofMinutes(1))
            treatments.upsertIfNewer(treatment("new", 5, modified = clock.now()))
            uploader.cycle()
            val first = uploads().single()
            assertTrue(LibreViewPayload.recordNumber("new", "rapid", clock.now().toEpochMilli()).toString() in first)
            assertFalse(
                "\"recordNumber\":${LibreViewPayload.recordNumber("old", "rapid", (now - Duration.ofHours(1)).toEpochMilli())}" in first,
            )
            val v1 = clock.now().toEpochMilli()

            // nothing new: nothing sent
            uploader.cycle()
            assertEquals(1, uploads().size)

            clock.advance(Duration.ofMinutes(1))
            treatments.upsertIfNewer(treatment("new", 5, amount = 3.0, modified = clock.now()))
            uploader.cycle()
            val edit = uploads().last()
            assertTrue(
                "\"action\":\"deleted\",\"linkedGlucoseRecordNumber\":\"0\"},\"recordNumber\":${LibreViewPayload.recordNumber(
                    "new",
                    "rapid",
                    v1,
                )}" in
                    edit,
            )
            assertTrue("\"units\":3.0" in edit)

            val v2 = clock.now().toEpochMilli()
            clock.advance(Duration.ofMinutes(1))
            treatments.upsertIfNewer(treatment("new", 5, amount = 3.0, modified = clock.now(), deleted = true))
            uploader.cycle()
            val delete = uploads().last()
            assertTrue(
                "\"action\":\"deleted\",\"linkedGlucoseRecordNumber\":\"0\"},\"recordNumber\":${LibreViewPayload.recordNumber(
                    "new",
                    "rapid",
                    v2,
                )}" in
                    delete,
            )
            assertFalse(LibreViewPayload.recordNumber("new", "rapid", clock.now().toEpochMilli()).toString() in delete)
            assertEquals(
                setOf("new"),
                store.data
                    .first()
                    .ledger.keys,
            )

            // Undo after the retraction went up: the entry goes up as new, never retracting a record LibreView never had
            val vDel = clock.now().toEpochMilli()
            clock.advance(Duration.ofMinutes(1))
            treatments.upsertIfNewer(treatment("new", 5, amount = 3.0, modified = clock.now()))
            uploader.cycle()
            val undo = uploads().last()
            assertFalse("\"action\":\"deleted\"" in undo)
            assertFalse(LibreViewPayload.recordNumber("new", "rapid", vDel).toString() in undo)
            assertTrue(LibreViewPayload.recordNumber("new", "rapid", clock.now().toEpochMilli()).toString() in undo)
        }

    @Test
    fun `a retracted entry brought back goes up only as a new entry would, and the mark agrees`() {
        val back = treatment("x", 5, modified = now).treatment
        val ledger = mapOf("x" to LvSent(0L, back.at.epochSecond, "rapid"))
        // entries switched off: nothing goes up, no mark
        assertTrue(LibreViewQueue.entries(listOf(TreatmentRecord(back)), ledger, Long.MAX_VALUE, now.epochSecond).isEmpty())
        assertEquals(null, LibreViewQueue.mark(back, ledger, 0L, now.epochSecond))
        // entries on: a new record, no retraction
        val sent = LibreViewQueue.entries(listOf(TreatmentRecord(back)), ledger, now.epochSecond - 60, now.epochSecond)
        assertEquals(listOf(0L), sent.map { it.lv })
        assertEquals(LvMark.Pending, LibreViewQueue.mark(back, ledger, now.epochSecond - 60, now.epochSecond))
    }

    @Test
    fun `home's marks follow the ledger, and a stopped upload marks nothing as on its way`() =
        runBlocking {
            signedInAndOn()
            treatments.upsertIfNewer(treatment("a", 5))
            val a = treatments.get("a")!!.treatment
            assertEquals(LvMark.Pending, uploader.entryMarks.first()!!.mark(a, clock.now()))
            assertFalse(uploader.entryMarks.first()!!.removesOnDelete(a))
            uploader.cycle()
            val marks = uploader.entryMarks.first()!!
            assertEquals(LvMark.Uploaded, marks.mark(a, clock.now()))
            assertTrue(marks.removesOnDelete(a))
            // edited: LibreView has the older version
            assertEquals(LvMark.Updating, marks.mark(a.copy(amount = 4.0, modifiedAt = clock.now() + Duration.ofSeconds(5)), clock.now()))
            // changed before the upload was switched on: never goes up, no mark
            val old = treatment("old", 600, modified = clock.now() - Duration.ofDays(1)).treatment
            assertEquals(null, marks.mark(old, clock.now()))

            measurements = MockResponse().setBody("""{"status":20,"reason":"wrongDeviceInToken"}""")
            otherDevice = true
            treatments.upsertIfNewer(treatment("b", 5))
            uploader.cycle()
            uploader.cycle()
            val stopped = uploader.entryMarks.first()!!
            assertTrue(stopped.stopped)
            assertFalse(stopped.removesOnDelete(a))
        }

    @Test
    fun `an expired token signs in again by itself`() =
        runBlocking {
            signedInAndOn()
            treatments.upsertIfNewer(treatment("a", 5, modified = clock.now() + Duration.ofSeconds(1)))
            refuseTokenOnce = true
            uploader.cycle()
            assertEquals(null, secure.read(LibreViewUploader.NAME))
            uploader.cycle()
            assertEquals(2, logins().size)
            assertTrue(uploader.page.first().status is UploadStatus.Ok)
            assertEquals(2, uploads().size)
        }

    @Test
    fun `a token another device took over waits for Take over`() =
        runBlocking {
            signedInAndOn()
            treatments.upsertIfNewer(treatment("a", 5, modified = clock.now() + Duration.ofSeconds(1)))
            measurements = MockResponse().setBody("""{"status":20,"reason":"wrongDeviceInToken"}""")
            otherDevice = true
            uploader.cycle()
            // the next cycle signs in again, and LibreView says another device uploads
            uploader.cycle()
            assertEquals(UploadStatus.AuthFailed, uploader.page.first().status)
            val n = requests.size
            uploader.cycle()
            assertEquals(n, requests.size)
            measurements = null
            assertEquals(LvTakeOver.Done, uploader.takeOver())
            uploader.cycle()
            assertTrue(uploader.page.first().status is UploadStatus.Ok)
        }

    @Test
    fun `server trouble backs off, a refusal waits for a change`() =
        runBlocking {
            signedInAndOn()
            treatments.upsertIfNewer(treatment("a", 5))
            measurements = MockResponse().setResponseCode(503)
            uploader.cycle()
            assertTrue(uploader.page.first().status is UploadStatus.Failing)
            val n = requests.size
            uploader.cycle()
            assertEquals("backoff: nothing sent", n, requests.size)

            clock.advance(Duration.ofMinutes(20))
            measurements = MockResponse().setBody("""{"status":5,"reason":"bad"}""")
            uploader.cycle()
            assertTrue(store.data.first().refused)
            val m = requests.size
            clock.advance(Duration.ofMinutes(20))
            uploader.cycle()
            assertEquals("refused: nothing sent until a change", m, requests.size)
        }

    @Test
    fun `glucose goes up only for the current sensor, from the moment it was switched on`() =
        runBlocking {
            signedInAndOn()
            val from = store.data.first().glucoseFrom
            clock.advance(Duration.ofMinutes(10))
            val t = clock.now().epochSecond
            sensor.value = CurrentSensor("3MH000ABCD", now - Duration.ofDays(2), now + Duration.ofDays(12))
            // another sensor's batch: never sent under this sensor
            uploader.buffer(LvReadings("OTHER00001", LvGlucose(2000, 99, t), emptyList()))
            uploader.cycle()
            assertEquals(0, uploads().size)

            uploader.buffer(
                LvReadings(
                    "3MH000ABCD",
                    LvGlucose(2880, 123, t, 0.2),
                    listOf(LvGlucose(2870, 118, from - 600), LvGlucose(2875, 120, t - 300)),
                ),
            )
            uploader.cycle()
            assertEquals(1, requests.count { it.path == "/api/nisperson" && it.method == "PUT" })
            val body = uploads().single()
            val h = LibreViewPayload.histor("3MH000ABCD")
            assertTrue("\"recordNumber\":${h or 2880L}," in body)
            assertTrue("\"recordNumber\":${h or 2875L}," in body)
            assertFalse("before the switch: not sent", "\"recordNumber\":${h or 2870L}," in body)
            assertTrue("sensorstart" in body)

            // sent once
            uploader.cycle()
            assertEquals(1, uploads().size)
        }

    @Test
    fun `switching off ends the windows and a follower never uploads`() =
        runBlocking {
            signedInAndOn()
            uploader.change { it.copy(on = false) }
            assertEquals(0L, store.data.first().glucoseFrom)
            assertEquals(0L, store.data.first().entriesFrom)
            uploader.change { it.copy(on = true) }
            role.value = Role.Follower
            treatments.upsertIfNewer(treatment("a", 5, modified = clock.now() + Duration.ofSeconds(1)))
            uploader.cycle()
            assertEquals(0, uploads().size)
            assertEquals(UploadStatus.Off, uploader.page.first().status)
        }

    @Test
    fun `signing out anywhere drops the token, and a token without an account goes at the next start`() =
        runBlocking {
            signedInAndOn()
            signOut()
            assertEquals(null, secure.read(LibreViewUploader.NAME))
            assertEquals(UploadStatus.Setup, uploader.page.first().status)
            assertEquals(null, uploader.entryMarks.first())

            // signed out while the app wasn't running (or an install from before the shared account)
            secure.write(LibreViewUploader.NAME, "{}".toByteArray())
            uploader = newUploader()
            uploader.load()
            assertEquals(null, secure.read(LibreViewUploader.NAME))
        }

    @Test
    fun `a changed kind is retracted under the kind LibreView has`() =
        runBlocking {
            signedInAndOn()
            clock.advance(Duration.ofMinutes(1))
            treatments.upsertIfNewer(treatment("k", 5, modified = clock.now()))
            uploader.cycle()
            val v1 = clock.now().toEpochMilli()
            clock.advance(Duration.ofMinutes(1))
            treatments.upsertIfNewer(treatment("k", 5, amount = 4.0, modified = clock.now(), kind = TreatmentKind.LongInsulin))
            uploader.cycle()
            val body = uploads().last()
            assertTrue("\"recordNumber\":${LibreViewPayload.recordNumber("k", "rapid", v1)}" in body)
            assertTrue("\"recordNumber\":${LibreViewPayload.recordNumber("k", "long", clock.now().toEpochMilli())}" in body)
            assertEquals(
                "long",
                store.data
                    .first()
                    .ledger
                    .getValue("k")
                    .k,
            )
            uploader.cycle()
            assertEquals(2, uploads().size)
        }

    @Test
    fun `a refused sensor registration isn't asked again until the next sign-in`() =
        runBlocking {
            signedInAndOn()
            clock.advance(Duration.ofMinutes(10))
            val t = clock.now().epochSecond
            sensor.value = CurrentSensor("3MH000ABCD", now - Duration.ofDays(2), now + Duration.ofDays(12))
            refuseRegistration = true
            uploader.buffer(LvReadings("3MH000ABCD", LvGlucose(2880, 123, t), emptyList()))
            uploader.cycle()
            uploader.cycle()
            assertEquals(1, requests.count { it.path == "/api/nisperson" })
            assertEquals(0, uploads().size)
            assertTrue(uploader.page.first().status is UploadStatus.Failing)
            refuseRegistration = false
            // signed out and in again: the upload's next sign-in may bring the profile
            signOut()
            login.save("me@example.com", "secret-pw")
            uploader.followLogin(login.current())
            uploader.cycle()
            assertEquals(2, requests.count { it.path == "/api/nisperson" })
            assertEquals(1, uploads().size)
        }

    @Test
    fun `refused entries don't hold back glucose`() =
        runBlocking {
            signedInAndOn()
            clock.advance(Duration.ofMinutes(10))
            val t = clock.now().epochSecond
            treatments.upsertIfNewer(treatment("a", 5, modified = clock.now()))
            sensor.value = CurrentSensor("3MH000ABCD", now - Duration.ofDays(2), now + Duration.ofDays(12))
            uploader.buffer(LvReadings("3MH000ABCD", LvGlucose(2880, 123, t), emptyList()))
            measurements = MockResponse().setBody("""{"status":5,"reason":"bad"}""")
            uploader.cycle()
            measurements = null
            uploader.cycle()
            val last = uploads().last()
            assertTrue("valueInMgPerDl" in last)
            assertFalse("insulinType" in last)
        }

    @Test
    fun `another account starts from nothing`() =
        runBlocking {
            signedInAndOn()
            clock.advance(Duration.ofMinutes(1))
            treatments.upsertIfNewer(treatment("a", 5, modified = clock.now()))
            uploader.cycle()
            assertEquals(
                1,
                store.data
                    .first()
                    .ledger.size,
            )
            val device = store.data.first().deviceId
            clock.advance(Duration.ofMinutes(1))
            signOut()
            login.save("other@example.com", "secret-pw")
            uploader.cycle()
            assertEquals(emptyMap<String, LvSent>(), store.data.first().ledger)
            assertEquals("other@example.com", store.data.first().email)
            assertEquals("the device id stays", device, store.data.first().deviceId)
        }

    @Test
    fun `the same account is decided by its AccountId, not the email`() =
        runBlocking {
            signedInAndOn()
            clock.advance(Duration.ofMinutes(1))
            treatments.upsertIfNewer(treatment("a", 5, modified = clock.now()))
            uploader.cycle()
            assertEquals(accountOf("me@example.com"), store.data.first().accountId)
            // another email of the same LibreView account keeps what LibreView has
            signOut()
            login.save("me.too@example.com", "secret-pw")
            uploader.cycle()
            assertEquals(
                1,
                store.data
                    .first()
                    .ledger.size,
            )
            assertEquals("me.too@example.com", store.data.first().email)
        }

    @Test
    fun `history not sent yet survives a process death and goes up after it`() =
        runBlocking {
            signedInAndOn()
            clock.advance(Duration.ofMinutes(10))
            val t = clock.now().epochSecond
            sensor.value = CurrentSensor("3MH000ABCD", now - Duration.ofDays(2), now + Duration.ofDays(12))
            measurements = MockResponse().setResponseCode(503)
            assertTrue(uploader.buffer(LvReadings("3MH000ABCD", null, listOf(LvGlucose(2870, 118, t - 300), LvGlucose(2875, 120, t)))))
            uploader.keepUnsent()
            uploader.cycle()
            assertEquals(
                listOf(2870, 2875),
                unsent.data
                    .first()
                    .history
                    .map { it.lifeCount },
            )

            // the process dies: a new uploader has only the stores
            measurements = null
            clock.advance(Duration.ofMinutes(20))
            uploader = newUploader()
            uploader.load()
            uploader.cycle()
            val h = LibreViewPayload.histor("3MH000ABCD")
            val body = uploads().last()
            assertTrue("\"recordNumber\":${h or 2870L}," in body)
            assertTrue("\"recordNumber\":${h or 2875L}," in body)
            assertEquals("sent: nothing kept", LvUnsent(), unsent.data.first())
        }
}
