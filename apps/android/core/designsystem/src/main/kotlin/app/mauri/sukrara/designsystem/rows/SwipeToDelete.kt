// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * THE way to delete a list row (#207): a swipe towards the start (right to left; the reverse in right-to-left
 * languages) past [DELETE_AT] of the row's width deletes it: one direction only, so a sideways nudge while scrolling
 * the other way does nothing (Gmail's and Messages' swipe, research in docs/37). The row
 * sliding over a strip with the delete icon that turns error-container and grows, with a gesture-threshold haptic, once
 * letting go would delete (snaps with reduced motion). TalkBack, Switch Access and Voice Access get the same as a
 * *Delete* custom action on the row (a swipe always needs that alternative): [content] applies the modifier it is
 * given to the row's focusable node. [onDelete] must offer *Undo*
 * ([showUndo]); a row whose delete can't be undone uses a confirmation dialog instead, not this (M3: undo for
 * reversible actions, confirm the irreversible ones). [enabled] false = a plain row. A delete that didn't take the row
 * away (refused) slides back after 1.5 s.
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val swipe = rememberSwipeToDismissBoxState(positionalThreshold = { it * DELETE_AT })
    var width by remember { mutableIntStateOf(0) }
    val armed by remember { derivedStateOf { width > 0 && abs(swipe.offsetOrZero()) >= width * DELETE_AT } }
    // one haptic where letting go deletes, one when the finger goes back before it
    LaunchedEffect(swipe) { snapshotFlow { armed }.drop(1).collect { haptics.threshold(it) } }
    val label = stringResource(R.string.ds_delete)
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier.onSizeChanged { width = it.width },
        backgroundContent = { DeleteStrip(armed) },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        gesturesEnabled = enabled,
        onDismiss = {
            onDelete()
            scope.launch {
                delay(RESET_AFTER_MS)
                swipe.reset()
            }
        },
    ) {
        // the action goes on the row's own node (the one TalkBack focuses), which applies [content]'s modifier
        val action =
            Modifier.semantics {
                if (enabled) {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(label) {
                                haptics.confirm()
                                onDelete()
                                true
                            },
                        )
                }
            }
        Surface(color = MaterialTheme.colorScheme.background) { content(action) }
    }
}

/**
 * The *Undo* snackbar after a delete (or any change *Undo* takes back) on [this]: [message] names what changed
 * ("Deleted: 4.5u rapid insulin"), *Undo* and a close button. It stays 10 s (M3's long snackbar: an action needs time
 * to be read and reached), longer with TalkBack or Android's *Time to take action* (SnackbarHost raises it). True =
 * *Undo* was tapped.
 */
suspend fun SnackbarHostState.showUndo(
    message: String,
    undo: String,
): Boolean = showSnackbar(message, undo, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed

/** Behind a row being swiped: the delete icon at the end, uncovered as the row leaves. */
@Composable
private fun DeleteStrip(armed: Boolean) {
    val r = LocalReducedMotion.current
    val color by animateColorAsState(
        if (armed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        Motion.defaultEffects(r),
    )
    val ink by animateColorAsState(
        if (armed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        Motion.defaultEffects(r),
    )
    val scale by animateFloatAsState(if (armed) 1.15f else 0.9f, Motion.fastSpatial(r))
    Box(
        Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(painterResource(R.drawable.ds_ic_delete), contentDescription = null, tint = ink, modifier = Modifier.scale(scale))
    }
}

/** How far the row is swiped now (0 before it was laid out). */
private fun SwipeToDismissBoxState.offsetOrZero(): Float = runCatching { requireOffset() }.getOrDefault(0f).takeUnless { it.isNaN() } ?: 0f

/** The share of the row's width past which letting go deletes. */
const val DELETE_AT = 0.4f

/** A swiped row whose delete didn't take it away slides back after this long. */
private const val RESET_AFTER_MS = 1_500L
