// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.source

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.SourceKind
import java.time.Instant

/**
 * THE words of an online source's health (contracts/sources/health.json), the same on home, Settings › Source and
 * setup's Connect step: what is happening and, when the person can do something, what. [host] names the site;
 * [kind] the source (a Nightscout site's uploader, or the Libre app behind LibreLinkUp, is where to look).
 */
@Composable
fun onlineHealthText(
    health: OnlineHealth,
    newest: Instant?,
    now: Instant,
    host: String,
    kind: SourceKind = SourceKind.Nightscout,
): String {
    val llu = kind == SourceKind.LibreLinkUp
    val ago =
        newest?.let {
            DateUtils
                .getRelativeTimeSpanString(
                    it.toEpochMilli(),
                    now.toEpochMilli(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString()
        }
    return when (health) {
        OnlineHealth.Connecting -> {
            stringResource(R.string.ds_online_connecting)
        }

        OnlineHealth.Live -> {
            ago?.let { stringResource(R.string.ds_online_live, it) } ?: stringResource(R.string.ds_online_connecting)
        }

        OnlineHealth.Late -> {
            ago?.let { stringResource(R.string.ds_online_late, it) } ?: stringResource(R.string.ds_online_connecting)
        }

        OnlineHealth.NoNewReadings -> {
            ago?.let { stringResource(if (llu) R.string.ds_online_llu_no_new else R.string.ds_online_no_new, it) }
                ?: stringResource(if (llu) R.string.ds_online_llu_no_glucose else R.string.ds_online_no_glucose)
        }

        OnlineHealth.Offline -> {
            stringResource(R.string.ds_online_offline)
        }

        OnlineHealth.Unreachable -> {
            if (llu) stringResource(R.string.ds_online_llu_unreachable) else stringResource(R.string.ds_online_unreachable, host)
        }

        OnlineHealth.SignedOut -> {
            stringResource(if (llu) R.string.ds_online_llu_signed_out else R.string.ds_online_signed_out)
        }

        OnlineHealth.RateLimited -> {
            stringResource(if (llu) R.string.ds_online_llu_rate_limited else R.string.ds_online_rate_limited)
        }

        OnlineHealth.ActionNeeded -> {
            stringResource(R.string.ds_online_llu_terms)
        }

        OnlineHealth.NotShared -> {
            stringResource(R.string.ds_online_llu_not_shared)
        }

        OnlineHealth.Stopped -> {
            stringResource(R.string.ds_online_llu_stopped)
        }
    }
}

/** The health's mark beside its words: a link while readings come, a broken link or a key when they don't. */
@DrawableRes
fun OnlineHealth.iconRes(): Int =
    when (this) {
        OnlineHealth.Connecting, OnlineHealth.Live, OnlineHealth.Late -> R.drawable.ds_ic_public

        OnlineHealth.SignedOut, OnlineHealth.ActionNeeded -> R.drawable.ds_ic_key

        OnlineHealth.Offline -> R.drawable.ds_ic_public_off

        OnlineHealth.NoNewReadings, OnlineHealth.Unreachable, OnlineHealth.RateLimited, OnlineHealth.NotShared,
        OnlineHealth.Stopped,
        -> R.drawable.ds_ic_link_off
    }
