// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import app.mauri.sukrara.core.model.AlertControls
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.KindFacts
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.storage.SecureStore
import app.mauri.sukrara.integrations.localserver.HttpAnswer
import app.mauri.sukrara.integrations.localserver.HttpRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** One entry from the watch, checked and signed (contracts/watchface/payload README "Entries from the watch"). */
data class WatchEntry(
    /** The watch's id for it, a lowercase UUID v4: the treatment's id in the log. */
    val id: String,
    val kind: TreatmentKind,
    /** The amount exactly as the watch wrote it ("4.5", "30"): the phone's own form rules read it. */
    val amount: String,
    val at: Instant,
    /** The person said *Log anyway* after [WatchLogged.Pen]. */
    val force: Boolean,
)

/** What the phone's entry path did with a [WatchEntry]. */
sealed interface WatchLogged {
    data class Stored(
        val treatment: Treatment,
    ) : WatchLogged

    /** An entry with this id is in the log; [treatment] null = it was deleted on the phone. */
    data class Known(
        val treatment: Treatment?,
    ) : WatchLogged

    /** A pen entry of this insulin already logged it: [at] epoch s, [units]. */
    data class Pen(
        val at: Long,
        val units: Double,
    ) : WatchLogged

    data object Amount : WatchLogged

    data object When : WatchLogged

    data object Off : WatchLogged
}

/**
 * The phone's own treatment entry, for entries from the watch (AppGraph: the treatments module's, so a watch entry goes
 * through exactly what the phone's Add treatment runs: who may add, the form's limits, *Already logged by your pen?*,
 * the log, sync, the alert runtime's quiet rules).
 */
interface WatchEntries {
    /** Whether this phone takes entries now (its Add treatment is offered). */
    val open: Flow<Boolean>

    suspend fun add(e: WatchEntry): WatchLogged

    /** What THE kinds' order is decided from now, as the phone's Add treatment decides it (the payload's `log.order`). */
    suspend fun facts(): KindFacts

    companion object {
        /** No entry path (tests, a build without treatments): nothing is taken. */
        val None =
            object : WatchEntries {
                override val open: Flow<Boolean> = flowOf(false)

                override suspend fun add(e: WatchEntry) = WatchLogged.Off

                override suspend fun facts() = KindFacts.NONE
            }
    }
}

/**
 * The key the watch signs its writes with: 32 random bytes made once on this phone and kept in the secure store,
 * pushed only to Sukrara's app (never the fetch, never a log line). Null while it can't be read: a stored key that
 * can't be opened is never replaced by a new one silently (the next read may work).
 */
class WatchKey(
    private val secure: SecureStore,
) {
    private val lock = Mutex()

    @Volatile private var cached: ByteArray? = null

    suspend fun get(): ByteArray? {
        cached?.let { return it }
        return lock.withLock {
            cached ?: runCatching {
                secure.read(NAME) ?: ByteArray(SIZE).also {
                    SecureRandom().nextBytes(it)
                    secure.write(NAME, it)
                }
            }.getOrNull()?.also { cached = it }
        }
    }

    companion object {
        const val NAME = "garmin-watch-key"
        const val SIZE = 32

        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    }
}

/**
 * `POST /sukrara/entries` and `POST /sukrara/mute` (contracts/watchface/payload README "Entries from the watch",
 * "Mute from the watch"): the watch's writes through Garmin Connect's loopback proxy. Each is checked in the contract's
 * order (the key's signature, the send time, the id, the fields) and handed to the phone's own path ([entries], the
 * alert runtime's [alerts]); every answer about a write is a 200 with `{id, r, …}`.
 */
class WatchWrites(
    private val key: suspend () -> ByteArray?,
    private val entries: WatchEntries,
    private val alerts: AlertControls?,
    private val clock: Clock,
    private val log: (String) -> Unit = {},
) {
    private val lock = Mutex()

    /** Mute actions already done (by id) in the last [CLOCK_S] × 2: a replay inside the send window does nothing. */
    private val doneMutes = LinkedHashMap<String, Long>()

    fun answer(req: HttpRequest): HttpAnswer =
        runBlocking {
            withTimeoutOrNull(TIMEOUT_MS) {
                when (req.path) {
                    ENTRIES -> entry(req)
                    MUTE -> mute(req)
                    else -> HttpAnswer.NOT_FOUND
                }
            } ?: HttpAnswer("503 Service Unavailable", "{}")
        }

    private suspend fun entry(req: HttpRequest): HttpAnswer {
        val f = fields(req) ?: return BAD
        val id = f["id"] ?: return BAD
        val k = f["k"] ?: return BAD
        val a = f["a"] ?: return BAD
        val t = f["t"] ?: return BAD
        val s = f["s"] ?: return BAD
        val force = f["f"] ?: return BAD
        val mac = f["mac"] ?: return BAD
        checked(id, listOf(id, k, a, t, s, force), s, mac)?.let { return it }
        if (!UUID_V4.matches(id)) return ok(id, "id")
        val kind = KINDS[k] ?: return ok(id, "kind")
        val at = t.toLongOrNull()?.let(Instant::ofEpochSecond) ?: return ok(id, "when")
        val done = lock.withLock { entries.add(WatchEntry(id, kind, a, at, force == "1")) }
        // ids and outcomes only, never an amount or the key
        log("watch entry $id: ${done.javaClass.simpleName}")
        return when (done) {
            is WatchLogged.Stored -> ok(id, "stored", "e" to entryJson(done.treatment))
            is WatchLogged.Known -> ok(id, "known", "e" to (done.treatment?.let(::entryJson) ?: JsonNull))
            is WatchLogged.Pen -> ok(id, "pen", "pen" to JsonObject(mapOf("t" to JsonPrimitive(done.at), "a" to JsonPrimitive(done.units))))
            WatchLogged.Amount -> ok(id, "amount")
            WatchLogged.When -> ok(id, "when")
            WatchLogged.Off -> ok(id, "off")
        }
    }

    private suspend fun mute(req: HttpRequest): HttpAnswer {
        val f = fields(req) ?: return BAD
        val id = f["id"] ?: return BAD
        val act = f["do"] ?: return BAD
        val side = f["side"] ?: return BAD
        val min = f["min"] ?: return BAD
        val s = f["s"] ?: return BAD
        val mac = f["mac"] ?: return BAD
        checked(id, listOf(id, act, side, min, s), s, mac)?.let { return it }
        if (!UUID_V4.matches(id)) return ok(id, "id")
        val minutes = min.toIntOrNull()
        val valid =
            side in SIDES &&
                when (act) {
                    // until turned off is Mute all's only: a side's mute always ends (contracts/alerts SPEC: 1–720 min)
                    "mute" -> minutes in MUTE_MINUTES && (side == "all" || minutes != 0)

                    "unmute" -> min == "0"

                    // Remind less / Remind as usual: one side's reminders (relaxMin), never Mute all
                    "less", "usual" -> min == "0" && side != "all"

                    else -> false
                }
        if (!valid) return ok(id, "action")
        val controls = alerts ?: return ok(id, "off")
        return lock.withLock {
            val now = clock.now().epochSecond
            doneMutes.entries.removeAll { now - it.value > 2 * CLOCK_S }
            if (id in doneMutes) return@withLock ok(id, "known")
            // the alert runtime's own actions, the ones the notification and home offer
            when {
                act == "unmute" && side == "all" -> controls.unmuteAll()
                act == "unmute" -> controls.unmute(side)
                act == "less" -> controls.remindLess(side)
                act == "usual" -> controls.remindAsUsual(side)
                side == "all" -> controls.muteAll(minutes.takeIf { it != 0 })
                else -> controls.mute(side, minutes!!)
            }
            doneMutes[id] = now
            log("watch $act $side ${minutes ?: 0} min: $id")
            ok(id, "done")
        }
    }

    /** The signature and the send time: null when both hold, else the answer. */
    private suspend fun checked(
        id: String,
        signed: List<String>,
        s: String,
        mac: String,
    ): HttpAnswer? {
        val k = key() ?: return ok(id, "sig")
        if (!MessageDigest.isEqual(sign(k, signed).toByteArray(), mac.lowercase().toByteArray())) return ok(id, "sig")
        val now = clock.now().epochSecond
        val sent = s.toLongOrNull()
        if (sent == null || kotlin.math.abs(sent - now) > CLOCK_S) return ok(id, "clock", "now" to JsonPrimitive(now))
        return null
    }

    private fun entryJson(t: Treatment): JsonElement =
        JsonObject(
            mapOf(
                "t" to JsonPrimitive(t.at.epochSecond),
                "k" to JsonPrimitive(KINDS.entries.first { it.value == t.kind }.key),
                "a" to JsonPrimitive(t.amount),
            ),
        )

    private fun ok(
        id: String,
        r: String,
        vararg more: Pair<String, JsonElement>,
    ) = HttpAnswer("200 OK", JsonObject(mapOf("id" to JsonPrimitive(id), "r" to JsonPrimitive(r)) + more).toString())

    companion object {
        const val ENTRIES = GarminApp.ENTRIES
        const val MUTE = "sukrara/mute"

        /** The send time may be this far from the phone's clock (s). */
        const val CLOCK_S = 300L

        /** The mute lengths the watch offers (minutes; 0 = until turned off). */
        val MUTE_MINUTES = setOf(15, 30, 60, 120, 0)
        val SIDES = setOf("all", "low", "high")

        private const val TIMEOUT_MS = 4_000L

        private val KINDS =
            mapOf("rapid" to TreatmentKind.RapidInsulin, "long" to TreatmentKind.LongInsulin, "carbs" to TreatmentKind.Carbs)
        private val UUID_V4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        private val BAD = HttpAnswer("400 Bad Request", """{"r":"bad"}""")

        /** HMAC-SHA256 with [key] over the fields joined by `|`, lowercase hex. */
        fun sign(
            key: ByteArray,
            fields: List<String>,
        ): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return WatchKey.hex(mac.doFinal(fields.joinToString("|").toByteArray()))
        }

        /** The body's fields as strings: a JSON object or a form; null when it is neither. */
        internal fun fields(req: HttpRequest): Map<String, String>? {
            val type =
                req.headers["content-type"]
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
            val body = req.body.trim()
            return if (type == "application/json" || (type == null && body.startsWith("{"))) {
                val o = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
                o.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { k to it.content } }.toMap()
            } else {
                body
                    .split('&')
                    .filter { it.isNotEmpty() }
                    .associate {
                        runCatching { URLDecoder.decode(it.substringBefore('='), "UTF-8") }.getOrDefault("") to
                            runCatching { URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }.getOrDefault("")
                    }.takeIf { it.isNotEmpty() }
            }
        }
    }
}
