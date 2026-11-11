// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.history

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.ImportSource
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryHistoryImport
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.integrations.tidepool.TidepoolUploader
import app.mauri.sukrara.integrations.tidepool.TpSignIn
import app.mauri.sukrara.integrations.tidepool.TpState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Import history end to end through the shared import path (in-memory stores with the same rules as Room): a file and a
 * fake Tidepool (MockWebServer on loopback: no real account is ever contacted), each run twice (the second adds
 * nothing), Tidepool's rate limit honoured, a signed-out account said, and imported readings never newer than 15
 * minutes. Synthetic data only.
 */
class HistoryImporterTest {
    private val now = Instant.parse("2026-11-14T15:00:00Z")
    private val clock = FakeClock(now, ZoneId.of("America/New_York"))
    private val readings = InMemoryReadingsRepository()
    private val treatments = InMemoryTreatmentsRepository()
    private val store = InMemoryHistoryImport(readings, treatments)
    private val server = MockWebServer()
    private val requests = mutableListOf<RecordedRequest>()
    private val waits = mutableListOf<Long>()
    private var busyOnce = true
    private lateinit var tidepool: TidepoolUploader

    private fun resource(name: String) = javaClass.getResource("/history/$name")!!.readText()

    private fun importer() =
        HistoryImporter(store, { block -> tidepool.withAccount(block) }, clock, Dispatchers.Unconfined, pause = { waits += it })

    private suspend fun all() = readings.range(now - Duration.ofDays(365), now + Duration.ofDays(1))

    /** One week of Tidepool data: readings in mmol/L, a bolus Sukrara sent itself, food, a calculator's carbs, a finger-stick. */
    private val week =
        """[
          {"type":"cbg","time":"2026-11-10T13:00:00.000Z","value":6.66,"units":"mmol/L","id":"c1"},
          {"type":"cbg","time":"2026-11-10T13:05:00.000Z","value":6.88,"units":"mmol/L","id":"c2"},
          {"type":"cbg","time":"2026-11-14T14:50:00.000Z","value":7.0,"units":"mmol/L","id":"c3"},
          {"type":"smbg","time":"2026-11-10T13:06:00.000Z","value":5.5,"units":"mmol/L","id":"s1"},
          {"type":"bolus","subType":"normal","normal":2.5,"time":"2026-11-10T13:10:00.000Z","id":"b1",
           "origin":{"id":"sukrara-tr-6f1c2b9a-0d3e-4c5f-8a7b-0123456789ab","name":"app.mauri.sukrara"}},
          {"type":"insulin","dose":{"total":12,"units":"Units"},"formulation":{"simple":{"actingType":"long"}},
           "time":"2026-11-10T22:00:00.000Z","id":"i1"},
          {"type":"food","nutrition":{"carbohydrate":{"net":45,"units":"grams"}},"time":"2026-11-10T13:10:00.000Z","id":"f1",
           "notes":["pasta"]},
          {"type":"wizard","carbInput":30,"time":"2026-11-11T12:00:00.000Z","id":"w1"}
        ]"""

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.path.orEmpty()
                    if (path == "/auth/login" && request.method == "POST") {
                        return MockResponse().setHeader("X-Tidepool-Session-Token", "tok").setBody("""{"userid":"u1"}""")
                    }
                    if (request.getHeader("X-Tidepool-Session-Token") != "tok") return MockResponse().setResponseCode(401)
                    return when {
                        path.startsWith("/data/u1?") -> {
                            if (busyOnce) {
                                busyOnce = false
                                return MockResponse().setResponseCode(429).setHeader("Retry-After", "7")
                            }
                            val url = request.requestUrl!!
                            val start = Instant.parse(url.queryParameter("startDate"))
                            val end = Instant.parse(url.queryParameter("endDate"))
                            val inWeek = Instant.parse("2026-11-10T13:00:00Z").let { it >= start && it < end }
                            MockResponse().setBody(if (inWeek) week else "[]")
                        }

                        path.startsWith("/message/notes/u1?") -> {
                            MockResponse().setBody(
                                """{"messages":[{"id":"m1","timestamp":"2026-11-09T12:00:00.000Z","messagetext":"sick day"},
                                   {"id":"m2","parentmessage":"m1","timestamp":"2026-11-09T12:30:00.000Z","messagetext":"a reply"}]}""",
                            )
                        }

                        else -> {
                            MockResponse().setResponseCode(404)
                        }
                    }
                }
            }
        server.start()
        tidepool =
            TidepoolUploader(
                InMemorySettingsRepository(),
                InMemorySecureStore(),
                InMemoryStateStore(TpState()),
                readings,
                treatments,
                MutableStateFlow<Role?>(Role.Wearer),
                OkHttpClient(),
                "1.0-test",
                clock,
                Dispatchers.Unconfined,
                baseUrl = server.url("/").toString(),
            )
        runBlocking { tidepool.load() }
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `a LibreView file - deduplicated, run twice adds nothing, the sensor's own reading wins`() =
        runBlocking {
            // the sensor already has 13:05 (New York): LibreView's copy of it stays out
            readings.upsert(listOf(Reading(Instant.parse("2026-11-08T18:05:30Z"), 117, null, SourceId.Libre3)))
            val importer = importer()
            importer.importFile(resource("libreview-us.csv"))
            val done = importer.state.value as ImportState.Done
            assertEquals(ImportSource.LibreView, done.source)
            // 7 readings: two copies of the sensor's 13:05 and a scan 2 minutes later stay out
            assertEquals(4, done.tally.readings)
            // 6 entries: the same bolus from two devices counts once
            assertEquals(5, done.tally.entries)
            assertEquals(2, done.tally.skipped)
            assertEquals(listOf(112, 117, 126, 98, 39), all().map { it.mgdl })

            importer.importFile(resource("libreview-us.csv"))
            val again = (importer.state.value as ImportState.Done).tally
            assertEquals(0, again.added)
            assertEquals(5, all().size)
            assertTrue(treatments.all.all { it.importedFrom == ImportSource.LibreView })
        }

    @Test
    fun `issue 176 - LibreView's file after the earlier app's export adds nothing twice, unread dates say which`() =
        runBlocking {
            // what the owner imported instead: the earlier app's export of the same evening (its exact times)
            val evening = Instant.parse("2026-11-10T02:55:00Z") // 9:55 PM in New York
            store.importReadings(
                listOf(
                    Reading(evening + Duration.ofSeconds(40), 140, null, ImportSource.File.readings),
                    Reading(evening + Duration.ofMinutes(5).plusSeconds(40), 138, null, ImportSource.File.readings),
                ),
            )
            store.importTreatments(
                listOf(
                    TreatmentRecord(
                        Treatment(
                            "earlier-app",
                            TreatmentKind.RapidInsulin,
                            3.5,
                            Instant.parse("2026-11-10T18:05:20Z"),
                            "lunch",
                            now,
                            false,
                            TreatmentOrigin.Phone,
                        ),
                        importedFrom = ImportSource.File,
                    ),
                ),
            )
            val importer = importer()
            importer.importFile(resource("libreview-us-12h.csv"))
            val done = importer.state.value as ImportState.Done
            assertEquals(5, done.tally.readings)
            // the carbs are new; the bolus is the export's
            assertEquals(1, done.tally.entries)
            assertEquals(3, done.tally.alreadyHere)
            assertEquals(7, all().size)
            assertEquals(2, treatments.all.size)

            importer.importFile(resource("libreview-us-12h.csv").replace("11-10-2026 11:55 AM", "11-10-2026 noon"))
            assertEquals(ImportFailure.UnknownDates("11-10-2026 noon"), (importer.state.value as ImportState.Failed).why)
            assertEquals(7, all().size)
        }

    @Test
    fun `a file that isn't a glucose file says so and adds nothing`() =
        runBlocking {
            val importer = importer()
            importer.importFile("just,some\nnumbers,1\n")
            assertEquals(ImportFailure.NotGlucoseData, (importer.state.value as ImportState.Failed).why)
            importer.importFile(null)
            assertEquals(ImportFailure.Unreadable, (importer.state.value as ImportState.Failed).why)
            assertTrue(all().isEmpty())
            importer.dismiss()
            assertEquals(ImportState.Idle, importer.state.value)
        }

    @Test
    fun `Tidepool - every week and the notes, mapped back, a rate limit waited out, run twice adds nothing`() =
        runBlocking {
            assertEquals(TpSignIn.Done, tidepool.signIn("me@example.com", "secret-pw"))
            val importer = importer()
            importer.importTidepool()
            val done = importer.state.value as ImportState.Done
            // 13 weeks of 90 days, then the notes
            assertEquals(13, requests.count { it.path!!.startsWith("/data/u1?") } - 1)
            assertEquals(1, requests.count { it.path!!.startsWith("/message/notes/u1?") })
            assertTrue("Tidepool's Retry-After: $waits", 7_000L in waits)
            // readings in mg/dL; the one from 10 minutes ago is left to the sensor; a finger-stick isn't a reading
            assertEquals(listOf(120, 124), all().map { it.mgdl })
            assertEquals(2, done.tally.readings)
            val entries = treatments.all.associateBy { it.treatment.kind to it.treatment.note }
            // Sukrara's own entry comes back with its own id
            assertEquals("6f1c2b9a-0d3e-4c5f-8a7b-0123456789ab", entries.getValue(TreatmentKind.RapidInsulin to "").treatment.id)
            assertEquals(12.0, entries.getValue(TreatmentKind.LongInsulin to "").treatment.amount, 1e-9)
            assertEquals(45.0, entries.getValue(TreatmentKind.Carbs to "pasta").treatment.amount, 1e-9)
            assertEquals(30.0, entries.getValue(TreatmentKind.Carbs to "").treatment.amount, 1e-9)
            assertEquals("sick day", entries.getValue(TreatmentKind.Note to "sick day").treatment.note)
            assertEquals(5, done.tally.entries)
            assertTrue(treatments.all.all { it.importedFrom == ImportSource.Tidepool })

            importer.importTidepool()
            assertEquals(0, (importer.state.value as ImportState.Done).tally.added)
        }

    @Test
    fun `Tidepool signed out - said, nothing asked`() =
        runBlocking {
            val importer = importer()
            importer.importTidepool()
            assertEquals(ImportFailure.SignedOut, (importer.state.value as ImportState.Failed).why)
            assertTrue(requests.none { it.path!!.startsWith("/data/") })
        }
}
