// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.designsystem.icons.PackageIcon
import app.mauri.sukrara.designsystem.rows.ActionRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.integrations.R
import app.mauri.sukrara.integrations.ui.ago
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * What the link is doing, in the order a person needs it (the row on Share with apps and the page): off; no Garmin
 * Connect; Garmin Connect not answering; no watch in it; the face on no watch; the watch away; messages arriving;
 * messages lost but the face fetching every 5 min; messages lost and nothing fetched; waiting for the first one.
 */
sealed interface AuraStatus {
    data object Off : AuraStatus

    data object NoGarminConnect : AuraStatus

    data object NotAnswering : AuraStatus

    data object NoWatch : AuraStatus

    data object NotInstalled : AuraStatus

    /** No watch with the face is connected; [at] = the last time it got anything (null = never). */
    data class Away(
        val watch: String,
        val at: Instant?,
    ) : AuraStatus

    data class Sent(
        val watch: String,
        val at: Instant,
    ) : AuraStatus

    /** Garmin Connect loses the messages, but the face fetched at [at] (it keeps up every 5 min). */
    data class Fetching(
        val at: Instant,
    ) : AuraStatus

    /** Messages don't arrive and the face hasn't fetched lately. */
    data class Failing(
        val watch: String,
    ) : AuraStatus

    data object Waiting : AuraStatus

    companion object {
        /** A fetch this recent means the face runs and reads this phone (it fetches every 5 min). */
        val FETCH_FRESH: Duration = Duration.ofMinutes(11)

        fun of(
            s: AuraLinkState,
            now: Instant,
        ): AuraStatus {
            val fetched = s.fetched?.takeIf { Duration.between(it, now) <= FETCH_FRESH }
            val withFace = s.watches.filter { it.installed != false }
            val near = withFace.filter { it.connected }
            return when {
                !s.on -> {
                    Off
                }

                !s.garminConnect -> {
                    NoGarminConnect
                }

                s.answering == false && fetched == null -> {
                    NotAnswering
                }

                s.answering == true && s.watches.isEmpty() -> {
                    NoWatch
                }

                s.watches.isNotEmpty() && withFace.isEmpty() -> {
                    NotInstalled
                }

                near.isEmpty() && withFace.isNotEmpty() -> {
                    Away(withFace.first().name, listOfNotNull(withFace.mapNotNull { it.delivered }.maxOrNull(), s.fetched).maxOrNull())
                }

                else -> {
                    val sent =
                        near
                            .filter {
                                it.delivered != null && (it.failed == null || it.failed < it.delivered)
                            }.maxByOrNull { it.delivered!! }
                    when {
                        sent != null -> Sent(sent.name, sent.delivered!!)
                        fetched != null -> Fetching(fetched)
                        near.any { it.failed != null } -> Failing(near.first().name)
                        else -> Waiting
                    }
                }
            }
        }
    }
}

class AuraViewModel(
    private val link: AuraLink,
    context: Context,
) : ViewModel() {
    private val app = context.applicationContext
    val state: StateFlow<AuraLinkState> = link.state

    fun setOn(on: Boolean) {
        viewModelScope.launch { link.setOn(on) }
    }

    fun sendNow() = link.sendNow()

    /** Garmin Connect, where the watch's connection and Bluetooth are fixed; false when it can't be opened. */
    fun openGarminConnect(): Boolean {
        val i = app.packageManager.getLaunchIntentForPackage(ConnectIq.GCM) ?: return false
        return runCatching { app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

class AuraActions(
    val setOn: (Boolean) -> Unit = {},
    val sendNow: () -> Unit = {},
    val openGarminConnect: () -> Unit = {},
)

@Composable
fun AuraPage(
    viewModel: AuraViewModel,
    now: () -> Instant,
    modifier: Modifier = Modifier,
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    AuraContent(s, ticking(now), AuraActions(viewModel::setOn, viewModel::sendNow, { viewModel.openGarminConnect() }), modifier)
}

/** [now] again every 30 s, so "2 minutes ago" keeps counting while nothing else changes. */
@Composable
fun ticking(now: () -> Instant): Instant {
    val clock by rememberUpdatedState(now)
    val t by produceState(now()) {
        while (true) {
            delay(TICK_MS)
            value = clock()
        }
    }
    return t
}

private const val TICK_MS = 30_000L

/**
 * Settings › Share with apps › *Sukrara Aura*: the switch (on by default), what the link does now, each watch, when the
 * face last fetched, and the ways out: *Send now*, *Open Garmin Connect* (Bluetooth, the watch's connection, its
 * battery settings) and the switch.
 */
@Composable
fun AuraContent(
    s: AuraLinkState,
    now: Instant,
    actions: AuraActions,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(stringResource(R.string.in_aura_intro))
    SettingsGroup {
        SwitchRow(stringResource(R.string.in_aura_switch), s.on, actions.setOn, summary = auraStatusText(AuraStatus.of(s, now), now))
    }
    if (s.on && s.garminConnect) {
        SettingsGroup(title = stringResource(R.string.in_aura_watches)) {
            if (s.watches.isEmpty()) {
                InfoRow(
                    stringResource(R.string.in_aura_no_watch_title),
                    summary = stringResource(R.string.in_aura_no_watch),
                )
            }
            for (w in s.watches) InfoRow(w.name, summary = watchText(w, now))
            InfoRow(
                stringResource(R.string.in_aura_fetch_title),
                summary =
                    when {
                        !s.fetchOpen && !s.portBusy -> stringResource(R.string.in_aura_fetch_closed)
                        s.fetched != null -> stringResource(R.string.in_aura_fetched, ago(s.fetched, now))
                        else -> stringResource(R.string.in_aura_not_fetched)
                    },
            )
            // the face's own account (sent with its fetch): which reading it draws
            s.report?.drawn?.let { drawn ->
                InfoRow(
                    stringResource(R.string.in_aura_face_shows_title),
                    summary = stringResource(R.string.in_aura_face_shows, ClockText.time(drawn.epochSecond), ago(s.report.at, now)),
                )
            }
        }
        SettingsGroup {
            ActionRow(
                stringResource(R.string.in_aura_send_now),
                actions.sendNow,
                summary = stringResource(R.string.in_aura_send_now_summary),
            )
            ActionRow(
                stringResource(R.string.in_aura_open_gcm),
                actions.openGarminConnect,
                summary = stringResource(R.string.in_aura_open_gcm_summary),
                leading = { PackageIcon(ConnectIq.GCM) },
            )
        }
    }
    FooterText(stringResource(if (s.portBusy) R.string.in_aura_footer_busy else R.string.in_aura_footer))
}

@Composable
private fun watchText(
    w: AuraWatch,
    now: Instant,
): String =
    when {
        w.installed == false -> {
            stringResource(R.string.in_aura_watch_no_face)
        }

        !w.connected -> {
            w.delivered?.let { stringResource(R.string.in_aura_watch_away_since, ago(it, now)) }
                ?: stringResource(R.string.in_aura_watch_away)
        }

        w.delivered != null && (w.failed == null || w.failed < w.delivered) -> {
            stringResource(R.string.in_aura_watch_sent, ago(w.delivered, now))
        }

        w.noAnswer -> {
            stringResource(R.string.in_aura_watch_no_answer)
        }

        w.failed != null -> {
            stringResource(R.string.in_aura_watch_failed)
        }

        else -> {
            stringResource(R.string.in_aura_watch_waiting)
        }
    }

/** The status line on the page and on Share with apps. */
@Composable
fun auraStatusText(
    status: AuraStatus,
    now: Instant,
): String =
    when (status) {
        AuraStatus.Off -> {
            stringResource(R.string.in_aura_off)
        }

        AuraStatus.NoGarminConnect -> {
            stringResource(R.string.in_aura_no_gcm)
        }

        AuraStatus.NotAnswering -> {
            stringResource(R.string.in_aura_not_answering)
        }

        AuraStatus.NoWatch -> {
            stringResource(R.string.in_aura_no_watch)
        }

        AuraStatus.NotInstalled -> {
            stringResource(R.string.in_aura_not_installed)
        }

        is AuraStatus.Away -> {
            status.at?.let { stringResource(R.string.in_aura_away_since, status.watch, ago(it, now)) }
                ?: stringResource(R.string.in_aura_away, status.watch)
        }

        is AuraStatus.Sent -> {
            stringResource(R.string.in_aura_sent, status.watch, ago(status.at, now))
        }

        is AuraStatus.Fetching -> {
            stringResource(R.string.in_aura_fetching, ago(status.at, now))
        }

        is AuraStatus.Failing -> {
            stringResource(R.string.in_aura_failing, status.watch)
        }

        AuraStatus.Waiting -> {
            stringResource(R.string.in_aura_waiting)
        }
    }
