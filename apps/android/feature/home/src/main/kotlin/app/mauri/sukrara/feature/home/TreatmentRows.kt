// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.motion.Reveal
import app.mauri.sukrara.designsystem.rows.SwipeToDelete
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.ClockText
import app.mauri.sukrara.graph.Freshness
import app.mauri.sukrara.graph.TreatmentKindBadge
import app.mauri.sukrara.graph.TreatmentMarks
import app.mauri.sukrara.graph.ValueGlyphs
import app.mauri.sukrara.graph.id
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs
import app.mauri.sukrara.core.designsystem.R as DsR
import app.mauri.sukrara.core.graph.R as GraphR

/** One entry of a treatment list (home's last 24 h, the Treatments page) and what this phone may do with it. */
data class TreatmentRowModel(
    val treatment: Treatment,
    /** An insulin pen logged it: the pen icon, and a tap opens its doses. */
    val fromPen: Boolean,
    /** This phone may not change it (a follower and the wearer's entry): no edit, no delete. */
    val readOnly: Boolean,
    /** Taken from the Nightscout source's site: read-only here, changed there (*From Nightscout*). */
    val fromSite: Boolean = false,
    /** The clinic upload's mark (null = none: the upload is off, or it never takes this entry). */
    val mark: UploadMark?,
    /** A writer's entry on its way ([TreatmentDelivery]); null once taken. */
    val delivery: TreatmentDelivery?,
    /** The reading the entry was logged at ([GlucoseAt]); null = none close enough. */
    val glucose: Reading?,
)

/** The rows of [treatments] from home's state (marks, pens, deliveries, the glucose then). */
fun HomeUiState.rows(treatments: List<Treatment> = this.treatments): List<TreatmentRowModel> =
    treatments.map { t ->
        TreatmentRowModel(
            treatment = t,
            fromPen = t.id in fromPen,
            readOnly = t.id in readOnly,
            fromSite = t.id in fromSite,
            mark = marks[t.id],
            delivery = delivery[t.id],
            glucose = GlucoseAt.of(t.at, history),
        )
    }

/**
 * One entry (#207, docs/37): the kind's badge (the chart's marker in its colour), the amount and kind with a pen icon
 * when a pen logged it, the time (and on home the time since), the note; at the end the glucose and trend when it was
 * logged, after the LibreView mark, shown only while the entry isn't there yet or for a moment right after it got there.
 *
 * A tap opens the entry's sheet (the edit form; a pen's doses), where *Delete* is too; a swipe either way deletes it
 * ([SwipeToDelete]: the snackbar's *Undo* brings it back). TalkBack: one element, its tap action and a *Delete*
 * action. A row this phone may not change only reads (a pen's still opens its read-only sheet).
 */
@Composable
internal fun TreatmentRow(
    row: TreatmentRowModel,
    now: Instant,
    unit: GlucoseUnit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onOpenUpload: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showAgo: Boolean = true,
    justUploaded: Boolean = rememberJustUploaded(row.mark),
) {
    val t = row.treatment
    val changeable = !row.readOnly
    val label = treatmentLabel(t)
    val openLabel = stringResource(if (row.fromPen) R.string.home_pen_open else R.string.home_treatment_edit)
    val open =
        if (row.fromPen || changeable) {
            Modifier.clickable(onClickLabel = openLabel, onClick = onOpen)
        } else {
            Modifier.semantics(mergeDescendants = true) {}
        }
    Column(modifier) {
        SwipeToDelete(onDelete = onDelete, enabled = changeable) { action ->
            RowContent(row, label, now, unit, showAgo, justUploaded, onOpenUpload, open.then(action))
        }
        DeliveryLine(row.delivery, label, onRetry)
    }
}

@Composable
private fun RowContent(
    row: TreatmentRowModel,
    label: String,
    now: Instant,
    unit: GlucoseUnit,
    showAgo: Boolean,
    justUploaded: Boolean,
    onOpenUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = row.treatment
    val resources = LocalContext.current.resources
    val words = remember(resources) { CardWords.of(resources) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(modifier)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TreatmentKindBadge(t.kind.id)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.fromPen) {
                    Icon(
                        painterResource(DsR.drawable.ds_ic_dia_pen),
                        contentDescription = stringResource(R.string.home_treatment_pen_source),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
            }
            // "14:05 · 12m" (home), "14:05" (the Treatments page: the day is the header), then the note
            val time = ClockText.time(t.at.epochSecond)
            val ago = TreatmentMarks.duration(now.epochSecond - t.at.epochSecond, words).takeIf { showAgo }
            val note = t.note.trim().takeIf { it.isNotEmpty() && t.kind != TreatmentKind.Note }
            val site = stringResource(R.string.home_treatment_from_site).takeIf { row.fromSite }
            Text(
                listOfNotNull(time, ago, note, site).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // the mark before the glucose, so the values line up at the end whichever rows have a mark
        UploadMarkButton(row.mark.shown(justUploaded), onOpenUpload)
        row.glucose?.let { GlucoseThen(it, unit, words) }
    }
}

/**
 * The glucose when the entry was logged: the value as the card writes it ([Freshness.valueText]: LO / HI, the unit's
 * decimals) and the card's arrow for that reading's trend ([ValueGlyphs.trendIcon]; the ring without one). TalkBack:
 * *glucose then 142 milligrams per decilitre, rising*.
 */
@Composable
private fun GlucoseThen(
    r: Reading,
    unit: GlucoseUnit,
    words: CardWords,
) {
    val value = Freshness.valueText(r.mgdl, unit, words.locale)
    val unitWords = stringResource(if (unit == GlucoseUnit.MMOL) GraphR.string.a11y_unit_mmol else GraphR.string.a11y_unit_mgdl)
    val spoken = stringResource(R.string.home_treatment_glucose_then, "$value $unitWords, ${words.trend(r.trend?.band)}")
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val arrow = remember(r.rate, r.trend?.band) { ValueGlyphs.trendIcon(r.rate, r.trend?.band, ARROW_PX).asImageBitmap() }
    Row(
        Modifier.padding(start = 4.dp, end = 12.dp).clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Image(arrow, contentDescription = null, colorFilter = ColorFilter.tint(ink), modifier = Modifier.size(16.dp))
    }
}

/**
 * The LibreView mark while it says something (#207): the entry still to go up or updating, the upload stopped (in the
 * error colour), or the check that pops in for a moment once it got there. Every mark opens the LibreView page.
 */
@Composable
private fun UploadMarkButton(
    mark: UploadMark?,
    onClick: () -> Unit,
) {
    var last by remember { mutableStateOf(mark) }
    if (mark != null) last = mark
    val r = LocalReducedMotion.current
    AnimatedVisibility(
        visible = mark != null,
        enter = fadeIn(Motion.defaultEffects(r)) + scaleIn(Motion.fastSpatial(r), initialScale = 0.4f),
        exit = fadeOut(Motion.defaultEffects(r)) + scaleOut(Motion.defaultSpatial(r), targetScale = 0.6f),
    ) {
        val shown = last ?: return@AnimatedVisibility
        IconButton(onClick = onClick, modifier = Modifier.testTag("upload-mark")) {
            val (icon, label) =
                when (shown) {
                    UploadMark.Uploaded -> R.drawable.home_ic_cloud_done to R.string.home_treatment_in_clinic
                    UploadMark.Updating -> R.drawable.home_ic_cloud_upload to R.string.home_treatment_clinic_updating
                    UploadMark.Pending -> R.drawable.home_ic_cloud_upload to R.string.home_treatment_not_in_clinic
                    UploadMark.Stopped -> R.drawable.home_ic_cloud_off to R.string.home_treatment_clinic_stopped
                }
            Icon(
                painterResource(icon),
                contentDescription = stringResource(label),
                tint =
                    when (shown) {
                        UploadMark.Stopped -> MaterialTheme.colorScheme.error
                        UploadMark.Uploaded -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * True for [JUST_UPLOADED_MS] after [mark] turned [UploadMark.Uploaded] while the row was shown (an entry that was
 * already there when the list opened shows nothing).
 */
@Composable
internal fun rememberJustUploaded(mark: UploadMark?): Boolean {
    var before by remember { mutableStateOf(mark) }
    var just by remember { mutableStateOf(false) }
    LaunchedEffect(mark) {
        val arrived = mark == UploadMark.Uploaded && before != null && before != UploadMark.Uploaded
        before = mark
        just = arrived
        if (arrived) {
            delay(JUST_UPLOADED_MS)
            just = false
        }
    }
    return just
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
            // under the row's words, past the badge
            Modifier.fillMaxWidth().padding(start = 68.dp, end = 4.dp).testTag("treatment-delivery"),
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

/**
 * "4.5u rapid insulin", "30g carbs": the amount as the card writes it ([TreatmentMarks.amount]) and the kind; a note is
 * its text ("Note" when it has none).
 */
@Composable
internal fun treatmentLabel(t: Treatment): String {
    val amount = TreatmentMarks.amount(t, LocalConfiguration.current.locales[0]).orEmpty()
    return when (t.kind) {
        TreatmentKind.RapidInsulin -> stringResource(R.string.home_treatment_rapid, amount)
        TreatmentKind.LongInsulin -> stringResource(R.string.home_treatment_long, amount)
        TreatmentKind.Carbs -> stringResource(R.string.home_treatment_carbs, amount)
        TreatmentKind.Note -> t.note.trim().ifEmpty { stringResource(R.string.home_treatment_note) }
    }
}

/** The trend arrow's bitmap size in px (drawn at 16 dp). */
private const val ARROW_PX = 64
