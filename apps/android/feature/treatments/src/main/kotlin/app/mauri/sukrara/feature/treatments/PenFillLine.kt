// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.PenFill
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Swap
import app.mauri.sukrara.designsystem.pen.penSpoken
import app.mauri.sukrara.designsystem.pen.penUnits

/** What the dialog shows of the pen under its fields (only while pens can be read into it). */
sealed interface PenLine {
    /** Pens can be read: *Injected? Hold your pen to the phone.* while no insulin is typed. */
    data object Ready : PenLine

    /** The pen answered: *Reading your pen…* with the wavy ring. */
    data object Reading : PenLine

    /**
     * [fill] went into its insulin field as [text] (shown while the field still says it: the entry is logged, so *Save*
     * doesn't log it again); [undo] = *Undo* is offered (gone after a rotation: the read's undo lives in the runtime).
     */
    data class Filled(
        val fill: PenFill.Logged,
        val text: String,
        val undo: Boolean = true,
    ) : PenLine

    /** The read's words for a moment: *Nothing new*, *Couldn't read the pen*, a question's title, *Pen read undone*. */
    data class Said(
        val text: String,
        val error: Boolean = false,
    ) : PenLine
}

/**
 * The pen's line under the dialog's fields: the hint, *Reading your pen…*, *Logged from your pen · 1.5u primed* with
 * *Undo* (and *Also logged: 2 earlier injections*), or the read's words. A polite live region, amounts spoken as
 * units; it crossfades between states (snaps with reduced motion).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PenFillLine(
    line: PenLine?,
    showHint: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = line?.takeIf { it != PenLine.Ready || showHint }
    Swap(shown, modifier.fillMaxWidth()) { l ->
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        when (l) {
            null -> {
                Unit
            }

            PenLine.Ready -> {
                Text(stringResource(R.string.treatment_pen_hint), style = MaterialTheme.typography.bodyMedium, color = muted)
            }

            PenLine.Reading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!LocalReducedMotion.current) CircularWavyProgressIndicator(Modifier.size(20.dp).clearAndSetSemantics { })
                    Text(
                        stringResource(R.string.treatment_pen_reading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            is PenLine.Filled -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val f = l.fill
                        val text =
                            if (f.primed > 0) {
                                stringResource(R.string.treatment_pen_logged_primed, penUnits(f.primed))
                            } else {
                                stringResource(R.string.treatment_pen_logged)
                            }
                        val spoken = penSpoken(text)
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted,
                            modifier =
                                Modifier.semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    contentDescription = spoken
                                },
                        )
                        if (f.also > 0) {
                            Text(
                                pluralStringResource(R.plurals.treatment_pen_also, f.also, f.also),
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                            )
                        }
                    }
                    if (l.undo) TextButton(onClick = onUndo) { Text(stringResource(R.string.treatment_pen_undo)) }
                }
            }

            is PenLine.Said -> {
                Text(
                    l.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (l.error) MaterialTheme.colorScheme.error else muted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** The pen's fill in the form, without the UI (tests): which field it is, what Save leaves out, the saved state. */
object PenFills {
    /** `8.5`, `8`: how the field writes a pen dose (a dot, whatever the language; the field reads both). */
    fun number(units: Double): String = penUnits(units).dropLast(1)

    /** What the filled field says now. */
    fun fieldText(
        line: PenLine.Filled,
        form: TreatmentFormState,
    ): String = if (line.fill.kind == "long") form.long else form.rapid

    fun setField(
        kind: String,
        form: TreatmentFormState,
        text: String,
    ) {
        if (kind == "long") form.long = text else form.rapid = text
    }

    /**
     * What *Save* stores of [typed]: the dose the pen read filled in is in the log already, so its field is left out
     * while it still shows exactly what the read put there (changed by hand, it's a typed dose again).
     */
    fun withoutFilled(
        typed: List<NewEntry>,
        line: PenLine?,
        form: TreatmentFormState,
    ): List<NewEntry> {
        val filled = (line as? PenLine.Filled)?.takeIf { fieldText(it, form) == it.text } ?: return typed
        val kind = if (filled.fill.kind == "long") TreatmentKind.LongInsulin else TreatmentKind.RapidInsulin
        return typed.filterNot { it.kind == kind }
    }

    fun save(
        line: PenLine.Filled,
        out: Bundle,
    ) {
        val f = line.fill
        out.putString(KIND, f.kind)
        out.putDoubleArray(AMOUNTS, doubleArrayOf(f.units, f.primed))
        out.putLong(T, f.t)
        out.putInt(ALSO, f.also)
        out.putString(TEXT, line.text)
    }

    /** A fill kept through a rotation (without its *Undo*: the read card and the notification keep that), or null. */
    fun restore(state: Bundle?): PenLine? {
        val s = state ?: return null
        val kind = s.getString(KIND) ?: return null
        val a = s.getDoubleArray(AMOUNTS)?.takeIf { it.size == 2 } ?: return null
        val text = s.getString(TEXT) ?: return null
        return PenLine.Filled(PenFill.Logged(kind, a[0], a[1], s.getLong(T), s.getInt(ALSO)), text, undo = false)
    }

    private const val KIND = "pen_fill_kind"
    private const val AMOUNTS = "pen_fill_amounts"
    private const val T = "pen_fill_t"
    private const val ALSO = "pen_fill_also"
    private const val TEXT = "pen_fill_text"
}
