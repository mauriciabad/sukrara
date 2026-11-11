// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import android.content.Intent
import android.view.MotionEvent
import app.mauri.sukrara.core.data.settings.HookPosition
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.LockAppearance
import app.mauri.sukrara.core.model.LockBackground
import app.mauri.sukrara.core.model.LockLook
import app.mauri.sukrara.core.model.ModuleState
import app.mauri.sukrara.graph.stats.Bands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The lock-screen widget in System UI (#66): its place, when it shows, which touches it gets, and the state it's sent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LockWidgetHostTest {
    /** docs/17's Pixel 10 Pro: 1080 px wide at 2.625, the fingerprint sensor at (441, 1666 – 639, 1864). */
    private val d = 2.625f

    @Test fun `the widget spans the free space around the fingerprint icon, 16 dp from the sides`() {
        val shortcutsTop = 2400 - ((32 + 48) * d).toInt()
        val p = checkNotNull(LockGeometry.place(1080, 441, 1666, 639, 1864, shortcutsTop, d))
        assertEquals(42, p.left)
        assertEquals("from the icon's top", 1666, p.top)
        assertEquals(1080 - 2 * 42, p.width)
        assertEquals("down to 8 dp above the shortcuts", shortcutsTop - 21 - 1666, p.height)
        // the icon in the widget, dp from its top-left, with a 6 dp gap on each side
        assertEquals((441 - 42 - 6 * d) / d, p.holeLeft, 0.01f)
        assertEquals((639 - 42 + 6 * d) / d, p.holeRight, 0.01f)
        assertEquals(198 / d, p.holeBottom, 0.01f)
        // centred: as much room for the value as for the slots
        assertEquals(p.holeLeft, p.width / d - p.holeRight, 0.5f)
    }

    @Test fun `no room above the shortcuts, no widget`() {
        assertNull(LockGeometry.place(1080, 441, 1666, 639, 1864, 1666 + (85 * d).toInt(), d))
    }

    @Test fun `it shows on the idle lock screen only and fades with the transitions`() {
        assertEquals(1f, sceneAlpha(idle = true, onLock = true))
        assertEquals("the shade or the bouncer over it", 0f, sceneAlpha(idle = true, onLock = true, overlay = true))
        assertEquals("unlocked, or another scene", 0f, sceneAlpha(idle = true, onLock = false))
        assertEquals("leaving the lock screen", 0.75f, sceneAlpha(idle = false, from = true, progress = 0.25f))
        assertEquals("coming back to it", 0.25f, sceneAlpha(idle = false, to = true, progress = 0.25f))
        assertEquals("a transition elsewhere", 0f, sceneAlpha(idle = false, progress = 0.5f))
        assertEquals("overshoot is clamped", 0f, sceneAlpha(idle = false, from = true, progress = 1.3f))
    }

    @Test fun `a touch on the widget counts, never in the fingerprint's column`() {
        val m = 24 * d
        // widget 42..1038 × 1666..2169, the icon 441..639
        assertTrue(LockTouches.hits(200f, 1700f, 42, 1666, 1038, 2169, 441, 639, m))
        assertTrue(LockTouches.hits(900f, 1700f, 42, 1666, 1038, 2169, 441, 639, m))
        assertFalse("on the sensor", LockTouches.hits(540f, 1760f, 42, 1666, 1038, 2169, 441, 639, m))
        assertFalse("beside it, in its margin", LockTouches.hits(441f - m + 1, 1700f, 42, 1666, 1038, 2169, 441, 639, m))
        assertFalse("below the icon, the same column", LockTouches.hits(540f, 2100f, 42, 1666, 1038, 2169, 441, 639, m))
        assertFalse("above the widget", LockTouches.hits(200f, 1600f, 42, 1666, 1038, 2169, 441, 639, m))
    }

    @Test fun `only taps reach the widget, a swipe or a long press is cancelled`() {
        val slop = 20f
        val long = 400L
        val tap = LockTouches()
        assertEquals(LockTouches.Do.DELIVER, tap.on(MotionEvent.ACTION_DOWN, 100f, 100f, 0, true, slop, long))
        assertEquals(LockTouches.Do.DELIVER, tap.on(MotionEvent.ACTION_MOVE, 105f, 102f, 50, true, slop, long))
        assertEquals(LockTouches.Do.DELIVER, tap.on(MotionEvent.ACTION_UP, 105f, 102f, 90, true, slop, long))

        val swipe = LockTouches()
        swipe.on(MotionEvent.ACTION_DOWN, 100f, 300f, 0, true, slop, long)
        assertEquals("the unlock swipe", LockTouches.Do.CANCEL, swipe.on(MotionEvent.ACTION_MOVE, 100f, 200f, 40, true, slop, long))
        assertEquals(
            "nothing more after the cancel",
            LockTouches.Do.NOTHING,
            swipe.on(MotionEvent.ACTION_UP, 100f, 100f, 80, true, slop, long),
        )

        val held = LockTouches()
        held.on(MotionEvent.ACTION_DOWN, 100f, 100f, 0, true, slop, long)
        assertEquals("a finger resting on it", LockTouches.Do.CANCEL, held.on(MotionEvent.ACTION_UP, 100f, 100f, 700, true, slop, long))

        val second = LockTouches()
        second.on(MotionEvent.ACTION_DOWN, 100f, 100f, 0, true, slop, long)
        assertEquals("a second finger", LockTouches.Do.CANCEL, second.on(MotionEvent.ACTION_POINTER_DOWN, 300f, 100f, 30, true, slop, long))

        val elsewhere = LockTouches()
        assertEquals(
            "a touch that starts off the widget",
            LockTouches.Do.NOTHING,
            elsewhere.on(MotionEvent.ACTION_DOWN, 1f, 1f, 0, false, slop, long),
        )
        assertEquals(LockTouches.Do.NOTHING, elsewhere.on(MotionEvent.ACTION_UP, 1f, 1f, 50, false, slop, long))
    }

    @Test fun `the widget's settings reach System UI, an unknown look is the default`() {
        val p = RootPrefs(on = false, position = HookPosition.DEFAULT, color = true, hideIcon = true)
        val look = LockLook(LockBackground.SHADOW, LockAppearance.LIGHT)
        val sent = RootStatusBar.hookState(GlucoseState.Initial, p, Bands(), GlucoseUnit.MGDL, LockPrefs(on = true, look = look))
        assertTrue(sent.lockWidget)
        assertFalse("the status-bar value stays off", sent.enabled)
        val got = HookState.from(Intent(SystemUi.actionState("app")).putExtras(sent.toBundle()))
        assertEquals(sent, got)
        assertEquals(look, got.lockLook)
        val odd =
            HookState.from(
                Intent(
                    SystemUi.actionState("app"),
                ).putExtras(sent.toBundle()).putExtra("lockBackground", "blur").putExtra("lockAppearance", "x"),
            )
        assertEquals(LockLook(), odd.lockLook)
        assertFalse("nothing said: off", HookState.from(Intent(SystemUi.actionState("app"))).lockWidget)
    }

    @Test fun `card and frosted glass keep the content off their edge, light and dark follow the setting`() {
        assertTrue(LockBackground.CARD.boxed)
        assertTrue(LockBackground.FROSTED.boxed)
        assertFalse(LockBackground.SHADOW.boxed)
        assertTrue(LockAppearance.AUTO.dark(systemNight = true))
        assertFalse(LockAppearance.AUTO.dark(systemNight = false))
        assertFalse(LockAppearance.LIGHT.dark(systemNight = true))
        assertTrue(LockAppearance.DARK.dark(systemNight = false))
    }

    @Test fun `a phone without LSPosed gets no broadcasts for the widget that is on by default`() {
        val gate = StateGate()
        val lock = HookState(lockWidget = true)
        assertNull(gate.next(lock, moduleMayRun = false))
        assertNull(gate.next(lock.copy(mgdl = 120, atS = 1), moduleMayRun = false))
        assertFalse(StateGate.mayRun(ModuleState.Missing))
        assertFalse(StateGate.mayRun(ModuleState.NoScope))
        assertFalse(StateGate.mayRun(ModuleState.Checking))
        assertTrue(StateGate.mayRun(ModuleState.Restart))
        assertTrue(StateGate.mayRun(ModuleState.Active))
        assertTrue(StateGate.mayRun(ModuleState.Update))
    }

    @Test fun `the widget alone sends its settings when they change, the first one asks for the hello`() {
        val gate = StateGate()
        val lock = HookState(lockWidget = true)
        assertEquals(HookState(lockWidget = true) to true, gate.next(lock.copy(mgdl = 120, atS = 1), moduleMayRun = true))
        // a new reading or a tick: the widget's settings didn't change, nothing goes out
        assertNull(gate.next(lock.copy(mgdl = 130, atS = 2), moduleMayRun = true))
        val dark = lock.copy(lockLook = LockLook(appearance = LockAppearance.DARK))
        assertEquals(dark to false, gate.next(dark, moduleMayRun = true))
        // turned off: one last state, so the module lets go; then nothing
        assertEquals(HookState(lockWidget = false, lockLook = dark.lockLook) to false, gate.next(dark.copy(lockWidget = false), true))
        assertNull(gate.next(dark.copy(lockWidget = false), moduleMayRun = true))
        // System UI restarted (its hello): it has nothing, so the state goes again
        gate.next(lock, moduleMayRun = true)
        assertNull(gate.next(lock, moduleMayRun = true))
        gate.reset()
        assertEquals(lock to false, gate.next(lock, moduleMayRun = true))
    }

    @Test fun `the status-bar value goes on every change and tick, and once more when it turns off`() {
        val gate = StateGate()
        val bar = HookState(enabled = true, mgdl = 120, atS = 1)
        assertEquals(bar to true, gate.next(bar, moduleMayRun = false))
        assertEquals(bar to false, gate.next(bar, moduleMayRun = false))
        assertEquals(HookState() to false, gate.next(bar.copy(enabled = false), moduleMayRun = false))
        assertNull(gate.next(bar.copy(enabled = false), moduleMayRun = false))
    }

    @Test fun `turning the widget off goes out even when LSPosed can't be asked`() {
        val gate = StateGate()
        assertNull(gate.next(HookState(lockWidget = true), moduleMayRun = false))
        assertEquals(HookState() to true, gate.next(HookState(lockWidget = false), moduleMayRun = false))
    }
}
