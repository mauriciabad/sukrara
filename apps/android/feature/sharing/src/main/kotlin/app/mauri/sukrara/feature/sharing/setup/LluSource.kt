// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import app.mauri.sukrara.core.model.OnlineHealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/** Someone a LibreLinkUp account follows: their patient id and name. */
data class LluChoice(
    val id: String,
    val name: String,
)

/** The LibreLinkUp source as setup's Connect step sees it (docs/44 step 2). */
data class LluConnection(
    /** The saved account's email, "" = none. */
    val email: String = "",
    /** An account is saved with a password LibreLinkUp accepted. */
    val signedIn: Boolean = false,
    /** Whose glucose is read (patient id), "" = not picked. */
    val patient: String = "",
    /** Their name, as LibreLinkUp says it. */
    val name: String = "",
    val people: List<LluChoice> = emptyList(),
    val health: OnlineHealth = OnlineHealth.Connecting,
    val newest: Instant? = null,
    /** The sensor's warm-up end (LibreLinkUp's start + the default warm-up), said while [health] is warming up. */
    val readyAt: Instant? = null,
    /** THE LibreView account's email when its password is saved: *Sign in as …* in one tap (your own glucose). */
    val libreViewEmail: String? = null,
)

/** The answer of *Connect* (the field the problem belongs to shows it). */
enum class LluConnectOutcome {
    /** Signed in and the person picked. */
    Connected,

    /** Signed in; the account follows several people: pick one. */
    Choose,

    BadCredentials,

    /** New terms to accept in the LibreLinkUp app first. */
    Terms,

    /** Too many attempts: wait. */
    RateLimited,

    /** Signed in, but no one shares with this account yet. */
    NoOne,

    Unreachable,

    Offline,

    /** LibreLinkUp refuses Sukrara (Abbott changed it). */
    Stopped,
}

/** Setup's way to the LibreLinkUp source (`:integrations`' account and source, joined in `:app`). */
interface LluSourcePort {
    val connection: Flow<LluConnection>

    /** Signs in with [email] and [password]; saved only when LibreLinkUp accepts them. */
    suspend fun connect(
        email: String,
        password: String,
    ): LluConnectOutcome

    /** Signs in with THE LibreView account's saved email and password (your own glucose: the same account). */
    suspend fun connectWithLibreView(): LluConnectOutcome

    /** Reads whom the saved account follows again (*Try again* after inviting it from the Libre app). */
    suspend fun recheck(): LluConnectOutcome

    /** The person picked among several. */
    suspend fun choose(patient: String)

    companion object {
        /** No LibreLinkUp source in this build or test. */
        val None =
            object : LluSourcePort {
                override val connection: Flow<LluConnection> = flowOf(LluConnection())

                override suspend fun connect(
                    email: String,
                    password: String,
                ) = LluConnectOutcome.Unreachable

                override suspend fun connectWithLibreView() = LluConnectOutcome.Unreachable

                override suspend fun recheck() = LluConnectOutcome.Unreachable

                override suspend fun choose(patient: String) = Unit
            }
    }
}

/** The Connect step's own state: the last attempt and whether one is running. */
data class LluUi(
    val connection: LluConnection = LluConnection(),
    val connecting: Boolean = false,
    /** The last attempt's answer (null = none in this visit). */
    val result: LluConnectOutcome? = null,
)
