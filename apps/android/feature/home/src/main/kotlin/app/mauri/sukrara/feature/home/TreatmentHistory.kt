// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.pen.PenEntrySheet
import app.mauri.sukrara.graph.ClockText
import java.time.LocalDate
import java.time.ZoneId
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * The Treatments page (#207, docs/37): every entry of the last 31 days by day, newest first, each row as on home (the
 * glucose and trend when it was logged, the LibreView mark only while it says something), with home's edit, pen sheet,
 * delete and *Undo*. [viewModel] is a [HomeViewModel] listing [HomeViewModel.HISTORY_WINDOW].
 */
@Composable
fun TreatmentHistoryScreen(
    viewModel: HomeViewModel,
    onAction: (HomeAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pen by viewModel.penSheet.collectAsStateWithLifecycle()
    val penNotice by viewModel.penNotice.collectAsStateWithLifecycle()
    TreatmentHistoryContent(
        state = state,
        callbacks =
            HomeCallbacks(
                onAction = onAction,
                onDelete = viewModel::delete,
                onUndo = viewModel::undoDelete,
                onUndoGone = viewModel::undoShown,
                onRetry = viewModel::retry,
                pen =
                    PenCallbacks(
                        open = viewModel::openPen,
                        close = viewModel::closePen,
                        save = viewModel::correctPen,
                        delete = viewModel::deletePen,
                        undo = viewModel::undoPen,
                        shown = viewModel::penNoticeShown,
                        note = viewModel::notePen,
                        undoNote = viewModel::undoNote,
                    ),
            ),
        onBack = onBack,
        pen = pen,
        penNotice = penNotice,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentHistoryContent(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pen: PenSheet? = null,
    penNotice: PenNotice? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val snackbar = remember { SnackbarHostState() }
    TreatmentSnackbars(state, callbacks, penNotice, snackbar)
    val bar = TopAppBarDefaults.pinnedScrollBehavior()
    val days = remember(state.treatments, zone) { TreatmentDays.of(state.treatments, zone) }
    val rows = remember(state) { state.rows().associateBy { it.treatment.id } }
    val list = rememberLazyListState()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(bar.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DsR.drawable.ds_ic_arrow_back), contentDescription = stringResource(R.string.history_back))
                    }
                },
                scrollBehavior = bar,
            )
        },
        floatingActionButton = {
            if (state.glucose.role != null) {
                val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 } }
                val label = stringResource(R.string.home_add_treatment)
                ExtendedFloatingActionButton(
                    onClick = { callbacks.onAction(HomeAction.AddTreatment) },
                    expanded = atTop,
                    icon = { Icon(painterResource(R.drawable.home_ic_add), contentDescription = if (atTop) null else label) },
                    text = { Text(label) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (days.isEmpty()) {
            Empty(Modifier.padding(padding))
            return@Scaffold
        }
        val today = LocalDate.ofInstant(state.now, zone)
        val r = LocalReducedMotion.current
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            // room for the floating button over the last row
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            days.forEach { (day, entries) ->
                stickyHeader(key = "day-$day", contentType = "day") {
                    DayHeader(dayTitle(day, today, zone))
                }
                items(entries, key = { it.id }, contentType = { "row" }) { t ->
                    val row = rows[t.id] ?: return@items
                    TreatmentRow(
                        row,
                        state.now,
                        state.config.unit,
                        onOpen = {
                            if (row.fromPen) callbacks.pen.open(t) else callbacks.onAction(HomeAction.EditTreatment(t.id))
                        },
                        onDelete = { callbacks.onDelete(t) },
                        onOpenUpload = { callbacks.onAction(HomeAction.OpenClinicUpload) },
                        onRetry = { callbacks.onRetry(t) },
                        showAgo = false,
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = Motion.defaultEffects(r),
                                placementSpec = Motion.defaultSpatial(r),
                                fadeOutSpec = Motion.fastEffects(r),
                            ),
                    )
                }
            }
            item(key = "footer", contentType = "footer") {
                Text(
                    stringResource(R.string.history_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                )
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
        )
    }
}

/** A day's header, pinned while its entries scroll under it. */
@Composable
private fun DayHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() },
    )
}

@Composable
private fun Empty(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painterResource(DsR.drawable.ds_ic_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.history_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** "Today", "Yesterday", "Mon, Nov 10" in the phone's language. */
@Composable
private fun dayTitle(
    day: LocalDate,
    today: LocalDate,
    zone: ZoneId,
): String =
    when (day) {
        today -> {
            stringResource(R.string.history_today)
        }

        today.minusDays(1) -> {
            stringResource(R.string.history_yesterday)
        }

        else -> {
            val ms = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val flags =
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR
            DateUtils.formatDateTime(LocalContext.current, ms, flags)
        }
    }

/** The entries by the day they happened in [zone], newest day first, each day's entries newest first. */
object TreatmentDays {
    fun of(
        treatments: List<Treatment>,
        zone: ZoneId,
    ): List<Pair<LocalDate, List<Treatment>>> =
        treatments
            .sortedByDescending { it.at }
            .groupBy { LocalDate.ofInstant(it.at, zone) }
            .toList()
}
