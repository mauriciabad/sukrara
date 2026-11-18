// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.librelinkup

import app.mauri.sukrara.core.data.libreview.LibreLinkUp
import app.mauri.sukrara.core.data.libreview.LluCall
import app.mauri.sukrara.core.data.libreview.LluCallOutcome
import app.mauri.sukrara.core.data.libreview.LluLogin
import app.mauri.sukrara.core.data.libreview.LluPerson
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.storage.SecretUnreadableException
import app.mauri.sukrara.core.model.storage.SecureStore
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the source's account answers, as the source and the Connect step act on it. */
sealed interface LluSession {
    data class Ready(
        val accountId: String,
        val token: String,
        val region: String?,
    ) : LluSession {
        override fun toString() = "Ready(***)"
    }

    /** No account saved, or the password was refused: sign in (again). */
    data object SignedOut : LluSession

    data class Terms(val step: String?) : LluSession

    data class RateLimited(val retryAfterS: Long?) : LluSession

    data object Retired : LluSession

    /** LibreView is down for maintenance in the account's region: try again later (the account stays as it is). */
    data object Maintenance : LluSession

    /** No answer, or one Sukrara can't read: try again later. */
    data object Failed : LluSession
}

/**
 * The LibreLinkUp source's account (schema `source.libreLinkUpSecret`, the secure store, apart from THE LibreView
 * account: someone else's glucose comes with another account): the email and password, and what the login gave (the
 * account's id, its region, the token and when it expires). The token is reused until [LibreLinkUp]'s renewal margin
 * before it expires, so a restart doesn't sign in again (each login counts towards LibreLinkUp's lockout); a refused
 * password is forgotten (the email stays for the form) and never retried by itself.
 */
class LluAccount(
    private val secure: SecureStore,
    val api: LibreLinkUp,
    private val clock: Clock,
) {
    /** Whether an account is saved: none, readable (its email), or saved but unreadable (sign in again). */
    sealed interface Saved {
        data object None : Saved

        data class Readable(val email: String, val signedOut: Boolean) : Saved

        data object Unreadable : Saved
    }

    private val savedFlow = MutableStateFlow<Saved>(Saved.None)
    val saved: StateFlow<Saved> = savedFlow.asStateFlow()
    private val mutex = Mutex()

    @Serializable
    private data class Stored(
        val email: String,
        /** null: LibreLinkUp refused it; sign in again. */
        val password: String? = null,
        val accountId: String = "",
        val region: String? = null,
        val token: String = "",
        val expiresS: Long = 0,
        /** The version LibreLinkUp asked for, once raised. */
        val version: String? = null,
    ) {
        override fun toString() = "Stored(***)"
    }

    /** Reads which account is saved (once at the app's load step). */
    suspend fun load() {
        savedFlow.value =
            try {
                read()?.let { Saved.Readable(it.email, it.password == null) } ?: Saved.None
            } catch (e: SecretUnreadableException) {
                Saved.Unreadable
            }
    }

    private suspend fun read(): Stored? {
        val bytes = secure.read(NAME) ?: return null
        return try {
            json.decodeFromString(Stored.serializer(), bytes.decodeToString()).also { s ->
                // a version LibreLinkUp asked for, unless this build already sends a newer one
                s.version?.takeIf { LibreLinkUp.newer(it, LibreLinkUp.VERSION) }?.let { api.version = it }
            }
        } catch (e: IllegalArgumentException) {
            throw SecretUnreadableException(NAME, transient = false, cause = e)
        }
    }

    private suspend fun write(s: Stored) {
        secure.write(NAME, json.encodeToString(Stored.serializer(), s).encodeToByteArray())
        savedFlow.value = Saved.Readable(s.email, s.password == null)
    }

    suspend fun forget() =
        mutex.withLock {
            secure.delete(NAME)
            savedFlow.value = Saved.None
        }

    /** The saved account's email (the Connect step's form), null without one. */
    suspend fun email(): String? = runCatching { read()?.email }.getOrNull()

    /** The saved account's region (the login's redirect: `eu`, `eu2`, `us`…), null without one or before a redirect. */
    suspend fun region(): String? = runCatching { read()?.region }.getOrNull()

    /** A token that works now: the saved one, or a new login with the saved password. */
    suspend fun session(): LluSession =
        mutex.withLock {
            val s =
                try {
                    read()
                } catch (e: SecretUnreadableException) {
                    return@withLock if (e.transient) LluSession.Failed else LluSession.SignedOut
                } ?: return@withLock LluSession.SignedOut
            val now = clock.now().epochSecond
            if (s.token.isNotEmpty() && s.accountId.isNotEmpty() && s.expiresS - RENEW_BEFORE_S > now) {
                return@withLock LluSession.Ready(s.accountId, s.token, s.region)
            }
            val password = s.password ?: return@withLock LluSession.SignedOut
            signIn(s.email, password, s.region).first
        }

    /** The token stopped working before its time ([LluCallOutcome.Expired]): the next [session] signs in again. */
    suspend fun expire() =
        mutex.withLock {
            val s = runCatching { read() }.getOrNull() ?: return@withLock
            write(s.copy(token = "", expiresS = 0))
        }

    /**
     * Signs in with [email] and [password] (the Connect step): the region redirect followed, a version LibreLinkUp asks
     * for sent once. Saved only when LibreLinkUp accepts them; a refusal of a saved account forgets the password.
     */
    suspend fun connect(
        email: String,
        password: String,
    ): LluSession =
        mutex.withLock {
            val keepRegion = runCatching { read() }.getOrNull()?.takeIf { it.email.equals(email.trim(), ignoreCase = true) }?.region
            signIn(email.trim(), password, keepRegion).first
        }

    /** One sign-in (call holding [mutex]); the session and whether the account was saved. */
    private suspend fun signIn(
        email: String,
        password: String,
        startRegion: String?,
    ): Pair<LluSession, Boolean> {
        var region = startRegion
        var raised = false
        repeat(3) {
            when (val l = api.login(email, password, region)) {
                is LluLogin.Account -> {
                    // the version in use stays with the account (raised now or by an earlier call)
                    val version = api.version.takeIf { it != LibreLinkUp.VERSION }
                    write(Stored(email, password, l.accountId, region, l.token, l.expires.epochSecond, version))
                    return LluSession.Ready(l.accountId, l.token, region) to true
                }

                is LluLogin.Redirect -> {
                    region = l.region
                }

                is LluLogin.Version -> {
                    if (raised) return LluSession.Retired to false
                    raised = true
                    api.version = l.minimumVersion
                }

                LluLogin.BadCredentials -> {
                    // never retried by itself: retries lock the account
                    val saved = runCatching { read() }.getOrNull()
                    if (saved != null && saved.email == email) write(saved.copy(password = null, token = "", expiresS = 0))
                    return LluSession.SignedOut to false
                }

                is LluLogin.Terms -> {
                    return LluSession.Terms(l.step) to false
                }

                is LluLogin.RateLimited -> {
                    return LluSession.RateLimited(l.retryAfterS) to false
                }

                LluLogin.Retired -> {
                    return LluSession.Retired to false
                }

                LluLogin.Maintenance -> {
                    return LluSession.Maintenance to false
                }

                is LluLogin.Failed -> {
                    return LluSession.Failed to false
                }
            }
        }
        return LluSession.Failed to false
    }

    /** The people the account follows (GET /llu/connections), or the call's outcome when it failed. */
    suspend fun people(session: LluSession.Ready): Pair<List<LluPerson>?, LluCall> {
        val r = call(session) { api.get(LibreLinkUp.CONNECTIONS, it.region, it.token, it.accountId) }
        return (if (r.outcome == LluCallOutcome.Ok) r.body?.let(LibreLinkUp::people) else null) to r
    }

    /**
     * One authenticated call ([get], with [first]'s token, calls.json): a version LibreLinkUp asks for is sent from then
     * on and the call made again, once per version (as the login does: a 403 naming a minimum); a token that stopped
     * working before its time signs in again once with the saved password and the call is made again. A sign-in that
     * fails then answers as the call's outcome ([LluCallOutcome.Expired] for a refused password).
     */
    suspend fun call(
        first: LluSession.Ready,
        get: (LluSession.Ready) -> LluCall,
    ): LluCall {
        var session = first
        var r = get(session)
        if (r.outcome == LluCallOutcome.Version && raiseVersion(r.minimumVersion)) r = get(session)
        if (r.outcome == LluCallOutcome.Expired) {
            expire()
            session =
                when (val s = session()) {
                    is LluSession.Ready -> s
                    LluSession.SignedOut -> return LluCall(LluCallOutcome.Expired)
                    is LluSession.Terms -> return LluCall(LluCallOutcome.Terms)
                    is LluSession.RateLimited -> return LluCall(LluCallOutcome.RateLimited, retryAfterS = s.retryAfterS)
                    LluSession.Retired -> return LluCall(LluCallOutcome.Retired)
                    LluSession.Maintenance -> return LluCall(LluCallOutcome.Maintenance)
                    LluSession.Failed -> return LluCall(LluCallOutcome.Network)
                }
            r = get(session)
            if (r.outcome == LluCallOutcome.Version && raiseVersion(r.minimumVersion)) r = get(session)
        }
        return r
    }

    /**
     * A call refused this client's version: [minimum] is sent from now on and kept with the account (a restart sends it
     * too). False when it is sent already (LibreLinkUp refuses it as well: Abbott changed more than the version).
     */
    private suspend fun raiseVersion(minimum: String?): Boolean =
        mutex.withLock {
            if (minimum == null || minimum == api.version) return@withLock false
            api.version = minimum
            runCatching { read() }.getOrNull()?.let { write(it.copy(version = minimum)) }
            true
        }

    companion object {
        /** The secure store's name (schema `source.libreLinkUpSecret`). */
        val NAME = SettingsSpec.Keys.LIBRE_LINK_UP_SOURCE

        /** contracts/sources/librelinkup/poll.json `renewBefore_s`. */
        const val RENEW_BEFORE_S = 300L
        private val json = Json { ignoreUnknownKeys = true }
    }
}
