// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.notify

import android.app.KeyguardManager
import android.content.Context
import android.view.WindowManager

/**
 * The ground System UI gives a notification without a colour of its own, where the glucose notification shows now:
 * colorized in it (to stay first in the shade), the glucose notification looks uncoloured. Read from the owner's Pixel
 * (Android 17.1, SystemUIGoogle `ActivatableNotificationView.calculateBgColor`, 2026-11-15):
 * - unlocked with blur: `customColorSurfaceEffect1` = the framework's `system_surface_effect_1_dark|light`, see-through
 *   over the blurred shade (`0x8a0a1519` there), from the wallpaper's palette like every `system_` colour;
 * - on the lock screen, or without blur: `materialColorSurfaceContainer` (opaque);
 * - a System UI without the effect colours (AOSP): `materialColorSurfaceContainerHigh` everywhere.
 * The effect colours aren't in the SDK: read by name, absent elsewhere. The service posts again when the phone locks or
 * unlocks and when blur turns on or off, so the colour follows ([MonitoringService][app.mauri.sukrara.monitor.MonitoringService]).
 */
object ShadeColor {
    /** [effect]: the see-through ground with blur, null where System UI has none. */
    internal fun choose(
        effect: Int?,
        container: Int,
        containerHigh: Int,
        locked: Boolean,
        blur: Boolean,
    ): Int =
        when {
            effect == null -> containerHigh
            !locked && blur -> effect
            else -> container
        }

    /** The ground for the theme ([dark]), the lock state and the blur now. */
    fun of(
        context: Context,
        dark: Boolean,
    ): Int {
        val res = context.resources
        val effectId = res.getIdentifier(if (dark) "system_surface_effect_1_dark" else "system_surface_effect_1_light", "color", "android")
        val effect = effectId.takeIf { it != 0 }?.let { runCatching { context.getColor(it) }.getOrNull() }
        val container =
            context.getColor(if (dark) android.R.color.system_surface_container_dark else android.R.color.system_surface_container_light)
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val blur = context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        return choose(effect, container, NotificationCard.surface(context, dark), locked, blur)
    }
}
