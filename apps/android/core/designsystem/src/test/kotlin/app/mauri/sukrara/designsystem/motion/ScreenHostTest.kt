// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.motion

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The screen host: pages are composed once per visit (composing one again when it starts or stops moving drew its
 * first frame from scratch, the PoC's 0.11.14 flash), each kind of screen moves for its own time, reduced motion
 * swaps pages at once, Back pops the top page after the page's own handler, and the back gesture shows the page under
 * the top one until it's released or cancelled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val stack = mutableStateListOf("home")
    private val alive = mutableListOf<String>()
    private val created = mutableListOf<String>()
    private val disposed = mutableListOf<String>()

    /** FadeThrough for "setup", Page for the rest; [page] draws inside each page. */
    private fun host(
        reduced: Boolean = false,
        page: @Composable (String) -> Unit = {},
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides reduced) {
                ScreenHost(
                    stack = stack.toList(),
                    key = { it },
                    motion = { if (it == "setup") ScreenMotion.FadeThrough else ScreenMotion.Page },
                    onBack = { stack.removeAt(stack.lastIndex) },
                ) { s ->
                    DisposableEffect(Unit) {
                        created += s
                        alive += s
                        onDispose {
                            disposed += s
                            alive -= s
                        }
                    }
                    page(s)
                }
            }
        }
    }

    private fun frames(n: Int) =
        repeat(n) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

    private fun settle() = frames(60)

    @Test fun pagesAreComposedOncePerVisit() {
        host()
        settle()
        assertEquals(listOf("home"), created)

        compose.runOnIdle { stack += "settings" }
        frames(6)
        // mid-move: both pages drawn, home not composed again
        assertEquals(listOf("home", "settings"), created)
        settle()
        // at rest on settings: settings kept, home gone
        assertEquals(listOf("home", "settings"), created)
        assertEquals(listOf("home"), disposed)

        compose.runOnIdle { stack.removeAt(stack.lastIndex) }
        settle()
        assertEquals(listOf("home", "settings", "home"), created)
        assertEquals(listOf("home", "settings"), disposed)
    }

    /** Back while a page is still coming in: that page animates back out (it doesn't vanish), the host ends on home. */
    @Test fun backDuringOpen() {
        host()
        frames(5)
        compose.runOnIdle { stack += "settings" }
        frames(8)
        compose.runOnIdle { stack.removeAt(stack.lastIndex) }
        frames(3)
        assertEquals(setOf("home", "settings"), alive.toSet())
        settle()
        assertEquals(listOf("home"), alive)
    }

    /** A page moves for 450 ms (the Settings app's activity motion), setup fades through in 300 ms. */
    @Test fun eachKindMovesForItsOwnTime() {
        host()
        settle()
        compose.runOnIdle { stack += "settings" }
        frames(24)
        assertEquals("a page is still sliding after 384 ms", listOf("home", "settings"), alive)
        settle()
        assertEquals(listOf("settings"), alive)

        compose.runOnIdle { stack += "setup" }
        frames(26)
        assertEquals("setup has faded through by 416 ms", listOf("setup"), alive)
    }

    /** *Remove animations*: the new page replaces the old one at once, never both on screen. */
    @Test fun reducedMotionSwapsAtOnce() {
        host(reduced = true)
        settle()
        var most = 0
        val watch = {
            repeat(3) {
                frames(1)
                most = maxOf(most, alive.size)
            }
        }
        compose.runOnIdle { stack += "settings" }
        watch()
        assertEquals(listOf("settings"), alive)
        compose.runOnIdle { stack.removeAt(stack.lastIndex) }
        watch()
        assertEquals(listOf("home"), alive)
        compose.runOnIdle { stack += "setup" }
        watch()
        assertEquals(listOf("setup"), alive)
        assertEquals(1, most)
    }

    /** A page is RESUMED only at rest on top; while it moves it is STARTED (no resume work mid-move). */
    @Test fun lifecycleResumesOnlyAtRest() {
        val states = mutableMapOf<String, Lifecycle.State>()
        host { s ->
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val observer = LifecycleEventObserver { o, _ -> states[s] = o.lifecycle.currentState }
                owner.lifecycle.addObserver(observer)
                onDispose { owner.lifecycle.removeObserver(observer) }
            }
        }
        settle()
        assertEquals(Lifecycle.State.RESUMED, states["home"])
        compose.runOnIdle { stack += "settings" }
        frames(6)
        assertEquals(Lifecycle.State.STARTED, states["home"])
        assertEquals(Lifecycle.State.STARTED, states["settings"])
        settle()
        assertEquals(Lifecycle.State.RESUMED, states["settings"])
    }

    /** System Back pops the top page; a page's own back handler comes first; on the last page it's the system's. */
    @Test fun backPopsAfterThePagesOwnHandler() {
        var ownBack = 0
        host { s ->
            if (s == "search") {
                val state = rememberNavigationEventState(NavigationEventInfo.None)
                NavigationBackHandler(state, isBackEnabled = true, onBackCompleted = { ownBack++ })
            }
        }
        settle()
        compose.runOnIdle { stack += listOf("settings", "search") }
        settle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertEquals(1, ownBack)
        assertEquals(listOf("home", "settings", "search"), stack.toList())

        compose.runOnIdle { stack.removeAt(stack.lastIndex) }
        settle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertEquals(listOf("home"), stack.toList())
        assertEquals(listOf("home"), alive)
    }

    /** The back gesture draws the page under the top one; cancelling drops it again, releasing pops the top page. */
    @Test fun backGesturePreviewsTheParent() {
        host()
        settle()
        compose.runOnIdle { stack += "settings" }
        settle()
        val back = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            back.dispatchOnBackStarted(BackEventCompat(10f, 1200f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(200f, 1250f, 0.4f, BackEventCompat.EDGE_LEFT))
        }
        frames(4)
        assertTrue("home shows under settings during the gesture", "home" in alive)
        assertEquals(listOf("home", "settings"), stack.toList())
        compose.runOnIdle { back.dispatchOnBackCancelled() }
        settle()
        assertEquals(listOf("settings"), alive)

        compose.runOnIdle {
            back.dispatchOnBackStarted(BackEventCompat(10f, 1200f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(300f, 1200f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        frames(4)
        val homeVisits = created.count { it == "home" }
        compose.runOnIdle { back.onBackPressed() }
        settle()
        assertEquals(listOf("home"), stack.toList())
        assertEquals(listOf("home"), alive)
        // the home page shown during the gesture is the one that stays: not composed again on release
        assertEquals(homeVisits, created.count { it == "home" })
    }

    /** A gesture started while a page is still opening takes that page (it doesn't draw a page twice or lose one). */
    @Test fun backGestureDuringAnOpen() {
        host()
        settle()
        compose.runOnIdle { stack += "settings" }
        frames(6)
        val back = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            back.dispatchOnBackStarted(BackEventCompat(10f, 1200f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(200f, 1200f, 0.3f, BackEventCompat.EDGE_LEFT))
        }
        frames(4)
        assertEquals(setOf("home", "settings"), alive.toSet())
        compose.runOnIdle { back.dispatchOnBackCancelled() }
        settle()
        assertEquals(listOf("settings"), alive)
    }

    /** *Remove animations*: the gesture shows nothing under the page; releasing it just goes back. */
    @Test fun reducedMotionGestureShowsNoPreview() {
        host(reduced = true)
        settle()
        compose.runOnIdle { stack += "settings" }
        settle()
        val back = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            back.dispatchOnBackStarted(BackEventCompat(10f, 1200f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(300f, 1200f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        frames(4)
        assertEquals(listOf("settings"), alive)
        compose.runOnIdle { back.onBackPressed() }
        frames(3)
        assertEquals(listOf("home"), stack.toList())
        assertEquals(listOf("home"), alive)
    }
}
