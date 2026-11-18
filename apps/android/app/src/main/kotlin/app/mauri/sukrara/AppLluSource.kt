// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.data.libreview.LibreViewAccountState
import app.mauri.sukrara.core.data.libreview.LibreViewLogin
import app.mauri.sukrara.feature.sharing.setup.LluChoice
import app.mauri.sukrara.feature.sharing.setup.LluConnectOutcome
import app.mauri.sukrara.feature.sharing.setup.LluConnection
import app.mauri.sukrara.feature.sharing.setup.LluSourcePort
import app.mauri.sukrara.integrations.librelinkup.LibreLinkUpSource
import app.mauri.sukrara.integrations.librelinkup.LluConnectResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Setup's LibreLinkUp step on the source (`:integrations`): *Sign in* proves the email and password and reads whom the
 * account follows; *Sign in as …* uses THE LibreView account's saved sign-in (your own glucose: the Libre app's
 * account, which LibreLinkUp signs in to as well), copied into the source's own account so signing out of one never
 * stops the other.
 */
class AppLluSource(
    private val source: LibreLinkUpSource,
    private val libreView: LibreViewLogin,
) : LluSourcePort {
    override val connection: Flow<LluConnection> =
        combine(source.view, libreView.account) { v, lv ->
            LluConnection(
                email = v.email,
                signedIn = v.signedIn,
                patient = v.patient,
                name = v.name,
                people = v.people.map { LluChoice(it.id, it.name) },
                health = v.health,
                newest = v.newest,
                readyAt = v.sensor?.warmupEnd,
                libreViewEmail = (lv as? LibreViewAccountState.SignedIn)?.email,
            )
        }.distinctUntilChanged()

    override suspend fun connect(
        email: String,
        password: String,
    ) = outcome(source.connect(email, password))

    override suspend fun connectWithLibreView(): LluConnectOutcome {
        val c = libreView.credentials() ?: return LluConnectOutcome.BadCredentials
        return outcome(source.connect(c.email, c.password))
    }

    override suspend fun recheck() = outcome(source.recheck())

    override suspend fun choose(patient: String) = source.choose(patient)

    private fun outcome(r: LluConnectResult): LluConnectOutcome =
        when (r) {
            is LluConnectResult.Connected -> if (r.picked == null) LluConnectOutcome.Choose else LluConnectOutcome.Connected
            LluConnectResult.BadCredentials -> LluConnectOutcome.BadCredentials
            LluConnectResult.Terms -> LluConnectOutcome.Terms
            LluConnectResult.RateLimited -> LluConnectOutcome.RateLimited
            LluConnectResult.NoOne -> LluConnectOutcome.NoOne
            LluConnectResult.Unreachable -> LluConnectOutcome.Unreachable
            LluConnectResult.Offline -> LluConnectOutcome.Offline
            LluConnectResult.Stopped -> LluConnectOutcome.Stopped
        }
}
