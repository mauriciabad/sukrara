// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.icons.ServiceIcon
import app.mauri.sukrara.designsystem.rows.ActionButtonsRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.MainSwitchRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.RowButton
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.integrations.R
import app.mauri.sukrara.integrations.localserver.ServerReader
import app.mauri.sukrara.integrations.localserver.ServerReaders
import app.mauri.sukrara.integrations.localserver.XdripServer
import app.mauri.sukrara.integrations.localserver.XdripState
import app.mauri.sukrara.integrations.localserver.xdripRowSummary
import app.mauri.sukrara.integrations.ui.since
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/** A fetch this recent means the face runs and reads this phone (it fetches every 5 min). */
private val FETCH_FRESH: Duration = Duration.ofMinutes(11)

private fun fresh(
    at: Instant?,
    now: Instant,
) = at?.takeIf { Duration.between(it, now) <= FETCH_FRESH }

/**
 * The newest reading [w] is known to have: the last one Garmin Connect delivered to it, or the one the face says it
 * keeps (its report comes with the fetch, which doesn't say which watch asked: it counts only while one watch has the
 * face). When the face [missed] what was delivered, only the face's own counts.
 */
internal fun shows(
    w: AuraWatch,
    s: AuraLinkState,
): Instant? {
    if (missed(w, s)) return s.report?.has
    return listOfNotNull(w.reading, s.report?.has?.takeIf { only(w, s) }).maxOrNull()
}

private fun only(
    w: AuraWatch,
    s: AuraLinkState,
) = s.watches.count { it.has(GarminApp.Aura) != false } == 1 && w.has(GarminApp.Aura) != false

/** The face's own link on [this] (a watch known only by its line: that line). */
private fun AuraWatch.face(): AppLink = link(GarminApp.Aura) ?: AppLink(installed, delivered, failed, noAnswer, reading, failingSince)

/** A report this long after a delivery has had time to count it. */
private val REPORT_AFTER: Duration = Duration.ofSeconds(30)

/** The face this far behind what was delivered didn't take it (more than one reading's gap). */
private val BEHIND: Duration = Duration.ofMinutes(2)

/**
 * The watch took what Garmin Connect delivered, but the face didn't (#244: after a watch restart Garmin didn't hand
 * its background's data to the face for 16 min): its report, sent well after the delivery, still has a reading
 * [BEHIND] or more older than the delivered one. Only while one watch has the face.
 */
internal fun missed(
    w: AuraWatch,
    s: AuraLinkState,
): Boolean {
    val r = s.report ?: return false
    val delivered = w.face().delivered ?: return false
    val reading = w.face().reading ?: return false
    return only(w, s) && r.at >= delivered + REPORT_AFTER && (r.has ?: Instant.EPOCH) <= reading - BEHIND
}

/**
 * The Garmin link in one line, in the order a person needs it (the row on Share with apps): off; no Garmin Connect;
 * Garmin Connect not answering; no watch in it; the face on no watch; the watch away; a watch has a reading; messages
 * lost but the face fetching every 5 min; messages lost and nothing fetched; waiting for the first one.
 */
sealed interface AuraStatus {
    data object Off : AuraStatus

    data object NoGarminConnect : AuraStatus

    data object NotAnswering : AuraStatus

    data object NoWatch : AuraStatus

    data object NotInstalled : AuraStatus

    /** No watch with the face is connected. */
    data class Away(
        val watch: String,
    ) : AuraStatus

    /** [watch] got the last message; [reading] = the newest reading it has (null = it carried none). */
    data class Sent(
        val watch: String,
        val reading: Instant?,
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
        fun of(
            s: AuraLinkState,
            now: Instant,
        ): AuraStatus {
            val fetched = fresh(s.fetched, now)
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
                    Away(withFace.first().name)
                }

                else -> {
                    val sent = near.filter { it.delivered() }.maxByOrNull { it.delivered!! }
                    when {
                        sent != null -> Sent(sent.name, shows(sent, s))
                        fetched != null -> Fetching(fetched)
                        near.any { it.failed != null } -> Failing(near.first().name)
                        else -> Waiting
                    }
                }
            }
        }
    }
}

/** The last message reached it and none failed since. */
private fun AuraWatch.delivered() = delivered != null && (failed == null || failed < delivered)

/** One watch's line on the Garmin page: connected or not, the newest reading it has, and what goes wrong. */
data class WatchLine(
    val connected: Boolean,
    val reading: Instant?,
    val problem: Problem?,
) {
    enum class Problem {
        /** Sukrara Aura isn't installed on it. */
        NoFace,

        /** The watch takes the messages but the face doesn't ([missed]): restart the watch (#244). */
        NotTaking,

        /** No message has got through for 15 min ([AuraWatch.stuck]): restarting the watch brings them back (#242). */
        Stuck,

        /** Garmin Connect loses the messages; the face keeps up by fetching every 5 min. */
        Slow,

        /** Garmin Connect takes the messages but never confirms them; sent again. */
        NoAnswer,

        /** The last message didn't arrive; sent again. */
        Failed,

        /** Connected, nothing sent yet. */
        Waiting,
    }

    companion object {
        fun of(
            w: AuraWatch,
            s: AuraLinkState,
            now: Instant,
        ): WatchLine {
            val reading = shows(w, s)
            val problem =
                when {
                    w.installed == false -> Problem.NoFace
                    !w.connected -> null
                    missed(w, s) -> Problem.NotTaking
                    w.delivered() -> null
                    w.stuck(now) -> Problem.Stuck
                    w.failed != null && fresh(s.fetched, now) != null -> Problem.Slow
                    w.failed != null && w.noAnswer -> Problem.NoAnswer
                    w.failed != null -> Problem.Failed
                    reading == null -> Problem.Waiting
                    else -> null
                }
            return WatchLine(w.connected, reading.takeIf { problem != Problem.NoFace }, problem)
        }
    }
}

/**
 * One Sukrara app's row under *Sukrara on your watch*: whether a watch has it and whether it runs (it asked this phone
 * lately: for the face, that it is the face in use).
 */
sealed interface AppLine {
    /** No watch Garmin Connect knows has it. */
    data object NotInstalled : AppLine

    /** Another app holds the port: only Garmin Connect's messages reach it. */
    data object PortBusy : AppLine

    /** It asked this phone at [at]: it runs. */
    data class InUse(
        val at: Instant,
    ) : AppLine

    /** On a watch, but it hasn't asked this phone lately. */
    data object Idle : AppLine

    /** Garmin Connect hasn't said yet. */
    data object Unknown : AppLine

    companion object {
        fun of(
            app: GarminApp,
            s: AuraLinkState,
            now: Instant,
        ): AppLine {
            val fetched = fresh(s.fetchedBy[app], now)
            return when {
                s.watches.isNotEmpty() && s.watches.all { it.has(app) == false } && fetched == null -> NotInstalled
                s.portBusy -> PortBusy
                fetched != null -> InUse(fetched)
                s.watches.any { it.has(app) == true } -> Idle
                else -> Unknown
            }
        }
    }
}

/** What the Garmin page shows: Sukrara's own link, and the xDrip+ server for other faces and data fields. */
data class GarminUi(
    val link: AuraLinkState,
    val xdrip: XdripState = XdripState.Off,
    val readers: List<ServerReader> = emptyList(),
)

class GarminViewModel(
    private val link: AuraLink,
    xdrip: XdripServer,
    context: Context,
    io: CoroutineDispatcher,
) : ViewModel() {
    private val app = context.applicationContext
    private val readers = MutableStateFlow<List<ServerReader>>(emptyList())
    val ui: StateFlow<GarminUi> =
        combine(link.state, xdrip.state, readers, ::GarminUi)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarminUi(link.state.value))

    init {
        viewModelScope.launch { readers.value = withContext(io) { ServerReaders.installed(app) } }
    }

    fun setOn(on: Boolean) {
        viewModelScope.launch { link.setOn(on) }
    }

    fun sendNow() = link.sendNow()

    /** Garmin Connect, where the watch's connection and Bluetooth are fixed; false when it can't be opened. */
    fun openGarminConnect(): Boolean {
        val i = app.packageManager.getLaunchIntentForPackage(ConnectIq.GCM) ?: return false
        return start(i)
    }

    /** Garmin Connect's page in the phone's app store. */
    fun getGarminConnect(): Boolean = start(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ConnectIq.GCM}")))

    private fun start(i: Intent) = runCatching { app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

class GarminActions(
    val setOn: (Boolean) -> Unit = {},
    val sendNow: () -> Unit = {},
    val openGarminConnect: () -> Unit = {},
    val getGarminConnect: () -> Unit = {},
    val openXdrip: () -> Unit = {},
)

@Composable
fun GarminPage(
    viewModel: GarminViewModel,
    now: () -> Instant,
    openXdrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    GarminContent(
        ui,
        ticking(now),
        GarminActions(
            viewModel::setOn,
            viewModel::sendNow,
            { viewModel.openGarminConnect() },
            { viewModel.getGarminConnect() },
            openXdrip,
        ),
        modifier,
    )
}

/** [now] again every 30 s, so a fetch older than 11 minutes stops counting while nothing else changes. */
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
 * Settings › Share with apps › *Garmin*, built like a stock device page: the main switch for Sukrara's own link (on by
 * default) with what stops it under it; the actions as stock action buttons (*Send now*, *Open Garmin Connect*, or *Get
 * Garmin Connect*); *Watches* (every watch Garmin Connect knows, by its own name, one line each: connected or not, the
 * reading it has as a clock time, what goes wrong); *Sukrara on your watch* (the face, the app and the data field:
 * each on a watch or not, and whether it asks this phone); and the xDrip+ server for other faces and data fields, which has its own switch and page.
 */
@Composable
fun GarminContent(
    ui: GarminUi,
    now: Instant,
    actions: GarminActions,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    val s = ui.link
    val status = AuraStatus.of(s, now)
    SettingsIntro(stringResource(R.string.in_garmin_intro))
    MainSwitchRow(
        stringResource(R.string.in_garmin_switch),
        s.on,
        actions.setOn,
        note =
            when (status) {
                AuraStatus.Off -> stringResource(R.string.in_garmin_off_note)
                AuraStatus.NoGarminConnect -> stringResource(R.string.in_garmin_no_gcm)
                AuraStatus.NotAnswering -> stringResource(R.string.in_garmin_not_answering)
                else -> null
            },
    )
    if (s.on) {
        if (s.garminConnect) {
            // always enabled: SPA draws a disabled button's label at full contrast, and a send with no watch near does no harm
            ActionButtonsRow(
                listOf(
                    RowButton(stringResource(R.string.in_garmin_send_now), DsR.drawable.ds_ic_send, onClick = actions.sendNow),
                    RowButton(
                        stringResource(R.string.in_garmin_open_gcm),
                        DsR.drawable.ds_ic_open_in_new,
                        onClick = actions.openGarminConnect,
                    ),
                ),
            )
            SettingsGroup(title = stringResource(R.string.in_garmin_watches)) {
                if (s.answering == true && s.watches.isEmpty()) {
                    InfoRow(
                        stringResource(R.string.in_garmin_no_watch),
                        summary = stringResource(R.string.in_garmin_no_watch_summary),
                    )
                }
                for (w in s.watches) {
                    InfoRow(w.name, summary = watchText(WatchLine.of(w, s, now), now))
                }
            }
            // Sukrara's own Connect IQ apps, one row each. While Garmin Connect hasn't said which watches it knows,
            // nothing is known about them
            if (s.answering != false || s.watches.isNotEmpty()) {
                SettingsGroup(title = stringResource(R.string.in_garmin_sukrara_group)) {
                    for (app in GarminApp.entries) {
                        InfoRow(
                            stringResource(appName(app)),
                            summary = appText(app, AppLine.of(app, s, now), now),
                            icon = appIcon(app),
                        )
                    }
                }
            }
        } else {
            ActionButtonsRow(
                listOf(
                    RowButton(stringResource(R.string.in_garmin_get_gcm), DsR.drawable.ds_ic_download, onClick = actions.getGarminConnect),
                ),
            )
        }
    }
    SettingsGroup(title = stringResource(R.string.in_garmin_other_group)) {
        NavigationRow(
            stringResource(R.string.in_apps_xdrip),
            actions.openXdrip,
            summary = xdripRowSummary(ui.xdrip, ui.readers),
            leading = { ServiceIcon(Service.Xdrip) },
            warning = ui.xdrip == XdripState.PortBusy,
        )
    }
    FooterText(stringResource(R.string.in_garmin_footer))
}

/** "Connected · has the 14:21 reading · …": one time per line, a clock time to compare with the phone's. */
@Composable
internal fun watchText(
    line: WatchLine,
    now: Instant,
): String {
    val parts = ArrayList<String>()
    parts += stringResource(if (line.connected) R.string.in_garmin_connected else R.string.in_garmin_not_connected)
    line.reading?.let { parts += stringResource(R.string.in_garmin_has_reading, since(it, now)) }
    when (line.problem) {
        WatchLine.Problem.NoFace -> {
            parts += stringResource(R.string.in_garmin_watch_no_face)
        }

        WatchLine.Problem.NotTaking -> {
            parts += stringResource(R.string.in_garmin_watch_not_taking)
        }

        WatchLine.Problem.Stuck -> {
            parts += stringResource(R.string.in_garmin_watch_stuck)
        }

        WatchLine.Problem.Slow -> {
            parts += stringResource(R.string.in_garmin_watch_slow)
        }

        WatchLine.Problem.NoAnswer -> {
            parts += stringResource(R.string.in_garmin_watch_no_answer)
        }

        WatchLine.Problem.Failed -> {
            parts += stringResource(R.string.in_garmin_watch_failed)
        }

        WatchLine.Problem.Waiting -> {
            parts += stringResource(R.string.in_garmin_watch_waiting)
        }

        null -> {}
    }
    return parts.joinToString(" · ")
}

private fun appName(app: GarminApp) =
    when (app) {
        GarminApp.Aura -> R.string.in_garmin_aura
        GarminApp.App -> R.string.in_garmin_app
        GarminApp.Field -> R.string.in_garmin_field
    }

private fun appIcon(app: GarminApp) =
    when (app) {
        GarminApp.Aura -> DsR.drawable.ds_ic_wallpaper
        GarminApp.App -> DsR.drawable.ds_ic_apps
        GarminApp.Field -> DsR.drawable.ds_ic_timer
    }

/** "Watch face · in use, asked this phone at 14:21": what it is, then whether it is there and runs. */
@Composable
private fun appText(
    app: GarminApp,
    line: AppLine,
    now: Instant,
): String {
    val detail =
        when (line) {
            AppLine.NotInstalled -> {
                stringResource(R.string.in_garmin_face_not_installed)
            }

            AppLine.PortBusy -> {
                stringResource(R.string.in_garmin_face_port_busy)
            }

            is AppLine.InUse -> {
                stringResource(
                    if (app ==
                        GarminApp.Aura
                    ) {
                        R.string.in_garmin_face_in_use
                    } else {
                        R.string.in_garmin_app_in_use
                    },
                    since(line.at, now),
                )
            }

            AppLine.Idle -> {
                stringResource(
                    when (app) {
                        GarminApp.Aura -> R.string.in_garmin_face_idle
                        GarminApp.App -> R.string.in_garmin_app_idle
                        GarminApp.Field -> R.string.in_garmin_field_idle
                    },
                )
            }

            AppLine.Unknown -> {
                null
            }
        }
    val kind =
        when (app) {
            GarminApp.Aura -> R.string.in_garmin_face_kind
            GarminApp.App -> R.string.in_garmin_app_kind
            GarminApp.Field -> R.string.in_garmin_field_kind
        }
    return listOfNotNull(stringResource(kind), detail).joinToString(" · ")
}

/** The Garmin row's line on Share with apps. */
@Composable
fun garminStatusText(
    status: AuraStatus,
    now: Instant,
): String =
    when (status) {
        AuraStatus.Off -> {
            stringResource(R.string.in_garmin_off)
        }

        AuraStatus.NoGarminConnect -> {
            stringResource(R.string.in_garmin_no_gcm)
        }

        AuraStatus.NotAnswering -> {
            stringResource(R.string.in_garmin_not_answering)
        }

        AuraStatus.NoWatch -> {
            stringResource(R.string.in_garmin_no_watch_status)
        }

        AuraStatus.NotInstalled -> {
            stringResource(R.string.in_garmin_not_installed)
        }

        is AuraStatus.Away -> {
            stringResource(R.string.in_garmin_away, status.watch)
        }

        is AuraStatus.Sent -> {
            status.reading?.let { stringResource(R.string.in_garmin_sent, status.watch, since(it, now)) }
                ?: stringResource(R.string.in_garmin_sent_no_reading, status.watch)
        }

        is AuraStatus.Fetching -> {
            stringResource(R.string.in_garmin_fetching)
        }

        is AuraStatus.Failing -> {
            stringResource(R.string.in_garmin_failing, status.watch)
        }

        AuraStatus.Waiting -> {
            stringResource(R.string.in_garmin_waiting)
        }
    }

/** Whether the Garmin row's line is a problem the person fixes outside the app (drawn in the error colour). */
fun AuraStatus.isProblem() =
    this is AuraStatus.NotAnswering || this is AuraStatus.Failing || this == AuraStatus.NotInstalled
