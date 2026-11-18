// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import app.mauri.sukrara.core.model.BlockedFeature
import app.mauri.sukrara.core.model.DndStatus
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.core.model.PenAnswer
import app.mauri.sukrara.core.model.PenEntryInfo
import app.mauri.sukrara.core.model.PenQuestionInfo
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceProblem
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
import app.mauri.sukrara.designsystem.banner.BannerSlot
import app.mauri.sukrara.designsystem.banner.NoticeTone
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.motion.Reveal
import app.mauri.sukrara.designsystem.motion.Swap
import app.mauri.sukrara.designsystem.pen.PenEntrySheet
import app.mauri.sukrara.designsystem.pen.PenQuestionCard
import app.mauri.sukrara.designsystem.rows.rememberAllowFeature
import app.mauri.sukrara.designsystem.rows.rememberCopyAlertReport
import app.mauri.sukrara.designsystem.rows.showUndo
import app.mauri.sukrara.designsystem.rows.textRes
import app.mauri.sukrara.designsystem.rows.titleRes
import app.mauri.sukrara.designsystem.sharing.FirstThatFits
import app.mauri.sukrara.designsystem.sharing.LinePart
import app.mauri.sukrara.designsystem.sharing.MarkedLine
import app.mauri.sukrara.designsystem.sharing.PathMark
import app.mauri.sukrara.designsystem.state.StateKind
import app.mauri.sukrara.designsystem.state.StateScreen
import app.mauri.sukrara.feature.home.banners.BannerKind
import app.mauri.sukrara.graph.A11y
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.graph.GlucoseLook
import app.mauri.sukrara.graph.SensorRenew
import app.mauri.sukrara.graph.StatusBadge
import app.mauri.sukrara.graph.TreatmentKindMark
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
    val allowFeature = rememberAllowFeature(viewModel.featureChecks)
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
                onAllowFeature = allowFeature,
                onTurnOffFeature = viewModel::turnOff,
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
                        note = viewModel::notePen,
                        undoNote = viewModel::undoNote,
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
    /** A blocked feature's *Allow* (the permission dialog, then Android's screen) and *Turn off* (docs/40). */
    val onAllowFeature: (BlockedFeature) -> Unit = {},
    val onTurnOffFeature: (BlockedFeature) -> Unit = {},
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
    /** The sheet's *Save* with a changed note, and that snackbar's *Undo*. */
    val note: (String) -> Unit = {},
    val undoNote: (Treatment) -> Unit = {},
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
    TreatmentSnackbars(state, callbacks, penNotice, snackbar)
    val undoText = stringResource(R.string.home_undo)
    val follower = state.glucose.spec?.stream == true
    // Mute alerts' lengths: from the home control, its Change time, the quiet sheet and the card's TalkBack action
    var muteDialog by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val atTop by remember { derivedStateOf { scroll.value == 0 } }
    val scope = rememberCoroutineScope()
    val closedText = stringResource(R.string.home_tip_closed)
    val updatesOnText = stringResource(R.string.banner_updates_on)
    val shownBanner =
        banner(
            state,
            callbacks,
            onDismiss = { kind ->
                haptics.confirm()
                // every close can be undone here; Settings › Show closed tips brings them back later
                scope.launch {
                    if (snackbar.showUndo(closedText, undoText)) {
                        haptics.confirm()
                        callbacks.onRestore(kind)
                    }
                }
            },
            onTurnOnUpdates = {
                haptics.toggle(true)
                callbacks.onAction(HomeAction.TurnOnUpdates)
                // the banner leaves: say what is on now and where it changes
                scope.launch { snackbar.showSnackbar(updatesOnText, withDismissAction = true, duration = SnackbarDuration.Short) }
            },
        )
    // a safety banner sits right under the card (seen without scrolling); a tip goes below the glucose block
    val safetyBanner = shownBanner?.takeIf { it.tone == NoticeTone.Error }
    val infoBanner = shownBanner?.takeIf { it.tone != NoticeTone.Error }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // the page scrolls under the bar (HomeColumn): see-through at the top, so the card's number can overflow
            // its top edge (the owner, 2026-11-15: "it should simply overflow"); the bar's own colour once anything
            // has scrolled under it
            val barColors = TopAppBarDefaults.topAppBarColors()
            TopAppBar(
                colors = if (atTop) barColors.copy(containerColor = Color.Transparent) else barColors,
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
            if (state.glucose.spec != null) {
                // icon-only while a safety banner shows or the page is scrolled, so it never covers what matters
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
        // every side but the top: the column starts under the top bar and pads its content instead, so nothing at the
        // top of the page (the card's number) is clipped at the bar's edge
        val direction = LocalLayoutDirection.current
        val top = padding.calculateTopPadding()
        BoxWithConstraints(
            Modifier
                .padding(
                    start = padding.calculateStartPadding(direction),
                    end = padding.calculateEndPadding(direction),
                    bottom = padding.calculateBottomPadding(),
                ).fillMaxSize(),
        ) {
            HomeColumn(state, callbacks, scroll, top, safetyBanner, infoBanner, viewport = maxHeight - top, penQuestions, penRead) {
                muteDialog = true
            }
        }
    }
    pen?.let { p ->
        PenEntrySheet(
            p.entry,
            { ClockText.time(it) },
            onSave = if (p.editable) callbacks.pen.save else null,
            onDismiss = callbacks.pen.close,
            onDelete = if (p.deletable) callbacks.pen.delete else null,
            onNote = if (p.editable) callbacks.pen.note else null,
            noteMark = { TreatmentKindMark("note") },
        )
    }
    if (muteDialog) {
        MuteAllDialog(onClose = { muteDialog = false }) { callbacks.onAlert(HomeAlertAction.MuteAll(it)) }
    }
}

/**
 * The treatment lists' snackbars on [snackbar] (home and the Treatments page): *Treatment deleted* with *Undo* for as
 * long as it shows, and the pen sheet's results with their *Undo*.
 */
@Composable
internal fun TreatmentSnackbars(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    penNotice: PenNotice?,
    snackbar: SnackbarHostState,
) {
    val haptics = rememberHaptics()
    // names what went ("Deleted: 4.5u rapid insulin"): a swipe can take the wrong row, the words say which
    val deletedText =
        state.undo?.let {
            stringResource(
                if (state.undoInClinic) R.string.home_treatment_deleted_clinic else R.string.home_treatment_deleted,
                treatmentLabel(it),
            )
        }
    val undoText = stringResource(R.string.home_undo)
    LaunchedEffect(state.undo) {
        val text = deletedText ?: return@LaunchedEffect
        if (snackbar.showUndo(text, undoText)) {
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
            PenNotice.Kind.NoteChanged to stringResource(R.string.home_pen_changed),
        )
    LaunchedEffect(penNotice) {
        val notice = penNotice ?: return@LaunchedEffect
        val text = penTexts.getValue(notice.kind)
        val undone =
            if (notice.undo != null || notice.noteUndo != null) {
                snackbar.showUndo(text, undoText)
            } else {
                snackbar.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Short)
                false
            }
        callbacks.pen.shown(notice)
        if (undone) {
            haptics.confirm()
            notice.undo?.let(callbacks.pen.undo)
            notice.noteUndo?.let(callbacks.pen.undoNote)
        }
    }
}

/**
 * Home's scrolling column, from under the top bar: its content starts [top] lower (the bar's height), so whatever
 * overflows the card's top edge (the number's tops) is still inside the column and shows through the bar. [viewport] =
 * its visible height under the bar (a safety banner under the card must fit in it).
 */
@Composable
private fun HomeColumn(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    scroll: ScrollState,
    top: Dp,
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
            .verticalScroll(scroll)
            .padding(top = top),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // glucose first: the card (or, before any reading, why there is none) is always at the top
        GlucoseSection(state, callbacks, safetyBanner, viewport, onMuteAll)
        // a pen dose waiting to be named sits right under the card (never above it: glucose stays first)
        PenQuestionCard(penQuestions, { ClockText.time(it) }, callbacks.pen.answer, Modifier.padding(horizontal = 16.dp))
        BannerSlot(infoBanner)
        MuteControl(state, callbacks.onAlert, onMuteAll)
        NightLine(state, callbacks.onAction)
        DndLine(state, callbacks.onAction)
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
            openAll = { callbacks.onAction(HomeAction.OpenTreatments) },
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
 * The wearer as followers see them: picture, name (or "Add your name"), how many follow and how many of their devices
 * are connected over Bluetooth now ([FollowersLine]). Tapping it opens How you appear; it never pushes the card down
 * (it is the top bar's title).
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
            FollowersLine(profile.followers, profile.nearby)
        }
    }
}

/**
 * *2 followers · ᛒ 1*: how many follow, then the Bluetooth mark with how many of their devices are connected nearby
 * (none: no mark). Always whole on one line: where the words don't fit (a large font, Spanish, a narrow window) the
 * people icon takes their place (*👥 2 · ᛒ 1*). TalkBack reads it in words.
 */
@Composable
private fun FollowersLine(
    followers: Int,
    nearby: Int,
) {
    val words = pluralStringResource(R.plurals.home_followers, followers, followers)
    val near =
        if (nearby > 0) {
            listOf(LinePart.Words(" · "), LinePart.Mark(PathMark.Nearby), LinePart.Words(" $nearby"))
        } else {
            emptyList()
        }
    val description =
        if (nearby > 0) "$words, " + pluralStringResource(R.plurals.home_followers_nearby, nearby, nearby) else words
    FirstThatFits(
        listOf(
            { MarkedLine(listOf(LinePart.Words(words)) + near, null, plainBluetooth = true) },
            {
                MarkedLine(
                    listOf(LinePart.Glyph(DsR.drawable.ds_ic_group), LinePart.Words(" $followers")) + near,
                    null,
                    plainBluetooth = true,
                )
            },
        ),
        Modifier.testTag(FOLLOWERS_LINE_TAG).clearAndSetSemantics { contentDescription = description },
    )
}

/** The wearer header's followers line (tests check it is whole at every font size). */
internal const val FOLLOWERS_LINE_TAG = "home-followers-line"

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
        val color = if (trouble) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        // how the readings come now (the Mac panel's Nearby / Internet / Offline), first so a long line never cuts it
        val path = state.followPath?.takeIf { state.reason != NoDataReason.LinkRevoked }?.let(PathMark::of)
        if (path == null) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            MarkedLine(
                listOf(LinePart.Mark(path), LinePart.Words(" $line")),
                "${stringResource(path.words)}, $line",
                color = color,
                maxLines = 2,
                plainBluetooth = true,
            )
        }
    }
}

/**
 * The card, always (its chart keeps the last trajectory and the renderer draws the stale / warm-up / ended face), then
 * a safety banner, then the compact reason with its action. Only before there is anything to draw (not set up, no
 * sensor yet, no core) a full-width reason takes the card's place; that swap cross-fades (reduced motion: snaps).
 * While a safety banner shows, the card gives up height (down to [MIN_CARD_HEIGHT], easing) so the banner's action is
 * on screen without scrolling in [viewport], above the floating button, at any font size.
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
    val canMute = state.glucose.spec != null
    // what sits between the card and the safety banner's bottom edge (the quiet chips and the banner), px
    var under by remember { mutableIntStateOf(0) }
    val fit =
        if (safetyBanner == null) {
            CARD_HEIGHT
        } else {
            // the banner ends above the floating button (bottom-end), so its actions at the end are never under it
            val room = if (state.glucose.spec != null) FAB_SPACE else SECTION_GAP
            (viewport - with(density) { under.toDp() } - room).coerceIn(MIN_CARD_HEIGHT, CARD_HEIGHT)
        }
    // a small screen at a large font: even the smallest card leaves the banner's end over the floating button, so its
    // buttons stop short of it (and wrap rather than slide under it)
    val bannerEnd = fit + with(density) { under.toDp() }
    val overFab = safetyBanner != null && state.glucose.spec != null && bannerEnd > viewport - FAB_SPACE + 1.dp
    val actionsEnd = if (overFab) FAB_CLEARANCE else 0.dp
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
                            is PillAction.Treat -> {
                                callbacks.onAction(HomeAction.Treat(carbs = action.carbs))
                            }

                            // a new sensor paired here, or started in the source's app: Settings › Source says how
                            PillAction.ReplaceSensor -> {
                                callbacks.onAction(
                                    if (SensorRenew.of(state.glucose.spec) ==
                                        SensorRenew.HERE
                                    ) {
                                        HomeAction.PairSensor
                                    } else {
                                        HomeAction.OpenSource
                                    },
                                )
                            }

                            is PillAction.Quiet, PillAction.MuteAll -> {
                                sheet = QuietTarget.of(action)
                            }

                            PillAction.None -> {
                                Unit
                            }
                        }
                    },
                    height = cardHeight,
                )
                Column(Modifier.onSizeChanged { under = it.height }) {
                    QuietChips(QuietTarget.chips(state.glucose.alerts, pill), state.now) { sheet = it }
                    BannerSlot(safetyBanner, actionsEndInset = actionsEnd)
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
    return state.glucose.spec?.owner == true && Duration.between(state.now, ends) <= Duration.ofDays(1)
}

/** What the card's pill does for [state] (only the wearer pairs a sensor; anyone with a role may add a treatment). */
internal fun pillAction(state: HomeUiState): PillAction {
    val sensor = sensorPill(state)
    // the card draws *Replace sensor* there: only the wearer can act on it
    if (sensor && state.glucose.spec?.owner != true) return PillAction.None
    return PillAction.of(
        state.glucose.alerts.status,
        state.now.epochSecond,
        canAdd = state.glucose.spec != null,
        ended = sensor,
        muteAll = state.mutedUntil != null,
    )
}

/**
 * The card draws the ended sensor's pill where the status pill goes ([StatusBadge.sensor]): *Replace sensor* once its
 * readings stop, *Ended · Replace sensor* beside a value while they keep arriving, unless an alert's pill takes the place.
 */
internal fun sensorPill(state: HomeUiState): Boolean =
    state.look.state == GlucoseLook.State.ENDED ||
        (state.look.ended && !StatusBadge.shows(state.glucose.alerts.status, state.now.epochSecond))

/** TalkBack's words for the pill: what it shows, as the card's sentence says it ([A11y.status]). */
internal fun pillWords(
    state: HomeUiState,
    resources: android.content.res.Resources,
): String {
    val words = CardWords.of(resources)
    if (sensorPill(state)) return StatusBadge.sensor(state.look, words)?.text.orEmpty()
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

        is NoDataReason.SensorEnded -> {
            stringResource(R.string.reason_ended_title) to
                stringResource(
                    when (reason.renew) {
                        SensorRenew.HERE -> R.string.reason_ended_body
                        SensorRenew.LIBRE_APP -> R.string.reason_ended_body_libre
                        SensorRenew.JUGGLUCO -> R.string.reason_ended_body_juggluco
                        SensorRenew.ITS_APP -> R.string.reason_ended_body_uploader
                    },
                )
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

        NoDataReason.SiteNotConnected -> {
            stringResource(R.string.reason_site_title) to stringResource(R.string.reason_site_not_connected)
        }

        is NoDataReason.SourceTrouble -> {
            val llu = reason.kind == SourceKind.LibreLinkUp
            stringResource(if (llu) R.string.reason_llu_title else R.string.reason_site_title) to
                when (reason.problem) {
                    SourceProblem.SignedOut -> {
                        stringResource(if (llu) DsR.string.ds_online_llu_signed_out else DsR.string.ds_online_signed_out)
                    }

                    SourceProblem.Offline -> {
                        stringResource(DsR.string.ds_online_offline)
                    }

                    SourceProblem.Unreachable -> {
                        stringResource(if (llu) DsR.string.ds_online_llu_unreachable else R.string.reason_site_unreachable)
                    }

                    SourceProblem.RateLimited -> {
                        stringResource(if (llu) DsR.string.ds_online_llu_rate_limited else DsR.string.ds_online_rate_limited)
                    }

                    SourceProblem.NoNewReadings -> {
                        reason.since?.let {
                            stringResource(if (llu) DsR.string.ds_online_llu_no_new else DsR.string.ds_online_no_new, relative(it, now))
                        } ?: stringResource(if (llu) DsR.string.ds_online_llu_no_glucose else DsR.string.ds_online_no_glucose)
                    }

                    SourceProblem.ActionNeeded -> {
                        stringResource(DsR.string.ds_online_llu_terms)
                    }

                    SourceProblem.NotShared -> {
                        stringResource(DsR.string.ds_online_llu_not_shared)
                    }

                    SourceProblem.Stopped -> {
                        stringResource(DsR.string.ds_online_llu_stopped)
                    }
                }
        }

        is NoDataReason.PhoneAppSilent -> {
            when (reason.problem) {
                PhoneAppProblem.Missing -> {
                    stringResource(R.string.reason_juggluco_missing_title) to stringResource(R.string.reason_juggluco_missing_body)
                }

                PhoneAppProblem.Unreachable -> {
                    stringResource(R.string.reason_juggluco_title) to stringResource(R.string.reason_juggluco_unreachable)
                }

                PhoneAppProblem.Refused -> {
                    stringResource(R.string.reason_juggluco_title) to stringResource(R.string.reason_juggluco_refused)
                }

                null -> {
                    stringResource(R.string.reason_juggluco_title) to
                        (
                            reason.since?.let { stringResource(R.string.reason_juggluco_silent_body, relative(it, now)) }
                                ?: stringResource(R.string.reason_juggluco_silent_never)
                        )
                }
            }
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
            NoDataReason.SetupIncomplete -> {
                R.string.reason_setup_action
            }

            is NoDataReason.NoSensorYet -> {
                if (reason.follower) R.string.reason_no_wearer_action else R.string.reason_no_sensor_action
            }

            NoDataReason.BluetoothOff -> {
                R.string.reason_bluetooth_action
            }

            NoDataReason.MonitoringStopped -> {
                R.string.reason_stopped_action
            }

            NoDataReason.UsedByAnotherApp -> {
                R.string.reason_taken_over_action
            }

            NoDataReason.ScanNeeded -> {
                R.string.reason_scan_action
            }

            NoDataReason.PairAgain -> {
                R.string.reason_pair_again_action
            }

            is NoDataReason.ClockBehind -> {
                R.string.reason_clock_action
            }

            is NoDataReason.SensorEnded -> {
                when (reason.renew) {
                    SensorRenew.HERE -> R.string.reason_ended_action
                    SensorRenew.ITS_APP -> R.string.reason_sensor_action
                    else -> R.string.reason_site_action
                }
            }

            NoDataReason.LinkRevoked -> {
                R.string.reason_revoked_action
            }

            NoDataReason.SiteNotConnected, is NoDataReason.PhoneAppSilent -> {
                R.string.reason_site_action
            }

            is NoDataReason.SourceTrouble -> {
                if (reason.problem == SourceProblem.SignedOut) DsR.string.ds_online_sign_in else R.string.reason_site_action
            }

            else -> {
                R.string.reason_no_readings_action
            }
        },
    )

/** The one banner for [state] (or null), with its texts and exits. */
@Composable
private fun banner(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    onDismiss: (BannerKind) -> Unit,
    /** *Turn on* on the updates suggestion (the screen confirms it: haptic and snackbar). */
    onTurnOnUpdates: () -> Unit = {},
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
    // safety: alerts or readings may not work; a warning: the person has to act (Android blocks something, LibreView
    // lacks glucose); the rest are tips
    val severity =
        when (kind) {
            BannerKind.TapsBlocked, BannerKind.FeatureBlocked, BannerKind.ClinicUploadBehind -> NoticeTone.Warning
            else -> if (kind.safety) NoticeTone.Error else NoticeTone.Info
        }
    return when (kind) {
        BannerKind.MonitoringStopped -> {
            Banner(
                kind.name,
                DsR.drawable.ds_ic_warning,
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
            val delivery = problem == AlertRequirement.AlertShown || problem == AlertRequirement.AlertDelivery
            val copyReport = rememberCopyAlertReport(state.readiness ?: AlertReadiness(setOf(problem)))
            Banner(
                kind.name,
                DsR.drawable.ds_ic_warning,
                stringResource(R.string.banner_alerts_title),
                requirementText(problem),
                stringResource(if (delivery) DsR.string.ds_copy_report else R.string.banner_alerts_action),
                when (problem) {
                    AlertRequirement.AlertShown, AlertRequirement.AlertDelivery -> copyReport

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
                DsR.drawable.ds_ic_warning,
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

        // the taps banner's shape: Allow in front, Turn off beside it; a feature without a switch closes instead
        BannerKind.FeatureBlocked -> {
            val feature = state.blockedFeature ?: return null
            val haptics = rememberHaptics()
            Banner(
                "${kind.name}:${feature.name}",
                DsR.drawable.ds_ic_warning,
                stringResource(feature.titleRes()),
                stringResource(feature.textRes()),
                stringResource(DsR.string.ds_blocked_allow),
                {
                    haptics.confirm()
                    callbacks.onAllowFeature(feature)
                },
                dismiss.takeUnless { feature.canTurnOff },
                severity,
                secondary =
                    if (feature.canTurnOff) {
                        stringResource(DsR.string.ds_blocked_turn_off) to {
                            haptics.toggle(false)
                            callbacks.onTurnOffFeature(feature)
                        }
                    } else {
                        null
                    },
            )
        }

        // the treatment list's "upload stopped" cloud: Fix opens the LibreView page, which says why
        BannerKind.ClinicUploadBehind -> {
            val since = state.clinicBehind ?: return null
            Banner(
                "${kind.name}:${since.epochSecond}",
                R.drawable.home_ic_cloud_off,
                stringResource(R.string.banner_lv_behind_title),
                stringResource(R.string.banner_lv_behind_text, sinceTime(since, state.now)),
                stringResource(R.string.banner_alerts_action),
                open(HomeAction.OpenClinicUpload),
                dismiss,
                severity,
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

        // opt-in (F-Droid's rule): two equal pills; Not now closes it like any tip (Undo, Show closed tips)
        BannerKind.TurnOnUpdates -> {
            Banner(
                kind.name,
                DsR.drawable.ds_ic_update,
                stringResource(R.string.banner_updates_title),
                stringResource(R.string.banner_updates_text),
                null,
                {},
                null,
                severity,
                choices =
                    listOf(
                        BannerChoice(stringResource(R.string.banner_updates_turn_on), null, onTurnOnUpdates),
                        BannerChoice(stringResource(R.string.banner_updates_not_now), null, dismiss ?: {}),
                    ),
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
                            Service.LibreView,
                            open(HomeAction.OpenClinicUpload),
                        ),
                        BannerChoice(
                            stringResource(R.string.banner_clinic_tidepool),
                            Service.Tidepool,
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
            AlertRequirement.AlertShown -> R.string.req_shown

            AlertRequirement.AlertDelivery -> R.string.req_delivery

            AlertRequirement.Notifications -> R.string.req_notifications

            AlertRequirement.FullScreenIntent -> R.string.req_full_screen

            AlertRequirement.ExactAlarm -> R.string.req_exact_alarm

            AlertRequirement.BatteryExemption -> R.string.req_battery

            AlertRequirement.AlertChannel -> R.string.req_channel

            // never missing (a choice, not a fault); DndLine says when a mode holds alerts back
            AlertRequirement.DoNotDisturb -> DsR.string.ds_dnd_respected

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
    if (state.glucose.spec == null) return
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
 * While a Do Not Disturb mode keeps some or all alerts from making a sound (#184): "Do Not Disturb is on: alerts make no
 * sound" under the card, in the neutral colours (the person's mode, not a fault), with *Change*, which opens Settings ›
 * Alert reliability (its *Do Not Disturb* item lets Sukrara through, or not). It goes when the mode ends or lets the
 * alerts through. TalkBack reads the line, then *Change* as its button.
 */
@Composable
private fun DndLine(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    val held =
        state.readiness
            ?.dnd
            ?.heldNow
            ?.takeIf { state.glucose.spec != null && it != DndStatus.Held.None }
    // the last line shown stays while it hides (a plain holder: no state written during composition)
    val last = remember { arrayOf(DndStatus.Held.All) }
    if (held != null) last[0] = held
    val shown = last[0]
    Reveal(held != null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("dnd-held"), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(DsR.drawable.ds_ic_notifications_off),
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp).size(18.dp),
            )
            Text(
                stringResource(if (shown == DndStatus.Held.Some) R.string.home_dnd_held_some else R.string.home_dnd_held_all),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            val label = stringResource(R.string.home_dnd_change_label)
            TextButton(
                onClick = { onAction(HomeAction.OpenAlertReliability) },
                modifier = Modifier.semantics { contentDescription = label },
            ) { Text(stringResource(R.string.home_dnd_change)) }
        }
    }
}

/**
 * While the night alerts apply (#38): "Night alerts until 7:00" under the card with *Change*, which opens Settings ›
 * Alerts › Night alerts (its switch and hours are the ways to change or leave it). Nothing in the day.
 */
@Composable
private fun NightLine(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    val until =
        state.glucose.alerts.nightUntil
            ?.takeIf { state.glucose.spec != null }
    Reveal(until != null) {
        val words = rememberAlertWords()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("night-alerts"), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(DsR.drawable.ds_ic_bedtime),
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp).size(18.dp),
            )
            Text(
                until?.let { u -> MuteText.night(u.epochSecond, words) { clockTime(Instant.ofEpochSecond(it)) } }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onAction(HomeAction.OpenNightAlerts) }) { Text(stringResource(R.string.home_night_change)) }
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
 * The last 24 h of entries (#207, docs/37): each a [TreatmentRow] (tap edits or opens a pen's doses, long press opens
 * *Edit* / *Delete*, swipe deletes, *Undo* on the snackbar), newest first; *See all* opens the Treatments page with
 * the last 31 days. Shown while there is anything to list or to see there.
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
    openAll: () -> Unit = {},
    onDelete: (Treatment) -> Unit,
) {
    val treatments = state.treatments
    val now = state.now
    val resources = LocalContext.current.resources
    val words = remember(resources) { CardWords.of(resources) }
    val stale = penRead?.takeIf { now.epochSecond - it.at > PEN_STALE_S }
    val older = state.chartTreatments.any { it.at <= now }
    Reveal(treatments.isNotEmpty() || stale != null || older) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_treatments),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = openAll) { Text(stringResource(R.string.home_treatments_see_all)) }
        }
        // the pen hasn't been read for a day: a quiet reminder where the doses are, opening that pen
        if (stale != null) {
            val ago = TreatmentMarks.duration(now.epochSecond - stale.at, words)
            TextButton(onClick = { openPenPage(stale.serial) }, modifier = Modifier.padding(start = 4.dp)) {
                Text(stringResource(R.string.home_pen_read_ago, ago), style = MaterialTheme.typography.labelLarge)
            }
        }
        Reveal(treatments.isEmpty()) {
            Text(
                stringResource(R.string.home_treatments_none_today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val r = LocalReducedMotion.current
        Column(Modifier.animateContentSize(Motion.defaultSpatial(r))) {
            state.rows().forEach { row ->
                // keyed by the entry: a row's state stays with its treatment when one is added or removed above it
                key(row.treatment.id) {
                    val t = row.treatment
                    TreatmentRow(
                        row,
                        now,
                        state.config.unit,
                        onOpen = { if (row.fromPen) openPen(t) else onEdit(t) },
                        onDelete = { onDelete(t) },
                        onOpenUpload = openClinicUpload,
                        onRetry = { onRetry(t) },
                    )
                }
            }
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

/** Room kept under a safety banner where the floating button is: its 56 dp and its 16 dp margin. */
private val FAB_SPACE = 72.dp

/**
 * How far a safety banner's buttons stop from its end while the banner reaches the floating button: the button (56 dp)
 * and its margin (16 dp) and 8 dp between, less the card's margin and padding (16 + 16 dp).
 */
private val FAB_CLEARANCE = 48.dp

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
private fun clockTime(t: Instant): String = ClockText.time(t.epochSecond)
