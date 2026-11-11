// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.motion

import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch

/**
 * The app's screens: [stack] bottom to top (the last one shows), each drawn by [content], moving between them the
 * way their [ScreenMotion] says ([ScreenMoves]). One page at rest, two while moving. Back (the system back, the
 * predictive back gesture) calls [onBack] while there is a page under the top one; a page's own back handler, composed
 * inside it, comes first. A page's lifecycle is RESUMED only while it's at rest on top, STARTED while it moves.
 *
 * Every page keeps its composition for as long as it's drawn (a fixed slot, z-order by zIndex): composing it again
 * when it starts or stops moving drew its first frame from scratch (an empty chart, rows before their final layout;
 * the PoC's 0.11.14 transition flash). [key] identifies a page across stacks; an entry that left [stack] is still
 * drawn until its move ends.
 */
@Composable
fun <E : Any> ScreenHost(
    stack: List<E>,
    key: (E) -> Any,
    motion: (E) -> ScreenMotion,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (E) -> Unit,
) {
    require(stack.isNotEmpty()) { "ScreenHost needs a screen" }
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // the device's own window corner radius, what the system rounds a page to during the back gesture
    val cornerPx =
        remember(context) {
            runCatching {
                context.display
                    .getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                    ?.radius
                    ?.toFloat()
            }.getOrNull()
        } ?: with(density) { 28.dp.toPx() }

    val keys = stack.map(key)
    val top = keys.last()
    val parent = keys.getOrNull(keys.size - 2)
    // the latest entry of every page still drawn (a popped one until its move ends)
    val entries = remember { HashMap<Any, E>() }
    stack.forEach { entries[key(it)] = it }
    // a new top that was already in the stack, under the old top, is a back; anything else opens
    val lastKeys = remember { arrayOf(keys) }
    val back = remember(top) { top != lastKeys[0].last() && top in lastKeys[0] }
    lastKeys[0] = keys

    var shown by remember { mutableStateOf(top) } // the page at rest / the one being left
    var other by remember { mutableStateOf<Any?>(null) } // the page coming in, or the parent during a gesture
    var move by remember { mutableStateOf(ScreenMove.Open) }
    var style by remember { mutableStateOf(ScreenMotion.Page) }
    val slots = remember { arrayOfNulls<Any>(2) } // which page each of the two page slots holds
    val t = remember { Animatable(0f) } // linear time 0..1 of an open / back / commit
    val g = remember { Animatable(0f) } // gesture progress (eased)
    var dy by remember { mutableFloatStateOf(0f) } // finger travel up / down since the gesture started
    var startY by remember { mutableStateOf<Float?>(null) }
    entries.keys.retainAll { it in keys || it == shown || it == other }

    val currentOnBack by rememberUpdatedState(onBack)
    val currentMotion by rememberUpdatedState(motion)
    val navState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        navState,
        isBackEnabled = parent != null,
        onBackCancelled = {
            scope.launch {
                g.animateTo(0f, spring(1f, 1500f))
                if (move == ScreenMove.Gesture) {
                    other = null
                    startY = null
                    dy = 0f
                    move = ScreenMove.Open
                }
            }
        },
        onBackCompleted = { currentOnBack() },
    )
    val ts = navState.transitionState
    LaunchedEffect(ts) {
        // with reduced motion the gesture shows nothing: Back just changes the page when it's done
        if (ts !is NavigationEventTransitionState.InProgress || parent == null || reduced) return@LaunchedEffect
        if (move != ScreenMove.Gesture || other == null) {
            // a move still running ends where it was going: the gesture takes the page on top now
            shown = top
            move = ScreenMove.Gesture
            other = parent
            t.snapTo(0f)
        }
        val e = ts.latestEvent
        val y0 = startY ?: e.touchY.also { startY = it }
        dy = e.touchY - y0
        g.snapTo(ScreenMoves.gestureProgress(e.progress))
    }
    LaunchedEffect(top) {
        val target = top
        if (target == shown && other == null) return@LaunchedEffect
        // back to the page being left before the move finished: go back from the page that was coming in (drawing
        // the same page twice isn't possible)
        if (target == shown && move != ScreenMove.Gesture) other?.let { shown = it }
        move =
            when {
                move == ScreenMove.Gesture && back -> ScreenMove.Commit
                back -> ScreenMove.Back
                else -> ScreenMove.Open
            }
        // the screen on top of the move says how it moves: the one opening, or the one closing
        val mover = if (move == ScreenMove.Open) target else shown
        style = entries[mover]?.let(currentMotion) ?: ScreenMotion.Page
        if (!reduced) {
            other = target
            t.snapTo(0f)
            // the move's clock starts on the frame after the one that composes the coming page: a page that takes long
            // to compose the first time (a cold start, a debug build) would otherwise skip the start of its move
            withFrameNanos { }
            t.animateTo(1f, tween(ScreenMoves.durationMs(move, style), easing = LinearEasing))
        }
        shown = target
        other = null
        g.snapTo(0f)
        startY = null
        dy = 0f
    }

    val background = MaterialTheme.colorScheme.background
    // the theme's background behind the pages: a fade through goes through it, not through the window's colour
    BoxWithConstraints(modifier.fillMaxSize().background(background)) {
        val space =
            MoveSpace(
                width = constraints.maxWidth.toFloat(),
                height = constraints.maxHeight.toFloat(),
                slide = with(density) { ScreenMoves.SLIDE_DP.dp.toPx() },
                edge = with(density) { ScreenMoves.EDGE_DP.dp.toPx() },
                dark = dark,
            )
        val o = other
        val m = move
        val st = style
        val frame = { ScreenMoves.frame(m, st, t.value, space, g.value, dy) }
        // The pages to draw, bottom first, with their look (read while drawing, so a move redraws without composing).
        // A page must keep its composition when it starts or stops moving: drawing it from a different call site per
        // case disposed it and composed it again from scratch.
        val layers: List<Pair<Any, () -> PageLook>> =
            when {
                o == null -> listOf(shown to { PageLook.Rest })
                m == ScreenMove.Open -> listOf(shown to { frame().bottom }, o to { frame().top })
                else -> listOf(o to { frame().bottom }, shown to { frame().top })
            }
        // Two fixed slots: a page takes a free slot when it appears and keeps it until it's gone; the drawing order
        // comes from zIndex. No composition is ever moved (moving a page's group between positions recreated it or,
        // with SubcomposeLayouts inside, hung the app).
        val visible = layers.map { it.first }
        for (i in slots.indices) if (slots[i] !in visible) slots[i] = null
        for (v in visible) if (v !in slots) slots[slots.indexOfFirst { it == null }] = v
        for (i in slots.indices) {
            key(i) {
                val s = slots[i]
                val z = visible.indexOf(s)
                val entry = s?.let(entries::get)
                if (s != null && z >= 0 && entry != null) {
                    key(s) {
                        val look = layers[z].second
                        Box(
                            Modifier
                                .zIndex(z.toFloat())
                                .fillMaxSize()
                                // the scrim covers the whole screen over this page, outside its (scaled, moved) layer
                                .drawWithContent {
                                    drawContent()
                                    val scrim = look().scrim
                                    if (scrim > 0f) drawRect(Color.Black, alpha = scrim)
                                }.graphicsLayer {
                                    val l = look()
                                    translationX = l.translationX
                                    translationY = l.translationY
                                    scaleX = l.scale
                                    scaleY = l.scale
                                    alpha = l.alpha
                                    clip = l.rounded > 0f
                                    shape = RoundedCornerShape(cornerPx * l.rounded)
                                }
                                // opaque: the page moving over another covers it cleanly
                                .background(background),
                        ) {
                            val atRest = o == null && s == top
                            val lifecycle = rememberLifecycleOwner(if (atRest) Lifecycle.State.RESUMED else Lifecycle.State.STARTED)
                            CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) { content(entry) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A page's own Back while [enabled] (a flow's step going back a step, a search clearing itself): composed inside a
 * [ScreenHost] page it comes before the host's, so the system back and the predictive back gesture call [onBack]
 * instead of closing the page (the gesture shows no preview). While it's off, Back closes the page with the host's
 * motion.
 */
@Composable
fun PageBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    val currentOnBack by rememberUpdatedState(onBack)
    NavigationBackHandler(state, isBackEnabled = enabled, onBackCompleted = { currentOnBack() })
}
