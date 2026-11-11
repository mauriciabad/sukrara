// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.pens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.data.settings.SchemaSettings
import app.mauri.sukrara.core.model.PenAnswer
import app.mauri.sukrara.core.model.PenCheck
import app.mauri.sukrara.core.model.PenDoseInfo
import app.mauri.sukrara.core.model.PenEntryInfo
import app.mauri.sukrara.core.model.PenInfo
import app.mauri.sukrara.core.model.PenNearEntry
import app.mauri.sukrara.core.model.PenQuestionInfo
import app.mauri.sukrara.core.model.PenReadUndo
import app.mauri.sukrara.core.model.PenReview
import app.mauri.sukrara.core.model.PenSessionInfo
import app.mauri.sukrara.core.model.PenSessionStatus
import app.mauri.sukrara.core.model.PensPort
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.ChoiceDialog
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.pen.PenDoseRow
import app.mauri.sukrara.designsystem.pen.PenEntrySheet
import app.mauri.sukrara.designsystem.pen.PenQuestionAnswers
import app.mauri.sukrara.designsystem.pen.PenSameSheet
import app.mauri.sukrara.designsystem.pen.penQuestionLine
import app.mauri.sukrara.designsystem.pen.penSpoken
import app.mauri.sukrara.designsystem.rows.CardRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.NumberButton
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsHeader
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.graph.ClockText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import app.mauri.sukrara.core.designsystem.R as DsR

/** `8.5u`: dot, lowercase u, no space (the pen's own way of writing doses). */
internal fun units(a: Double) = (if (a % 1.0 == 0.0) "${a.toLong()}" else String.format(Locale.ROOT, "%.1f", a)) + "u"

/** A held read older than this asks for a new tap: the pen's display counts hours from now, not from the read. */
private const val CHECK_FRESH_S = 600L

private val Offer = SchemaSettings.number("pens.offer")

/** The pen page offers *Review the last read* for this long after a big read (later it is everyday history). */
private const val REVIEW_SHOWN_S = 7 * 86400L

/**
 * *Review* of the pen's last big read: what it logged ([review], null once undone or gone) and its sessions. The
 * read's dose [keys] are kept once seen, so after *Undo this read* its sessions stay listed (as *Not logged*).
 */
data class ReviewUi(
    val review: PenReview?,
    val keys: Set<String>,
    val sessions: List<PenSessionInfo>,
) {
    companion object {
        fun next(
            prev: ReviewUi?,
            review: PenReview?,
            sessions: List<PenSessionInfo>,
        ): ReviewUi {
            val keys = review?.keys ?: prev?.keys.orEmpty()
            return ReviewUi(review, keys, sessions.filter { s -> s.doses.any { it.key in keys } })
        }
    }
}

/** What a pen's row says under its name: *Rapid · read 13:14*, *Review its settings*, or its display check. */
@Composable
internal fun penSummary(p: PenInfo): String =
    when {
        p.untested && p.check == PenCheck.Failed -> {
            stringResource(R.string.st_pen_not_usable)
        }

        p.untested && p.check == PenCheck.None -> {
            stringResource(R.string.st_pen_check_to_start)
        }

        else -> {
            val kind = stringResource(if (p.kind == "long") R.string.st_pen_kind_long else R.string.st_pen_kind_rapid)
            val line = stringResource(R.string.st_pen_row, kind, ClockText.time(p.lastScan))
            if (p.opened) line else line + " · " + stringResource(R.string.st_pen_review_hint)
        }
    }

/** One pen's page and its *Doses read*. */
class PenViewModel(
    private val port: PensPort,
    val serial: String,
    settings: SettingsRepository,
    /** Now, epoch s (the days of *Doses read*, the offer's and *Review*'s windows; fixed in the screens' goldens). */
    val now: () -> Long = { System.currentTimeMillis() / 1000 },
) : ViewModel() {
    val pen: StateFlow<PenInfo?> =
        port.pens.map { l -> l.firstOrNull { it.serial == serial } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The pen isn't known (any more): its page closes. */
    val gone: StateFlow<Boolean> =
        port.pens.map { l -> l.none { it.serial == serial } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val sessions: StateFlow<List<PenSessionInfo>?> =
        port.sessions(serial).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A new pen's newest injection before Sukrara, under the offer window: *Log it*. */
    val offer: StateFlow<PenSessionInfo?> =
        combine(sessions, settings.flow(Offer)) { s, h ->
            val now = now()
            s
                ?.filter { it.status == PenSessionStatus.Before && it.units > 0 && now - it.t < (h * 3600).toLong() }
                ?.maxByOrNull { it.t }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** *Review* (the read card's, the notification's, the pen page's row): the last big read and its sessions. */
    val review: StateFlow<ReviewUi?> =
        combine(pen, sessions) { p, s -> p?.review to s }
            .scan(null as ReviewUi?) { prev, (r, s) -> if (s == null) prev else ReviewUi.next(prev, r, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** This pen's open questions (a waiting session's sheet answers its own). */
    val questions: StateFlow<List<PenQuestionInfo>> =
        port.questions
            .map { l -> l.filter { it.serial == serial } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What the page's parts do (the sheets and cards get this, never the ViewModel). */
    val actions = PenActions(port, serial, viewModelScope)

    fun opened() = actions.opened()

    fun forget() = actions.forget()
}

/** The page's one-line results (the snackbar): a message and, for a change, its *Undo*. */
data class PenSnack(
    val message: Int,
    val undo: PenEntryInfo? = null,
)

/** One pen's actions over the port, in the page's scope; results go to [snack]. */
class PenActions(
    private val port: PensPort,
    private val serial: String,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    val snack = kotlinx.coroutines.flow.MutableStateFlow<PenSnack?>(null)

    fun opened() = launch { port.opened(serial) }

    fun rename(name: String) = launch { port.rename(serial, name) }

    fun setKind(kind: String) = launch { port.setKind(serial, kind) }

    fun forget() = launch { port.forget(serial) }

    fun checkPassed(readAt: Long) =
        launch {
            if (!port.checkPassed(
                    serial,
                    readAt,
                )
            ) {
                snack.value = PenSnack(R.string.st_pen_check_tap_again)
            }
        }

    fun checkFailed() = launch { port.checkFailed(serial) }

    fun checkAgain() = launch { port.checkAgain(serial) }

    suspend fun match(
        t: Long,
        a: Double,
    ): PenNearEntry? = port.match(serial, t, a)

    suspend fun sessionEntry(t: Long): PenNearEntry? = port.sessionEntry(serial, t)

    suspend fun entry(id: String): PenEntryInfo? = port.entry(id)

    fun logDoses(doses: Map<String, Boolean>) =
        launch { snack.value = PenSnack(if (port.logDoses(serial, doses)) R.string.st_pen_logged else R.string.st_pen_changed_since) }

    fun join(
        doses: Map<String, Boolean>,
        id: String,
    ) = launch { snack.value = PenSnack(if (port.joinEntry(serial, doses, id)) R.string.st_pen_logged else R.string.st_pen_changed_since) }

    fun correct(
        e: PenEntryInfo,
        primed: List<Boolean>,
    ) = launch {
        val was = port.correct(e, primed)
        snack.value =
            when {
                was == null -> PenSnack(R.string.st_pen_changed_since)
                primed.all { it } -> PenSnack(R.string.st_pen_removed, was)
                else -> PenSnack(R.string.st_pen_entry_changed, was)
            }
    }

    fun restore(was: PenEntryInfo) = launch { if (!port.restore(was)) snack.value = PenSnack(R.string.st_pen_undo_failed) }

    /** *Undo this read*: says how it went (refused with the reason when its entries changed since). */
    fun undoRead() =
        launch {
            snack.value =
                PenSnack(
                    when (port.undoRead(serial)) {
                        PenReadUndo.Done -> R.string.st_pen_read_undone
                        PenReadUndo.Changed -> R.string.st_pen_undo_read_changed
                        PenReadUndo.Gone -> R.string.st_pen_undo_read_gone
                    },
                )
        }

    /** A waiting session's question answered as it was shown (refused when it changed since). */
    fun answer(
        q: PenQuestionInfo,
        a: PenAnswer,
    ) = launch { if (!port.answer(q, a)) snack.value = PenSnack(R.string.st_pen_changed_since) }

    /** An entry the page listed is gone (deleted elsewhere): the list follows, the snackbar says why nothing opened. */
    fun gone() {
        snack.value = PenSnack(R.string.st_pen_changed_since_open)
    }

    private fun launch(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }
}

/** Shows the page's results; *Undo* puts a corrected or removed entry back. */
@Composable
private fun PenSnackbar(
    vm: PenActions,
    host: SnackbarHostState,
) {
    val s by vm.snack.collectAsStateWithLifecycle()
    val message = s?.let { stringResource(it.message) }
    val undo = stringResource(R.string.st_pen_undo)
    LaunchedEffect(s) {
        val snack = s ?: return@LaunchedEffect
        val r =
            host.showSnackbar(
                message!!,
                actionLabel = if (snack.undo != null) undo else null,
                duration = if (snack.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        // cleared only once shown (clearing first would restart this effect and cancel the snackbar)
        vm.snack.value = null
        if (r == SnackbarResult.ActionPerformed) snack.undo?.let { vm.restore(it) }
    }
}

/**
 * One pen: the facts card, the untested model's display check (or *Not usable*), a new pen's last injection to log,
 * *Name*, *Insulin*, *Doses read*, *Check again* (untested) and *Forget this pen*. Opening it is reviewing the pen.
 */
@Composable
fun PenPage(
    vm: PenViewModel,
    onBack: () -> Unit,
    openDoses: () -> Unit,
    modifier: Modifier = Modifier,
    openReview: () -> Unit = {},
) {
    val p by vm.pen.collectAsStateWithLifecycle()
    val offer by vm.offer.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var choosingKind by rememberSaveable { mutableStateOf(false) }
    var forgetting by rememberSaveable { mutableStateOf(false) }
    // an untested pen opened before its check: the red confirmation first (Cancel = back, it stays unchecked)
    var confirmed by rememberSaveable { mutableStateOf(false) }
    val host = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.opened() }
    val gone by vm.gone.collectAsStateWithLifecycle()
    // forgotten (here or elsewhere): nothing left to show
    val back by rememberUpdatedState(onBack)
    if (gone) LaunchedEffect(Unit) { back() }
    val pen = p ?: return
    val kindLabel = stringResource(if (pen.kind == "long") R.string.st_pen_kind_long else R.string.st_pen_kind_rapid)
    PenSnackbar(vm.actions, host)
    SettingsPage(pen.name, onBack, modifier, snackbar = host) {
        SettingsGroup {
            CardRow {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (pen.untested) Text(stringResource(R.string.st_pen_untested_tag), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.st_pen_model, pen.model), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.st_pen_serial, pen.serial), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.st_pen_started, ClockText.dayTime(pen.since)), style = MaterialTheme.typography.bodyMedium)
                    if (pen.lastScan > 0) {
                        Text(
                            stringResource(R.string.st_pen_last_read, ClockText.dayTime(pen.lastScan)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val back = pen.returningSince
                    if (back != null) {
                        Text(
                            stringResource(R.string.st_pen_returning, ClockText.dayTime(back)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else if (pen.older > 0) {
                        Text(
                            pluralStringResource(R.plurals.st_pen_older, pen.older, pen.older),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (pen.untested && pen.check == PenCheck.None) {
            SettingsGroup(title = stringResource(R.string.st_pen_check_title)) {
                CardRow { DisplayCheck(pen, confirmed, { confirmed = true }, vm.actions, vm.now) }
            }
        }
        if (pen.untested && pen.check == PenCheck.Failed) {
            SettingsGroup {
                InfoRow(
                    stringResource(R.string.st_pen_not_usable),
                    summary = stringResource(R.string.st_pen_not_usable_text),
                    icon = DsR.drawable.ds_ic_warning,
                )
            }
        }
        val o = offer
        if (o != null && (!pen.untested || pen.check == PenCheck.Ok)) OfferCard(o, vm.actions)
        SettingsGroup {
            ValueRow(stringResource(R.string.st_pen_name), pen.name, { renaming = true }, icon = DsR.drawable.ds_ic_edit_note)
            ValueRow(
                stringResource(R.string.st_pen_insulin),
                kindLabel,
                { choosingKind = true },
                icon = DsR.drawable.ds_ic_dia_insulin_rapid,
            )
            pen.review?.takeIf { vm.now() - it.at < REVIEW_SHOWN_S }?.let { r ->
                NavigationRow(
                    stringResource(R.string.st_pen_review_row),
                    openReview,
                    summary = pluralStringResource(R.plurals.st_pen_review_line, r.entries, r.entries, ClockText.dayLabel(r.at)),
                    icon = DsR.drawable.ds_ic_history,
                )
            }
            NavigationRow(stringResource(R.string.st_pen_doses_read), openDoses, icon = DsR.drawable.ds_ic_history)
            if (pen.untested && pen.check != PenCheck.None) {
                NavigationRow(
                    stringResource(R.string.st_pen_check_again),
                    vm.actions::checkAgain,
                    summary = stringResource(R.string.st_pen_check_again_summary),
                    icon = DsR.drawable.ds_ic_science,
                )
            }
            NavigationRow(stringResource(R.string.st_pen_forget), { forgetting = true }, icon = DsR.drawable.ds_ic_delete)
        }
        FooterText(stringResource(R.string.st_pen_footer))
    }
    if (renaming) {
        var name by rememberSaveable { mutableStateOf(pen.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.st_pen_rename)) },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    vm.actions.rename(name)
                    renaming = false
                }) { Text(stringResource(R.string.st_pen_save)) }
            },
            dismissButton = { TextButton({ renaming = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (choosingKind) {
        ChoiceDialog(
            stringResource(R.string.st_pen_insulin),
            listOf(
                Choice(stringResource(R.string.st_pen_kind_rapid), "rapid"),
                Choice(stringResource(R.string.st_pen_kind_long), "long"),
            ),
            pen.kind,
            { choosingKind = false },
            onChoose = vm.actions::setKind,
        )
    }
    if (forgetting) {
        AlertDialog(
            onDismissRequest = { forgetting = false },
            title = { Text(stringResource(R.string.st_pen_forget_title)) },
            text = { Text(stringResource(R.string.st_pen_forget_text)) },
            confirmButton = {
                Button(onClick = {
                    forgetting = false
                    // the page closes when the pen is gone (one Back, not two)
                    vm.forget()
                }) { Text(stringResource(R.string.st_pen_forget_confirm)) }
            },
            dismissButton = { TextButton({ forgetting = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/**
 * The display check of an untested model: the red confirmation first (*Continue anyway* after 10 s), then the newest
 * dose and the whole hours since it, as the pen's display shows them now. *It matches* applies that read; *It doesn't*
 * marks the pen not usable. No fresh read (a restart, over 10 min): *Tap your pen to check it*.
 */
@Composable
private fun DisplayCheck(
    pen: PenInfo,
    confirmed: Boolean,
    onConfirm: () -> Unit,
    vm: PenActions,
    clock: () -> Long,
) {
    val haptics = rememberHaptics()
    var left by remember { mutableIntStateOf(10) }
    LaunchedEffect(confirmed) {
        while (!confirmed && left > 0) {
            kotlinx.coroutines.delay(1000)
            left--
        }
    }
    // the display counts hours from now: the line and the 10-minute limit follow the clock while the page is open
    val currentClock by rememberUpdatedState(clock)
    val now by produceState(currentClock()) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            value = currentClock()
        }
    }
    val held = pen.held?.takeIf { now - it.readAt <= CHECK_FRESH_S }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!confirmed) {
            Text(
                stringResource(R.string.st_pen_untested_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(stringResource(R.string.st_pen_untested_text, pen.model), style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onConfirm,
                enabled = left == 0,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    if (left >
                        0
                    ) {
                        stringResource(R.string.st_pen_untested_wait, left)
                    } else {
                        stringResource(R.string.st_pen_untested_continue)
                    },
                )
            }
        } else if (held == null) {
            Text(
                stringResource(R.string.st_pen_check_tap),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            val hours = ((now - held.t) / 3600).toInt()
            Text(
                stringResource(R.string.st_pen_check_q, units(held.units), pluralStringResource(R.plurals.st_pen_hours, hours, hours)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                FilledTonalButton(onClick = vm::checkFailed) { Text(stringResource(R.string.st_pen_check_no)) }
                Button(onClick = {
                    haptics.confirm()
                    vm.checkPassed(held.readAt)
                }) { Text(stringResource(R.string.st_pen_check_yes)) }
            }
        }
    }
}

/** A new pen's newest injection before Sukrara (under the offer window): *Log it*, or *Replace your entry*. */
@Composable
private fun OfferCard(
    o: PenSessionInfo,
    vm: PenActions,
) {
    var match by remember(o.t) { mutableStateOf<PenNearEntry?>(null) }
    LaunchedEffect(o.t, o.units) { match = vm.match(o.t, o.units) }
    SettingsGroup {
        CardRow {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.st_pen_offer, units(o.units), ClockText.time(o.t)), style = MaterialTheme.typography.bodyLarge)
                match?.let {
                    Text(
                        stringResource(R.string.st_pen_matches, units(it.units), ClockText.time(it.t)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { vm.logDoses(o.doses.associate { it.key to it.primed }) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(if (match != null) R.string.st_pen_replace_entry else R.string.st_pen_log_it)) }
            }
        }
    }
}

/**
 * *Doses read*: every session the pen holds, newest first, grouped by day, with its status; the doses from before
 * Sukrara folded behind a count. A tap opens the entry's sheet (correct each dose), the question's answers, or what
 * logging the session would do. [review]: only the last big read's sessions under its card with *Undo this read*.
 */
@Composable
fun DosesReadPage(
    vm: PenViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    review: Boolean = false,
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val pen by vm.pen.collectAsStateWithLifecycle()
    val questions by vm.questions.collectAsStateWithLifecycle()
    var unfold by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf<PenSessionInfo?>(null) }
    val host = remember { SnackbarHostState() }
    PenSnackbar(vm.actions, host)
    if (review) {
        val ui by vm.review.collectAsStateWithLifecycle()
        ReviewPage(ui, pen, host, vm.actions::undoRead, onBack, { open = it }, modifier, vm.now())
        open?.let { s -> SessionSheet(s, questions, vm.actions) { open = null } }
        return
    }
    val all = sessions.orEmpty()
    val (before, rest) = all.partition { it.status == PenSessionStatus.Before }
    SettingsPage(stringResource(R.string.st_pen_doses_read), onBack, modifier, snackbar = host) {
        val now = vm.now()
        val since = now - 30 * 86400L
        val recent = all.filter { it.t >= since }
        val logged = recent.filter { it.entry != null && it.status != PenSessionStatus.NotLogged }.sumOf { it.units }
        val primed = recent.filter { it.status != PenSessionStatus.Before && it.status != PenSessionStatus.Ignored }.sumOf { it.primed }
        FooterText(stringResource(R.string.st_pen_totals, units(round1(logged)), units(round1(primed))))
        if (sessions != null && all.isEmpty()) SettingsGroup { InfoRow(stringResource(R.string.st_pen_no_doses)) }
        rest.groupBy { dayOf(it.t, now) }.forEach { (day, list) ->
            SettingsGroup(title = dayTitle(day, now)) {
                for (s in list) {
                    SessionRow(
                        s,
                        enabled = s.status != PenSessionStatus.Ignored && pen?.let { usable(it) } == true,
                    ) { open = s }
                }
            }
        }
        if (before.isNotEmpty()) {
            // the hidden rest of the list: SettingsLib's NumberButtonPreference under it (*See less* folds it again)
            val title = stringResource(R.string.st_pen_before_title)
            if (unfold) {
                SettingsGroup(title = title) {
                    for (s in before) SessionRow(s, enabled = pen?.let { usable(it) } == true) { open = s }
                }
                NumberButton(null, stringResource(R.string.st_pen_see_less), { unfold = false })
            } else {
                SettingsHeader(title, Modifier.padding(start = 16.dp))
                NumberButton(before.sumOf { it.doses.size }, stringResource(R.string.st_pen_earlier_doses), { unfold = true })
            }
        }
    }
    open?.let { s -> SessionSheet(s, questions, vm.actions) { open = null } }
}

/** The sheet a session opens: its entry's (correct each dose), its question's answers, or what logging it would do. */
@Composable
private fun SessionSheet(
    s: PenSessionInfo,
    questions: List<PenQuestionInfo>,
    actions: PenActions,
    close: () -> Unit,
) {
    when {
        s.entry != null && s.status != PenSessionStatus.NotLogged -> {
            EntrySheet(s.entry!!, actions, close)
        }

        s.status == PenSessionStatus.Waiting && s.question != null -> {
            // answered elsewhere meanwhile (home, the notification): the row follows, nothing opens
            val q = questions.firstOrNull { it.id == s.question }
            val closing by rememberUpdatedState(close)
            if (q != null) QuestionSheet(q, actions, close) else LaunchedEffect(s.question) { closing() }
        }

        else -> {
            LogSheet(s, actions, close)
        }
    }
}

/**
 * *Review* of a big read (a read that logged an injection older than 72 h): *23 injections logged on 23 Oct*, the
 * oldest one's day, *Undo this read* (asks first: it takes every entry of the read out of the log), and the read's
 * sessions by day, each opening its sheet. Once undone the sessions stay listed as *Not logged* (each can be logged
 * again); Back leaves.
 */
@Composable
private fun ReviewPage(
    ui: ReviewUi?,
    pen: PenInfo?,
    host: SnackbarHostState,
    onUndo: () -> Unit,
    onBack: () -> Unit,
    onOpen: (PenSessionInfo) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis() / 1000,
) {
    var undoing by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val r = ui?.review
    SettingsPage(stringResource(R.string.st_pen_review_title), onBack, modifier, snackbar = host) {
        if (r != null) {
            SettingsGroup {
                CardRow {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            pluralStringResource(R.plurals.st_pen_review_line, r.entries, r.entries, ClockText.dayLabel(r.at)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.st_pen_review_text, ClockText.dayTime(r.since)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = { undoing = true },
                            enabled = pen?.let { usable(it) } == true,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(R.string.st_pen_undo_read)) }
                    }
                }
            }
        }
        val list = ui?.sessions.orEmpty()
        if (ui != null && list.isEmpty()) SettingsGroup { InfoRow(stringResource(R.string.st_pen_review_none)) }
        list.groupBy { dayOf(it.t, now) }.forEach { (day, l) ->
            SettingsGroup(title = dayTitle(day, now)) {
                for (s in l) SessionRow(s, enabled = s.status != PenSessionStatus.Ignored && pen?.let { usable(it) } == true) { onOpen(s) }
            }
        }
    }
    if (undoing && r != null) {
        AlertDialog(
            onDismissRequest = { undoing = false },
            title = { Text(stringResource(R.string.st_pen_undo_read_title)) },
            text = { Text(pluralStringResource(R.plurals.st_pen_undo_read_text, r.entries, r.entries)) },
            confirmButton = {
                Button(onClick = {
                    undoing = false
                    haptics.confirm()
                    onUndo()
                }) { Text(stringResource(R.string.st_pen_undo_read_confirm)) }
            },
            dismissButton = { TextButton({ undoing = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

private fun usable(p: PenInfo) = !p.untested || p.check == PenCheck.Ok

private fun round1(x: Double) = Math.round(x * 10) / 10.0

/** Days from [t] to [now] (0 = today, 1 = yesterday), both epoch s, in the phone's zone. */
private fun dayOf(
    t: Long,
    now: Long,
): Long {
    val zone = java.time.ZoneId.systemDefault()
    val day = { x: Long ->
        java.time.Instant
            .ofEpochSecond(x)
            .atZone(zone)
            .toLocalDate()
    }
    return java.time.temporal.ChronoUnit.DAYS
        .between(day(t), day(now))
}

@Composable
private fun dayTitle(
    daysAgo: Long,
    now: Long,
): String =
    when (daysAgo) {
        0L -> stringResource(R.string.st_pen_today)
        1L -> stringResource(R.string.st_pen_yesterday)
        else -> ClockText.dayLabel(now - daysAgo * 86400)
    }

@Composable
private fun SessionRow(
    s: PenSessionInfo,
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    val summary =
        when (s.status) {
            PenSessionStatus.Logged -> {
                if (s.primed >
                    0
                ) {
                    stringResource(R.string.st_pen_h_logged_primed, units(s.primed))
                } else {
                    stringResource(R.string.st_pen_h_logged)
                }
            }

            PenSessionStatus.Merged -> {
                s.mergedFrom?.let { stringResource(R.string.st_pen_h_merged, units(it)) } ?: stringResource(R.string.st_pen_h_logged)
            }

            PenSessionStatus.Corrected -> {
                stringResource(R.string.st_pen_h_corrected)
            }

            PenSessionStatus.Priming -> {
                stringResource(R.string.st_pen_h_priming)
            }

            PenSessionStatus.Waiting -> {
                stringResource(
                    if (s.sameQuestion) {
                        R.string.st_pen_h_same
                    } else if (s.question !=
                        null
                    ) {
                        R.string.st_pen_h_question
                    } else {
                        R.string.st_pen_h_waiting
                    },
                )
            }

            PenSessionStatus.Review -> {
                stringResource(R.string.st_pen_h_review)
            }

            PenSessionStatus.Before -> {
                stringResource(R.string.st_pen_h_before)
            }

            PenSessionStatus.Ignored -> {
                stringResource(R.string.st_pen_h_ignored)
            }

            PenSessionStatus.NotLogged -> {
                stringResource(R.string.st_pen_h_not_logged)
            }
        }.let { if (s.incomplete) stringResource(R.string.st_pen_h_incomplete, it) else it }
    val amount = units(if (s.status == PenSessionStatus.Priming) s.primed else s.units)
    NavigationRow("${ClockText.time(s.t)}  ·  $amount", onOpen, summary = summary, enabled = enabled)
}

/**
 * A pen entry's sheet (the shared [PenEntrySheet]): each dose's *Priming* / *Injected*; *Save* corrects it (the
 * snackbar's *Undo* puts it back).
 */
@Composable
private fun EntrySheet(
    id: String,
    vm: PenActions,
    onDismiss: () -> Unit,
) {
    var e by remember { mutableStateOf<PenEntryInfo?>(null) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(id) {
        e = vm.entry(id)
        if (e == null) {
            vm.gone()
            dismiss()
        }
    }
    val entry = e ?: return
    PenEntrySheet(entry, { ClockText.time(it) }, onSave = { vm.correct(entry, it) }, onDismiss = onDismiss)
}

/**
 * A session that isn't an entry (before Sukrara, not logged, only priming, after a pen clock reset): its doses with
 * *Priming* / *Injected*, what logging it would do (*Matches your 8u entry at 13:10*), *Log this injection* (*Log as
 * injection* for priming), or *Add to the 13:10 entry* for a dose that belongs to an entry's session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSheet(
    s: PenSessionInfo,
    vm: PenActions,
    onDismiss: () -> Unit,
) {
    val roles =
        remember(s) {
            // only priming: the last dose is offered as the injection
            val pre = s.doses.mapIndexed { i, d -> if (s.status == PenSessionStatus.Priming) i < s.doses.lastIndex else d.primed }
            mutableStateListOf(*pre.toTypedArray())
        }
    val inj = s.doses.filterIndexed { i, _ -> !roles[i] }
    val a = round1(inj.sumOf { it.units })
    val t = inj.minOfOrNull { it.t }
    var match by remember { mutableStateOf<PenNearEntry?>(null) }
    var joins by remember { mutableStateOf<PenNearEntry?>(null) }
    LaunchedEffect(t, a) {
        match = t?.let { vm.match(it, a) }
        joins =
            if ((s.status == PenSessionStatus.Review || s.status == PenSessionStatus.NotLogged) && t != null) vm.sessionEntry(t) else null
    }
    val doses = { s.doses.mapIndexed { i, d -> d.key to roles[i] }.toMap() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (inj.isEmpty()) stringResource(R.string.st_pen_nothing_injected) else units(a),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            HorizontalDivider()
            s.doses.forEachIndexed { i, d -> PenDoseRow(d, roles[i], { ClockText.time(it) }, { roles[i] = it }) }
            HorizontalDivider()
            match?.let {
                Text(
                    stringResource(R.string.st_pen_matches, units(it.units), ClockText.time(it.t)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.align(Alignment.End), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                joins?.let { j ->
                    FilledTonalButton(onClick = {
                        vm.join(doses(), j.id)
                        onDismiss()
                    }) { Text(stringResource(R.string.st_pen_add_to_entry, ClockText.time(j.t))) }
                }
                Button(enabled = inj.isNotEmpty(), onClick = {
                    vm.logDoses(doses())
                    onDismiss()
                }) {
                    Text(
                        stringResource(
                            when {
                                match != null -> R.string.st_pen_replace_entry
                                s.status == PenSessionStatus.Priming -> R.string.st_pen_log_as_injection
                                else -> R.string.st_pen_log_injection
                            },
                        ),
                    )
                }
            }
        }
    }
}

/**
 * A session waiting on a question: its doses as they are (the suggestion's roles) and the question's answers, the read
 * card's and home's; with several typed entries nearby *Choose* opens the shared *Same injection?* sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionSheet(
    q: PenQuestionInfo,
    vm: PenActions,
    onDismiss: () -> Unit,
) {
    var choosing by rememberSaveable(q.id) { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val answer = { a: PenAnswer ->
        haptics.confirm()
        vm.answer(q, a)
        onDismiss()
    }
    if (choosing) {
        PenSameSheet(q, { ClockText.time(it) }, onAnswer = answer, onDismiss = onDismiss)
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (q.same) R.string.st_pen_h_same else R.string.st_pen_h_question),
                style = MaterialTheme.typography.headlineSmall,
            )
            val line = penQuestionLine(q) { ClockText.time(it) }
            val spoken = penSpoken(line)
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = spoken },
            )
            HorizontalDivider()
            q.doses.forEach { d -> PenDoseRow(d, d.primed, { ClockText.time(it) }, null) }
            HorizontalDivider()
            PenQuestionAnswers(q, { _, a -> answer(a) }, { choosing = true })
        }
    }
}
