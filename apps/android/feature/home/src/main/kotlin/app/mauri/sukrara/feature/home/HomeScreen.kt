// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import android.text.format.DateUtils
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mauri.sukrara.alerts.MuteLine
import app.mauri.sukrara.core.model.AlertQuietSide
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.core.model.PenAnswer
import app.mauri.sukrara.core.model.PenEntryInfo
import app.mauri.sukrara.core.model.PenQuestionInfo
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.designsystem.alerts.MuteAllState
import app.mauri.sukrara.designsystem.alerts.MuteText
import app.mauri.sukrara.designsystem.alerts.QuietExits
import app.mauri.sukrara.designsystem.alerts.alertA11yActions
import app.mauri.sukrara.designsystem.alerts.rememberAlertWords
import app.mauri.sukrara.designsystem.avatar.AvatarImage
import app.mauri.sukrara.designsystem.banner.Banner
import app.mauri.sukrara.designsystem.banner.BannerChoice
import app.mauri.sukrara.designsystem.banner.BannerSeverity
import app.mauri.sukrara.designsystem.banner.BannerSlot
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.motion.Reveal
import app.mauri.sukrara.designsystem.motion.Swap
import app.mauri.sukrara.designsystem.pen.PenEntrySheet
import app.mauri.sukrara.designsystem.pen.PenQuestionCard
import app.mauri.sukrara.designsystem.rows.rememberCopyAlertReport
import app.mauri.sukrara.designsystem.state.StateKind
import app.mauri.sukrara.designsystem.state.StateScreen
import app.mauri.sukrara.feature.home.banners.BannerKind
import app.mauri.sukrara.graph.A11y
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.Clock
import app.mauri.sukrara.graph.TreatmentMarks
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import app.mauri.sukrara.core.designsystem.R as DsR

/** The home screen: [onAction] routes what home asks for (setup, sensor, settings, add treatment, …) in `:app`. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAction: (HomeAction) -> Unit,
    onOpenDataProblems: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pen by viewModel.penSheet.collectAsStateWithLifecycle()
    val penNotice by viewModel.penNotice.collectAsStateWithLifecycle()
    val penQuestions by viewModel.penQuestions.collectAsStateWithLifecycle()
    val penRead by viewModel.penRead.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        pen = pen,
        penNotice = penNotice,
        penQuestions = penQuestions,
        penRead = penRead,
        callbacks =
            HomeCallbacks(
                onAction = onAction,
                onDismiss = viewModel::dismiss,
                onRestore = viewModel::restore,
                onFix = viewModel::fix,
                onAlert = viewModel::alert,
                onDelete = viewModel::delete,
                onUndo = viewModel::undoDelete,
                onUndoGone = viewModel::undoShown,
                onRetry = viewModel::retry,
                onOpenDataProblems = onOpenDataProblems,
                pen =
                    PenCallbacks(
                        open = viewModel::openPen,
                        close = viewModel::closePen,
                        save = viewModel::correctPen,
                        delete = viewModel::deletePen,
                        undo = viewModel::undoPen,
                        shown = viewModel::penNoticeShown,
                        answer = viewModel::answerPen,
                    ),
            ),
        modifier = modifier,
    )
}

/** Everything the screen can ask for (one object so the stateless [HomeContent] stays testable). */
data class HomeCallbacks(
    val onAction: (HomeAction) -> Unit = {},
    val onDismiss: (BannerKind) -> Unit = {},
    /** Undo on the "Tip closed" snackbar. */
    val onRestore: (BannerKind) -> Unit = {},
    val onFix: (AlertRequirement) -> Unit = {},
    /** Mute alerts, Unmute, a side's Alert me anyway / Remind as usual / Change to (the alert runtime). */
    val onAlert: (HomeAlertAction) -> Unit = {},
    val onDelete: (Treatment) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onUndoGone: () -> Unit = {},
    /** *Retry* on an entry nothing took (a writer's *Not sent*). */
    val onRetry: (Treatment) -> Unit = {},
    val onOpenDataProblems: () -> Unit = {},
    val pen: PenCallbacks = PenCallbacks(),
)

/**
 * A pen row's sheet: open it, close it, *Save* the doses' roles, *Delete*, the snackbar's *Undo* and its end; and the
 * question card's answers.
 */
data class PenCallbacks(
    val open: (Treatment) -> Unit = {},
    val close: () -> Unit = {},
    val save: (List<Boolean>) -> Unit = {},
    val delete: () -> Unit = {},
    val undo: (PenEntryInfo) -> Unit = {},
    val shown: (PenNotice) -> Unit = {},
    val answer: (PenQuestionInfo, PenAnswer) -> Unit = { _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
    pen: PenSheet? = null,
    penNotice: PenNotice? = null,
    penQuestions: List<PenQuestionInfo> = emptyList(),
    penRead: PenRead? = null,
) {
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val deletedText = stringResource(if (state.undoInClinic) R.string.home_treatment_deleted_clinic else R.string.home_treatment_deleted)
    val undoText = stringResource(R.string.home_undo)
    LaunchedEffect(state.undo) {
        if (state.undo == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(deletedText, undoText, withDismissAction = true, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) {
            haptics.confirm()
            callbacks.onUndo()
        } else {
            callbacks.onUndoGone()
        }
    }
    val penTexts =
        mapOf(
            PenNotice.Kind.Changed to stringResource(R.string.home_pen_changed),
            PenNotice.Kind.Removed to stringResource(R.string.home_pen_removed),
            PenNotice.Kind.ChangedSince to stringResource(R.string.home_pen_changed_since),
            PenNotice.Kind.UndoFailed to stringResource(R.string.home_pen_undo_failed),
        )
    LaunchedEffect(penNotice) {
        val notice = penNotice ?: return@LaunchedEffect
        val result =
            snackbar.showSnackbar(
                penTexts.getValue(notice.kind),
                actionLabel = if (notice.undo != null) undoText else null,
                withDismissAction = true,
                duration = if (notice.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        callbacks.pen.shown(notice)
        if (result == SnackbarResult.ActionPerformed) {
            haptics.confirm()
            notice.undo?.let(callbacks.pen.undo)
        }
    }
    val follower = state.glucose.role == Role.Follower
    // Mute alerts' lengths: from the home control, its Change time, the quiet sheet and the card's TalkBack action
    var muteDialog by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val closedText = stringResource(R.string.home_tip_closed)
    val shownBanner =
        banner(state, callbacks) { kind ->
            haptics.confirm()
            // every close can be undone here; Settings › Show closed tips brings them back later
            scope.launch {
                val result = snackbar.showSnackbar(closedText, undoText, withDismissAction = true, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) {
                    haptics.confirm()
                    callbacks.onRestore(kind)
                }
            }
        }
    // a safety banner sits right under the card (seen without scrolling); a tip goes below the glucose block
    val safetyBanner = shownBanner?.takeIf { it.severity == BannerSeverity.Safety }
    val infoBanner = shownBanner?.takeIf { it.severity == BannerSeverity.Info }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    when {
                        follower -> FollowerHeader(state)
                        state.profile != null -> ProfileHeader(state.profile) { callbacks.onAction(HomeAction.OpenProfile) }
                        else -> Text(stringResource(R.string.home_title))
                    }
                },
                actions = {
                    if (!follower && state.profile != null) {
                        IconButton(onClick = { callbacks.onAction(HomeAction.Share) }) {
                            Icon(painterResource(R.drawable.home_ic_share), contentDescription = stringResource(R.string.home_share))
                        }
                    }
                    IconButton(onClick = { callbacks.onAction(HomeAction.OpenSettings) }) {
                        Icon(painterResource(R.drawable.home_ic_settings), contentDescription = stringResource(R.string.home_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.glucose.role != null) {
                // icon-only while a safety banner shows or the page is scrolled, so it never covers what matters
                val atTop by remember { derivedStateOf { scroll.value == 0 } }
                val expanded = safetyBanner == null && atTop
                val label = stringResource(R.string.home_add_treatment)
                ExtendedFloatingActionButton(
                    onClick = { callbacks.onAction(HomeAction.AddTreatment) },
                    expanded = expanded,
                    icon = { Icon(painterResource(R.drawable.home_ic_add), contentDescription = if (expanded) null else label) },
                    text = { Text(label) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            HomeColumn(state, callbacks, scroll, safetyBanner, infoBanner, viewport = maxHeight, penQuestions, penRead) {
                muteDialog = true
            }
        }
    }
    pen?.let { p ->
        PenEntrySheet(
            p.entry,
            { Clock.time(it) },
            onSave = if (p.editable) callbacks.pen.save else null,
            onDismiss = callbacks.pen.close,
            onDelete = if (p.deletable) callbacks.pen.delete else null,
        )
    }
    if (muteDialog) {
        MuteAllDialog(onClose = { muteDialog = false }) { callbacks.onAlert(HomeAlertAction.MuteAll(it)) }
    }
}

/** Home's scrolling column; [viewport] = its visible height (a safety banner under the card must fit in it). */
@Composable
private fun HomeColumn(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    scroll: ScrollState,
    safetyBanner: Banner?,
    infoBanner: Banner?,
    viewport: Dp,
    penQuestions: List<PenQuestionInfo>,
    penRead: PenRead?,
    onMuteAll: () -> Unit,
) {
    val haptics = rememberHaptics()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // glucose first: the card (or, before any reading, why there is none) is always at the top
        GlucoseSection(state, callbacks, safetyBanner, viewport, onMuteAll)
        // a pen dose waiting to be named sits right under the card (never above it: glucose stays first)
        PenQuestionCard(penQuestions, { Clock.time(it) }, callbacks.pen.answer, Modifier.padding(horizontal = 16.dp))
        BannerSlot(infoBanner)
        MuteControl(state, callbacks.onAlert, onMuteAll)
        Treatments(
            state,
            { callbacks.onAction(HomeAction.OpenClinicUpload) },
            callbacks.pen.open,
            onEdit = { callbacks.onAction(HomeAction.EditTreatment(it.id)) },
            onRetry = {
                haptics.confirm()
                callbacks.onRetry(it)
            },
            penRead = penRead,
            openPenPage = { callbacks.onAction(HomeAction.OpenPen(it)) },
        ) {
            haptics.confirm()
            callbacks.onDelete(it)
        }
        if (state.history.isNotEmpty()) ReportCard(state.history, state.now, state.config.bands)
        // room for the floating button over the last row
        Spacer(Modifier.height(88.dp))
    }
}

/**
 * The wearer as followers see them: picture, name (or "Add your name") and how many follow. Tapping it opens How you
 * appear; it never pushes the card down (it is the top bar's title).
 */
@Composable
private fun ProfileHeader(
    profile: HomeProfile,
    onClick: () -> Unit,
) {
    val name = profile.name.ifBlank { null }
    Row(
        Modifier
            .clip(
                MaterialTheme.shapes.medium,
            ).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarImage(profile.avatar, name.orEmpty(), size = 36.dp)
        Column {
            Text(
                name ?: stringResource(R.string.home_add_your_name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (name == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                pluralStringResource(R.plurals.home_followers, profile.followers, profile.followers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Who is followed and how the link is, from the same decision as the card's reason: fresh = when last heard ("just
 * now", "2 minutes ago"); silent = since when nothing came, in the error colour; removed = no longer following.
 */
@Composable
private fun FollowerHeader(state: HomeUiState) {
    val link = state.glucose.link
    val name = link?.wearerName
    Column {
        Text(
            when {
                // removed: just the name, so the title never says "Following" over "No longer following"
                state.reason == NoDataReason.LinkRevoked -> name ?: stringResource(R.string.home_following_unnamed)

                name != null -> stringResource(R.string.home_following, name)

                else -> stringResource(R.string.home_following_unnamed)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val (line, trouble) =
            when (val reason = state.reason) {
                NoDataReason.LinkRevoked -> {
                    stringResource(R.string.home_link_revoked) to true
                }

                is NoDataReason.WearerSilent -> {
                    val since = reason.since
                    when {
                        since == null -> stringResource(R.string.home_link_never)
                        name == null -> stringResource(R.string.home_link_silent_unnamed, sinceTime(since, state.now))
                        else -> stringResource(R.string.home_link_silent, name, sinceTime(since, state.now))
                    } to true
                }

                else -> {
                    (
                        link?.lastHeardAt?.let { stringResource(R.string.home_link_heard, relative(it, state.now)) }
                            ?: stringResource(R.string.home_link_never)
                    ) to
                        false
                }
            }
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = if (trouble) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The card, always (its chart keeps the last trajectory and the renderer draws the stale / warm-up / ended face), then
 * a safety banner, then the compact reason with its action. Only before there is anything to draw (not set up, no
 * sensor yet, no core) a full-width reason takes the card's place; that swap cross-fades (reduced motion: snaps).
 * While a safety banner shows, the card gives up height (down to [MIN_CARD_HEIGHT], easing) so the banner's action is
 * on screen without scrolling in [viewport], at any font size.
 */
@Composable
private fun GlucoseSection(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    safetyBanner: Banner?,
    viewport: Dp,
    onMuteAll: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val density = LocalDensity.current
    val reduced = LocalReducedMotion.current
    val canMute = state.glucose.role != null
    // what sits between the card and the safety banner's bottom edge (the quiet chips and the banner), px
    var under by remember { mutableIntStateOf(0) }
    val fit =
        if (safetyBanner == null) {
            CARD_HEIGHT
        } else {
            (viewport - with(density) { under.toDp() } - SECTION_GAP).coerceIn(MIN_CARD_HEIGHT, CARD_HEIGHT)
        }
    val cardHeight by animateDpAsState(fit, Motion.defaultSpatial(reduced), label = "card height")
    Swap(state.reason?.takeIf { it.fullPage }) { fullPage ->
        if (fullPage != null) {
            Column {
                ReasonPage(fullPage, state.now) { callbacks.onAction(it) }
                // safety banners show in every state (e.g. alerts that may not ring before the first sensor)
                BannerSlot(safetyBanner)
            }
        } else {
            Column {
                val pill = pillAction(state)
                var sheet by remember { mutableStateOf<QuietTarget?>(null) }
                val a11y =
                    alertA11yActions(
                        state.glucose.alerts,
                        muted = state.mutedUntil != null,
                        onExit = { q, e -> callbacks.onAlert(HomeAlertAction.exit(q, e)) },
                        onUnmuteAll = { callbacks.onAlert(HomeAlertAction.UnmuteAll) },
                        onMuteAll = onMuteAll,
                    )
                PanelCard(
                    glucose = state.glucose,
                    history = state.history,
                    treatments = state.chartTreatments,
                    now = state.now,
                    config = state.config,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    actions = if (canMute) a11y else emptyList(),
                    pill = pill,
                    pillWords = pillWords(state, resources),
                    onPill = { action ->
                        when (action) {
                            is PillAction.Treat -> callbacks.onAction(HomeAction.Treat(carbs = action.carbs))
                            PillAction.ReplaceSensor -> callbacks.onAction(HomeAction.PairSensor)
                            is PillAction.Quiet, PillAction.MuteAll -> sheet = QuietTarget.of(action)
                            PillAction.None -> Unit
                        }
                    },
                    height = cardHeight,
                )
                Column(Modifier.onSizeChanged { under = it.height }) {
                    QuietChips(QuietTarget.chips(state.glucose.alerts, pill), state.now) { sheet = it }
                    BannerSlot(safetyBanner)
                }
                sheet?.let { shown ->
                    QuietSheet(
                        target = shown,
                        alerts = state.glucose.alerts,
                        muted = state.mutedUntil != null,
                        now = state.now,
                        onDismiss = { sheet = null },
                        onAlert = callbacks.onAlert,
                        onChangeMute = onMuteAll,
                        onSettings = {
                            sheet = null
                            callbacks.onAction(HomeAction.OpenAlertSettings)
                        },
                    )
                }
                // Monitoring stopped already has its safety banner (banner 0): never say it twice
                val compact =
                    state.reason?.takeUnless {
                        it == NoDataReason.MonitoringStopped &&
                            state.banner == BannerKind.MonitoringStopped
                    }
                ReasonRow(compact, state.now) { callbacks.onAction(it) }
                UntestedSensorCard(state.untestedSensor)
                SensorLine(state) { callbacks.onAction(HomeAction.PairSensor) }
            }
        }
    }
}

/**
 * When the sensor ends; in its last day (the wearer) *Pair new sensor* as a tonal button under it, so the new one can
 * be put on before the old one stops. In a new sensor's first hours ([EarlyReadings]) a note says its readings can be
 * less accurate until when, and what to do; it leaves by itself.
 */
@Composable
private fun SensorLine(
    state: HomeUiState,
    onPair: () -> Unit,
) {
    val ends = (state.glucose.sensor as? SensorState.Active)?.endsAt ?: return
    val left = Duration.between(state.now, ends).seconds
    val early = EarlyReadings.until(state.glucose.sensor, state.now)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Reveal(early != null) {
            // keeps its last text while it slides away
            var shown by remember { mutableStateOf("") }
            if (early != null) shown = stringResource(R.string.home_sensor_early, clockTime(early))
            Text(
                shown,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp).testTag("home-sensor-early"),
            )
        }
        Text(
            if (left > 0) {
                // partial minutes up: never "in 0 min" while it still runs
                stringResource(R.string.home_sensor_ends, spanText((left + 59) / 60))
            } else {
                stringResource(R.string.home_sensor_ended, spanText((-left / 60).coerceAtLeast(1)))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Reveal(lastDay(state)) {
            FilledTonalButton(onClick = onPair, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                Text(stringResource(R.string.home_pair_new_sensor))
            }
        }
    }
}

/** The wearer's sensor ends within a day: home offers *Pair new sensor*. */
internal fun lastDay(state: HomeUiState): Boolean {
    val ends = (state.glucose.sensor as? SensorState.Active)?.endsAt ?: return false
    return state.glucose.role == Role.Wearer && Duration.between(state.now, ends) <= Duration.ofDays(1)
}

/** What the card's pill does for [state] (only the wearer pairs a sensor; anyone with a role may add a treatment). */
internal fun pillAction(state: HomeUiState): PillAction {
    // the card draws *Replace sensor* there: only the wearer can act on it
    if (state.glucose.sensor == SensorState.Ended && state.glucose.role != Role.Wearer) return PillAction.None
    return PillAction.of(
        state.glucose.alerts.status,
        state.now.epochSecond,
        canAdd = state.glucose.role != null,
        ended = state.glucose.sensor == SensorState.Ended,
        muteAll = state.mutedUntil != null,
    )
}

/** TalkBack's words for the pill: what it shows, as the card's sentence says it ([A11y.status]). */
internal fun pillWords(
    state: HomeUiState,
    resources: android.content.res.Resources,
): String {
    val words = CardWords.of(resources)
    if (state.glucose.sensor == SensorState.Ended) return words.replaceSensor
    return A11y.status(state.glucose.alerts.status, state.now.epochSecond, words).orEmpty()
}

/** Before there is anything to draw: the reason in the card's place, announced politely. */
@Composable
private fun ReasonPage(
    reason: NoDataReason,
    now: Instant,
    onAction: (HomeAction) -> Unit,
) {
    val text = reasonText(reason, now)
    StateScreen(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        kind = StateKind.NoData,
        title = text.first,
        reason = text.second,
        action = reason.action?.let { actionLabel(reason) },
        onAction = { reason.action?.let(onAction) },
    )
}

/**
 * The reason under the card: title, one explanation, the action. No icon: the card above already draws
 * `sensors_off`, the warm-up ring or the flag. Appears / leaves with the shared reveal motion; announced politely.
 */
@Composable
private fun ReasonRow(
    reason: NoDataReason?,
    now: Instant,
    onAction: (HomeAction) -> Unit,
) {
    // keep the last reason while it animates out
    var shown by remember { mutableStateOf(reason) }
    if (reason != null) shown = reason
    Reveal(reason != null) {
        val r = shown ?: return@Reveal
        val text = reasonText(r, now)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(text.first, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(text.second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            r.action?.let { action ->
                FilledTonalButton(onClick = { onAction(action) }, modifier = Modifier.align(Alignment.End).padding(top = 4.dp)) {
                    Text(actionLabel(r))
                }
            }
        }
    }
}

/** Title and explanation for [reason]. */
@Composable
private fun reasonText(
    reason: NoDataReason,
    now: Instant,
): Pair<String, String> =
    when (reason) {
        NoDataReason.SetupIncomplete -> {
            stringResource(R.string.reason_setup_title) to stringResource(R.string.reason_setup_body)
        }

        is NoDataReason.NoSensorYet -> {
            if (reason.follower) {
                stringResource(R.string.reason_no_wearer_title) to stringResource(R.string.reason_no_wearer_body)
            } else {
                stringResource(R.string.reason_no_sensor_title) to stringResource(R.string.reason_no_sensor_body)
            }
        }

        NoDataReason.CoreMissing -> {
            stringResource(R.string.reason_core_missing_title) to stringResource(R.string.reason_core_missing_body)
        }

        NoDataReason.BluetoothOff -> {
            stringResource(R.string.reason_bluetooth_title) to stringResource(R.string.reason_bluetooth_body)
        }

        NoDataReason.MonitoringStopped -> {
            stringResource(R.string.reason_stopped_title) to stringResource(R.string.reason_stopped_body)
        }

        NoDataReason.UsedByAnotherApp -> {
            stringResource(R.string.reason_taken_over_title) to stringResource(R.string.reason_taken_over_body)
        }

        NoDataReason.ScanNeeded -> {
            stringResource(R.string.reason_scan_title) to stringResource(R.string.reason_scan_body)
        }

        NoDataReason.PairAgain -> {
            stringResource(R.string.reason_pair_again_title) to stringResource(R.string.reason_pair_again_body)
        }

        NoDataReason.SensorEnded -> {
            stringResource(R.string.reason_ended_title) to stringResource(R.string.reason_ended_body)
        }

        is NoDataReason.ClockBehind -> {
            stringResource(R.string.reason_clock_title) to stringResource(R.string.reason_clock_body, spanText(reason.ahead.toMinutes()))
        }

        is NoDataReason.WarmingUp -> {
            stringResource(R.string.reason_warmup_title) to stringResource(R.string.reason_warmup_body, clockTime(reason.readyAt))
        }

        NoDataReason.LinkRevoked -> {
            stringResource(R.string.reason_revoked_title) to stringResource(R.string.reason_revoked_body)
        }

        is NoDataReason.WearerSilent -> {
            stringResource(R.string.reason_wearer_silent_title) to
                (
                    reason.since?.let { stringResource(R.string.reason_wearer_silent_body, relative(it, now)) }
                        ?: stringResource(R.string.reason_wearer_silent_never)
                )
        }

        is NoDataReason.NoReadings -> {
            stringResource(R.string.reason_no_readings_title) to
                (
                    reason.since?.let { stringResource(R.string.reason_no_readings_body, relative(it, now)) }
                        ?: stringResource(R.string.reason_no_readings_never)
                )
        }
    }

@Composable
private fun actionLabel(reason: NoDataReason): String =
    stringResource(
        when (reason) {
            NoDataReason.SetupIncomplete -> R.string.reason_setup_action
            is NoDataReason.NoSensorYet -> if (reason.follower) R.string.reason_no_wearer_action else R.string.reason_no_sensor_action
            NoDataReason.BluetoothOff -> R.string.reason_bluetooth_action
            NoDataReason.MonitoringStopped -> R.string.reason_stopped_action
            NoDataReason.UsedByAnotherApp -> R.string.reason_taken_over_action
            NoDataReason.ScanNeeded -> R.string.reason_scan_action
            NoDataReason.PairAgain -> R.string.reason_pair_again_action
            is NoDataReason.ClockBehind -> R.string.reason_clock_action
            NoDataReason.SensorEnded -> R.string.reason_ended_action
            NoDataReason.LinkRevoked -> R.string.reason_revoked_action
            else -> R.string.reason_no_readings_action
        },
    )

/** The one banner for [state] (or null), with its texts and exits. */
@Composable
private fun banner(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    onDismiss: (BannerKind) -> Unit,
): Banner? {
    val kind = state.banner ?: return null
    val dismiss: (() -> Unit)? =
        if (kind.safety) {
            null
        } else {
            {
                callbacks.onDismiss(kind)
                onDismiss(kind)
            }
        }

    fun open(action: HomeAction): () -> Unit = { callbacks.onAction(action) }
    val severity = if (kind.safety) BannerSeverity.Safety else BannerSeverity.Info
    return when (kind) {
        BannerKind.MonitoringStopped -> {
            Banner(
                kind.name,
                R.drawable.home_ic_warning,
                stringResource(R.string.reason_stopped_title),
                stringResource(R.string.reason_stopped_body),
                stringResource(R.string.reason_stopped_action),
                open(HomeAction.ResumeMonitoring),
                null,
                severity,
            )
        }

        BannerKind.AlertsUnreliable -> {
            val problem = state.firstProblem ?: return null
            // a failed alert step has no Android screen to open: its way out is the report (it goes when the step works)
            val delivery = problem == AlertRequirement.AlertDelivery
            val copyReport = rememberCopyAlertReport(state.readiness ?: AlertReadiness(setOf(problem)))
            Banner(
                kind.name,
                R.drawable.home_ic_warning,
                stringResource(R.string.banner_alerts_title),
                requirementText(problem),
                stringResource(if (delivery) DsR.string.ds_copy_report else R.string.banner_alerts_action),
                when (problem) {
                    AlertRequirement.AlertDelivery -> copyReport

                    // the person answers it on the reliability page (Android hides the setting from apps)
                    AlertRequirement.SoundWhenUnlocked -> open(HomeAction.OpenAlertReliability)

                    else -> ({ callbacks.onFix(problem) })
                },
                null,
                severity,
            )
        }

        BannerKind.DataProblem -> {
            Banner(
                kind.name,
                DsR.drawable.ds_ic_error,
                stringResource(R.string.banner_data_title),
                pluralStringResource(R.plurals.banner_data_text, state.dataProblems.size, state.dataProblems.size),
                stringResource(R.string.banner_data_action),
                callbacks.onOpenDataProblems,
                null,
                severity,
            )
        }

        BannerKind.TapsBlocked -> {
            val taps = state.blockedTaps
            val haptics = rememberHaptics()
            val (title, text) =
                when {
                    taps.pen && taps.sensor -> R.string.banner_taps_both_title to R.string.banner_taps_both_text
                    taps.sensor -> R.string.banner_taps_sensor_title to R.string.banner_taps_sensor_text
                    else -> R.string.banner_taps_pen_title to R.string.banner_taps_pen_text
                }
            Banner(
                kind.name,
                R.drawable.home_ic_warning,
                stringResource(title),
                stringResource(text),
                stringResource(R.string.banner_taps_allow),
                {
                    haptics.confirm()
                    callbacks.onAction(HomeAction.AllowTaps)
                },
                null,
                severity,
                secondary = stringResource(R.string.banner_taps_turn_off) to open(HomeAction.TurnOffTaps(taps)),
            )
        }

        BannerKind.RestartSystemUi -> {
            val updated = state.systemUiUpdated
            Banner(
                kind.name,
                DsR.drawable.ds_ic_info,
                stringResource(R.string.banner_systemui_title),
                stringResource(if (updated) R.string.banner_systemui_text_updated else R.string.banner_systemui_text),
                stringResource(R.string.banner_systemui_action),
                open(HomeAction.RestartSystemUi),
                dismiss,
                severity,
            )
        }

        BannerKind.FinishSetup -> {
            Banner(
                kind.name,
                DsR.drawable.ds_ic_info,
                stringResource(R.string.banner_setup_title),
                stringResource(R.string.banner_setup_text, state.nextSetupStep.orEmpty()),
                stringResource(R.string.banner_setup_action),
                open(HomeAction.OpenSetup),
                dismiss,
                severity,
            )
        }

        BannerKind.NotBackedUp -> {
            Banner(
                kind.name,
                DsR.drawable.ds_ic_info,
                stringResource(R.string.banner_backup_title),
                stringResource(R.string.banner_backup_text),
                stringResource(R.string.banner_backup_action),
                open(HomeAction.OpenBackup),
                dismiss,
                severity,
            )
        }

        // the launcher can't place it from the app: where to find it by hand (Close is the way out)
        BannerKind.AddWidget -> {
            val pinnable = state.widgetPinnable
            Banner(
                kind.name,
                DsR.drawable.ds_ic_info,
                stringResource(R.string.banner_widget_title),
                stringResource(if (pinnable) R.string.banner_widget_text else R.string.banner_widget_manual),
                if (pinnable) stringResource(R.string.banner_widget_action) else null,
                open(HomeAction.AddWidget),
                dismiss,
                severity,
            )
        }

        // either service is the clinic's copy: one pill each, with the service's own icon
        BannerKind.ShareWithClinic -> {
            Banner(
                kind.name,
                DsR.drawable.ds_ic_cloud_upload,
                stringResource(R.string.banner_clinic_title),
                stringResource(R.string.banner_clinic_text),
                null,
                {},
                dismiss,
                severity,
                choices =
                    listOf(
                        BannerChoice(
                            stringResource(R.string.banner_clinic_libreview),
                            DsR.drawable.ds_ic_service_libreview,
                            open(HomeAction.OpenClinicUpload),
                        ),
                        BannerChoice(
                            stringResource(R.string.banner_clinic_tidepool),
                            DsR.drawable.ds_ic_service_tidepool,
                            open(HomeAction.OpenTidepool),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun requirementText(requirement: AlertRequirement): String =
    stringResource(
        when (requirement) {
            AlertRequirement.AlertDelivery -> R.string.req_delivery
            AlertRequirement.Notifications -> R.string.req_notifications
            AlertRequirement.FullScreenIntent -> R.string.req_full_screen
            AlertRequirement.ExactAlarm -> R.string.req_exact_alarm
            AlertRequirement.BatteryExemption -> R.string.req_battery
            AlertRequirement.AlertChannel -> R.string.req_channel
            AlertRequirement.AlarmsInDnd -> R.string.req_dnd
            AlertRequirement.SoundWhenUnlocked -> R.string.req_unlocked
            AlertRequirement.Bluetooth -> R.string.req_bluetooth
        },
    )

/**
 * Home's *Mute alerts* control (anyone with a role: alerts ring on this phone): an outlined *Mute alerts* button that
 * opens the lengths ([onChange]) while alerts ring; while Mute alerts is on, its state counting down with *Unmute* and
 * *Change time* ([MuteAllState]), in the neutral secondary colours, never red.
 */
@Composable
private fun MuteControl(
    state: HomeUiState,
    onAlert: (HomeAlertAction) -> Unit,
    onChange: () -> Unit,
) {
    if (state.glucose.role == null) return
    Swap(state.mutedUntil != null, modifier = Modifier.padding(horizontal = 16.dp)) { muted ->
        if (muted) {
            MuteAllState(state.glucose.alerts, state.now, onUnmute = { onAlert(HomeAlertAction.UnmuteAll) }, onChange = onChange)
        } else {
            OutlinedButton(onClick = onChange, modifier = Modifier.fillMaxWidth().testTag("mute-alerts")) {
                Icon(
                    painterResource(DsR.drawable.ds_ic_notifications_off),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                )
                Text(stringResource(R.string.home_mute_alerts))
            }
        }
    }
}

/**
 * The quiet states the card's pill doesn't show (the other side, a fast-change alert, reminders slowed down), one chip
 * each with its line counting down; a tap opens that side's ways out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuietChips(
    quiet: List<AlertQuietSide>,
    now: Instant,
    onOpen: (QuietTarget) -> Unit,
) {
    val words = rememberAlertWords()
    Reveal(quiet.isNotEmpty()) {
        FlowRow(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (q in quiet) {
                val line = MuteText.side(q, now, words) ?: continue
                val side = QuietExits.side(q) ?: continue
                AssistChip(
                    onClick = { onOpen(QuietTarget(side)) },
                    label = { Text(line) },
                    leadingIcon = {
                        Icon(
                            painterResource(DsR.drawable.ds_ic_notifications_off),
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Mute alerts' lengths: THE number input with the schema's spec ("For 30 min" … "For 8 h", "Until you turn it off",
 * Custom), worded by [MuteText]; [onChoose] gets the minutes (null = until turned off).
 */
@Composable
private fun MuteAllDialog(
    onClose: () -> Unit,
    onChoose: (Int?) -> Unit,
) {
    val words = rememberAlertWords()
    val spec = SettingsSpec.MUTE_ALL
    NumberDialog(
        stringResource(R.string.home_mute_alerts),
        NumberInput(spec),
        spec.default,
        { m -> MuteText.line(if (m <= 0.0) MuteLine.ChoiceForever else MuteLine.Choice(m.roundToInt()), words) },
        onClose,
    ) { m -> onChoose(if (m <= 0.0) null else m.roundToInt()) }
}

/**
 * The last 24 h of entries. A row this phone may change is one button that opens the treatment dialog on it (amount,
 * time, note; TalkBack *edit*), a pen's row opens its sheet; Delete stays a button of its own. A writer's entry nothing
 * took yet says *Sending…* under it, then *Not sent* with *Retry*.
 */
@Composable
private fun Treatments(
    state: HomeUiState,
    openClinicUpload: () -> Unit,
    openPen: (Treatment) -> Unit,
    onEdit: (Treatment) -> Unit,
    onRetry: (Treatment) -> Unit,
    penRead: PenRead? = null,
    openPenPage: (String) -> Unit = {},
    onDelete: (Treatment) -> Unit,
) {
    val treatments = state.treatments
    val readOnly = state.readOnly
    val fromPen = state.fromPen
    val marks = state.marks
    val now = state.now
    val resources = LocalContext.current.resources
    val words = remember(resources) { CardWords.of(resources) }
    val stale = penRead?.takeIf { now.epochSecond - it.at > PEN_STALE_S }
    Reveal(treatments.isNotEmpty() || stale != null) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_treatments),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // the pen hasn't been read for a day: a quiet reminder where the doses are, opening that pen
            if (stale != null) {
                val ago = TreatmentMarks.duration(now.epochSecond - stale.at, words)
                TextButton(onClick = { openPenPage(stale.serial) }) {
                    Text(stringResource(R.string.home_pen_read_ago, ago), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        val openLabel = stringResource(R.string.home_pen_open)
        val editLabel = stringResource(R.string.home_treatment_edit)
        treatments.forEach { t ->
            val pen = t.id in fromPen
            val label = treatmentLabel(t, pen)
            val time = clockTime(t.at)
            // a pen row is one button (its sheet: the doses, Priming / Injected), any other row this phone may change
            // opens its edit; Delete stays a button of its own
            val open =
                when {
                    pen -> Modifier.clickable(onClickLabel = openLabel) { openPen(t) }.heightIn(min = 48.dp)
                    t.id !in readOnly -> Modifier.clickable(onClickLabel = editLabel) { onEdit(t) }.heightIn(min = 48.dp)
                    else -> Modifier
                }
            Row(
                Modifier.fillMaxWidth().then(open).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                // the time since the entry in the card's short durations ("12m", "3h")
                Text(
                    TreatmentMarks.duration(now.epochSecond - t.at.epochSecond, words),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                marks[t.id]?.let { mark ->
                    // every mark opens the LibreView page: what it has, why an entry waits, the way back in when stopped
                    IconButton(onClick = openClinicUpload) { UploadMarkIcon(mark) }
                }
                if (t.id !in readOnly) {
                    IconButton(onClick = { onDelete(t) }) {
                        Icon(
                            painterResource(R.drawable.home_ic_delete),
                            contentDescription = stringResource(R.string.home_treatment_delete, label, time),
                        )
                    }
                }
            }
            DeliveryLine(state.delivery[t.id], label) { onRetry(t) }
        }
    }
    // carbs logged, no rapid insulin near them yet, and a pen at hand: the quiet way to log it (an hour at most)
    Reveal(penRead != null && PenHints.carbsWithoutInsulin(treatments, now)) {
        Text(
            stringResource(R.string.home_pen_hint_carbs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * A writer's entry on its way (#75): *Sending…* while a relay or the wearer's phone hasn't taken it, *Not sent* in the
 * error colour with *Retry* once 2 minutes passed without (it is still sent again on every connect). Slides in and out;
 * the line is a polite live region, *Retry* names the entry for TalkBack.
 */
@Composable
private fun DeliveryLine(
    delivery: TreatmentDelivery?,
    label: String,
    onRetry: () -> Unit,
) {
    var last by remember { mutableStateOf(delivery) }
    if (delivery != null) last = delivery
    Reveal(delivery != null) {
        val failed = last == TreatmentDelivery.Failed
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).testTag("treatment-delivery"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(if (failed) DsR.drawable.ds_ic_error else DsR.drawable.ds_ic_send),
                contentDescription = null,
                tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(if (failed) R.string.home_treatment_not_sent else R.string.home_treatment_sending),
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (failed) {
                val retryLabel = stringResource(R.string.home_treatment_retry_label, label)
                TextButton(onClick = onRetry, modifier = Modifier.semantics { contentDescription = retryLabel }) {
                    Text(stringResource(R.string.home_treatment_retry))
                }
            }
        }
    }
}

/** The pen's passive hints on home (docs/29): when they show, from the entries alone (pure, tested). */
object PenHints {
    /** Carbs logged in the last hour with no rapid insulin within 30 min of them. */
    fun carbsWithoutInsulin(
        treatments: List<Treatment>,
        now: Instant,
    ): Boolean {
        val carbs =
            treatments
                .filter { it.kind == TreatmentKind.Carbs && !it.deleted && Duration.between(it.at, now) < Duration.ofHours(1) }
                .maxByOrNull { it.at } ?: return false
        return treatments.none {
            it.kind == TreatmentKind.RapidInsulin && !it.deleted && Duration.between(it.at, carbs.at).abs() <= Duration.ofMinutes(30)
        }
    }
}

/** *Pen read 2d ago* shows once the newest read is older than a day. */
private const val PEN_STALE_S = 24 * 3600L

/** The clinic upload's quiet mark: the entry is in LibreView, on its way, or waiting for the upload to run again. */
@Composable
private fun UploadMarkIcon(mark: UploadMark) {
    val (icon, label) =
        when (mark) {
            UploadMark.Uploaded -> R.drawable.home_ic_cloud_done to R.string.home_treatment_in_clinic
            UploadMark.Updating -> R.drawable.home_ic_cloud_upload to R.string.home_treatment_clinic_updating
            UploadMark.Pending -> R.drawable.home_ic_cloud_upload to R.string.home_treatment_not_in_clinic
            UploadMark.Stopped -> R.drawable.home_ic_cloud_off to R.string.home_treatment_clinic_stopped
        }
    Icon(
        painterResource(icon),
        contentDescription = stringResource(label),
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(18.dp),
    )
}

/**
 * "4.5u rapid insulin", "30g carbs": the amount as the card writes it ([TreatmentMarks.amount]) and the kind; a pen's
 * entry adds "· from the pen".
 */
@Composable
private fun treatmentLabel(
    t: Treatment,
    fromPen: Boolean,
): String {
    val amount = TreatmentMarks.amount(t).orEmpty()
    val kind =
        when (t.kind) {
            TreatmentKind.RapidInsulin -> stringResource(R.string.home_treatment_rapid, amount)
            TreatmentKind.LongInsulin -> stringResource(R.string.home_treatment_long, amount)
            TreatmentKind.Carbs -> stringResource(R.string.home_treatment_carbs, amount)
            TreatmentKind.Note -> stringResource(R.string.home_treatment_note)
        }.let { if (fromPen) stringResource(R.string.home_treatment_from_pen, it) else it }
    return if (t.note.isBlank()) kind else "$kind · ${t.note}"
}

/** "just now" / "5 minutes ago" / "in 3 days", in the phone's language (never "0 minutes ago"). */
@Composable
private fun relative(
    t: Instant,
    now: Instant,
): String {
    val past = Duration.between(t, now)
    if (!past.isNegative && past < Duration.ofMinutes(1)) return stringResource(R.string.home_a11y_just_now)
    return DateUtils.getRelativeTimeSpanString(t.toEpochMilli(), now.toEpochMilli(), DateUtils.MINUTE_IN_MILLIS).toString()
}

/**
 * [min] minutes as the card's spoken sentence says the sensor's time left: whole days from 24 h ("13 days"), else the
 * shared duration words ("4 h 59 min", "45 min"; `Durations.text`), so home never says "In 4 hours" beside a "5 h".
 */
@Composable
private fun spanText(min: Long): String {
    if (min >= MINUTES_PER_DAY) return pluralStringResource(DsR.plurals.ds_days, (min / MINUTES_PER_DAY).toInt(), min / MINUTES_PER_DAY)
    val resources = LocalContext.current.resources
    return Durations.text(min, remember(resources) { CardWords.of(resources) }.durations)
}

private const val MINUTES_PER_DAY = 1440L

/** Room kept under a safety banner at the viewport's bottom edge. */
private val SECTION_GAP = 8.dp

/** "1:24 AM" within the last 12 hours, else the date and time ("Oct 26, 11:40 PM"): when a silence began. */
@Composable
private fun sinceTime(
    t: Instant,
    now: Instant,
): String {
    if (Duration.between(t, now) < Duration.ofHours(12)) return clockTime(t)
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    return DateUtils.formatDateTime(LocalContext.current, t.toEpochMilli(), flags)
}

/** The time of day in the phone's 12/24-hour setting (the renderer's one clock formatter). */
private fun clockTime(t: Instant): String = Clock.time(t.epochSecond)
