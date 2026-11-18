// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.feature.sharing.setup.SiteConnectResult
import app.mauri.sukrara.feature.sharing.setup.SiteConnection
import app.mauri.sukrara.feature.sharing.setup.SiteSourcePort
import app.mauri.sukrara.integrations.nightscout.site.NightscoutAccount
import app.mauri.sukrara.integrations.nightscout.site.NightscoutSite
import app.mauri.sukrara.integrations.nightscout.site.NightscoutSource
import app.mauri.sukrara.integrations.nightscout.site.NsConnectResult
import app.mauri.sukrara.integrations.nightscout.site.NsSourceConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration

/**
 * Setup's Connect step on the Nightscout source (`:integrations`): *Connect* proves the address and the token, then
 * saves the site next to the answer (`source.json`) and the token in the secure store; a new token lets treatment
 * writes be tried again.
 */
class AppSiteSource(
    private val account: NightscoutAccount,
    private val source: NightscoutSource,
    private val config: NsSourceConfig,
    private val clock: Clock,
) : SiteSourcePort {
    override val connection: Flow<SiteConnection> =
        source.view
            .map { v -> SiteConnection(v.root, v.host, v.configured, v.health, v.newest, v.pulling) }
            .distinctUntilChanged()

    override suspend fun connect(
        address: String,
        credential: String,
    ): SiteConnectResult {
        val saved = config.data.first()
        val savedRoot = saved.url.takeIf { it.isNotEmpty() }?.let { NightscoutSite.parse(it).root }
        val (site, result) = account.connect(address, credential, savedRoot)
        if (site != null) {
            config.set(saved.copy(url = site.root))
            source.retryWrites()
        }
        return when (result) {
            is NsConnectResult.Connected -> SiteConnectResult.Connected
            NsConnectResult.BadAddress -> SiteConnectResult.BadAddress
            NsConnectResult.NeedsHttps -> SiteConnectResult.NeedsHttps
            NsConnectResult.Refused -> SiteConnectResult.Refused
            NsConnectResult.NotNightscout -> SiteConnectResult.NotNightscout
            NsConnectResult.Unreachable -> SiteConnectResult.Unreachable
            NsConnectResult.Offline -> SiteConnectResult.Offline
        }
    }

    override suspend fun pullHistory() = source.pullHistory(clock.now().minus(HISTORY))

    private companion object {
        /** *Bring back 90 days from Nightscout*: as far back as Import history goes. */
        val HISTORY: Duration = Duration.ofDays(90)
    }
}
