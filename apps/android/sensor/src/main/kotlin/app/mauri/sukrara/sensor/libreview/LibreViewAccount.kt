// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.sensor.libreview

import app.mauri.sukrara.core.data.libreview.LibreLinkUp
import app.mauri.sukrara.core.data.libreview.LibreViewAccountState
import app.mauri.sukrara.core.data.libreview.LibreViewLogin
import app.mauri.sukrara.core.data.libreview.LluLogin
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.sensor.pairing.ReceiverIdState
import app.mauri.sukrara.sensor.pairing.ReceiverIds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

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
        const val HOST = LibreLinkUp.HOST

        /** Pure: one login answer (contracts/sensor/libreview-auth.json), read by THE LibreLinkUp client's rules. */
        fun parse(body: String): LoginAnswer = answerOf(LibreLinkUp.parseLogin(200, body))

        private fun answerOf(l: LluLogin): LoginAnswer =
            when (l) {
                is LluLogin.Account -> LoginAnswer.Account(l.accountId)
                is LluLogin.Redirect -> LoginAnswer.Redirect(l.region)
                LluLogin.BadCredentials -> LoginAnswer.BadCredentials
                is LluLogin.Terms -> LoginAnswer.AcceptTerms
                is LluLogin.RateLimited -> LoginAnswer.Failed("too many attempts: try again later")
                is LluLogin.Version -> LoginAnswer.Failed("version ${l.minimumVersion} needed")
                LluLogin.Retired -> LoginAnswer.Failed("the login is gone")
                is LluLogin.Failed -> LoginAnswer.Failed(l.why)
            }
    }

    /** THE LibreLinkUp client: the same login, headers and version as the LibreLinkUp source. */
    private val llu = LibreLinkUp(http, scheme, baseHost) { if (baseHost == HOST) "api-$it.libreview.io" else baseHost }

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
            var region: String? = null
            var raised = false
            repeat(3) {
                val l = llu.login(email, password, region)
                // a 403 naming a newer version: that version once (LibreLinkUp raises its minimum now and then)
                if (l is LluLogin.Version && !raised) {
                    raised = true
                    llu.version = l.minimumVersion
                    return@repeat
                }
                when (val answer = answerOf(l)) {
                    is LoginAnswer.Redirect -> {
                        region = answer.region
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
}
