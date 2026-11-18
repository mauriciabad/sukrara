// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout.site

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.ServiceIcon
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.source.iconRes
import app.mauri.sukrara.designsystem.source.onlineHealthText
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.integrations.R
import app.mauri.sukrara.integrations.ui.since
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/** What Settings › Source's Nightscout rows can do. */
class NightscoutSourceActions(
    /** The site, or *Sign in again*: setup's Connect step on its own, the address filled in. */
    val editSite: () -> Unit = {},
    /** *Try again* now (offline, unreachable, asked to wait). */
    val retry: () -> Unit = {},
    val setTreatments: (Boolean) -> Unit = {},
    /** *Bring back 90 days from Nightscout*. */
    val pullHistory: () -> Unit = {},
)

/**
 * Settings › Source for a Nightscout site (docs/44 "Settings": Site, Token, Treatments both ways): the site (its own
 * row: setup's Connect step changes it or signs in again), the health in words with its one exit (*Sign in again*,
 * *Try again*; the others clear by themselves), *Treatments both ways* with what it does now (a read-only token says
 * so), *Open Nightscout* in the browser and, for your own glucose, *Bring back 90 days*. The token is never shown.
 */
@Composable
fun NightscoutSourceRows(
    view: NsSourceView,
    person: Person,
    now: Instant,
    actions: NightscoutSourceActions,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    SettingsGroup(title = stringResource(R.string.in_nss_title)) {
        NavigationRow(
            stringResource(R.string.in_nss_site),
            actions.editSite,
            summary = view.host.ifEmpty { stringResource(R.string.in_nss_site_none) },
            leading = { ServiceIcon(Service.Nightscout) },
        )
        val health = if (view.credentialUnreadable) OnlineHealth.SignedOut else view.health
        // the state changes by itself as calls come back: cross-fades, and TalkBack hears it once
        val readyAt = view.readyAt?.takeIf { it.isAfter(now) }?.let { ClockText.time(it.epochSecond) }
        AnimatedContent(health to onlineHealthText(health, view.newest, now, view.host, readyAt = readyAt), transitionSpec = {
            Motion.fadeThrough(reduced)
        }, label = "nss-health") { (h, text) ->
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                when (h) {
                    OnlineHealth.SignedOut -> {
                        NavigationRow(
                            stringResource(DsR.string.ds_online_sign_in),
                            actions.editSite,
                            summary = text,
                            icon = h.iconRes(),
                            warning = true,
                        )
                    }

                    OnlineHealth.Unreachable, OnlineHealth.Offline, OnlineHealth.RateLimited -> {
                        NavigationRow(
                            stringResource(DsR.string.ds_online_try_again),
                            actions.retry,
                            summary = text,
                            icon = h.iconRes(),
                            warning = true,
                        )
                    }

                    // nothing new reaching the site: the line, no action here (the uploader is the place to look); a
                    // sensor warming up: when it's ready
                    OnlineHealth.NoNewReadings, OnlineHealth.WarmingUp -> {
                        InfoRow(stringResource(R.string.in_nss_status), summary = text, icon = h.iconRes())
                    }

                    // live, late, connecting: the page's *Now* says it already
                    else -> {
                        Unit
                    }
                }
            }
        }
        SwitchRow(
            stringResource(R.string.in_nss_treatments),
            view.treatments != NsTreatments.Off,
            actions.setTreatments,
            summary =
                stringResource(
                    when (view.treatments) {
                        NsTreatments.Off -> R.string.in_nss_treatments_off
                        NsTreatments.BothWays -> R.string.in_nss_treatments_on
                        NsTreatments.ReadOnly -> R.string.in_nss_treatments_read_only
                    },
                ),
            icon = DsR.drawable.ds_ic_sync_alt,
        )
        if (view.root.isNotEmpty()) {
            NavigationRow(
                stringResource(R.string.in_nss_open),
                { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(view.root))) } },
                summary = stringResource(R.string.in_nss_open_summary),
                icon = DsR.drawable.ds_ic_open_in_new,
            )
        }
    }
    if (person == Person.Yours && view.configured) {
        SettingsGroup {
            val pulling = view.pulling
            NavigationRow(
                stringResource(R.string.in_nss_history),
                actions.pullHistory,
                summary =
                    pulling?.let { stringResource(R.string.in_nss_history_pulling, since(it, now)) }
                        ?: stringResource(R.string.in_nss_history_summary),
                icon = DsR.drawable.ds_ic_history,
                enabled = pulling == null,
            )
        }
    }
}
