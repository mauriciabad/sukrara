// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.sheet

import android.graphics.Insets
import android.os.CancellationSignal
import android.view.View
import android.view.WindowInsets.Type
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import android.view.animation.LinearInterpolator
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * THE app's bottom sheet (#207 round 2: one surface for everything opened from a list or a card, never a dialog in one
 * place and a sheet in another): an M3 modal bottom sheet, fully expanded, its content in a column with the app's
 * sheet padding (24 dp sides and bottom) and gap (12 dp); its last row is [SheetActions]. [leaving] = it slides away.
 * Dialogs stay for confirmations and pickers (M3: a dialog interrupts for a decision, a sheet shows an item's details
 * and actions).
 *
 * The sheet and the keyboard move as one (#181, as on iOS): a field that wants the keyboard at once waits for
 * [SheetScope.awaitEntrance] (the sheet starting to rise), so both come up together, the fields riding on the keyboard's
 * own animation (the content's IME padding follows it frame by frame); a drag down takes the keyboard along under
 * the finger ([KeyboardFollowsSheet]) and it leaves with the sheet, or comes back up with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SukraraSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    leaving: Boolean = false,
    content: @Composable SheetScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // the owner closes it (saved, deleted): it slides away like a swipe down would
    LaunchedEffect(leaving) { if (leaving) state.hide() }
    val keyboard = remember { KeyboardFollowsSheet() }
    val defaultInsets = BottomSheetDefaults.modalWindowInsets
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = state,
        // while a drag carries the keyboard, the content keeps the keyboard's full height under it: the sheet and the
        // keyboard then move by the same distance and stay glued
        contentWindowInsets = {
            val ime = keyboard.frozen ?: keyboardSpace()
            defaultInsets.exclude(WindowInsets.ime).union(ime)
        },
    ) {
        keyboard.Follow(state)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val scope =
                remember(this, state) {
                    object : SheetScope, ColumnScope by this {
                        override suspend fun awaitEntrance() {
                            snapshotFlow { state.targetValue == SheetValue.Expanded }.first { it }
                            withFrameNanos { }
                        }
                    }
                }
            scope.content()
        }
    }
}

/**
 * The room the sheet keeps for the keyboard: while it is coming up, the height it will end at (the sheet takes its
 * final place in one move, together with the keyboard, instead of rising a second time on top of it); otherwise its
 * height on screen, frame by frame (it goes down with the keyboard).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun keyboardSpace(): WindowInsets {
    val density = LocalDensity.current
    val target = WindowInsets.imeAnimationTarget
    return if (target.getBottom(density) > WindowInsets.ime.getBottom(density)) target else WindowInsets.ime
}

/** What a sheet's content can ask of its sheet. */
interface SheetScope : ColumnScope {
    /**
     * Suspends until the sheet starts rising: a field that opens the keyboard on its own waits for it, so the keyboard
     * and the sheet come up together instead of one after the other.
     */
    suspend fun awaitEntrance()
}

/**
 * The keyboard under the finger while the sheet is dragged down (iOS's interactive keyboard dismissal): the window's
 * IME animation is taken over ([WindowInsetsController.controlWindowInsetsAnimation], the API behind Compose's
 * `imeNestedScroll` and the platform's WindowInsetsAnimation sample) and its height follows the sheet's offset frame by
 * frame, while the content's keyboard padding stays [frozen]. Released, it follows the sheet's own settle: down and
 * hidden with a dismissed sheet, back up with one that springs back. Nothing happens while the keyboard is down. It is
 * direct manipulation, so reduced motion changes nothing here (the keyboard's own show and hide animations follow the
 * system's animation scale).
 */
@Stable
internal class KeyboardFollowsSheet {
    /** The keyboard's full height kept as the content's padding during a drag; null = the live insets. */
    var frozen by mutableStateOf<WindowInsets?>(null)
        private set

    private var controller: WindowInsetsAnimationController? = null
    private var requested = false
    private var cancel: CancellationSignal? = null

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    fun Follow(state: SheetState) {
        val view = LocalView.current
        val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
        val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
        DisposableEffect(view) {
            onDispose { release(shown = false) }
        }
        // where the sheet rests while expanded (its top, which the keyboard's own animation moves): every drag is
        // measured from it
        val rest = remember { floatArrayOf(Float.NaN) }
        if (controller == null && !requested) {
            LaunchedEffect(imeBottom) { rest[0] = Float.NaN }
        }
        LaunchedEffect(state, view) {
            snapshotFlow { runCatching { state.requireOffset() }.getOrNull() to (state.currentValue == state.targetValue) }
                .collect { (offset, settled) ->
                    if (offset == null) return@collect
                    val free = controller == null && !requested
                    if (free && settled && state.currentValue == SheetValue.Expanded) {
                        rest[0] = if (rest[0].isNaN()) offset else minOf(rest[0], offset)
                    }
                    if (rest[0].isNaN()) return@collect
                    val drag = (offset - rest[0]).coerceAtLeast(0f)
                    if (drag > 0f && free && imeVisible) take(view)
                    controller?.let { c -> follow(c, drag) }
                    // sprang back: the keyboard is the system's again
                    if (!free && drag == 0f && settled && state.currentValue == SheetValue.Expanded) release(shown = true)
                }
        }
    }

    private fun take(view: View) {
        val insets = view.windowInsetsController ?: return
        requested = true
        val signal = CancellationSignal()
        cancel = signal
        insets.controlWindowInsetsAnimation(
            Type.ime(),
            -1,
            LinearInterpolator(),
            signal,
            object : WindowInsetsAnimationControlListener {
                override fun onReady(
                    c: WindowInsetsAnimationController,
                    types: Int,
                ) {
                    if (!requested) return c.finish(true)
                    controller = c
                    frozen = WindowInsets(bottom = c.shownStateInsets.bottom)
                }

                override fun onFinished(c: WindowInsetsAnimationController) = done()

                override fun onCancelled(c: WindowInsetsAnimationController?) = done()
            },
        )
    }

    /** The keyboard's top stays at the content's bottom: its height shrinks by the distance the sheet went down. */
    private fun follow(
        c: WindowInsetsAnimationController,
        drag: Float,
    ) {
        if (!c.isReady) return
        val shown = c.shownStateInsets.bottom
        val hidden = c.hiddenStateInsets.bottom
        val bottom = (shown - drag.roundToInt()).coerceIn(hidden, shown)
        val fraction = if (shown == hidden) 1f else (bottom - hidden).toFloat() / (shown - hidden)
        c.setInsetsAndAlpha(Insets.of(0, 0, 0, bottom), 1f, fraction)
    }

    /** Gives the keyboard back: shown (the sheet sprang back) or hidden (it was dismissed, or left). */
    private fun release(shown: Boolean) {
        val c = controller
        requested = false
        if (c != null && c.isReady) {
            c.finish(shown)
        } else if (c == null) {
            cancel?.cancel()
        }
        done()
    }

    private fun done() {
        controller = null
        cancel = null
        requested = false
        frozen = null
    }
}

/**
 * A sheet's action row: a destructive action at the start ([start], e.g. [SheetButton] *Delete*), the others at the
 * end with the confirming one last (M3 dialogs and sheets).
 */
@Composable
fun SheetActions(
    end: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    start: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        start?.invoke(this)
        Spacer(Modifier.weight(1f))
        end()
    }
}

/**
 * A sheet's button: always an icon and a word (the owner: every option gets an icon). [primary] = the filled
 * confirming button; [destructive] = a text button in the error colour (*Delete*); else a text button.
 */
@Composable
fun SheetButton(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val label: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text, maxLines = 1)
    }
    if (primary) {
        Button(onClick, modifier, enabled = enabled, contentPadding = ButtonDefaults.ButtonWithIconContentPadding, content = label)
    } else {
        TextButton(
            onClick,
            modifier,
            enabled = enabled,
            colors =
                if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            content = label,
        )
    }
}
