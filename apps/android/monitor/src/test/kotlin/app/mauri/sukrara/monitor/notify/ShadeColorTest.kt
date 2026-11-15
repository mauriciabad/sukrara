// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ShadeColor.choose]: the ground System UI gives a notification without a colour of its own, which the glucose
 * notification copies to look uncoloured while colorized (first in the shade). Read from the owner's Pixel 10 Pro
 * (Android 17.1, SystemUIGoogle `ActivatableNotificationView.calculateBgColor`, 2026-11-15): unlocked with blur, the
 * see-through `system_surface_effect_1`; on the lock screen or without blur, `system_surface_container`. AOSP's System UI
 * (no effect colours) draws `system_surface_container_high` everywhere.
 */
class ShadeColorTest {
    private val effect = 0x8a0a1519.toInt()
    private val container = 0xff1a2125.toInt()
    private val high = 0xff202c30.toInt()

    @Test fun `unlocked with blur, the see-through effect colour the other notifications have`() {
        assertEquals(effect, ShadeColor.choose(effect, container, high, locked = false, blur = true))
    }

    @Test fun `on the lock screen the opaque container colour`() {
        assertEquals(container, ShadeColor.choose(effect, container, high, locked = true, blur = true))
        assertEquals(container, ShadeColor.choose(effect, container, high, locked = true, blur = false))
    }

    @Test fun `without blur the opaque container colour`() {
        assertEquals(container, ShadeColor.choose(effect, container, high, locked = false, blur = false))
    }

    @Test fun `a System UI without effect colours, container high everywhere`() {
        for (locked in listOf(true, false)) {
            for (blur in listOf(true, false)) {
                assertEquals(high, ShadeColor.choose(null, container, high, locked, blur))
            }
        }
    }
}
