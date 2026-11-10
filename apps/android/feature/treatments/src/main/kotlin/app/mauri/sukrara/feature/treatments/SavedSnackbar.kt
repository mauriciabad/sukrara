// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.core.designsystem.R as DsR

/** What the snackbar after Save says, and its *Undo* (null once undone: the snackbar says so and goes). */
data class Confirmation(
    val text: String,
    val undo: Saved?,
)

/**
 * The quick dialog's confirmation after Save (#100): the dialog has gone and a snackbar at the bottom says what was
 * saved (*Saved: 4.5u rapid insulin · 30g carbs*) with *Undo*. The check pops in with a spring (none with reduced
 * motion); the text is a polite live region (TalkBack hears it once). Undo plays the confirm haptic and the text turns
 * into *Undone*. The activity closes it by itself (its time raised for TalkBack and *Time to take action*).
 */
@Composable
internal fun SavedSnackbar(
    confirmation: Confirmation,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    val pop = remember { Animatable(if (reduced) 1f else 0.4f) }
    LaunchedEffect(Unit) { if (!reduced) pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
    Snackbar(
        modifier = modifier.padding(16.dp).testTag("treatment-saved"),
        action =
            confirmation.undo?.let {
                {
                    TextButton(
                        onClick = {
                            haptics.confirm()
                            onUndo()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
                    ) { Text(stringResource(R.string.treatment_undo)) }
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(DsR.drawable.ds_ic_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inversePrimary,
                modifier =
                    Modifier.size(20.dp).graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
            )
            AnimatedContent(confirmation.text, transitionSpec = { Motion.fadeThrough(reduced) }, label = "saved") { text ->
                Text(
                    text,
                    modifier = Modifier.padding(start = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}
