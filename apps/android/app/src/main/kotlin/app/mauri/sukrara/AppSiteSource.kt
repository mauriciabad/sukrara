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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.Duration

/**
 * Setup's Connect step on the Nightscout source (`:integrations`): *Connect* proves the address and the token, then
 * saves the site next to the answer (`source.json`) and the token in the secure store; a new token lets treatment
 * writes be tried again. After a change of whose glucose the site read before is only offered ([confirm]): nothing is
 * read until *Connect* confirms it (its saved token is kept for it), or another site replaces it.
 */
class AppSiteSource(
    private val account: NightscoutAccount,
    private val source: NightscoutSource,
    private val config: NsSourceConfig,
    private val clock: Clock,
) : SiteSourcePort {
    /** The site read for the person before a change of whose glucose, to confirm ("" = none). In memory only. */
    private val earlier = MutableStateFlow("")

    override val connection: Flow<SiteConnection> =
        combine(source.view, earlier) { v, e ->
            SiteConnection(
                v.root,
                v.host,
                v.configured,
                v.health,
                v.newest,
                v.pulling,
                v.readyAt,
                earlier = e.takeIf { v.root.isEmpty() }.orEmpty(),
            )
        }.distinctUntilChanged()

    /** A change of whose glucose cleared the site [root] (another person's): the Connect step offers it to confirm. */
    fun confirm(root: String) {
        earlier.value = root
    }

    override suspend fun connect(
        address: String,
        credential: String,
    ): SiteConnectResult {
        val saved = config.data.first()
        // the site to confirm keeps its saved token too: *Connect* with the address offered is the confirmation
        val savedRoot =
            saved.url
                .ifEmpty { earlier.value }
                .takeIf { it.isNotEmpty() }
                ?.let { NightscoutSite.parse(it).root }
        val (site, result) = account.connect(address, credential, savedRoot)
        if (site != null) {
            earlier.value = ""
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
