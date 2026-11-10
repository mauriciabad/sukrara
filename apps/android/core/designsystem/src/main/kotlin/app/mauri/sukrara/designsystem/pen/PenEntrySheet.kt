// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.pen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.PenDoseInfo
import app.mauri.sukrara.core.model.PenEntryInfo
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import java.util.Locale

/** `8.5u`: dot, lowercase u, no space (the pen's own way of writing doses). */
fun penUnits(a: Double): String = (if (a % 1.0 == 0.0) "${a.toLong()}" else String.format(Locale.ROOT, "%.1f", a)) + "u"

private fun round1(x: Double) = Math.round(x * 10) / 10.0

/**
 * One pen dose: time, amount, *Incomplete*, and *Priming* / *Injected* (an M3 segmented button; [onChange] null = read
 * only, the role in words). TalkBack reads the row as one node ("13:10, 1 unit, priming") with a custom action that
 * flips it while it can be changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenDoseRow(
    d: PenDoseInfo,
    primed: Boolean,
    time: (Long) -> String,
    onChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val set = { p: Boolean ->
        if (p != primed && onChange != null) {
            haptics.tick()
            onChange(p)
        }
    }
    val role = stringResource(if (primed) R.string.ds_pen_priming else R.string.ds_pen_injected)
    val spoken = pluralStringResource(R.plurals.ds_pen_units_spoken, if (d.units == 1.0) 1 else 2, penUnits(d.units).dropLast(1))
    val desc = stringResource(R.string.ds_pen_dose_a11y, time(d.t), spoken, role)
    val flip = stringResource(if (primed) R.string.ds_pen_mark_injected else R.string.ds_pen_mark_priming)
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = desc
            if (onChange != null) customActions = listOf(CustomAccessibilityAction(flip) { set(!primed).let { true } })
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            time(d.t),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Column(Modifier.widthIn(min = 48.dp)) {
            Text(penUnits(d.units), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (d.incomplete) Text(stringResource(R.string.ds_pen_incomplete), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Box(Modifier.weight(1f))
        if (onChange == null) {
            Text(role, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(primed, {
                    set(true)
                }, SegmentedButtonDefaults.itemShape(0, 2), icon = {}, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(
                        stringResource(R.string.ds_pen_priming),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                SegmentedButton(!primed, {
                    set(false)
                }, SegmentedButtonDefaults.itemShape(1, 2), icon = {}, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(
                        stringResource(R.string.ds_pen_injected),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * A pen entry's sheet (Settings › Insulin pens › Doses read, home's pen rows): the amount and time, *From the pen ·
 * 1.5u primed*, each dose with *Priming* / *Injected*, the note and the entry it replaced. Once a dose changed:
 * *Cancel* / *Save* ([onSave] with each dose's role; *Remove from log* when every dose is priming). [onSave] null = read
 * only (a follower): the roles in words and *Done*. [onDelete] (the entry's own delete with its *Undo*) shows *Delete*
 * while nothing is being changed.
 *
 * Feel: a flipped dose's amount flies from its row to the total it joins (the injected amount, or *primed*) and both
 * totals count to their new values in 0.5u steps; with reduced motion the numbers simply change. TalkBack hears the
 * final totals (a polite live region), never the counting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenEntrySheet(
    entry: PenEntryInfo,
    time: (Long) -> String,
    onSave: ((List<Boolean>) -> Unit)?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val roles = remember(entry) { mutableStateListOf(*entry.doses.map { it.primed }.toTypedArray()) }
    val changed = roles.toList() != entry.doses.map { it.primed }
    val inj = entry.doses.filterIndexed { i, _ -> !roles[i] }
    val a = round1(inj.sumOf { it.units })
    val pr = round1(entry.doses.filterIndexed { i, _ -> roles[i] }.sumOf { it.units })
    val t = inj.minOfOrNull { it.t } ?: entry.t
    val kind = stringResource(if (entry.kind == "long") R.string.ds_pen_kind_long else R.string.ds_pen_kind_rapid)
    val reduced = LocalReducedMotion.current
    val flight = remember { DoseFlight() }
    val amountX = with(LocalDensity.current) { 64.dp.toPx() }
    val shownA = countedUnits(a)
    val shownPr = countedUnits(pr)
    val title = if (inj.isEmpty()) stringResource(R.string.ds_pen_nothing_injected) else "${penUnits(a)} $kind"
    val from = stringResource(R.string.ds_pen_from_pen)
    val primedText = pr.takeIf { it > 0 }?.let { stringResource(R.string.ds_pen_primed, penUnits(it)) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box(Modifier.onGloballyPositioned { flight.origin = it.positionInRoot() }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (inj.isEmpty() && shownA < 0.01) title else "${penUnits(shownA)} $kind",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier =
                            Modifier
                                .weight(1f)
                                .onGloballyPositioned { flight.injected = it.positionInRoot() }
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    contentDescription = title
                                },
                    )
                    Text(time(t), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    listOfNotNull(from, shownPr.takeIf { it > 0 }?.let { stringResource(R.string.ds_pen_primed, penUnits(it)) })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .onGloballyPositioned {
                                flight.primed = it.positionInRoot() + Offset(it.size.width * 0.6f, 0f)
                            }.semantics { contentDescription = listOfNotNull(from, primedText).joinToString(" · ") },
                )
                HorizontalDivider()
                entry.doses.forEachIndexed { i, d ->
                    PenDoseRow(
                        d,
                        roles[i],
                        time,
                        onSave?.let {
                            { p: Boolean ->
                                roles[i] = p
                                if (!reduced) flight.launch(penUnits(d.units), i, toPriming = p, amountX)
                            }
                        },
                        Modifier.onGloballyPositioned { flight.rows[i] = it.positionInRoot() },
                    )
                }
                HorizontalDivider()
                if (entry.note.isNotEmpty()) Text(entry.note, style = MaterialTheme.typography.bodyMedium)
                entry.replaced?.let { (ra, rt) ->
                    Text(
                        stringResource(R.string.ds_pen_replaced, penUnits(ra), time(rt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (changed && onSave != null) {
                        TextButton(onClick = {
                            roles.clear()
                            roles.addAll(entry.doses.map { it.primed })
                        }) { Text(stringResource(R.string.ds_cancel)) }
                        Button(onClick = {
                            onSave(roles.toList())
                            onDismiss()
                        }) { Text(stringResource(if (inj.isEmpty()) R.string.ds_pen_remove else R.string.ds_pen_save)) }
                    } else {
                        if (onDelete != null) {
                            TextButton(
                                onClick = {
                                    onDelete()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringResource(R.string.ds_pen_delete)) }
                        }
                        Button(onClick = onDismiss) { Text(stringResource(R.string.ds_pen_done)) }
                    }
                }
            }
            FlyingDose(flight)
        }
    }
}

/** Where a flipped dose's amount flies: from its row to the total it joins (positions in the root, set on layout). */
private class DoseFlight {
    var origin = Offset.Zero
    var injected = Offset.Zero
    var primed = Offset.Zero
    val rows = mutableMapOf<Int, Offset>()

    /** The amount in the air (text, from, to, a counter so the same dose can fly twice), or null. */
    var current by mutableStateOf<Triple<String, Pair<Offset, Offset>, Int>?>(null)
    private var n = 0

    fun launch(
        text: String,
        row: Int,
        toPriming: Boolean,
        amountX: Float,
    ) {
        // the amount sits right of the time column
        val from = (rows[row] ?: return) + Offset(amountX, 0f) - origin
        current = Triple(text, from to ((if (toPriming) primed else injected) - origin), n++)
    }
}

@Composable
private fun FlyingDose(flight: DoseFlight) {
    val f = flight.current ?: return
    val travel = remember(f.third) { Animatable(0f) }
    LaunchedEffect(f.third) {
        travel.animateTo(1f, tween(FLY_MS, easing = FastOutSlowInEasing))
        if (flight.current?.third == f.third) flight.current = null
    }
    val (from, to) = f.second
    Text(
        f.first,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .clearAndSetSemantics { }
                .offset {
                    val p = travel.value
                    IntOffset((from.x + (to.x - from.x) * p).toInt(), (from.y + (to.y - from.y) * p).toInt())
                }.graphicsLayer {
                    val p = travel.value
                    alpha = 1f - p * p
                    scaleX = 1f + 0.2f * p
                    scaleY = 1f + 0.2f * p
                },
    )
}

private const val FLY_MS = 320
