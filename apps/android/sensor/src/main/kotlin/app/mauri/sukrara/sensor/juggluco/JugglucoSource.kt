// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.sensor.juggluco

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.sensor.SensorInfo
import app.mauri.sukrara.sensor.SensorLife
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** What the Juggluco source is doing (the UI words live in the sensor screens). */
sealed interface JugglucoStatus {
    data object FetchingHistory : JugglucoStatus

    data object Live : JugglucoStatus

    /** Juggluco's server isn't answering (not running, web server off, wrong mode); retried with backoff. */
    data class Unreachable(val why: String) : JugglucoStatus

    /**
     * Juggluco refused Sukrara (HTTP 401 / 403): its web server has an API secret and Sukrara's is missing or
     * different ([JugglucoSecret]). No readings until they match; retried with backoff (a secret saved meanwhile is
     * used at the next try).
     */
    data object Refused : JugglucoStatus
}

/** Juggluco answered 401 / 403: the API secret is missing or wrong. */
internal class JugglucoRefusedException : Exception("refused: API secret")

/** One Juggluco reading with the sensor it came from: its [serial] and the sensor's own minute counter ([lifeCount]). */
data class JugglucoEntry(
    val reading: Reading,
    val serial: String,
    val lifeCount: Int,
)

/** Juggluco's live value: the reading, Juggluco's sensor index ([sensorId], -1 = none) and the sensor's minute counter. */
data class JugglucoLive(
    val reading: Reading,
    val sensorId: Int,
    val lifeCount: Int,
)

/** Where the Juggluco source sends what it learns. */
internal interface JugglucoSink {
    /** [live] = Juggluco's current value from the stream; false = history. */
    fun onReadings(readings: List<Reading>, live: Boolean)

    /** The same readings with their sensor and life count, where Juggluco names them (the LibreView upload's records). */
    fun onSensorReadings(entries: List<JugglucoEntry>, live: Boolean) = Unit

    fun onStatus(status: JugglucoStatus)

    fun onSensor(info: SensorInfo)
}

/**
 * Glucose from Juggluco's web server on this phone ("Local only" mode, loopback cleartext): GET /api/v1/entries.json
 * for history (at the first connection the current sensor's whole life, so the phone holds all of it even when Sukrara
 * came mid-sensor; after a reconnect the last 3 h), then hold GET /x/live (SSE) open — push, no polling. Reconnects
 * with backoff 5 s → 60 s. [run] loops until its coroutine is cancelled; cancelling also cancels the open call. With an
 * API secret ([secret], read again at every connection) every request carries `api-secret: sha1hex(secret)`; a refusal
 * is [JugglucoStatus.Refused].
 */
internal class JugglucoSource(
    http: OkHttpClient,
    private val baseUrl: String = LOCAL_URL,
    private val secret: suspend () -> String? = { null },
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val zone: () -> ZoneId,
) {
    companion object {
        /** Juggluco's default web-server port on this phone. Loopback only (never leaves the device). */
        const val LOCAL_URL = "http://127.0.0.1:17580"
        const val FIRST_BACKOFF_MS = 5_000L
        const val MAX_BACKOFF_MS = 60_000L

        /** History asked for at the first connection without a known sensor, and after a reconnect (minutes). */
        const val FIRST_COUNT = 1600
        const val AGAIN_COUNT = 180

        /** The longest sensor life read back (a 15-day sensor and a day of margin), minutes. */
        const val MAX_LIFE_MIN = 16 * 24 * 60
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Nightscout entries → readings. Refuses entries whose device is "Sukrara": if anything of ours ever holds
         * Juggluco's port, re-publishing our own values as fresh would be fake data.
         */
        fun parseEntries(body: String): List<Reading> = parseHistory(body).first

        /**
         * [parseEntries], and the readings whose `_id` names their sensor (Juggluco writes `SERIAL#lifeCount`) as
         * [JugglucoEntry]s.
         */
        fun parseHistory(body: String): Pair<List<Reading>, List<JugglucoEntry>> {
            val arr = json.parseToJsonElement(body) as JsonArray
            val entries = arr.map { it.jsonObject }
            check(entries.none { it.str("device") == "Sukrara" }) { "port 17580 is served by Sukrara, not Juggluco" }
            val readings = ArrayList<Reading>(entries.size)
            val named = ArrayList<JugglucoEntry>(entries.size)
            for (e in entries) {
                val t = e["date"]?.jsonPrimitive?.longOrNull ?: continue
                val mgdl = e["sgv"]?.jsonPrimitive?.intOrNull ?: continue
                val delta = e["delta"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull
                val rate = if (e.str("direction").isNullOrEmpty() || delta == null) null else delta / 5.0
                val r = reading(t / 1000, mgdl, rate)
                readings += r
                val id = e.str("_id").orEmpty()
                val serial = id.substringBefore('#', "")
                val life = id.substringAfter('#', "").toIntOrNull()
                if (serial.isNotEmpty() && life != null && life >= 0) named += JugglucoEntry(r, serial, life)
            }
            return readings to named
        }

        /** One SSE `glucose` event's data → the reading, Juggluco's sensor index (-1 = none) and the life count (-1). */
        fun parseLive(data: String): JugglucoLive? {
            val j = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
            val t = j["timestamp"]?.jsonPrimitive?.longOrNull ?: return null
            val mgdl = j["glucoseMgDl"]?.jsonPrimitive?.intOrNull ?: return null
            val rate = j["rateMgDlPerMinute"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull
            return JugglucoLive(
                reading(t, mgdl, rate),
                j["sensorId"]?.jsonPrimitive?.intOrNull ?: -1,
                j["recordId"]?.jsonPrimitive?.intOrNull ?: -1,
            )
        }

        /**
         * The history request: at the [first] connection with the current sensor's start known ([startEpoch]), its whole
         * life, every minute; else the last [FIRST_COUNT] / [AGAIN_COUNT] minutes.
         */
        fun historyPath(first: Boolean, startEpoch: Long?, nowEpoch: Long): String {
            val start =
                startEpoch?.takeIf { first && it in (nowEpoch - MAX_LIFE_MIN * 60L)..nowEpoch }
                    ?: return "/api/v1/entries.json?count=${if (first) FIRST_COUNT else AGAIN_COUNT}&interval=0"
            val count = (nowEpoch - start) / 60 + 60
            return "/api/v1/entries.json?count=$count&interval=0&find[date][\$gte]=${start * 1000}"
        }

        /**
         * Juggluco's `sensor_status` (watchserver.cpp `firstdata`): `"dd-mm-yy HH:MM (3d 5h)"`, the start in the phone's
         * local time; the serial is the entry's `_id` before `#`. No wear length published → the Libre 3 Plus default.
         */
        fun parseSensorStatus(body: String, zone: ZoneId): SensorInfo? =
            runCatching {
                val e = (json.parseToJsonElement(body) as JsonArray).first().jsonObject
                val st = e.str("sensor_status")?.takeIf { it.length >= 14 } ?: return null
                val start =
                    LocalDateTime
                        .parse(st.substring(0, 14), DateTimeFormatter.ofPattern("dd-MM-yy HH:mm"))
                        .atZone(zone)
                        .toEpochSecond()
                SensorLife.fromStart(e.str("_id").orEmpty().substringBefore('#'), start, 0)
            }.getOrNull()

        private fun JsonObject.str(key: String) = this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

        private fun reading(epochSec: Long, mgdl: Int, rate: Double?) =
            Reading(Instant.ofEpochSecond(epochSec), mgdl, rate, SourceId.Juggluco)
    }

    private val http =
        http
            .newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS) // > Juggluco's 20 s SSE keep-alive: a silent stream fails and reconnects
            .build()

    @Volatile private var call: Call? = null

    @Volatile private var liveCall: Call? = null

    /** The `api-secret` header value of this connection, null without a secret. */
    @Volatile private var hashed: String? = null

    /** The current sensor's serial (Juggluco's live events name only its index). */
    @Volatile private var serial: String? = null

    /** Cancels the open requests, the live stream included (the coroutine running [run] must be cancelled too). */
    fun cancelCall() {
        call?.cancel()
        liveCall?.cancel()
    }

    suspend fun run(sink: JugglucoSink) {
        var first = true
        var backoff = FIRST_BACKOFF_MS
        while (currentCoroutineContext().isActive) {
            try {
                hashed = secret()?.let(JugglucoSecret::header)
                sink.onStatus(JugglucoStatus.FetchingHistory)
                // the sensor first: the first history reaches back to its start
                val info = sensor()
                val (readings, named) = get(historyPath(first, info?.startEpoch, now()), ::parseHistory)
                sink.onReadings(readings, live = false)
                if (named.isNotEmpty()) sink.onSensorReadings(named, live = false)
                first = false
                info?.let(sink::onSensor)
                serial = info?.serial
                live(sink) { backoff = FIRST_BACKOFF_MS }
                sink.onStatus(JugglucoStatus.Unreachable("live stream ended"))
            } catch (e: JugglucoRefusedException) {
                if (!currentCoroutineContext().isActive) break
                sink.onStatus(JugglucoStatus.Refused)
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive) break
                sink.onStatus(JugglucoStatus.Unreachable("${e.javaClass.simpleName}: ${e.message?.take(80)}"))
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun request(path: String): Request.Builder =
        Request.Builder().url("$baseUrl$path").apply { hashed?.let { header(JugglucoSecret.HEADER, it) } }

    private fun answered(code: Int) {
        if (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN) throw JugglucoRefusedException()
    }

    private fun <T> get(path: String, parse: (String) -> T): T {
        val req = request(path).build()
        http.newCall(req).also { call = it }.execute().use { resp ->
            answered(resp.code)
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            return parse(resp.body?.string().orEmpty())
        }
    }

    /** The current sensor; a refusal (the API secret) is the source's, anything else just leaves it unknown. */
    private fun sensor(): SensorInfo? =
        try {
            get("/sgv.json?count=1&sensor=Y") { parseSensorStatus(it, zone()) }
        } catch (e: JugglucoRefusedException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun live(sink: JugglucoSink, onData: () -> Unit) {
        val req = request("/x/live").header("Accept", "text/event-stream").build()
        http.newCall(req).also { liveCall = it }.execute().use { resp ->
            answered(resp.code)
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            sink.onStatus(JugglucoStatus.Live)
            val src = checkNotNull(resp.body).source()
            var event = ""
            var sensorId = -1
            while (true) {
                val line = src.readUtf8Line() ?: return
                when {
                    line.isEmpty() -> {
                        event = ""
                    }

                    line.startsWith("event:") -> {
                        event = line.substring(6).trim()
                    }

                    line.startsWith("data:") && event == "glucose" -> {
                        val live = parseLive(line.substring(5).trim()) ?: continue
                        onData()
                        sink.onReadings(listOf(live.reading), live = true)
                        // a new sensor in Juggluco: read its start again
                        if (sensorId != -1 && live.sensorId != sensorId) {
                            sensor()?.let {
                                sink.onSensor(it)
                                serial = it.serial
                            }
                        }
                        sensorId = live.sensorId
                        val s = serial
                        if (s != null && live.lifeCount >= 0) {
                            sink.onSensorReadings(listOf(JugglucoEntry(live.reading, s, live.lifeCount)), live = true)
                        }
                    }
                }
            }
        }
    }
}
