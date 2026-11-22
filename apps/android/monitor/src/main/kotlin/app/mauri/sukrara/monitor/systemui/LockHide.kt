// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.systemui

import app.mauri.sukrara.monitor.notify.NotificationIds

/**
 * The glucose notification steps aside on the lock screen while the lock-screen widget shows the same (Settings › Lock
 * screen › *Hide the notification*, docs/17 › In Sukrara). Two halves, both fail-safe (no module, no widget on screen,
 * a failure: the notification shows as always):
 *  - **the app marks** each post that carries nothing the widget can't show or act on ([EXTRA]): no alert or muted note,
 *    no sensor button, no line the card doesn't say ([marks]);
 *  - **the module hides** only a marked post of the glucose notification, only while the widget is on the lock screen
 *    with the screen on ([hides]), through System UI's own lock-screen filter (KeyguardNotificationVisibilityProvider),
 *    so every other notification, the unlocked shade, the always-on display, notification listeners (watch bridges)
 *    and the foreground service are untouched.
 */
object LockHide {
    /** The mark in the notification's extras (true = the widget covers this post). */
    const val EXTRA = "app.mauri.sukrara.lockHide"

    /**
     * The app's mark for a post: [covered] = its content says only what the widget says (the setting on and a state
     * the card shows, `LiveNotificationContent.hideUnderWidget`), [alert] = an alert or a muted note is on it (its ring,
     * the window after it, its buttons, its countdown and *Unmute*).
     */
    fun marks(
        covered: Boolean,
        alert: Boolean,
    ): Boolean = covered && !alert

    /** The glucose notification itself (id, no tag) of [ourPackage]: never any other notification. */
    fun ours(
        packageName: String?,
        id: Int,
        tag: String?,
        ourPackage: String,
    ): Boolean = packageName == ourPackage && id == NotificationIds.GLUCOSE && tag == null

    /**
     * System UI's answer for one notification on the lock screen, with ours: [systemHides] (System UI's own answer) always
     * wins; ours hides only [ours] + [marked], while the widget is [widgetShown] (`LockWidgetHost.shown`), the screen
     * on ([screenOn]: the always-on display keeps it, the widget isn't drawn there) and the phone [locked] (the lock
     * screen, the shade pulled over it, the bouncer; never unlocked).
     */
    fun hides(
        systemHides: Boolean,
        ours: Boolean,
        marked: Boolean,
        widgetShown: Boolean,
        screenOn: Boolean,
        locked: Boolean,
    ): Boolean = systemHides || (ours && marked && widgetShown && screenOn && locked)
}
