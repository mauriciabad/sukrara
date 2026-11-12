// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.motion.Reveal
import app.mauri.sukrara.designsystem.motion.Swap
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.graph.TreatmentMarks
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * What the form holds while the person types: the three amounts and the note as typed (after the input filters)
 * and when the entries happen ([at] null = now, resolved when saving). The fields edit [rapidText] & co. directly
 * (Material's state-based text field: the only one whose label can stay on the outline, so nothing floats); [rapid]
 * & co. read and replace that text.
 */
@Stable
class TreatmentFormState(
    rapid: String = "",
    long: String = "",
    carbs: String = "",
    note: String = "",
    at: Instant? = null,
    ago: Int? = null,
    /** Editing one stored entry of this kind (home's list): one amount field and its note, one entry; null = adding. */
    val editing: TreatmentKind? = null,
) {
    internal val rapidText = TextFieldState(rapid)
    internal val longText = TextFieldState(long)
    internal val carbsText = TextFieldState(carbs)
    internal val noteText = TextFieldState(note)

    var rapid: String
        get() = rapidText.text.toString()
        set(value) = rapidText.put(value)
    var long: String
        get() = longText.text.toString()
        set(value) = longText.put(value)
    var carbs: String
        get() = carbsText.text.toString()
        set(value) = carbsText.put(value)
    var note: String
        get() = noteText.text.toString()
        set(value) = noteText.put(value)
    var at by mutableStateOf(at)

    /** Minutes before the moment of saving (the *Minutes ago* mode) instead of [at]; null = not relative. */
    var ago by mutableStateOf(ago)

    /** When the entries happen if saved at [now]: minutes ago, the picked time, or null = now. */
    fun whenAt(now: Instant): Instant? = ago?.let { TreatmentForm.agoTime(now, it) } ?: at

    /** null = an amount is invalid. */
    val entries: List<NewEntry>?
        get() =
            when (editing) {
                null -> TreatmentForm.entries(rapid, long, carbs, note)
                TreatmentKind.RapidInsulin -> TreatmentForm.edited(editing, rapid, note)
                TreatmentKind.LongInsulin -> TreatmentForm.edited(editing, long, note)
                TreatmentKind.Carbs -> TreatmentForm.edited(editing, carbs, note)
                TreatmentKind.Note -> TreatmentForm.edited(editing, "", note)
            }
    val canSave: Boolean get() = entries?.isNotEmpty() == true
    val hasInput: Boolean get() = listOf(rapid, long, carbs, note).any { it.isNotBlank() }

    /** Takes every field of [other] (a kept draft, or an empty form for *Discard*). */
    fun fill(other: TreatmentFormState) {
        rapid = other.rapid
        long = other.long
        carbs = other.carbs
        note = other.note
        at = other.at
        ago = other.ago
    }

    companion object {
        val Saver: Saver<TreatmentFormState, Any> =
            listSaver(
                save = { listOf(it.rapid, it.long, it.carbs, it.note, it.at?.toEpochMilli(), it.ago, it.editing?.name) },
                restore = {
                    TreatmentFormState(
                        it[0] as String,
                        it[1] as String,
                        it[2] as String,
                        it[3] as String,
                        (it[4] as Long?)?.let(Instant::ofEpochMilli),
                        it[5] as Int?,
                        (it[6] as String?)?.let(TreatmentKind::valueOf),
                    )
                },
            )

        /** The form for editing [t]: its amount in its kind's field, its note and its time. */
        fun editing(t: Treatment): TreatmentFormState {
            val amount = TreatmentForm.amountText(t.amount)
            return TreatmentFormState(
                rapid = if (t.kind == TreatmentKind.RapidInsulin) amount else "",
                long = if (t.kind == TreatmentKind.LongInsulin) amount else "",
                carbs = if (t.kind == TreatmentKind.Carbs) amount else "",
                note = t.note,
                at = t.at,
                editing = t.kind,
            )
        }
    }
}

/** Replaces the text (cursor at the end) only when it differs, so a field being typed in keeps its cursor. */
private fun TextFieldState.put(value: String) {
    if (text.toString() != value) setTextAndPlaceCursorAtEnd(value)
}

/** The form, kept through a rotation; [at] = *When* to start with (a pen read's injection), else now. */
@Composable
fun rememberTreatmentFormState(at: Instant? = null): TreatmentFormState =
    rememberSaveable(saver = TreatmentFormState.Saver) { TreatmentFormState(at = at) }

/** The edit form for [t], kept across a configuration change. */
@Composable
fun rememberEditFormState(t: Treatment): TreatmentFormState =
    rememberSaveable(t.id, saver = TreatmentFormState.Saver) { TreatmentFormState.editing(t) }

/**
 * The quick dialog's countdown over the lock screen: [seconds] left (the ring's digits), [spokenSeconds] (TalkBack's
 * calmer 5 s steps), the ring's [fraction] (1 = full), [warnings] (bumps once at the last 5 s: the ring pulses) and
 * the way to keep it open; [announce] = the one spoken *Closes in 10 seconds* is due.
 */
data class LockInfo(
    val seconds: Int,
    val onUnlock: () -> Unit,
    val spokenSeconds: Int = seconds,
    val fraction: () -> Float = { 1f },
    val warnings: Int = 0,
    /** 10 s or less left: TalkBack hears *Closes in 10 seconds* once (a polite live region). */
    val announce: Boolean = false,
)

/** A kept draft shown in the form (#100): when the countdown kept it, and *Discard* (the form empties, it's dropped). */
data class DraftLine(
    val keptAt: Instant,
    val onDiscard: () -> Unit,
)

/**
 * Add insulin, carbs and a note in one go: rapid, long-acting and carbs side by side, a note below, all optional;
 * each filled field becomes its own entry at the chosen time (default now). The PoC's dialog (#181: the owner's
 * reference): an M3 basic dialog (28 dp corners, surface-container-high, 24 dp padding, confirming action last) with
 * the title and the *When* chip on one line, each field's label marked with its kind's chart marker, and the start
 * action (*Open Sukrara* / *Unlock*) apart from *Cancel* and *Save*. The first field is focused so the number pad is up
 * at once — Carbs instead when [focusCarbs] (a low is going on), Long-acting when [focusLong] (a rapid pen's dose was
 * just read). Next walks the fields, Done saves.
 *
 * [onOpenApp] adds *Open Sukrara* (the quick dialog, where a tap no longer opens the app); [lock] shows the
 * lock-screen countdown (the ring on *Cancel*) with *Unlock* in *Open Sukrara*'s place; [draft] says the form holds
 * what a countdown kept, with *Discard*; [penLine] is the pen's line under the fields (reading a pen into the dialog).
 * Editing one entry ([TreatmentFormState.editing]): that kind's field only, its note and its time, under *Edit carbs*
 * / … .
 */
@Composable
fun TreatmentDialogContent(
    form: TreatmentFormState,
    focusCarbs: Boolean,
    zone: ZoneId,
    now: () -> Instant,
    onSave: (List<NewEntry>, Instant?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenApp: (() -> Unit)? = null,
    lock: LockInfo? = null,
    quiet: QuietRules = QuietRules.NONE,
    draft: DraftLine? = null,
    focusLong: Boolean = false,
    penLine: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val first = remember { FocusRequester() }
    val longFocus = remember { FocusRequester() }
    val carbsFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    var picking by remember { mutableStateOf(false) }
    val entries = form.entries
    val save = {
        val list = form.entries
        if (list.isNullOrEmpty()) {
            haptics.reject()
        } else {
            haptics.confirm()
            onSave(list, form.whenAt(now()))
        }
    }
    LaunchedEffect(Unit) {
        runCatching {
            when {
                form.editing == TreatmentKind.Note -> noteFocus
                form.editing == TreatmentKind.Carbs || (form.editing == null && focusCarbs) -> carbsFocus
                form.editing == TreatmentKind.LongInsulin || (form.editing == null && focusLong) -> longFocus
                else -> first
            }.requestFocus()
        }
    }

    Surface(
        modifier = modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxWidth().testTag("treatment-dialog"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        // optional lines (draft, quiet, limits) carry their own top gap inside their reveal, so they slide in and out
        // without a gap that jumps
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(editTitle(form.editing)),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Spacer(Modifier.width(8.dp))
                WhenChip(form, zone, now, onPick = { picking = true })
            }
            DraftRow(draft, zone)
            // side by side; stacked at large font sizes so labels and units never truncate. Editing: its kind's field
            // only (a note has none)
            val stacked = LocalDensity.current.fontScale > STACK_FONT_SCALE
            val shown = form.editing?.let { setOf(it) } ?: setOf(TreatmentKind.RapidInsulin, TreatmentKind.LongInsulin, TreatmentKind.Carbs)
            val fields: @Composable (Modifier) -> Unit = { fieldModifier ->
                if (TreatmentKind.RapidInsulin in shown) {
                    AmountField(
                        form.rapidText,
                        "rapid",
                        R.string.treatment_rapid,
                        fieldModifier.focusRequester(first).testTag("treatment-rapid"),
                    )
                }
                if (TreatmentKind.LongInsulin in shown) {
                    AmountField(
                        form.longText,
                        "long",
                        R.string.treatment_long,
                        fieldModifier.focusRequester(longFocus).testTag("treatment-long"),
                    )
                }
                if (TreatmentKind.Carbs in shown) {
                    AmountField(
                        form.carbsText,
                        "carbs",
                        R.string.treatment_carbs,
                        fieldModifier.focusRequester(carbsFocus).testTag("treatment-carbs"),
                    )
                }
            }
            if (form.editing != TreatmentKind.Note) {
                Spacer(Modifier.height(GAP))
                if (stacked || form.editing != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { fields(Modifier.fillMaxWidth()) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { fields(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(GAP))
            NoteField(form, onDone = { save() }, modifier = Modifier.fillMaxWidth().focusRequester(noteFocus).testTag("treatment-note"))
            penLine?.let { line -> Box(Modifier.padding(top = GAP)) { line() } }
            QuietLine(form, quiet, now)
            Reveal(entries == null) {
                Text(
                    stringResource(R.string.treatment_limits, TreatmentForm.MAX_UNITS.toInt(), TreatmentForm.MAX_GRAMS.toInt()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = GAP).semantics { liveRegion = LiveRegionMode.Polite }.testTag("treatment-limits"),
                )
            }
            Spacer(Modifier.height(GAP))
            Box {
                DialogActionRow(
                    start =
                        if (onOpenApp == null && lock == null) {
                            null
                        } else {
                            { StartAction(lock, onOpenApp) }
                        },
                ) {
                    CountdownCancel(lock, onCancel)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = save, enabled = form.canSave, modifier = Modifier.testTag("treatment-save")) {
                        Text(stringResource(R.string.treatment_save))
                    }
                }
                if (lock != null) LockAnnouncement(lock.announce)
            }
        }
    }
    if (picking) {
        WhenDialog(
            form = form,
            zone = zone,
            now = now,
            lock = lock,
            onDone = { picking = false },
        )
    }
}

/** *Kept at 14:05, not saved yet* · *Discard*: slides away once discarded or saved. */
@Composable
private fun DraftRow(
    draft: DraftLine?,
    zone: ZoneId,
) {
    val haptics = rememberHaptics()
    // keeps the last line while it slides out
    var last by remember { mutableStateOf(draft) }
    if (draft != null) last = draft
    Reveal(draft != null) {
        val shown = last ?: return@Reveal
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = GAP).testTag("treatment-draft")) {
            Text(
                stringResource(R.string.treatment_draft_line, ClockText.time(shown.keptAt.epochSecond, zone = zone)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = {
                haptics.confirm()
                shown.onDiscard()
            }) { Text(stringResource(R.string.treatment_draft_discard)) }
        }
    }
}

/**
 * One amount (the PoC's field, #181): the label always sits small on the outline (never inside the empty field) with
 * the kind's marker, the unit shows throughout and the digits are large. A label floating up and down resized the
 * field and the dialog while it moved (`TreatmentDialogHeightTest`); this way nothing moves when the focus does.
 */
@Composable
private fun AmountField(
    state: TextFieldState,
    kind: String,
    label: Int,
    modifier: Modifier = Modifier,
) {
    val grams = kind == "carbs"
    val invalid = TreatmentForm.parse(state.text.toString(), grams) == TreatmentForm.Amount.Invalid
    OutlinedTextField(
        state = state,
        modifier = modifier,
        inputTransformation = AmountInput,
        textStyle = MaterialTheme.typography.titleLarge,
        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
        label = { KindLabel(kind, stringResource(label)) },
        suffix = { Text(stringResource(if (grams) R.string.treatment_unit_g else R.string.treatment_unit_u)) },
        isError = invalid,
        lineLimits = TextFieldLineLimits.SingleLine,
        // whole grams: the plain number pad; insulin has a decimal mark
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (grams) KeyboardType.Number else KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
    )
}

/** Keeps what [TreatmentForm.filterAmount] keeps of an edit. */
private val AmountInput =
    InputTransformation {
        val typed = toString()
        val kept = TreatmentForm.filterAmount(typed)
        if (kept != typed) replace(0, length, kept)
    }

/**
 * The note: one line of text that wraps up to three lines (a hardware Enter types a space), the note marker in its
 * label (always on the outline, like the amounts'), an example while it's empty, the byte count from 20 bytes before
 * the limit. Done saves.
 */
@Composable
private fun NoteField(
    form: TreatmentFormState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bytes = form.note.toByteArray().size
    OutlinedTextField(
        state = form.noteText,
        modifier = modifier,
        inputTransformation = NoteInput,
        // on the outline like the amounts' labels: a label sinking back into the empty note resized the dialog too
        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
        label = { KindLabel("note", stringResource(R.string.treatment_note)) },
        placeholder = { Text(stringResource(R.string.treatment_note_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingText =
            if (bytes > TreatmentForm.NOTE_BYTES - NOTE_COUNT_FROM) {
                { Text(stringResource(R.string.treatment_note_count, bytes, TreatmentForm.NOTE_BYTES)) }
            } else {
                null
            },
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
    )
}

/** No line breaks and at most [TreatmentForm.NOTE_BYTES] bytes. */
private val NoteInput =
    InputTransformation {
        val typed = toString()
        val kept = TreatmentForm.clip(typed.replace('\n', ' '), TreatmentForm.NOTE_BYTES)
        if (kept != typed) replace(0, length, kept)
    }

/** A field's label: the kind's marker, then its name on one line. */
@Composable
private fun KindLabel(
    kind: String,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KindMark(kind)
        Spacer(Modifier.width(6.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The marker the charts draw for [kind] ([TreatmentMarks.marker], in its colour): rapid ▼ filled, long ▽ outlined,
 * carbs ●, note ◆. Decoration only: the label's words say the kind.
 */
@Composable
internal fun KindMark(
    kind: String,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val color = TreatmentMarks.color(TreatmentMarks.palette(dark), kind)
    Canvas(modifier.size(MARK_SIZE).clearAndSetSemantics { }) {
        drawIntoCanvas { TreatmentMarks.marker(it.nativeCanvas, kind, size.width / 2, size.height / 2, size.width / 2, color) }
    }
}

/**
 * When the entries happen, at the end of the title line (the PoC's place and look: an M3 assist chip): *Now*,
 * *16 min ago*, *Today 14:05* or *Yesterday 23:40*; its words cross-fade when they change. A tap opens *When?*.
 */
@Composable
private fun WhenChip(
    form: TreatmentFormState,
    zone: ZoneId,
    now: () -> Instant,
    onPick: () -> Unit,
) {
    val label = whenLabel(form, zone, now(), CardWords.of(LocalContext.current.resources))
    val spoken = stringResource(R.string.treatment_when_a11y, label)
    AssistChip(
        onClick = onPick,
        label = { Swap(label) { Text(it, maxLines = 1) } },
        modifier = Modifier.testTag("treatment-when").semantics { contentDescription = spoken },
    )
}

/** The *When* chip's words: "Now", "16 min ago", "Today 14:05" or "Yesterday 23:40". */
@Composable
private fun whenLabel(
    form: TreatmentFormState,
    zone: ZoneId,
    now: Instant,
    words: CardWords,
): String {
    val ago = form.ago
    val at = form.at
    return when {
        ago != null -> {
            stringResource(R.string.treatment_ago, Durations.text(ago.toLong(), words.durations))
        }

        at == null -> {
            stringResource(R.string.treatment_now)
        }

        at.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() -> {
            stringResource(R.string.treatment_today, ClockText.time(at.epochSecond, zone = zone))
        }

        else -> {
            stringResource(R.string.treatment_yesterday, ClockText.time(at.epochSecond, zone = zone))
        }
    }
}

/**
 * What saving these entries quiets on this phone ("Highs quiet for 45 min · lows quiet for 15 min"), from the filled
 * fields and the chosen time; slides in and out as the fields change.
 */
@Composable
private fun QuietLine(
    form: TreatmentFormState,
    rules: QuietRules,
    now: () -> Instant,
) {
    val words = CardWords.of(LocalContext.current.resources)
    val instant = now()
    val note =
        QuietNote.of(
            rules,
            rapid = TreatmentForm.parse(form.rapid, grams = false) is TreatmentForm.Amount.Valid,
            carbs = TreatmentForm.parse(form.carbs, grams = true) is TreatmentForm.Amount.Valid,
            at = form.whenAt(instant) ?: instant,
            now = instant,
        )
    // keeps the last text while the line slides out
    var shown by remember { mutableStateOf("") }
    if (note != null) shown = quietText(note, words)
    Reveal(note != null) {
        Text(
            shown,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = GAP).semantics { liveRegion = LiveRegionMode.Polite }.testTag("treatment-quiet"),
        )
    }
}

@Composable
private fun quietText(
    note: QuietNote,
    words: CardWords,
): String {
    val highs = note.highsMin?.let { Durations.text(it, words.durations) }
    val lows = note.lowsMin?.let { Durations.text(it, words.durations) }
    return when {
        highs != null && lows != null -> stringResource(R.string.treatment_quiet_both, highs, lows)
        highs != null -> stringResource(R.string.treatment_quiet_highs, highs)
        else -> stringResource(R.string.treatment_quiet_lows, lows.orEmpty())
    }
}

/**
 * TalkBack's one *Closes in 10 seconds* ([on] once the activity says it is due): a polite live region that is empty
 * until then, so it is spoken once and never per second (the Cancel button's state carries the 5 s steps). It takes
 * no room in the layout.
 */
@Composable
private fun LockAnnouncement(on: Boolean) {
    val text = if (on) pluralStringResource(R.plurals.treatment_lock_closes_in, 10, 10) else ""
    Box(
        Modifier.size(1.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        },
    )
}

/**
 * The action row's start (the PoC's): *Open Sukrara*, or over the lock screen *Unlock* in its place (TalkBack: *Unlock
 * to keep editing*); they cross-fade when the phone is unlocked with the dialog up.
 */
@Composable
private fun StartAction(
    lock: LockInfo?,
    onOpenApp: (() -> Unit)?,
) {
    val reduced = LocalReducedMotion.current
    Crossfade(lock != null, animationSpec = Motion.defaultEffects(reduced), label = "unlock") { locked ->
        if (locked) {
            val spoken = stringResource(R.string.treatment_unlock_a11y)
            TextButton(onClick = {
                lock?.onUnlock?.invoke()
            }, modifier = Modifier.semantics { contentDescription = spoken }.testTag("treatment-unlock")) {
                Text(stringResource(R.string.treatment_unlock))
            }
        } else if (onOpenApp != null) {
            TextButton(onClick = onOpenApp) { Text(stringResource(R.string.treatment_open_app)) }
        }
    }
}

/**
 * The dialog's action row: [start] (*Open Sukrara* / *Unlock*) at the start, [end] (*Cancel*, *Save*) at the end. When
 * they don't fit on one line, [start] wraps to its own start-aligned line above (M3: dialog buttons that don't fit
 * stack; a button is never ellipsized).
 */
@Composable
private fun DialogActionRow(
    start: (@Composable () -> Unit)?,
    end: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(start ?: {}, { Row(verticalAlignment = Alignment.CenterVertically) { end() } }),
        modifier = Modifier.testTag("treatment-actions"),
    ) { (startMeasurables, endMeasurables), c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val e = endMeasurables.first().measure(loose)
        val st = startMeasurables.map { it.measure(loose) }
        val sw = st.maxOfOrNull { it.width } ?: 0
        val sh = st.maxOfOrNull { it.height } ?: 0
        val gap = 8.dp.roundToPx()
        val w = if (c.hasBoundedWidth) c.maxWidth else sw + gap + e.width
        if (st.isEmpty() || sw + gap + e.width <= w) {
            val h = maxOf(sh, e.height)
            layout(w, h) {
                st.forEach { it.place(0, (h - it.height) / 2) }
                e.place(w - e.width, (h - e.height) / 2)
            }
        } else {
            layout(w, sh + e.height) {
                st.forEach { it.place(0, 0) }
                e.place(w - e.width, sh)
            }
        }
    }
}

/**
 * *Cancel*. Over the lock screen the countdown ring is its leading icon (it does what the timeout does: closes without
 * saving) and TalkBack hears *Closes in N seconds* as its state; after an unlock the ring shrinks and fades away.
 */
@Composable
private fun CountdownCancel(
    lock: LockInfo?,
    onCancel: () -> Unit,
) {
    val state = lock?.let { pluralStringResource(R.plurals.treatment_lock_closes_in, it.spokenSeconds, it.spokenSeconds) }
    // keeps drawing the last countdown while the ring leaves after an unlock
    var last by remember { mutableStateOf(lock) }
    if (lock != null) last = lock
    TextButton(
        onClick = onCancel,
        modifier =
            Modifier.animateContentSize().semantics {
                if (state != null) stateDescription = state
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                lock != null,
                enter = fadeIn(tween(0)),
                exit = fadeOut(tween(200)) + scaleOut(tween(200)) + shrinkHorizontally(tween(200)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    last?.let { CountdownRing(it, stopped = lock == null) }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                }
            }
            Text(stringResource(R.string.treatment_cancel))
        }
    }
}

/**
 * The 24 dp ring with the seconds left inside: full at the start, emptying smoothly, primary on its track (red is for
 * errors only). It fills in with the dialog (300 ms) and pulses once (1 → 1.2 → 1) at 5 s left. Reduced motion: it
 * steps once a second (the activity's [LockInfo.fraction]), no fill-in, no pulse. The digits are hidden from TalkBack,
 * which hears the 5 s steps on Cancel instead.
 */
@Composable
private fun CountdownRing(
    lock: LockInfo,
    stopped: Boolean,
) {
    val reduced = LocalReducedMotion.current
    val fill = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) fill.animateTo(1f, tween(300)) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(lock.warnings) {
        if (lock.warnings > 0 && !reduced) {
            pulse.animateTo(1.2f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium))
            pulse.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }
    // a fixed size (dp, not sp): the number has to fit inside the ring; TalkBack and the ring carry the meaning
    val digits = with(LocalDensity.current) { 10.dp.toSp() }
    Box(
        Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }.clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { (if (stopped) 1f else lock.fraction()) * fill.value },
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
            strokeWidth = 2.dp,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
        // tabular figures so 10 → 9 doesn't jump sideways
        Text(
            "${lock.seconds}",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = digits,
                    lineHeight = digits,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Medium,
                ),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * The M3 scrim behind a dialog opened over the treatment dialog (*When?*, *Already logged by your pen?*). The quick
 * dialog's window draws its own scrim and turns the window dim off (`Theme.Sukrara.QuickTreatment`), and a Compose
 * dialog's window takes that theme, so without this it opened undimmed, flat on the card behind it (an M3 dialog has
 * tonal elevation, no shadow). Called from inside the dialog's content: it dims what is behind that dialog's window.
 */
@Composable
internal fun DialogScrim() {
    val view = LocalView.current
    SideEffect {
        val window =
            generateSequence<Any>(view) { (it as? View)?.parent }
                .firstNotNullOfOrNull { (it as? DialogWindowProvider)?.window }
        window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(DIALOG_DIM)
    }
}

/** M3's scrim opacity (the quick dialog's own scrim is the same). */
internal const val DIALOG_DIM = 0.32f

/**
 * When it happened: a segmented button switches between the time picker (a time later than now means yesterday,
 * [TreatmentForm.pickTime]) and *Minutes ago*: a number field with one-tap 5 / 15 / 30 / 60 chips
 * ([TreatmentForm.agoMinutes], under a day; resolved when saving). *Now* resets it; Cancel and back keep what was set.
 * Over the lock screen Cancel carries the countdown ring (the countdown keeps running behind the picker).
 */
@Composable
private fun WhenDialog(
    form: TreatmentFormState,
    zone: ZoneId,
    now: () -> Instant,
    lock: LockInfo?,
    onDone: () -> Unit,
) {
    val initial = (form.at ?: now()).atZone(zone).toLocalTime()
    val state = rememberTimePickerState(initial.hour, initial.minute, ClockText.hour24)
    var relative by rememberSaveable { mutableStateOf(form.ago != null) }
    var minutes by rememberSaveable { mutableStateOf(form.ago?.toString().orEmpty()) }
    val parsed = TreatmentForm.agoMinutes(minutes)
    val field = remember { FocusRequester() }
    val words = CardWords.of(LocalContext.current.resources)

    fun pickAgo(m: Int) {
        form.ago = m.takeIf { it > 0 }
        form.at = null
        onDone()
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = {
            DialogScrim()
            Text(stringResource(R.string.treatment_pick_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !relative,
                        onClick = { relative = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("when-time"),
                    ) { Text(stringResource(R.string.treatment_mode_time)) }
                    SegmentedButton(
                        selected = relative,
                        onClick = { relative = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("when-ago"),
                    ) { Text(stringResource(R.string.treatment_mode_ago)) }
                }
                if (!relative) {
                    TimePicker(state)
                } else {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = TreatmentForm.filterMinutes(it) },
                        modifier = Modifier.width(200.dp).focusRequester(field).testTag("when-minutes"),
                        suffix = { Text(stringResource(R.string.treatment_unit_min)) },
                        singleLine = true,
                        isError = minutes.isNotEmpty() && parsed == null,
                        textStyle = MaterialTheme.typography.headlineMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { parsed?.let(::pickAgo) }),
                    )
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        for (q in AGO_CHIPS) {
                            FilterChip(
                                selected = parsed == q,
                                onClick = { minutes = "$q" },
                                label = { Text(Durations.text(q.toLong(), words.durations), maxLines = 1) },
                            )
                        }
                    }
                    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !relative || parsed != null,
                onClick = {
                    if (relative) {
                        parsed?.let(::pickAgo)
                    } else {
                        form.at = TreatmentForm.pickTime(now(), zone, LocalTime.of(state.hour, state.minute))
                        form.ago = null
                        onDone()
                    }
                },
            ) { Text(stringResource(R.string.treatment_ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    form.at = null
                    form.ago = null
                    onDone()
                }) { Text(stringResource(R.string.treatment_now)) }
                // over the lock screen the countdown keeps running behind the picker: its ring stays in sight here
                CountdownCancel(lock, onDone)
            }
        },
        modifier = Modifier.testTag("treatment-time"),
    )
}

/** *Add treatment*, or *Edit rapid insulin* / *Edit carbs* / … while editing one entry. */
private fun editTitle(kind: TreatmentKind?): Int =
    when (kind) {
        null -> R.string.treatment_title
        TreatmentKind.RapidInsulin -> R.string.treatment_edit_rapid
        TreatmentKind.LongInsulin -> R.string.treatment_edit_long
        TreatmentKind.Carbs -> R.string.treatment_edit_carbs
        TreatmentKind.Note -> R.string.treatment_edit_note
    }

/** The *Minutes ago* one-tap lengths. */
private val AGO_CHIPS = listOf(5, 15, 30, 60)

private const val STACK_FONT_SCALE = 1.3f

/** The gap between the dialog's parts (the PoC's). */
private val GAP = 16.dp

/** A field label's kind marker (the PoC's 10 dp). */
private val MARK_SIZE = 10.dp

/** The note's byte count shows from this many bytes before the limit. */
private const val NOTE_COUNT_FROM = 20
