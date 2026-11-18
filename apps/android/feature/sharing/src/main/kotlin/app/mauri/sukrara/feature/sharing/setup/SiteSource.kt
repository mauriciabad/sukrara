// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import app.mauri.sukrara.core.model.OnlineHealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/** The online source's site as setup's Connect step sees it (docs/44 step 1: a Nightscout site). */
data class SiteConnection(
    /** The saved site's root, "" = none yet. */
    val root: String = "",
    val host: String = "",
    /** A token or API secret is saved. */
    val saved: Boolean = false,
    val health: OnlineHealth = OnlineHealth.Connecting,
    /** The newest reading the site had, null = none yet. */
    val newest: Instant? = null,
    /** *Bring back 90 days* is running: from when. */
    val pulling: Instant? = null,
    /** The site's sensor's warm-up end, said while [health] is [OnlineHealth.WarmingUp]. */
    val readyAt: Instant? = null,
    /**
     * The site read for the person before a change of whose glucose, offered to confirm (nothing is read until
     * *Connect*; its saved token is kept for it); "" = none.
     */
    val earlier: String = "",
)

/** The answer of *Connect* (the field the problem belongs to shows it). */
enum class SiteConnectResult {
    Connected,

    /** Not a web address (the address field). */
    BadAddress,

    /** Plain http to another device (the address field). */
    NeedsHttps,

    /** The token or secret was refused, or none was given (the token field). */
    Refused,

    /** Something answered, but not a Nightscout (the address field). */
    NotNightscout,

    /** No answer from the site. */
    Unreachable,

    /** No internet on this phone. */
    Offline,
}

/** Setup's way to the online source (`:integrations`' Nightscout account and source, joined in `:app`). */
interface SiteSourcePort {
    val connection: Flow<SiteConnection>

    /** Tries [address] with [credential] (a pasted `?token=` fills an empty one) and saves both when the site answers. */
    suspend fun connect(
        address: String,
        credential: String,
    ): SiteConnectResult

    /** *Bring back 90 days from Nightscout*: the source reads back that far (its readings, ranked as online). */
    suspend fun pullHistory()

    companion object {
        /** No online source in this build or test. */
        val None =
            object : SiteSourcePort {
                override val connection: Flow<SiteConnection> = flowOf(SiteConnection())

                override suspend fun connect(
                    address: String,
                    credential: String,
                ) = SiteConnectResult.Unreachable

                override suspend fun pullHistory() = Unit
            }
    }
}

/** The Connect step's own state: the last attempt and whether one is running. */
data class SiteUi(
    val connection: SiteConnection = SiteConnection(),
    val connecting: Boolean = false,
    /** The last *Connect*'s answer (null = none in this visit). */
    val result: SiteConnectResult? = null,
)
