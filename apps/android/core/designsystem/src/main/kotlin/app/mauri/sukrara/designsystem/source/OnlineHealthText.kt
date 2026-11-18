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
import java.time.Instant

/**
 * THE words of an online source's health (contracts/sources/health.json), the same on home, Settings › Source and
 * setup's Connect step: what is happening and, when the person can do something, what. [host] names the site.
 */
@Composable
fun onlineHealthText(
    health: OnlineHealth,
    newest: Instant?,
    now: Instant,
    host: String,
): String {
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
            ago?.let { stringResource(R.string.ds_online_no_new, it) }
                ?: stringResource(R.string.ds_online_no_glucose)
        }

        OnlineHealth.Offline -> {
            stringResource(R.string.ds_online_offline)
        }

        OnlineHealth.Unreachable -> {
            stringResource(R.string.ds_online_unreachable, host)
        }

        OnlineHealth.SignedOut -> {
            stringResource(R.string.ds_online_signed_out)
        }

        OnlineHealth.RateLimited -> {
            stringResource(R.string.ds_online_rate_limited)
        }
    }
}

/** The health's mark beside its words: a link while readings come, a broken link or a key when they don't. */
@DrawableRes
fun OnlineHealth.iconRes(): Int =
    when (this) {
        OnlineHealth.Connecting, OnlineHealth.Live, OnlineHealth.Late -> R.drawable.ds_ic_public
        OnlineHealth.SignedOut -> R.drawable.ds_ic_key
        OnlineHealth.Offline -> R.drawable.ds_ic_public_off
        OnlineHealth.NoNewReadings, OnlineHealth.Unreachable, OnlineHealth.RateLimited -> R.drawable.ds_ic_link_off
    }
