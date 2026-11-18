// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.librelinkup

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.ChoiceDialog
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.ServiceIcon
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.source.iconRes
import app.mauri.sukrara.designsystem.source.onlineHealthText
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.integrations.R
import app.mauri.sukrara.integrations.ui.since
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/** What Settings › Source's LibreLinkUp rows can do. */
class LibreLinkUpSourceActions(
    /** The account, or *Sign in again*: setup's Connect step on its own. */
    val editAccount: () -> Unit = {},
    /** *Try again* now (offline, unreachable, asked to wait, new terms accepted, invited). */
    val retry: () -> Unit = {},
    /** Whose glucose among the account's people. */
    val choose: (String) -> Unit = {},
    /** *Read the sensor in Sukrara*: the new-sensor flow, which takes the running Libre 3 over with a tap. */
    val takeOver: () -> Unit = {},
    val signOut: () -> Unit = {},
)

/**
 * Settings › Source for LibreLinkUp (docs/44 "Settings": Account, Person; Appendix C "States and exits"): the account
 * (setup's Connect step changes it or signs in again), whose glucose (a pick when the account follows several people),
 * the health in words with its one exit, the sensor LibreLinkUp says is running and, for your own glucose, what the
 * Libre app keeps (the LibreView upload) and the move: *Read the sensor in Sukrara*. *Sign out* is the exit, confirmed
 * (glucose stops until a new sign-in). The password is never shown.
 */
@Composable
fun LibreLinkUpSourceRows(
    view: LluSourceView,
    person: Person,
    now: Instant,
    actions: LibreLinkUpSourceActions,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    val reduced = LocalReducedMotion.current
    var choosing by rememberSaveable { mutableStateOf(false) }
    var signingOut by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(title = stringResource(R.string.in_llu_title)) {
        NavigationRow(
            stringResource(R.string.in_llu_account),
            actions.editAccount,
            summary = view.email.ifEmpty { stringResource(R.string.in_llu_account_none) },
            leading = { ServiceIcon(Service.LibreLinkUp) },
        )
        // the person LibreLinkUp reads: not *Whose glucose* (the page's first row, yours or someone else's) said twice
        if (view.people.size > 1) {
            NavigationRow(
                stringResource(R.string.in_llu_person),
                { choosing = true },
                summary = view.name.ifEmpty { stringResource(R.string.in_llu_whose_none) },
                icon = DsR.drawable.ds_ic_account_circle,
            )
        } else if (view.name.isNotEmpty()) {
            InfoRow(
                stringResource(R.string.in_llu_person),
                summary = view.name,
                icon = DsR.drawable.ds_ic_account_circle,
            )
        }
        val health = if (view.signInAgain) OnlineHealth.SignedOut else view.health
        // the state changes by itself as calls come back: cross-fades, and TalkBack hears it once
        AnimatedContent(
            health to
                onlineHealthText(
                    health,
                    view.newest,
                    now,
                    "LibreLinkUp",
                    SourceKind.LibreLinkUp,
                    readyAt =
                        view.sensor
                            ?.warmupEnd
                            ?.takeIf { it.isAfter(now) }
                            ?.let { ClockText.time(it.epochSecond) },
                ),
            transitionSpec = { Motion.fadeThrough(reduced) },
            label = "llu-health",
        ) { (h, text) ->
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                when (h) {
                    OnlineHealth.SignedOut -> {
                        NavigationRow(
                            stringResource(DsR.string.ds_online_sign_in),
                            actions.editAccount,
                            summary = text,
                            icon = h.iconRes(),
                            warning = true,
                        )
                    }

                    OnlineHealth.NotShared -> {
                        NavigationRow(
                            stringResource(if (view.people.isNotEmpty()) R.string.in_llu_pick_again else DsR.string.ds_online_try_again),
                            if (view.people.isNotEmpty()) ({ choosing = true }) else actions.retry,
                            // others still share with the account: only the one picked stopped (not "no one")
                            summary = if (view.people.isNotEmpty()) stringResource(R.string.in_llu_picked_gone) else text,
                            icon = h.iconRes(),
                            warning = true,
                        )
                    }

                    OnlineHealth.Unreachable, OnlineHealth.Offline, OnlineHealth.ActionNeeded, OnlineHealth.Stopped -> {
                        NavigationRow(
                            stringResource(DsR.string.ds_online_try_again),
                            actions.retry,
                            summary = text,
                            icon = h.iconRes(),
                            warning = true,
                        )
                    }

                    // nothing new: the line, no action here (the Libre app is the place to look); asked to wait: no
                    // *Try again* (a call before the time LibreLinkUp gave may lengthen the lockout), it asks by itself;
                    // a sensor warming up: when it's ready
                    OnlineHealth.NoNewReadings, OnlineHealth.RateLimited, OnlineHealth.WarmingUp -> {
                        InfoRow(stringResource(R.string.in_nss_status), summary = text, icon = h.iconRes())
                    }

                    // live, late, connecting: the page's *Last reading* says it already
                    else -> {
                        Unit
                    }
                }
            }
        }
        view.sensor?.let { s ->
            InfoRow(
                stringResource(R.string.in_llu_sensor),
                summary = stringResource(R.string.in_llu_sensor_summary, since(s.start, now), s.id),
                icon = DsR.drawable.ds_ic_calendar_month,
            )
        }
    }
    if (person == Person.Yours && view.signedIn) {
        SettingsGroup(title = stringResource(R.string.in_llu_libre_app)) {
            InfoRow(
                stringResource(R.string.in_llu_upload),
                summary = stringResource(R.string.in_llu_upload_summary),
                icon = DsR.drawable.ds_ic_cloud_upload,
            )
            NavigationRow(
                stringResource(R.string.in_llu_take_over),
                actions.takeOver,
                summary = stringResource(R.string.in_llu_take_over_summary),
                icon = DsR.drawable.ds_ic_nfc,
            )
        }
    }
    if (view.email.isNotEmpty()) {
        SettingsGroup {
            NavigationRow(
                stringResource(R.string.in_llu_sign_out),
                { signingOut = true },
                summary = stringResource(R.string.in_llu_sign_out_summary),
                icon = DsR.drawable.ds_ic_person_remove,
            )
        }
    }
    if (choosing) {
        ChoiceDialog(
            stringResource(R.string.in_llu_whose),
            view.people.map { Choice(it.name.ifBlank { stringResource(R.string.in_llu_unnamed) }, it.id) },
            view.patient,
            onClose = { choosing = false },
        ) { actions.choose(it) }
    }
    if (signingOut) {
        AlertDialog(
            onDismissRequest = { signingOut = false },
            title = { Text(stringResource(R.string.in_llu_sign_out_title)) },
            text = { Text(stringResource(R.string.in_llu_sign_out_text)) },
            confirmButton = {
                TextButton({
                    signingOut = false
                    actions.signOut()
                }) { Text(stringResource(R.string.in_llu_sign_out)) }
            },
            dismissButton = { TextButton({ signingOut = false }) { Text(stringResource(DsR.string.ds_cancel)) } },
        )
    }
}
