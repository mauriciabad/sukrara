// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import app.mauri.sukrara.core.model.CallOutcome
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.storage.SecretUnreadableException
import app.mauri.sukrara.core.model.storage.SecureStore
import app.mauri.sukrara.integrations.nightscout.NsApi
import app.mauri.sukrara.integrations.nightscout.NsAuth
import app.mauri.sukrara.integrations.nightscout.NsClient
import app.mauri.sukrara.integrations.nightscout.NsConfig
import app.mauri.sukrara.integrations.nightscout.NsSiteProblem
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * The Nightscout source's token or API secret (schema `source.nightscoutSecret`, the secure store, apart from the
 * upload's), and *Connect*: one read of the site that proves the address and the credential before anything is saved.
 * The person pastes either; Sukrara tells them apart by trying (a token looks like `name-0123456789abcdef`).
 */
class NightscoutAccount(
    private val secure: SecureStore,
    private val http: OkHttpClient,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val online: () -> Boolean = { true },
) {
    /** Whether a credential is saved: none, readable, or saved but damaged (enter it again). */
    enum class Saved { None, Readable, Unreadable }

    private val savedFlow = MutableStateFlow(Saved.None)
    val saved: StateFlow<Saved> = savedFlow.asStateFlow()

    /** Reads which credential is saved (once at the app's load step). */
    suspend fun load() {
        savedFlow.value =
            try {
                if (read() != null) Saved.Readable else Saved.None
            } catch (e: SecretUnreadableException) {
                Saved.Unreadable
            }
    }

    /** The saved credential, null without one; throws [SecretUnreadableException] for a damaged one. */
    suspend fun auth(): NsAuth? = read()

    private suspend fun read(): NsAuth? {
        val bytes = secure.read(NAME) ?: return null
        val c =
            try {
                json.decodeFromString(Stored.serializer(), bytes.decodeToString())
            } catch (e: IllegalArgumentException) {
                throw SecretUnreadableException(NAME, transient = false, cause = e)
            }
        return if (c.token) NsAuth.AccessToken(c.value) else NsAuth.ApiSecret(c.value)
    }

    suspend fun forget() {
        secure.delete(NAME)
        savedFlow.value = Saved.None
    }

    /**
     * *Connect*: [address] (a pasted `?token=` fills [credential] when it is empty) and [credential] (token or API
     * secret) are tried with one read of the newest entry; saved only when the site answers. The caller saves the site.
     */
    suspend fun connect(
        address: String,
        credential: String,
        /** The saved site: an empty [credential] keeps the saved one for it (never sent to another address). */
        savedRoot: String? = null,
    ): Pair<NightscoutSite?, NsConnectResult> {
        val site = NightscoutSite.parse(address)
        when (site.problem) {
            NsSiteProblem.Url -> return null to NsConnectResult.BadAddress
            NsSiteProblem.Https -> return null to NsConnectResult.NeedsHttps
            null -> Unit
        }
        val typed = credential.trim().ifEmpty { site.token.orEmpty() }
        val kept = if (typed.isEmpty() && savedRoot == site.root) runCatching { read() }.getOrNull() else null
        if (typed.isEmpty() && kept == null) return null to NsConnectResult.Refused
        val tries =
            when {
                kept != null -> listOf(kept)
                TOKEN.matches(typed) -> listOf(NsAuth.AccessToken(typed), NsAuth.ApiSecret(typed))
                else -> listOf(NsAuth.ApiSecret(typed), NsAuth.AccessToken(typed))
            }
        var last: NsConnectResult = NsConnectResult.Refused
        for (auth in tries) {
            val (code, newest) = withContext(io) { probe(site, auth) }
            when (NightscoutSource.outcomeOf(code)) {
                CallOutcome.Ok -> {
                    save(auth)
                    return site to NsConnectResult.Connected(newest)
                }

                CallOutcome.Refused -> {
                    last = NsConnectResult.Refused
                }

                CallOutcome.NotFound -> {
                    return null to NsConnectResult.NotNightscout
                }

                CallOutcome.Network -> {
                    return null to if (online()) NsConnectResult.Unreachable else NsConnectResult.Offline
                }

                // LibreLinkUp's own outcomes: a site never answers with them
                CallOutcome.Server, CallOutcome.RateLimited, CallOutcome.Terms, CallOutcome.NotShared, CallOutcome.Retired -> {
                    return null to NsConnectResult.Unreachable
                }
            }
        }
        return null to last
    }

    /** The answer's code and, on success, the newest entry's time. */
    private fun probe(
        site: NightscoutSite,
        auth: NsAuth,
    ): Pair<Int, Instant?> {
        val r = NsClient(NsConfig(site.site, auth, NsApi.V1, "Sukrara"), http) { clock.now().toEpochMilli() }.read(PROBE)
        if (r.code !in 200..299) return r.code to null
        // an answer that isn't an entries array: not a Nightscout (a login page, another app at that address)
        val newest =
            runCatching { NsEntries.parse(r.body, null).lastOrNull()?.t }.getOrElse {
                return NOT_ENTRIES to null
            }
        return r.code to newest
    }

    private suspend fun save(auth: NsAuth) {
        val stored = Stored(token = auth is NsAuth.AccessToken, value = auth.value)
        secure.write(NAME, json.encodeToString(Stored.serializer(), stored).encodeToByteArray())
        savedFlow.value = Saved.Readable
    }

    @Serializable
    private data class Stored(
        val token: Boolean,
        val value: String,
    ) {
        override fun toString() = "Stored($token, ***)"
    }

    companion object {
        /** The secure store's name (schema `source.nightscoutSecret`). */
        val NAME = SettingsSpec.Keys.NIGHTSCOUT_SOURCE
        private const val PROBE = "/api/v1/entries/sgv.json?count=1"

        /** What a probe that didn't get an entries array reports (no Nightscout at that address). */
        private const val NOT_ENTRIES = 404

        /** Nightscout's access tokens: the subject's name, a dash, 16 hex digits. */
        private val TOKEN = Regex("^[A-Za-z0-9_.]+(?:-[A-Za-z0-9_.]+)*-[0-9a-f]{16}$")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
