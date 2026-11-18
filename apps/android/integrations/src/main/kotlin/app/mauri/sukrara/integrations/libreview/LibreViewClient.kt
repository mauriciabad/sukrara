// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.libreview

import app.mauri.sukrara.designsystem.errors.Trouble
import app.mauri.sukrara.designsystem.errors.UserErrors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * What a LibreView app sign-in leaves behind (SecureStore `libreview`, never anywhere else): the token, where to send
 * it and the person's fields the sensor registration repeats. Never the password.
 */
@Serializable
data class LvSession(
    val token: String,
    val baseUrl: String,
    val apiKey: String,
    val dateOfBirth: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val guardianFirstName: String? = null,
    val guardianLastName: String? = null,
    /** The account's id (a GUID; the sensor's receiver ID comes from it), null when the answer had none. */
    val accountId: String? = null,
    /** The app identity the token is of (each identity has its own session, docs/45). */
    val identity: LvIdentity = LvIdentity.Libre3,
) {
    /** The sensor registration repeats the profile: only send it when sign-in returned it (never blank it). */
    val hasProfile: Boolean get() = dateOfBirth != null && firstName != null && lastName != null

    override fun toString() = "LvSession(***)"
}

/** One sign-in's answer. */
sealed interface LvAuth {
    data class SignedIn(val session: LvSession) : LvAuth

    /** Another device (LibreLink, Juggluco, another phone) uploads for this account: only a take-over gets past it. */
    data object OtherDevice : LvAuth

    data object BadCredentials : LvAuth

    /** Not sent: the identity is (or may be) the official app's, and a sign-in there stops its upload ([LvIdentity.mayUse]). */
    data object Guarded : LvAuth

    /** Network or server trouble, in plain words. */
    data class Failed(val why: Trouble) : LvAuth
}

/** One upload's answer. */
sealed interface LvPost {
    data object Ok : LvPost

    /** The token no longer works (another device took over, or it expired): sign in again. */
    data object SignInAgain : LvPost

    /**
     * `status 20` with an "invalid …" reason: LibreView doesn't take the token (yet: the first registration right after
     * a sign-in answers this, docs/04; or any more). Tried again later; again and again, with a new sign-in.
     */
    data object InvalidSession : LvPost

    /** Try again later (network, 5xx, 408, 429), [why] in plain words. */
    data class Retry(val why: Trouble) : LvPost

    /** LibreView refused it ([why] = its status and reason, for the log). */
    data class Rejected(val why: String) : LvPost
}

/**
 * LibreView's app API, as the FreeStyle Libre 3 app and LibreLink use it (contracts/treatments/SPEC.md §3, each
 * [LvIdentity] with its own config, headers and body). Blocking: call on IO. Every URL is https (the config's too);
 * redirects are never followed, so the token can't be carried to another host. It never decides on which identity it
 * may sign in: the uploader does ([LvIdentity.mayUse]).
 */
class LibreViewClient internal constructor(
    http: OkHttpClient,
    private val assetsUrl: String,
    /** LibreLink's config (newYuUrl): the GB one, which took an EU account's upload (docs/45). */
    private val linkConfigUrl: String,
    /** Tests and a debug build's fake LibreView only: a server on this phone's loopback speaks http. */
    private val allowLoopbackHttp: Boolean,
) {
    constructor(http: OkHttpClient) : this(http, ASSETS, LINK_CONFIG, false)

    companion object {
        const val ASSETS = "https://fsll3.freestyleserver.com/Payloads/Mobile/FSLibre3/Android/Assets/3.3.0/DE.json"
        const val LINK_CONFIG =
            "https://fsll.freestyleserver.com/Payloads/Mobile/FSLibreLink/Android/Config/FSLibreLink_Android_2.10_GB_config.json"
        private val JSON = "application/json".toMediaType()
        private val JSON_UTF8 = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * A debug build's fake LibreView (`tools/librelinkup-probe/fake.py`, the `LIBRELINKUP` command): [scheme] and
         * [host] (`127.0.0.1:8766`) on this phone's loopback; the fake serves both configs.
         */
        fun fake(
            http: OkHttpClient,
            scheme: String,
            host: String,
        ) = LibreViewClient(http, "$scheme://$host/lv/assets", "$scheme://$host/lv/librelink-config", allowLoopbackHttp = true)
    }

    private val client =
        http
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /** The URL if it's one we may send to (https, no user info), else null. */
    internal fun safe(url: String): HttpUrl? {
        val u = url.trim().toHttpUrlOrNull() ?: return null
        if (u.username.isNotEmpty() || u.password.isNotEmpty()) return null
        val loopback = allowLoopbackHttp && (u.host == "127.0.0.1" || u.host == "localhost")
        return u.takeIf { it.scheme == "https" || loopback }
    }

    /**
     * Signs in with the LibreView app login of [identity]. [takeOver] true makes this phone that identity's uploading
     * device (the other device stops uploading): only after the person chose it. The password goes into this one request
     * only. Any sign-in that gets a token ends [identity]'s previous session, whichever device had it (docs/45 test D).
     */
    fun signIn(
        email: String,
        password: String,
        deviceId: String,
        takeOver: Boolean,
        identity: LvIdentity = LvIdentity.Libre3,
    ): LvAuth {
        val (base, apiKey) =
            try {
                config(identity)
            } catch (e: IOException) {
                return LvAuth.Failed(UserErrors.of(e))
            }
        val body =
            buildJsonObject {
                put("Culture", LibreViewPayload.CULTURE)
                put("DeviceId", deviceId)
                put("Password", password)
                put("SetDevice", takeOver)
                put("UserName", email.trim())
                put("Domain", "Libreview")
                put("GatewayType", identity.gateway)
            }.toString()
        val (code, o) =
            try {
                call("POST", base, "/api/nisperson/getauthentication", body, apiKey, token = "", identity, signIn = true)
            } catch (e: IOException) {
                return LvAuth.Failed(UserErrors.of(e))
            }
        if (code == 401 || code == 403) return LvAuth.BadCredentials
        if (code != 200 || o == null) return LvAuth.Failed(if (code == 200) Trouble.Other else UserErrors.ofHttp(code))
        val status = o["status"]?.jsonPrimitive?.intOrNull
        // down for maintenance (HTTP 911 is ofHttp's): tried again later, never a refusal (docs/45)
        if (status == UserErrors.MAINTENANCE) return LvAuth.Failed(Trouble.Maintenance)
        val reason = o["reason"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (status == 20 && "wrongDeviceForUser" in reason) return LvAuth.OtherDevice
        if (status == 2) return LvAuth.BadCredentials
        val r = (o["result"] as? JsonObject)
        val token = r?.get("UserToken")?.jsonPrimitive?.contentOrNull
        if (status != 0 || token.isNullOrEmpty()) return LvAuth.Failed(Trouble.Other)

        fun field(k: String) = r[k]?.jsonPrimitive?.contentOrNull
        return LvAuth.SignedIn(
            LvSession(
                token,
                base.toString().trimEnd('/'),
                apiKey,
                field("DateOfBirth"),
                field("FirstName"),
                field("LastName"),
                field("GuardianFirstName"),
                field("GuardianLastName"),
                field("AccountId")?.takeIf { it.isNotBlank() },
                identity,
            ),
        )
    }

    /** `POST /api/measurements` with [body] (a [LibreViewPayload.payload]). */
    fun postMeasurements(
        s: LvSession,
        body: String,
    ): LvPost = post("POST", "/api/measurements", s, body)

    /** Tells the account which sensor is active (before that sensor's first glucose; the Libre 3 identity's only). */
    fun putSensor(
        s: LvSession,
        serial: String,
    ): LvPost {
        if (s.identity != LvIdentity.Libre3) return LvPost.Rejected("not the Libre 3 identity")
        if (!s.hasProfile) return LvPost.Rejected("no profile")
        val body =
            buildJsonObject {
                put("DateOfBirth", s.dateOfBirth)
                put("DomainData", "{\"activeSensor\":\"${LibreSerial.active(serial)}\"}")
                put("FirstName", s.firstName)
                put("GuardianFirstName", s.guardianFirstName.orEmpty())
                put("GuardianLastName", s.guardianLastName.orEmpty())
                put("LastName", s.lastName)
                put("UserToken", s.token)
                put("Domain", "Libreview")
                put("GatewayType", LibreViewPayload.GATEWAY)
            }.toString()
        return post("PUT", "/api/nisperson", s, body)
    }

    private fun post(
        method: String,
        path: String,
        s: LvSession,
        body: String,
    ): LvPost {
        val base = safe(s.baseUrl) ?: return LvPost.SignInAgain
        val (code, o) =
            try {
                call(method, base, path, body, s.apiKey, s.token, s.identity, signIn = false)
            } catch (e: IOException) {
                return LvPost.Retry(UserErrors.of(e))
            }
        return when {
            code == 401 || code == 403 -> {
                LvPost.SignInAgain
            }

            // 911: LibreView is down for maintenance (docs/45), waited out like a busy server, never "stopped"
            code == 408 || code == 425 || code == 429 || code >= 500 -> {
                LvPost.Retry(UserErrors.ofHttp(code))
            }

            code != 200 || o == null -> {
                LvPost.Rejected("HTTP $code")
            }

            else -> {
                val status = o["status"]?.jsonPrimitive?.intOrNull ?: -1
                val reason = o["reason"]?.jsonPrimitive?.contentOrNull.orEmpty()
                when {
                    status == 0 -> LvPost.Ok
                    status == UserErrors.MAINTENANCE -> LvPost.Retry(Trouble.Maintenance)
                    status == 20 && "wrongDevice" in reason -> LvPost.SignInAgain
                    status == 20 && "invalid" in reason.lowercase() -> LvPost.InvalidSession
                    else -> LvPost.Rejected("$status ${reason.take(80)}".trim())
                }
            }
        }
    }

    /**
     * Abbott's app config of [identity]: the API's address and key (https only). The Libre 3 app's comes through its
     * assets file; LibreLink's is one file and has no key (its calls send none).
     */
    private fun config(identity: LvIdentity): Pair<HttpUrl, String> {
        val cfgUrl =
            when (identity) {
                LvIdentity.Libre3 -> {
                    val assets = safe(assetsUrl) ?: throw IOException("config address")
                    jsonOf(get(assets))["Configuration"]?.jsonPrimitive?.contentOrNull?.let(::safe)
                        ?: throw IOException("config address")
                }

                LvIdentity.LibreLink -> {
                    safe(linkConfigUrl) ?: throw IOException("config address")
                }
            }
        val cfg = jsonOf(get(cfgUrl))
        val base = cfg["newYuUrl"]?.jsonPrimitive?.contentOrNull?.let(::safe) ?: throw IOException("config address")
        val key =
            when (identity) {
                LvIdentity.Libre3 -> cfg["newYuApiKey"]?.jsonPrimitive?.contentOrNull ?: throw IOException("config key")
                LvIdentity.LibreLink -> ""
            }
        return base to key
    }

    private fun jsonOf(text: String): JsonObject =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: throw IOException("config is not JSON")

    private fun get(url: HttpUrl): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.code != 200) throw IOException("config HTTP ${r.code}")
            r.body?.string().orEmpty()
        }

    /**
     * One call with [identity]'s headers: the Libre 3 app's on every call (the token in `x-newyu-token` too);
     * LibreLink's (Juggluco `Libreview.java`, docs/45) only on the sign-in, its uploads carry the token in the body only.
     */
    private fun call(
        method: String,
        base: HttpUrl,
        path: String,
        body: String,
        apiKey: String,
        token: String,
        identity: LvIdentity,
        signIn: Boolean,
    ): Pair<Int, JsonObject?> {
        val url = safe(base.toString().trimEnd('/') + path) ?: throw IOException("address")
        val b = Request.Builder().url(url)
        when (identity) {
            LvIdentity.Libre3 -> {
                b
                    .header("Platform", "Android")
                    .header("Version", "3.3.0")
                    .header("Abbott-ADC-App-Platform", "Android/16/FSL3/3.3.0.9092")
                    .header("Accept-Language", LibreViewPayload.CULTURE)
                    .header("x-api-key", apiKey)
                    .header("x-newyu-token", token)
                    .method(method, body.toRequestBody(JSON))
            }

            LvIdentity.LibreLink -> {
                if (signIn) {
                    b
                        .header("Abbott-ADC-App-Platform", "Android/16/FSLL/2.10.1.10406")
                        .header("Accept-Language", "${LibreViewPayload.CULTURE}, en;q=0.8")
                        .method(method, body.toRequestBody(JSON_UTF8))
                } else {
                    b.method(method, body.toRequestBody(JSON))
                }
            }
        }
        val request = b.build()
        return client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            r.code to runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        }
    }
}
