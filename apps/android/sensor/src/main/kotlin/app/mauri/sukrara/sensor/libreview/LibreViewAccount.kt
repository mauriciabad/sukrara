// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.sensor.libreview

import app.mauri.sukrara.core.data.libreview.LibreViewAccountState
import app.mauri.sukrara.core.data.libreview.LibreViewLogin
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.sensor.pairing.ReceiverIdState
import app.mauri.sukrara.sensor.pairing.ReceiverIds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What one LibreLinkUp login answer means. */
sealed interface LoginAnswer {
    data class Account(val accountId: String) : LoginAnswer

    /** Sign in again at the account's regional host (`api-<region>.libreview.io`). */
    data class Redirect(val region: String) : LoginAnswer

    data object BadCredentials : LoginAnswer

    /** The account must accept new terms in the LibreLinkUp / LibreView app first. */
    data object AcceptTerms : LoginAnswer

    data class Failed(val why: String) : LoginAnswer
}

/** The result of [LibreViewAccount.signIn]. */
sealed interface SignInResult {
    data class SignedIn(val receiverId: Long) : SignInResult

    data object BadCredentials : SignInResult

    data object AcceptTerms : SignInResult

    /** Network or server trouble; [why] is for diagnostics. Try again later. */
    data class Failed(val why: String) : SignInResult
}

/**
 * The sign-in to THE LibreView account ([LibreViewLogin], one per app) and its receiver ID: what lets Sukrara start
 * sensors that Juggluco or Abbott's apps on the same account can take over, and take over sensors they started.
 *
 * Signs in through LibreLinkUp's login: it answers with the same account ID as LibreView's app login without making
 * this phone the account's upload device (which would cut off another app's LibreView uploads). A sign-in LibreView
 * accepts keeps the email and password in [login] (the Keystore), so the LibreView upload and Import history use the
 * same account without asking again, and the receiver ID here (it never changes for an account). Signing out forgets
 * both: everything that uses the account stops.
 */
class LibreViewAccount internal constructor(
    private val state: StateStore<ReceiverIdState>,
    private val login: LibreViewLogin,
    private val http: OkHttpClient,
    private val io: CoroutineDispatcher,
    private val baseHost: String = HOST,
    private val scheme: String = "https",
) {
    constructor(state: StateStore<ReceiverIdState>, login: LibreViewLogin, http: OkHttpClient, io: CoroutineDispatcher) :
        this(state, login, http, io, HOST, "https")

    companion object {
        const val HOST = "api.libreview.io"
        private val JSON = "application/json".toMediaType()

        /** Pure: one login answer (status 0 + 36-char user id, redirect, 2 = credentials, 4 = terms). */
        fun parse(body: String): LoginAnswer {
            val o =
                runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
                    ?: return LoginAnswer.Failed("answer is not JSON")
            val status = o["status"]?.jsonPrimitive?.intOrNull
            val data = o["data"] as? JsonObject
            val region = data?.get("region")?.jsonPrimitive?.contentOrNull
            if (status == 0 && data?.get("redirect")?.jsonPrimitive?.contentOrNull == "true" && region != null) {
                return if (region.matches(Regex("[a-z]{2,3}"))) LoginAnswer.Redirect(region) else LoginAnswer.Failed("bad region")
            }
            val id = (data?.get("user") as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
            return when {
                status == 0 && id != null && id.length == 36 -> LoginAnswer.Account(id)
                status == 2 -> LoginAnswer.BadCredentials
                status == 4 -> LoginAnswer.AcceptTerms
                else -> LoginAnswer.Failed("status $status")
            }
        }
    }

    /** The app's LibreView account. */
    val account: Flow<LibreViewAccountState> = login.account

    /**
     * The account's receiver ID (shown in Settings › Sensor to compare with other apps), or null: none without an
     * account (signed out, or never signed in on this install).
     */
    val receiverId: Flow<Long?> = combine(login.account, state.data) { a, s -> s.account.takeIf { a != LibreViewAccountState.SignedOut } }

    /** Whether sensors can be started and taken over with the account's receiver ID now. */
    suspend fun hasReceiverId(): Boolean = receiverId.first() != null

    suspend fun signIn(email: String, password: String): SignInResult =
        withContext(io) {
            val body =
                buildJsonObject {
                    put("email", email.trim())
                    put("password", password)
                }.toString()
            var host = baseHost
            repeat(2) {
                val answer =
                    try {
                        post("$scheme://$host/llu/auth/login", body)
                    } catch (e: IOException) {
                        return@withContext SignInResult.Failed("${e.javaClass.simpleName}")
                    }
                when (answer) {
                    is LoginAnswer.Redirect -> {
                        host = "api-${answer.region}.libreview.io"
                    }

                    is LoginAnswer.Account -> {
                        val rid = ReceiverIds.fromAccountId(answer.accountId)
                        state.update { it.copy(account = rid) }
                        login.save(email, password)
                        return@withContext SignInResult.SignedIn(rid)
                    }

                    LoginAnswer.BadCredentials -> {
                        return@withContext SignInResult.BadCredentials
                    }

                    LoginAnswer.AcceptTerms -> {
                        return@withContext SignInResult.AcceptTerms
                    }

                    is LoginAnswer.Failed -> {
                        return@withContext SignInResult.Failed(answer.why)
                    }
                }
            }
            SignInResult.Failed("LibreView kept redirecting")
        }

    /**
     * Signs out everywhere: forgets the account (email, password; the LibreView upload drops its token) and its receiver
     * ID. Sukrara's own ID and a running sensor's pairing stay.
     */
    suspend fun signOut() {
        login.signOut()
        state.update { it.copy(account = null) }
    }

    private fun post(url: String, body: String): LoginAnswer {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("product", "llu.android")
                .header("version", "4.16.0")
                .post(body.toRequestBody(JSON))
                .build()
        http
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute()
            .use { resp ->
                if (resp.code == 401) return LoginAnswer.BadCredentials
                if (resp.code != 200) return LoginAnswer.Failed("HTTP ${resp.code}")
                return parse(resp.body?.string().orEmpty())
            }
    }
}
