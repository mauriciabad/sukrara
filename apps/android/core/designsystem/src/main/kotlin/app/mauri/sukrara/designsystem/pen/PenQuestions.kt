// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.pen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.PenAnswer
import app.mauri.sukrara.core.model.PenCandidate
import app.mauri.sukrara.core.model.PenQuestionInfo
import app.mauri.sukrara.core.model.PenTypedBy
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.motion.Reveal

/*
 * The pen's open questions where the person looks anyway (home, under the glucose card) and the *Same injection?*
 * sheet for a pen injection near several typed entries (home and *Doses read*). Every answer is the read card's and the
 * notification's: *Log 1.5u* / *All priming*, *Same injection* / *Different injections*; with several typed entries
 * *Choose* opens the sheet's radio rows and *None of these*.
 */

/** What TalkBack says for a pen line: amounts as words ("8.5 units"), never "8.5u". */
@Composable
fun penSpoken(text: String): String {
    val res = LocalContext.current.resources
    return remember(text, res) {
        Regex("(\\d+(?:[.,]\\d)?)u\\b").replace(text) { m ->
            val shown = m.groupValues[1]
            res.getQuantityString(R.plurals.ds_pen_units_spoken, if (shown == "1") 1 else 2, shown)
        }
    }
}

/** "1u", "1u and 1.5u", "1u, 0.5u and 1.5u". */
@Composable
private fun amounts(list: List<Double>): String {
    val all = list.map(::penUnits)
    return when (all.size) {
        0 -> ""
        1 -> all[0]
        else -> stringResource(R.string.ds_pen_and, all.dropLast(1).joinToString(", "), all.last())
    }
}

/** The question's line: "1u and 1.5u at 13:02", "Pen 8.5u at 13:12 · yours 8u at 13:05", "… · 2 typed entries nearby". */
@Composable
fun penQuestionLine(
    q: PenQuestionInfo,
    time: (Long) -> String,
): String {
    if (!q.same) return stringResource(R.string.ds_pen_q_doses_line, amounts(q.doses.map { it.units }), time(q.doses.first().t))
    val c = q.candidates
    return when {
        c.size == 1 -> {
            stringResource(R.string.ds_pen_q_same_one, penUnits(q.units), time(q.t), penUnits(c[0].units), time(c[0].t))
        }

        c.size > 1 -> {
            stringResource(R.string.ds_pen_q_same_line, penUnits(q.units), time(q.t)) + " · " +
                pluralStringResource(R.plurals.ds_pen_q_nearby, c.size, c.size)
        }

        else -> {
            stringResource(R.string.ds_pen_q_same_line, penUnits(q.units), time(q.t))
        }
    }
}

/**
 * Home's card while a pen question waits (under the glucose card: glucose stays first). The oldest question with its
 * answers and how many more wait; the next one fades through once it's answered. Not red: nothing is wrong, the dose
 * is only waiting to be named. *Choose* (several typed entries) opens [PenSameSheet].
 */
@Composable
fun PenQuestionCard(
    questions: List<PenQuestionInfo>,
    time: (Long) -> String,
    onAnswer: (PenQuestionInfo, PenAnswer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    var choosing by remember { mutableStateOf<PenQuestionInfo?>(null) }
    // keeps the last question while the card slides out after the last answer
    var shown by remember { mutableStateOf(questions.firstOrNull()) }
    questions.firstOrNull()?.let { shown = it }
    val answer = { q: PenQuestionInfo, a: PenAnswer ->
        haptics.confirm()
        onAnswer(q, a)
    }
    Reveal(questions.isNotEmpty(), modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AnimatedContent(shown, contentKey = { it?.id }, transitionSpec = { Motion.fadeThrough(reduced) }, label = "pen question") { q ->
                if (q == null) return@AnimatedContent
                val more = (questions.size - 1).coerceAtLeast(0)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (q.same) R.string.ds_pen_q_same else R.string.ds_pen_q_doses),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    val line = penQuestionLine(q, time)
                    val spoken = penSpoken(line)
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = spoken },
                    )
                    if (more > 0) {
                        Text(
                            pluralStringResource(R.plurals.ds_pen_q_more, more, more),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PenQuestionAnswers(q, answer, { choosing = q }, Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    choosing?.let { q ->
        PenSameSheet(q, time, onAnswer = { a -> answer(q, a) }, onDismiss = { choosing = null })
    }
}

/**
 * The read card's answers, end-aligned and wrapping: *All priming* / *Log 1.5u*, *Different injections* / *Same
 * injection*, or *Different injections* / *Choose*.
 */
@Composable
fun PenQuestionAnswers(
    q: PenQuestionInfo,
    onAnswer: (PenQuestionInfo, PenAnswer) -> Unit,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        when {
            !q.same -> {
                TextButton(onClick = { onAnswer(q, PenAnswer.AllPriming) }) { Text(stringResource(R.string.ds_pen_q_all_priming)) }
                if (q.units > 0) {
                    val label = stringResource(R.string.ds_pen_q_log, penUnits(q.units))
                    val spoken = penSpoken(label)
                    Button(
                        onClick = { onAnswer(q, PenAnswer.Suggested) },
                        modifier = Modifier.semantics { contentDescription = spoken },
                    ) { Text(label) }
                }
            }

            q.candidates.size > 1 -> {
                TextButton(onClick = { onAnswer(q, PenAnswer.Same(null)) }) { Text(stringResource(R.string.ds_pen_q_different)) }
                Button(onClick = onChoose) { Text(stringResource(R.string.ds_pen_q_choose)) }
            }

            else -> {
                TextButton(onClick = { onAnswer(q, PenAnswer.Same(null)) }) { Text(stringResource(R.string.ds_pen_q_different)) }
                q.candidates.firstOrNull()?.let { c ->
                    Button(onClick = { onAnswer(q, PenAnswer.Same(c.id)) }) { Text(stringResource(R.string.ds_pen_q_same_yes)) }
                }
            }
        }
    }
}

/**
 * *Same injection?* with several typed entries near the pen's injection: the pen's dose, one radio row per typed entry
 * (amount and time, where it was typed) and *None of these* (different injections); *Cancel* / *Save*. Nothing is
 * pre-selected, so *Save* waits for a choice. TalkBack: a radio group, each row one node.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenSameSheet(
    q: PenQuestionInfo,
    time: (Long) -> String,
    onAnswer: (PenAnswer) -> Unit,
    onDismiss: () -> Unit,
) {
    // "" = None of these; null = nothing picked yet
    var pick by rememberSaveable(q.id) { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.ds_pen_q_same),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            val pen = stringResource(R.string.ds_pen_q_sheet_pen, penUnits(q.units), time(q.t))
            val penSpoken = penSpoken(pen)
            Text(pen, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { contentDescription = penSpoken })
            Text(
                stringResource(R.string.ds_pen_q_sheet_ask),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Column(Modifier.selectableGroup()) {
                q.candidates.forEach { c -> CandidateRow(c, time, pick == c.id) { pick = c.id } }
                ChoiceRow(
                    stringResource(R.string.ds_pen_q_none),
                    stringResource(R.string.ds_pen_q_none_text),
                    pick == "",
                ) { pick = "" }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ds_cancel)) }
                Button(
                    enabled = pick != null,
                    onClick = {
                        onAnswer(PenAnswer.Same(pick?.takeIf { it.isNotEmpty() }))
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.ds_pen_save)) }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    c: PenCandidate,
    time: (Long) -> String,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val by =
        stringResource(
            when (c.by) {
                PenTypedBy.Phone -> R.string.ds_pen_q_by_phone
                PenTypedBy.Mac -> R.string.ds_pen_q_by_mac
                PenTypedBy.Follower -> R.string.ds_pen_q_by_follower
            },
        )
    ChoiceRow(stringResource(R.string.ds_pen_q_candidate, penUnits(c.units), time(c.t)), by, selected, onPick)
}

@Composable
private fun ChoiceRow(
    title: String,
    supporting: String,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val spoken = penSpoken(title)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onPick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { contentDescription = spoken })
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * [units] as it counts towards a new value in the pen dial's 0.5u steps (a total that changed; from [from] the first
 * time, e.g. 0 for a dose that was just read); the final value at once with reduced motion. Only for the eye: give
 * TalkBack the final value.
 */
@Composable
fun countedUnits(
    units: Double,
    from: Double = units,
): Double {
    val reduced = LocalReducedMotion.current
    val anim = remember { Animatable(if (reduced) units.toFloat() else from.toFloat()) }
    LaunchedEffect(units, reduced) {
        if (reduced) anim.snapTo(units.toFloat()) else anim.animateTo(units.toFloat(), tween(COUNT_MS))
    }
    val v = anim.value
    return if (kotlin.math.abs(v - units) < 0.01) units else Math.round(v * 2) / 2.0
}

private const val COUNT_MS = 350
