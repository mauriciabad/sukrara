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
import androidx.compose.ui.res.pluralStringResource
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
import app.mauri.sukrara.graph.WatchDisplay
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/** A fetch this recent means the app runs and reads this phone (it fetches every 5 min). */
private val FETCH_FRESH: Duration = Duration.ofMinutes(11)

private fun fresh(
    at: Instant?,
    now: Instant,
) = at?.takeIf { Duration.between(it, now) <= FETCH_FRESH }

/**
 * The newest reading [w] is known to have: the last one Garmin Connect delivered to it, or the one its watch face says
 * it keeps (the face profile's report comes with the fetch, which doesn't say which watch asked: it counts only while
 * one watch may have a Sukrara face). When the face [missed] what was delivered, only the face's own counts.
 */
internal fun shows(
    w: GarminWatch,
    s: GarminLinkState,
): Instant? {
    if (missed(w, s) || rejected(w, s)) return s.report?.has
    return listOfNotNull(w.reading, s.report?.has?.takeIf { only(w, s) }).maxOrNull()
}

/** Whether a Sukrara watch face is on this watch (true / false / unknown): any of [GarminApp.faces]. */
internal fun GarminWatch.hasFace(): Boolean? {
    val l = GarminApp.faces.map { has(it) }
    return when {
        true in l -> true
        l.all { it == false } -> false
        else -> null
    }
}

private fun only(
    w: GarminWatch,
    s: GarminLinkState,
) = s.watches.count { it.hasFace() != false } == 1 && w.hasFace() != false

/** The faces' link on [this]: the one Garmin Connect says is there, else the first known (a watch known only by its line: that line). */
private fun GarminWatch.face(): AppLink =
    GarminApp.faces.mapNotNull { link(it) }.let { l -> l.firstOrNull { it.installed == true } ?: l.firstOrNull() }
        ?: AppLink(installed, delivered, failed, noAnswer, reading, failingSince)

/**
 * The face profile's fetch and report count for [face]: no other Sukrara face may be on a watch (the fetch path is the
 * faces' one, so it can't say which face asks).
 */
private fun soleFace(
    face: GarminApp,
    s: GarminLinkState,
) = GarminApp.faces.none { f -> f != face && (s.watches.isEmpty() || s.watches.any { it.has(f) != false }) }

/** [app]'s last fetch: its profile's, a face's only while it is the [soleFace]. */
private fun fetchedBy(
    app: GarminApp,
    s: GarminLinkState,
) = s.fetchedBy[app.profile]?.takeIf { !app.isFace || soleFace(app, s) }

private fun reportOf(
    app: GarminApp,
    s: GarminLinkState,
) = s.reports[app.profile]?.takeIf { !app.isFace || soleFace(app, s) }

/** The face on [w] keeps every message it gets and takes none: it can't read this phone's (it needs updating). */
internal fun rejected(
    w: GarminWatch,
    s: GarminLinkState,
) = only(w, s) && s.report?.rejected == true

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
    w: GarminWatch,
    s: GarminLinkState,
): Boolean {
    val r = s.report ?: return false
    // the delivery made before the report (pushes go every minute, reports every 5: the newest is usually after it)
    val before =
        w
            .face()
            .recent
            .filter { it.at + REPORT_AFTER <= r.at }
            .maxByOrNull { it.at } ?: return false
    val reading = before.reading ?: return false
    return only(w, s) && (r.has ?: Instant.EPOCH) <= reading - BEHIND
}

/**
 * The Garmin link in one line, in the order a person needs it (the row on Share with apps): off; no Garmin Connect;
 * Garmin Connect not answering; no watch in it; the face on no watch; the watch away; a watch has a reading; messages
 * lost but the face fetching every 5 min; messages lost and nothing fetched; waiting for the first one.
 */
sealed interface GarminStatus {
    data object Off : GarminStatus

    data object NoGarminConnect : GarminStatus

    data object NotAnswering : GarminStatus

    data object NoWatch : GarminStatus

    data object NotInstalled : GarminStatus

    /** No watch with the face is connected. */
    data class Away(
        val watch: String,
    ) : GarminStatus

    /** [watch] got the last message; [reading] = the newest reading it has (null = it carried none). */
    data class Sent(
        val watch: String,
        val reading: Instant?,
    ) : GarminStatus

    /** Garmin Connect loses the messages, but the face fetched at [at] (it keeps up every 5 min). */
    data class Fetching(
        val at: Instant,
    ) : GarminStatus

    /** Messages don't arrive and the face hasn't fetched lately. */
    data class Failing(
        val watch: String,
    ) : GarminStatus

    data object Waiting : GarminStatus

    /** The face on [watch] can't read this phone's messages: it needs updating. */
    data class Outdated(
        val watch: String,
    ) : GarminStatus

    companion object {
        fun of(
            s: GarminLinkState,
            now: Instant,
        ): GarminStatus {
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
                    val outdated = near.firstOrNull { rejected(it, s) }
                    when {
                        outdated != null -> Outdated(outdated.name)
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
private fun GarminWatch.delivered() = delivered != null && (failed == null || failed < delivered)

/** One watch's line on the Garmin page: connected or not, the newest reading it has, and what goes wrong. */
data class WatchLine(
    val connected: Boolean,
    val reading: Instant?,
    val problem: Problem?,
) {
    enum class Problem {
        /** No Sukrara app is installed on it. */
        NoFace,

        /** The face keeps every message it gets and takes none: it can't read this phone's, it needs updating. */
        Outdated,

        /** The watch takes the messages but the face doesn't ([missed]): restart the watch (#244). */
        NotTaking,

        /** No message has got through for 15 min ([GarminWatch.stuck]): restarting the watch brings them back (#242). */
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
            w: GarminWatch,
            s: GarminLinkState,
            now: Instant,
        ): WatchLine {
            val reading = shows(w, s)
            val problem =
                when {
                    w.installed == false -> Problem.NoFace
                    !w.connected -> null
                    rejected(w, s) -> Problem.Outdated
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

    /** It keeps every message it gets and takes none: it can't read this phone's, it needs updating. */
    data object Outdated : AppLine

    /** Another app holds the port: only Garmin Connect's messages reach it. */
    data object PortBusy : AppLine

    /** It asked this phone at [at]: it runs. */
    data class InUse(
        val at: Instant,
    ) : AppLine

    /** It hasn't asked lately, but Garmin Connect said this phone's message reached it at [at]. */
    data class Got(
        val at: Instant,
    ) : AppLine

    /** On a watch, but it hasn't asked this phone lately. */
    data object Idle : AppLine

    /** Garmin Connect hasn't said yet. */
    data object Unknown : AppLine

    companion object {
        fun of(
            app: GarminApp,
            s: GarminLinkState,
            now: Instant,
        ): AppLine {
            val fetched = fresh(fetchedBy(app, s), now)
            return when {
                s.watches.isNotEmpty() && s.watches.all { it.has(app) == false } && fetched == null -> NotInstalled
                s.portBusy -> PortBusy
                fetched != null -> InUse(fetched)
                s.watches.any { it.has(app) == true } -> Idle
                else -> Unknown
            }
        }

        /**
         * [app] on watch [w]: the watch app's fetch says which watch asks (`w`); the face's and the field's count only
         * while [w] is the one watch that may have it.
         */
        fun on(
            app: GarminApp,
            w: GarminWatch,
            s: GarminLinkState,
            now: Instant,
        ): AppLine {
            val only = w.has(app) != false && s.watches.count { it.has(app) != false } == 1
            val asked =
                fresh(
                    if (app == GarminApp.App) {
                        s.appFetchedOn[w.id] ?: fetchedBy(app, s)?.takeIf { only }
                    } else {
                        fetchedBy(app, s)?.takeIf { only }
                    },
                    now,
                )
            val got = w.link(app)?.delivered?.takeIf { w.has(app) != false }
            return when {
                w.has(app) == false && asked == null -> NotInstalled
                only && reportOf(app, s)?.rejected == true -> Outdated
                s.portBusy -> PortBusy
                asked != null -> InUse(asked)
                got != null -> Got(got)
                w.has(app) == true -> Idle
                else -> Unknown
            }
        }
    }
}

/** What the Garmin page shows: Sukrara's own link, and the xDrip+ server for other faces and data fields. */
data class GarminUi(
    val link: GarminLinkState,
    val xdrip: XdripState = XdripState.Off,
    val readers: List<ServerReader> = emptyList(),
    /** Each watch's own settings for the watch app (a watch not listed follows the app), and its name when they were set. */
    val own: Map<Long, WatchDisplay> = emptyMap(),
    val ownNames: Map<Long, String> = emptyMap(),
)

class GarminViewModel(
    private val link: GarminLink,
    xdrip: XdripServer,
    context: Context,
    io: CoroutineDispatcher,
    watches: WatchSettingsStore? = null,
) : ViewModel() {
    private val app = context.applicationContext
    private val readers = MutableStateFlow<List<ServerReader>>(emptyList())
    private val own = watches?.displays ?: flowOf(emptyMap())
    private val names = watches?.names ?: flowOf(emptyMap())
    val ui: StateFlow<GarminUi> =
        combine(combine(link.state, xdrip.state, readers, ::Triple), own, names) { (l, x, r), o, n -> GarminUi(l, x, r, o, n) }
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
    /** A watch's *Sukrara* settings (its id and name). */
    val openWatch: (Long, String) -> Unit = { _, _ -> },
)

@Composable
fun GarminPage(
    viewModel: GarminViewModel,
    now: () -> Instant,
    openXdrip: () -> Unit,
    openWatch: (Long, String) -> Unit,
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
            openWatch,
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
 * reading it has as a clock time, what goes wrong); *Sukrara on your watch* (the watch app and the data field: each on
 * a watch or not, and whether it asks this phone); *Watch faces* (each of Sukrara's faces, the same way); and the xDrip+
 * server for other faces and data fields, which has its own switch and page.
 */
@Composable
fun GarminContent(
    ui: GarminUi,
    now: Instant,
    actions: GarminActions,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    val s = ui.link
    val status = GarminStatus.of(s, now)
    SettingsIntro(stringResource(R.string.in_garmin_intro))
    MainSwitchRow(
        stringResource(R.string.in_garmin_switch),
        s.on,
        actions.setOn,
        note =
            when (status) {
                GarminStatus.Off -> stringResource(R.string.in_garmin_off_note)
                GarminStatus.NoGarminConnect -> stringResource(R.string.in_garmin_no_gcm)
                GarminStatus.NotAnswering -> stringResource(R.string.in_garmin_not_answering)
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
            // the watch app rings each alert itself, and Garmin Connect may forward the phone's too (SPEC §13 decided
            // 2): only who feels both acts on it (5.0 watches, and some 5.x ones, rely on Garmin Connect's)
            if (s.watches.any { it.has(GarminApp.App) == true }) FooterText(stringResource(R.string.in_garmin_two_buzz))
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
            // Sukrara's own Connect IQ apps per watch (one watch: Sukrara on your watch): the watch app (it opens that
            // watch's settings) and the data field, then the watch faces, a list of one kind (no icons). With several
            // watches each lists the same apps: no icons either. While Garmin Connect hasn't said which watches it
            // knows, nothing is known about them
            val apps = GarminApp.entries.filterNot { it.isFace }
            val icons = s.watches.size <= 1
            if (s.watches.isEmpty() && s.answering != false) {
                SettingsGroup(title = stringResource(R.string.in_garmin_sukrara_group)) {
                    for (app in apps) {
                        InfoRow(stringResource(appName(app)), summary = appText(app, AppLine.of(app, s, now), now), icon = appIcon(app))
                    }
                }
                SettingsGroup(title = stringResource(R.string.in_garmin_faces_group)) {
                    for (face in GarminApp.faces) {
                        InfoRow(stringResource(appName(face)), summary = appText(face, AppLine.of(face, s, now), now))
                    }
                }
            }
            for (w in s.watches) {
                val one = s.watches.size == 1
                val appsTitle =
                    if (one) {
                        stringResource(
                            R.string.in_garmin_sukrara_group,
                        )
                    } else {
                        stringResource(R.string.in_garmin_sukrara_on, w.name)
                    }
                SettingsGroup(title = appsTitle) {
                    for (app in apps) {
                        val text = appText(app, AppLine.on(app, w, s, now), now)
                        val icon = appIcon(app).takeIf { icons }
                        if (app == GarminApp.App) {
                            NavigationRow(
                                stringResource(appName(app)),
                                { actions.openWatch(w.id, w.name) },
                                summary = listOfNotNull(text, ownText(ui.own[w.id])).joinToString("\n"),
                                icon = icon,
                            )
                        } else {
                            InfoRow(stringResource(appName(app)), summary = text, icon = icon)
                        }
                    }
                }
                val facesTitle =
                    if (one) {
                        stringResource(
                            R.string.in_garmin_faces_group,
                        )
                    } else {
                        stringResource(R.string.in_garmin_faces_on, w.name)
                    }
                SettingsGroup(title = facesTitle) {
                    for (face in GarminApp.faces) {
                        InfoRow(stringResource(appName(face)), summary = appText(face, AppLine.on(face, w, s, now), now))
                    }
                }
            }
            // a watch Garmin Connect no longer knows that kept its own settings: its row lets them go (once it answered)
            if (s.answering == true) {
                for ((id, d) in ui.own.filterKeys { id -> s.watches.none { it.id == id } }) {
                    val name = ui.ownNames[id] ?: stringResource(R.string.in_garmin_watch_unnamed)
                    SettingsGroup(title = stringResource(R.string.in_garmin_sukrara_on, name)) {
                        NavigationRow(
                            stringResource(appName(GarminApp.App)),
                            { actions.openWatch(id, name) },
                            summary = "${stringResource(R.string.in_garmin_watch_gone)}\n${ownText(d)}",
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

        WatchLine.Problem.Outdated -> {
            parts += stringResource(R.string.in_garmin_watch_outdated)
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
    when (app.profile) {
        WatchProfile.Face -> null
        WatchProfile.App -> DsR.drawable.ds_ic_apps
        WatchProfile.Field -> DsR.drawable.ds_ic_timer
    }

/** "Settings: same as the app" / "Settings: 3 of its own": the watch app's own settings on this watch. */
@Composable
private fun ownText(d: WatchDisplay?): String =
    d?.ownCount?.takeIf { it > 0 }?.let { n -> pluralStringResource(R.plurals.in_garmin_watch_own, n, n) }
        ?: stringResource(R.string.in_garmin_watch_same)

/**
 * "Data field · hasn't asked this phone lately": what it is (a face's group says it), then whether it is there and runs;
 * null = nothing to say.
 */
@Composable
private fun appText(
    app: GarminApp,
    line: AppLine,
    now: Instant,
): String? {
    val detail =
        when (line) {
            AppLine.NotInstalled -> {
                stringResource(R.string.in_garmin_face_not_installed)
            }

            AppLine.PortBusy -> {
                stringResource(R.string.in_garmin_face_port_busy)
            }

            AppLine.Outdated -> {
                stringResource(R.string.in_garmin_app_outdated)
            }

            is AppLine.InUse -> {
                stringResource(if (app.isFace) R.string.in_garmin_face_in_use else R.string.in_garmin_app_in_use, since(line.at, now))
            }

            AppLine.Idle -> {
                stringResource(
                    when (app.profile) {
                        WatchProfile.Face -> R.string.in_garmin_face_idle
                        WatchProfile.App -> R.string.in_garmin_app_idle
                        WatchProfile.Field -> R.string.in_garmin_field_idle
                    },
                )
            }

            is AppLine.Got -> {
                stringResource(R.string.in_garmin_app_got, since(line.at, now))
            }

            AppLine.Unknown -> {
                null
            }
        }
    // a face's group already says what it is
    val kind =
        when (app.profile) {
            WatchProfile.Face -> null
            WatchProfile.App -> R.string.in_garmin_app_kind
            WatchProfile.Field -> R.string.in_garmin_field_kind
        }
    return listOfNotNull(kind?.let { stringResource(it) }, detail).joinToString(" · ").ifEmpty { null }?.replaceFirstChar { it.uppercase() }
}

/** The Garmin row's line on Share with apps. */
@Composable
fun garminStatusText(
    status: GarminStatus,
    now: Instant,
): String =
    when (status) {
        GarminStatus.Off -> {
            stringResource(R.string.in_garmin_off)
        }

        GarminStatus.NoGarminConnect -> {
            stringResource(R.string.in_garmin_no_gcm)
        }

        GarminStatus.NotAnswering -> {
            stringResource(R.string.in_garmin_not_answering)
        }

        GarminStatus.NoWatch -> {
            stringResource(R.string.in_garmin_no_watch_status)
        }

        GarminStatus.NotInstalled -> {
            stringResource(R.string.in_garmin_not_installed)
        }

        is GarminStatus.Away -> {
            stringResource(R.string.in_garmin_away, status.watch)
        }

        is GarminStatus.Sent -> {
            status.reading?.let { stringResource(R.string.in_garmin_sent, status.watch, since(it, now)) }
                ?: stringResource(R.string.in_garmin_sent_no_reading, status.watch)
        }

        is GarminStatus.Fetching -> {
            stringResource(R.string.in_garmin_fetching)
        }

        is GarminStatus.Failing -> {
            stringResource(R.string.in_garmin_failing, status.watch)
        }

        GarminStatus.Waiting -> {
            stringResource(R.string.in_garmin_waiting)
        }

        is GarminStatus.Outdated -> {
            stringResource(R.string.in_garmin_outdated, status.watch)
        }
    }

/** Whether the Garmin row's line is a problem the person fixes outside the app (drawn in the error colour). */
fun GarminStatus.isProblem() =
    this is GarminStatus.NotAnswering || this is GarminStatus.Failing || this == GarminStatus.NotInstalled || this is GarminStatus.Outdated
