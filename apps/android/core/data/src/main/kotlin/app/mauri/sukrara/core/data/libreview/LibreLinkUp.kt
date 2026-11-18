// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data.libreview

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.floor

/** One answer of LibreLinkUp's login (contracts/sources/librelinkup/login.json `answer`). */
sealed interface LluLogin {
    /** Signed in: the account's id, a token for the calls, and when it expires. */
    data class Account(
        val accountId: String,
        val token: String,
        val expires: Instant,
    ) : LluLogin {
        override fun toString() = "Account(***)"
    }

    /** Sign in again at the account's regional host (`api-<region>.libreview.io`). */
    data class Redirect(val region: String) : LluLogin

    data object BadCredentials : LluLogin

    /** New terms of use or a new privacy policy to accept in LibreLinkUp ([step]: `tou`, `pp`). */
    data class Terms(val step: String?) : LluLogin

    /** Too many attempts: wait at least [retryAfterS] (null = the source's own minimum). */
    data class RateLimited(val retryAfterS: Long?) : LluLogin

    /** This client's version is refused: [minimumVersion] is the one asked for. */
    data class Version(val minimumVersion: String) : LluLogin

    /** The login is gone: Abbott retired the API. */
    data object Retired : LluLogin

    data class Failed(val why: String) : LluLogin
}

/** An authenticated call's outcome (contracts/sources/librelinkup/calls.json). */
enum class LluCallOutcome { Ok, Expired, RateLimited, Terms, Version, Retired, Server, Network }

/** One authenticated call's answer: its [outcome], the body when [LluCallOutcome.Ok]. */
data class LluCall(
    val outcome: LluCallOutcome,
    val body: JsonObject? = null,
    val retryAfterS: Long? = null,
    val minimumVersion: String? = null,
)

/** Someone the account follows (a LibreLinkUp connection): their patient id and name. */
data class LluPerson(
    val id: String,
    val name: String,
)

/** Whose glucose the account brings (contracts/sources/librelinkup/connections.json). */
data class LluPick(
    val choices: List<LluPerson>,
    val state: State,
    val pick: String?,
) {
    enum class State { Picked, Choose, None, Gone }
}

/** A sensor LibreLinkUp says is running: its serial (a new serial = a new sensor) and start. */
data class LluSensor(
    val serial: String,
    val start: Instant,
)

/** One graph answer: the readings (the live one and the graph's points), the sensor, the person's name. */
data class LluGraph(
    val readings: List<Reading>,
    val sensor: LluSensor?,
    val name: String,
)

/**
 * THE LibreLinkUp client (docs/44 step 2, contracts/sources/librelinkup): the login Sukrara also signs in to LibreView
 * with (`LibreViewAccount`: the same headers, version, region redirect and terms step), the people an account follows
 * and their glucose graph. Pure parsing in the companion (both apps pass the same fixtures); [login] and [get] do one
 * HTTP call each, never following redirects, https only (a loopback `scheme` for tests). Never logs a body, a token or
 * a password.
 */
class LibreLinkUp(
    http: OkHttpClient,
    private val scheme: String = "https",
    private val baseHost: String = HOST,
    /** A test server's host for every region (`api-eu.libreview.io` would leave loopback). */
    private val regionHost: (String) -> String = { "api-$it.libreview.io" },
) {
    private val client =
        http
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /** The version header sent; raised once by a 403 that names a minimum ([LluLogin.Version]). */
    @Volatile var version: String = VERSION

    /** The host of [region] (null = the global one). */
    fun host(region: String?): String = region?.let(regionHost) ?: baseHost

    /** POST /llu/auth/login at [region]'s host. A network failure is [LluLogin.Failed] with its class name. */
    fun login(
        email: String,
        password: String,
        region: String?,
    ): LluLogin {
        val body =
            JsonObject(mapOf("email" to JsonPrimitive(email.trim()), "password" to JsonPrimitive(password))).toString()
        val request =
            Request
                .Builder()
                .url("$scheme://${host(region)}$LOGIN")
                .headers(headers(null, null))
                .post(body.toRequestBody(JSON))
                .build()
        return try {
            client.newCall(request).execute().use { r ->
                parseLogin(r.code, r.body?.string().orEmpty(), r.header("Retry-After")?.trim()?.toLongOrNull())
            }
        } catch (e: IOException) {
            LluLogin.Failed(e.javaClass.simpleName)
        }
    }

    /** GET [path] at [region]'s host with the token and the account's id. */
    fun get(
        path: String,
        region: String?,
        token: String,
        accountId: String,
    ): LluCall {
        val request =
            Request
                .Builder()
                .url("$scheme://${host(region)}$path")
                .headers(headers(token, accountId))
                .get()
                .build()
        return try {
            client.newCall(request).execute().use { r ->
                parseCall(r.code, r.body?.string().orEmpty(), r.header("Retry-After")?.trim()?.toLongOrNull())
            }
        } catch (e: IOException) {
            LluCall(LluCallOutcome.Network)
        }
    }

    private fun headers(
        token: String?,
        accountId: String?,
    ): okhttp3.Headers {
        val b =
            okhttp3.Headers
                .Builder()
                .add("product", PRODUCT)
                .add("version", version)
                .add("Accept", "application/json")
                .add("cache-control", "no-cache")
        if (token != null) b.add("Authorization", "Bearer $token")
        if (accountId != null) b.add("Account-Id", sha256(accountId))
        return b.build()
    }

    companion object {
        const val HOST = "api.libreview.io"
        const val PRODUCT = "llu.android"

        /** The LibreLinkUp version every call names (contracts/sources/librelinkup/poll.json `headers`). */
        const val VERSION = "4.17.0"
        const val LOGIN = "/llu/auth/login"
        const val CONNECTIONS = "/llu/connections"

        fun graph(patientId: String) = "/llu/connections/$patientId/graph"

        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
        private val REGION = Regex("[a-z]{2,3}")

        /** TrendArrow 1..5 (falling quickly .. rising quickly) as mg/dL/min; any other: none. */
        private val ARROW_RATE = mapOf(1 to -2.5, 2 to -1.5, 3 to 0.0, 4 to 1.5, 5 to 2.5)

        /** FactoryTimestamp: UTC, `M/d/yyyy h:mm:ss AM`. */
        private val TIME = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

        fun sha256(text: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        private fun parse(body: String): JsonObject? = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

        /** One login answer ([code], [body], the Retry-After header): login.json's rules. */
        fun parseLogin(
            code: Int,
            body: String,
            retryAfterS: Long? = null,
        ): LluLogin {
            val o = parse(body)
            val data = o?.get("data") as? JsonObject
            val status = o?.int("status")
            if (code == 429 || status == 429) {
                val lockout = (data?.get("data") as? JsonObject)?.long("lockout")
                return LluLogin.RateLimited(retryAfterS ?: lockout)
            }
            if (code == 403) data?.text("minimumVersion")?.let { return LluLogin.Version(it) }
            if (code == 401) return LluLogin.BadCredentials
            if (code == 404) return LluLogin.Retired
            if (code != 200 || o == null) return LluLogin.Failed("HTTP $code")
            if (status == 0 && data != null && (data.bool("redirect") == true || data.text("redirect") == "true")) {
                val region = data.text("region")
                return if (region != null && REGION.matches(region)) LluLogin.Redirect(region) else LluLogin.Failed("bad region")
            }
            if (status == 0 && data != null) {
                val id = (data["user"] as? JsonObject)?.text("id")
                val ticket = data["authTicket"] as? JsonObject
                val token = ticket?.text("token")
                if (id != null && id.length == 36 && !token.isNullOrEmpty()) {
                    return LluLogin.Account(id, token, Instant.ofEpochSecond(ticket.long("expires") ?: 0))
                }
                return LluLogin.Failed("no token")
            }
            return when (status) {
                2 -> LluLogin.BadCredentials
                4 -> LluLogin.Terms((data?.get("step") as? JsonObject)?.text("type"))
                else -> LluLogin.Failed("status $status")
            }
        }

        /** An authenticated call's answer: calls.json's rules. */
        fun parseCall(
            code: Int,
            body: String,
            retryAfterS: Long? = null,
        ): LluCall {
            val o = parse(body)
            val data = o?.get("data") as? JsonObject
            val status = o?.int("status")
            return when {
                code == 429 || status == 429 -> LluCall(LluCallOutcome.RateLimited, retryAfterS = retryAfterS)

                code == 401 -> LluCall(LluCallOutcome.Expired)

                code == 403 && data?.text(
                    "minimumVersion",
                ) != null -> LluCall(LluCallOutcome.Version, minimumVersion = data.text("minimumVersion"))

                code == 403 || code == 404 || code == 410 -> LluCall(LluCallOutcome.Retired)

                code != 200 || o == null -> LluCall(LluCallOutcome.Server)

                status == 4 -> LluCall(LluCallOutcome.Terms)

                status != 0 || "data" !in o -> LluCall(LluCallOutcome.Server)

                else -> LluCall(LluCallOutcome.Ok, o)
            }
        }

        private fun nameOf(c: JsonObject): String =
            listOfNotNull(c.text("firstName"), c.text("lastName"))
                .filter {
                    it.isNotBlank()
                }.joinToString(" ")
                .trim()

        /** The people a connections answer ([body] = its whole object) lists, in its order. */
        fun people(body: JsonObject): List<LluPerson> =
            ((body["data"] as? JsonArray) ?: JsonArray(emptyList()))
                .mapNotNull { it as? JsonObject }
                .mapNotNull { c -> c.text("patientId")?.takeIf { it.isNotEmpty() }?.let { LluPerson(it, nameOf(c)) } }

        /**
         * Whose glucose to read among [choices] (connections.json): [saved] while still shared (else gone); for your own
         * glucose ([yours]) the account itself, else the only one; for someone else's the only one besides the account.
         */
        fun pick(
            choices: List<LluPerson>,
            accountId: String,
            yours: Boolean,
            saved: String?,
        ): LluPick {
            val ids = choices.map { it.id }
            if (ids.isEmpty()) return LluPick(choices, LluPick.State.None, null)
            if (!saved.isNullOrEmpty()) {
                return if (saved in ids) LluPick(choices, LluPick.State.Picked, saved) else LluPick(choices, LluPick.State.Gone, null)
            }
            val candidates =
                if (yours) {
                    if (accountId in ids) return LluPick(choices, LluPick.State.Picked, accountId)
                    ids
                } else {
                    ids.filter { it != accountId }.ifEmpty { ids }
                }
            return if (candidates.size == 1) {
                LluPick(choices, LluPick.State.Picked, candidates.single())
            } else {
                LluPick(choices, LluPick.State.Choose, null)
            }
        }

        /** A FactoryTimestamp as an instant (UTC), null when it isn't one. */
        fun time(text: String?): Instant? =
            text?.trim()?.let { t ->
                runCatching { LocalDateTime.parse(t.uppercase(Locale.US), TIME).toInstant(ZoneOffset.UTC) }.getOrNull()
            }

        private fun reading(
            m: JsonObject,
            source: SourceId,
            live: Boolean,
        ): Reading? {
            val t = time(m.text("FactoryTimestamp")) ?: return null
            val v = m.double("ValueInMgPerDl")?.takeIf { it > 0 && it.isFinite() } ?: return null
            val rate = if (live) m.int("TrendArrow")?.let { ARROW_RATE[it] } else null
            return Reading(t, floor(v + 0.5).toInt(), rate, source)
        }

        /** One graph answer ([body] = its whole object): graph.json's rules. */
        fun graph(body: JsonObject): LluGraph {
            val data = body["data"] as? JsonObject ?: JsonObject(emptyMap())
            val conn = data["connection"] as? JsonObject ?: JsonObject(emptyMap())
            val out = ArrayList<Reading>()
            (data["graphData"] as? JsonArray)?.forEach { e ->
                (e as? JsonObject)?.let { reading(it, SourceId.LibreLinkUpGraph, live = false) }?.let(out::add)
            }
            (conn["glucoseMeasurement"] as? JsonObject)?.let { reading(it, SourceId.LibreLinkUp, live = true) }?.let(out::add)
            out.sortWith(compareBy({ it.t }, { it.source.id }))
            val first = ((data["activeSensors"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("sensor") as? JsonObject
            val sensor =
                listOfNotNull(first, conn["sensor"] as? JsonObject).firstNotNullOfOrNull { s ->
                    val sn = s.text("sn")?.takeIf { it.isNotEmpty() }
                    val a = s.double("a")?.takeIf { it > 0 }
                    if (sn != null && a != null) LluSensor(sn, Instant.ofEpochSecond(a.toLong())) else null
                }
            return LluGraph(out, sensor, nameOf(conn))
        }

        private fun JsonObject.prim(k: String): JsonPrimitive? = (this[k] as? JsonPrimitive)?.takeIf { it != JsonNull }

        private fun JsonObject.text(k: String): String? = prim(k)?.contentOrNull

        private fun JsonObject.int(k: String): Int? = prim(k)?.intOrNull

        private fun JsonObject.long(k: String): Long? = prim(k)?.longOrNull ?: prim(k)?.doubleOrNull?.toLong()

        private fun JsonObject.double(k: String): Double? = prim(k)?.doubleOrNull

        private fun JsonObject.bool(k: String): Boolean? = prim(k)?.booleanOrNull
    }
}
